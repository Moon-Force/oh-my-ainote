# 编译指南

本文说明如何从源码生成 oh-my-ainote 的 debug APK、release APK，以及如何运行仓库要求的最小自动测试。

## 环境要求

| 工具 | 要求 |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | 36 |
| Android SDK Build Tools | 36.0.0 |
| Gradle | 使用仓库自带 Wrapper 8.11.1，无需单独安装 |
| Git | 任意仍受支持的版本 |

工程的 `minSdk=29`、`targetSdk=36`、`compileSdk=36`。首次构建需要联网从 Google Maven 和 Maven Central 下载依赖。

## 获取源码

```bash
git clone https://github.com/Moon-Force/oh-my-ainote.git
cd oh-my-ainote
```

当前实现尚未合入 `main` 时，切换到实现分支：

```bash
git switch codex/implement-design
```

## 安装 Android SDK 组件

如果使用 Android Studio，在 SDK Manager 中安装：

- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0
- Android SDK Platform-Tools
- Android SDK Command-line Tools（latest）

也可以使用 `sdkmanager`。

Windows PowerShell：

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" `
  "platforms;android-36" "build-tools;36.0.0" "platform-tools"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

macOS/Linux：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-36" "build-tools;36.0.0" "platform-tools"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
```

如果 SDK 不在默认位置，在仓库根目录创建不入库的 `local.properties`：

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
```

macOS/Linux 示例：

```properties
sdk.dir=/home/your-name/Android/Sdk
```

## 检查 Java 与 Gradle

Windows PowerShell：

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"
.\gradlew.bat --version
```

macOS/Linux：

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew --version
```

输出中的 JVM 应为 17。不要把系统安装的 Gradle 命令替代仓库 Wrapper。

### 使用 Android Studio

用 Android Studio 打开仓库根目录，在 Gradle Settings 中选择 JDK 17，等待 Sync 完成。`Build > Build APK(s)` 可生成 debug APK；仓库要求的 JVM 测试仍建议使用下文 Wrapper 命令运行，以便与 CI 一致。

## 构建 debug APK

仓库要求的最小验证命令同时运行纯 JVM 测试并打包应用。

Windows：

```powershell
.\gradlew.bat :document:test :ai-api:test :app:assembleDebug
```

macOS/Linux：

```bash
./gradlew :document:test :ai-api:test :app:assembleDebug
```

成功后 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

连接已开启 USB 调试的 Android 设备后，可安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

debug APK 体积不作为发布门禁。安装后仍需按 [`MANUAL_TEST.md`](MANUAL_TEST.md) 在 USI 平板验收墨水与 PDF。

## 构建 release APK

### 未签名本地包

未设置签名环境变量时运行：

Windows：

```powershell
.\gradlew.bat :app:assembleRelease
```

macOS/Linux：

```bash
./gradlew :app:assembleRelease
```

输出为：

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

该命令会执行 R8、资源压缩和 release lint，适合检查发布构建，但未签名 APK 不能作为正式发布包。

### 本地签名包

首次发布可用 JDK 的 `keytool` 创建 keystore；文件和密码不得提交到仓库：

```bash
keytool -genkeypair -v -keystore release.jks -alias oh-my-ainote \
  -keyalg RSA -keysize 4096 -validity 10000
```

Windows PowerShell：

```powershell
$env:SIGNING_STORE_FILE = "D:\secure\release.jks"
$env:SIGNING_STORE_PASSWORD = "your-store-password"
$env:SIGNING_KEY_ALIAS = "oh-my-ainote"
$env:SIGNING_KEY_PASSWORD = "your-key-password"
.\gradlew.bat :document:test :ai-api:test :app:assembleRelease
```

macOS/Linux：

```bash
export SIGNING_STORE_FILE=/secure/release.jks
export SIGNING_STORE_PASSWORD='your-store-password'
export SIGNING_KEY_ALIAS='oh-my-ainote'
export SIGNING_KEY_PASSWORD='your-key-password'
./gradlew :document:test :ai-api:test :app:assembleRelease
```

签名成功后输出为：

```text
app/build/outputs/apk/release/app-release.apk
```

不要把真实密码写进 shell 历史、`local.properties`、Gradle 文件或 Issue。

## GitHub tag 发布

推送 `v*` tag 会触发 `.github/workflows/release.yml`。仓库需要配置以下 Actions secrets：

| Secret | 内容 |
| --- | --- |
| `SIGNING_KEY_BASE64` | `release.jks` 的 Base64 内容 |
| `SIGNING_STORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key 密码 |

Linux/macOS 生成单行 Base64：

```bash
base64 < release.jks | tr -d '\n'
```

PowerShell 生成 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\secure\release.jks"))
```

配置 secrets 后创建并推送 tag：

```bash
git tag v0.1.0
git push origin v0.1.0
```

workflow 会运行必要测试、构建签名 APK，并创建 GitHub Release。tag 版本应与 `app/build.gradle.kts` 中的 `versionName` 和 `versionCode` 对应。

## CI 等价检查

本地提交前至少执行：

```bash
./gradlew :document:test :ai-api:test :app:assembleDebug
```

依赖红线检查：

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

输出不得包含 iText；PDF 导出固定使用 PdfBox-Android。CI 配置见 `.github/workflows/ci.yml`。

## 常见问题

### `SDK location not found`

设置 `ANDROID_HOME`，或创建包含正确 `sdk.dir` 的 `local.properties`。

### `android-36` / Build Tools 缺失

重新执行本文的 `sdkmanager` 命令，并确认已接受 Android SDK licenses。

### Java 或 Kotlin 编译版本错误

确认 `JAVA_HOME` 指向 JDK 17，并用 `gradlew`/`gradlew.bat`，不要使用系统 Gradle。

### Windows 中文路径下 Test Worker 找不到测试类

这是部分 Gradle/JUnit Worker 环境的路径问题。可以临时映射短盘符：

```powershell
subst R: "$PWD"
R:\gradlew.bat :document:test :ai-api:test :app:assembleDebug
subst R: /d
```

映射只改变构建时路径，不修改源码或 APK。

### 首次构建下载超时

确认可以访问 Google Maven、Maven Central 和 Gradle distribution。修复网络后直接重跑 Wrapper；不要手工复制不明来源的 AAR/JAR 到仓库。

### Release 构建提示 JPEG2000 类缺失

仓库的 R8 规则已经忽略 PdfBox 对可选 Gemalto JP2 库的静态引用，因为 v1 在 PDF 导入阶段明确拒绝 JPX/JPEG2000。不要为消除提示而加入未经设计审核的备用解码库。

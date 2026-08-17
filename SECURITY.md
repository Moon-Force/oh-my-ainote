# Security Policy

## Reporting

请不要在公开 Issue 中粘贴 API Key、完整 AI 请求、笔记图片或 `.ainote` 文件。安全问题请通过 GitHub Security Advisory 私下报告。

## Secrets

- 仓库、示例、测试、日志和崩溃报告不得包含真实 API Key。
- Android 端使用 Keystore AES-256-GCM 密钥加密保存 BYOK Key。
- Release 签名材料只放在维护者本机或 GitHub Actions secrets。
- 若密钥曾被提交或公开，应立即在服务商处吊销并轮换；仅从 Git 历史删除并不安全。

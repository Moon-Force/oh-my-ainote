package com.moonforce.ohmyainote.ui.settings

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var pressure by remember { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }
    val activity = LocalView.current.context as? Activity
    DisposableEffect(activity) {
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LaunchedEffect(state.baseUrl, state.model, state.exportPressureVarying) {
        if (!initialized || (baseUrl.isBlank() && model.isBlank())) {
            baseUrl = state.baseUrl
            model = state.model
            pressure = state.exportPressureVarying
            initialized = true
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            apiKey = ""
            viewModel.consumeSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("OpenAI 兼容 BYOK", style = MaterialTheme.typography.titleLarge)
            Text("只有主动框选的 JPEG、问题和 API Key 会发送到这里。首次请求前还会显示完整地址并再次确认。")
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                supportingText = { Text("允许任意 HTTPS 或 HTTP 地址；HTTP 会明文传输密钥与图片。") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(if (state.hasApiKey) "API Key（留空保持原值）" else "API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                    if (focus.isFocused) {
                        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("导出钢笔按压感变宽", modifier = Modifier.weight(1f))
                Switch(checked = pressure, onCheckedChange = { pressure = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = baseUrl.isNotBlank() && model.isNotBlank() && (apiKey.isNotBlank() || state.hasApiKey),
                    onClick = { viewModel.save(baseUrl, model, apiKey, pressure) },
                ) { Text("保存") }
                OutlinedButton(enabled = state.hasApiKey, onClick = viewModel::clearKey) { Text("清除 API Key") }
            }
        }
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } },
            title = { Text("保存失败") },
            text = { Text(message) },
        )
    }
}

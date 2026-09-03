package com.searchhub.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searchhub.app.data.ProxyConfig
import com.searchhub.app.data.SiteConfig
import com.searchhub.app.data.SiteDefaults
import com.searchhub.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    model: AppViewModel,
    onBack: () -> Unit,
) {
    val sites by model.sites.collectAsStateWithLifecycle()
    val proxy by model.proxy.collectAsStateWithLifecycle()

    var draft by remember(sites) { mutableStateOf(sites.map { it.copy() }) }
    var proxyEnabled by remember(proxy) { mutableStateOf(proxy.enabled) }
    var proxyHost by remember(proxy) { mutableStateOf(proxy.host) }
    var proxyPort by remember(proxy) { mutableStateOf(proxy.port.toString()) }
    var saved by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf<String?>(null) }

    fun apply(): Boolean {
        val parsedPort = proxyPort.toIntOrNull()
        // long: 代理端口会直接进入网络引擎，保存前阻止无效值，避免静默回退导致连接到错误端口。
        if (proxyEnabled && (parsedPort == null || parsedPort !in 1..65535)) {
            portError = "请输入 1-65535 之间的端口"
            return false
        }
        model.saveSites(draft)
        model.saveProxy(ProxyConfig(proxyEnabled, proxyHost.trim(), parsedPort ?: 7890))
        portError = null
        saved = true
        return true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("连接设置", style = MaterialTheme.typography.titleLarge)
                        Text("管理索引与网络入口", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (apply()) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "保存并返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (saved) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("设置已保存", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    } else {
                        Text("修改会在保存后生效", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        draft = SiteDefaults.DEFAULT_SITES.map { it.copy() }
                        proxyEnabled = false
                        proxyHost = "127.0.0.1"
                        proxyPort = "7890"
                        portError = null
                        saved = false
                    }) { Text("恢复默认") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (apply()) onBack() }) { Text("保存") }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("公开索引", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("启用的站点会参与每次搜索", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            SectionTitle(title = "站点来源", detail = "修改域名或暂时停用站点")
            draft.forEachIndexed { idx, site ->
                SiteEditCard(
                    position = idx + 1,
                    site = site,
                    onEnabled = { enabled ->
                        saved = false
                        draft = draft.toMutableList().also { it[idx] = it[idx].copy(enabled = enabled) }
                    },
                    onBaseUrl = { value ->
                        saved = false
                        draft = draft.toMutableList().also { it[idx] = it[idx].copy(baseUrl = value) }
                    },
                )
            }
            SectionTitle(title = "网络代理", detail = "可选，适用于需要代理访问的网络")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(9.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("HTTP 代理", style = MaterialTheme.typography.titleMedium)
                            Text(if (proxyEnabled) "搜索请求将经过代理" else "当前使用设备直连", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = proxyEnabled,
                            onCheckedChange = { proxyEnabled = it; saved = false },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
                        )
                    }
                    if (proxyEnabled) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it; saved = false }, label = { Text("代理主机") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(9.dp))
                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it.filter(Char::isDigit); portError = null; saved = false },
                            label = { Text("端口") },
                            singleLine = true,
                            isError = portError != null,
                            supportingText = { Text(portError ?: "常见端口 7890") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Column(Modifier.padding(start = 3.dp, top = 7.dp, bottom = 1.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SiteEditCard(
    position: Int,
    site: SiteConfig,
    onEnabled: (Boolean) -> Unit,
    onBaseUrl: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (site.enabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.size(28.dp)) {
                    Text(position.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = if (site.enabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(site.name, style = MaterialTheme.typography.titleMedium)
                    Text(site.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = site.enabled, onCheckedChange = onEnabled)
            }
            if (site.enabled) {
                Spacer(Modifier.height(11.dp))
                OutlinedTextField(value = site.baseUrl, onValueChange = onBaseUrl, label = { Text("站点域名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(5.dp))
                Text("搜索路径  ${site.searchPath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            } else {
                Spacer(Modifier.height(8.dp))
                Text("已停用，不会参与搜索", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
    }
}

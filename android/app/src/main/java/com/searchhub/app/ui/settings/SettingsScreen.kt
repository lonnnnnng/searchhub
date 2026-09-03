package com.searchhub.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searchhub.app.data.ProxyConfig
import com.searchhub.app.data.SiteConfig
import com.searchhub.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    model: AppViewModel,
    onBack: () -> Unit,
) {
    val sites by model.sites.collectAsStateWithLifecycle()
    val proxy by model.proxy.collectAsStateWithLifecycle()

    // 可编辑副本
    var draft by remember(sites) { mutableStateOf(sites.map { it.copy() }) }
    var proxyEnabled by remember(proxy) { mutableStateOf(proxy.enabled) }
    var proxyHost by remember(proxy) { mutableStateOf(proxy.host) }
    var proxyPort by remember(proxy) { mutableStateOf(proxy.port.toString()) }
    var saved by remember { mutableStateOf(false) }

    fun apply() {
        model.saveSites(draft)
        model.saveProxy(ProxyConfig(proxyEnabled, proxyHost, proxyPort.toIntOrNull() ?: 7890))
        saved = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = {
                        apply()
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "保存并返回") }
                },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(onClick = { apply(); onBack() }, modifier = Modifier.weight(1f)) {
                    Text("保存")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    draft = com.searchhub.app.data.SiteDefaults.DEFAULT_SITES.map { it.copy() }
                    proxyEnabled = false; proxyHost = "127.0.0.1"; proxyPort = "7890"
                }) { Text("恢复默认") }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (saved) {
                Text("已保存 ✓", color = MaterialTheme.colorScheme.primary)
            }
            Text("站点配置(可修改域名)", style = MaterialTheme.typography.titleMedium)
            draft.forEachIndexed { idx, site ->
                SiteEditCard(
                    site = site,
                    onEnabled = { en ->
                        draft = draft.toMutableList().also { it[idx] = it[idx].copy(enabled = en) }
                    },
                    onBaseUrl = { v ->
                        draft = draft.toMutableList().also { it[idx] = it[idx].copy(baseUrl = v) }
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("网络代理(可选,默认关闭)", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("启用 HTTP 代理")
                        Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
                    }
                    if (proxyEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it },
                            label = { Text("代理主机") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it },
                            label = { Text("端口") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("提示: 若设备启用了 Clash 等,代理地址一般为 127.0.0.1,端口视软件而定(常见 7890)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SiteEditCard(
    site: SiteConfig,
    onEnabled: (Boolean) -> Unit,
    onBaseUrl: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(site.name, style = MaterialTheme.typography.titleSmall)
                Switch(checked = site.enabled, onCheckedChange = onEnabled)
            }
            Spacer(Modifier.height(4.dp))
            Text("ID: ${site.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            if (!site.enabled) {
                Spacer(Modifier.height(4.dp))
                Text("已停用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.background(Color(0x22FF0000)).padding(4.dp))
            }
            if (site.enabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = site.baseUrl,
                    onValueChange = onBaseUrl,
                    label = { Text("域名") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text("搜索模板: ${site.searchPath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
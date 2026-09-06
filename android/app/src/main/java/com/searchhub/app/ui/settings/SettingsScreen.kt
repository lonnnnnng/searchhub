package com.searchhub.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searchhub.app.data.SiteConfig
import com.searchhub.app.data.SiteDefaults
import com.searchhub.app.data.UpdateManager
import com.searchhub.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.io.File

// 参考"追剧"清爽绿白风
private val TitaGreen = Color(0xFF1E9C5A)

/** 在线更新流程状态 */
private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: UpdateManager.LatestRelease) : UpdateUiState
    data object Downloading : UpdateUiState
    data object Ready : UpdateUiState
    data object Error : UpdateUiState
}

/** 设置主页: 一级菜单(站点来源 / 检测更新 / 清除缓存) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    model: AppViewModel,
    onBack: () -> Unit,
    onOpenSites: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(model.engine) }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var pendingRelease by remember { mutableStateOf<UpdateManager.LatestRelease?>(null) }
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
    val currentVersion = remember { UpdateManager.currentVersion(context) }

    // 缓存大小: 进入页面时计算, 清除后归零
    var cacheSize by remember { mutableLongStateOf(0L) }
    var cacheCleared by remember { mutableStateOf(false) }
    LaunchedEffect(cacheCleared) {
        if (!cacheCleared) cacheSize = dirSize(context.cacheDir) + dirSize(context.externalCacheDir)
    }

    fun checkUpdate() {
        scope.launch {
            updateState = UpdateUiState.Checking
            val latest = updateManager.fetchLatest()
            updateState = when {
                latest == null -> UpdateUiState.Error
                UpdateManager.isNewer(latest.versionName, currentVersion) -> {
                    pendingRelease = latest
                    UpdateUiState.Available(latest)
                }
                else -> UpdateUiState.UpToDate
            }
        }
    }

    fun downloadAndInstall() {
        val release = pendingRelease ?: return
        scope.launch {
            updateState = UpdateUiState.Downloading
            updateProgress = 0
            val file = updateManager.downloadApk(release, context) { p -> updateProgress = (p * 100).toInt() }
            if (file == null) {
                updateState = UpdateUiState.Error
            } else {
                downloadedApk = file
                updateState = UpdateUiState.Ready
                // 有"安装未知应用"授权时直接拉起安装器; 否则跳授权页, 回来后再点"安装"
                updateManager.installApk(context, file)
            }
        }
    }

    fun clearCache() {
        context.cacheDir.deleteRecursively()
        context.externalCacheDir?.deleteRecursively()
        cacheCleared = true
        cacheSize = 0
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            // 站点来源
            MenuRow(
                icon = Icons.Default.Tune,
                title = "站点来源",
                subtitle = "已启用 ${model.sites.collectAsStateWithLifecycle().value.count { it.enabled }} / ${model.sites.collectAsStateWithLifecycle().value.size}",
                onClick = onOpenSites,
            )
            MenuDivider()
            // 检测更新
            MenuRow(
                icon = Icons.Default.SystemUpdate,
                title = "检测更新",
                subtitle = updateSubtitle(updateState, pendingRelease, currentVersion),
                progress = if (updateState is UpdateUiState.Downloading) updateProgress else null,
                onClick = {
                    when (updateState) {
                        UpdateUiState.Idle, UpdateUiState.UpToDate, UpdateUiState.Error -> checkUpdate()
                        is UpdateUiState.Available -> downloadAndInstall()
                        UpdateUiState.Ready -> downloadedApk?.let { updateManager.installApk(context, it) }
                        else -> {}
                    }
                },
                actionContent = when (updateState) {
                    UpdateUiState.Checking -> ({ CircularProgressIndicator(color = TitaGreen, modifier = Modifier.size(18.dp), strokeWidth = 2.dp) })
                    UpdateUiState.Downloading -> ({ Text("$updateProgress%", style = MaterialTheme.typography.labelMedium, color = TitaGreen) })
                    UpdateUiState.Ready -> ({ PillButton("安装") })
                    is UpdateUiState.Available -> ({ PillButton("升级") })
                    else -> null
                },
            )
            MenuDivider()
            // 清除缓存
            MenuRow(
                icon = Icons.Default.DeleteSweep,
                title = "清除缓存",
                subtitle = if (cacheCleared) "已清除" else formatSize(cacheSize),
                onClick = { if (cacheSize > 0L) clearCache() },
            )
        }
    }
}

/** 检测更新副标题 */
private fun updateSubtitle(
    state: UpdateUiState,
    release: UpdateManager.LatestRelease?,
    currentVersion: String,
): String = when (state) {
    UpdateUiState.Idle -> "当前版本 v$currentVersion · 点按检测"
    UpdateUiState.Checking -> "正在检测…"
    UpdateUiState.UpToDate -> "已是最新版本 v$currentVersion"
    is UpdateUiState.Available -> "发现新版本 v${release?.versionName} · ${release?.apkSize?.let { String.format("%.1fMB", it / 1024f / 1024f) }}，点按下载"
    UpdateUiState.Downloading -> "正在下载更新…"
    UpdateUiState.Ready -> "下载完成，点按安装"
    UpdateUiState.Error -> "检测/下载失败，点按重试"
}

/** 设置主页菜单行: 图标 + 标题/副标题 + 右侧动作 */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    progress: Int? = null,
    actionContent: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = TitaGreen, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (actionContent != null) {
                actionContent()
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFC0C0C0), modifier = Modifier.size(20.dp))
            }
        }
        // 下载进度条
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = TitaGreen,
                trackColor = Color(0xFFEFF3F0),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PillButton(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(TitaGreen, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** 目录大小(字节); 递归统计文件 */
private fun dirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0
    return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1fMB", bytes / 1024f / 1024f)
    bytes >= 1024 -> String.format("%.1fKB", bytes / 1024f)
    bytes > 0 -> "${bytes}B"
    else -> "0B"
}

/** 站点来源二级页: 站点域名编辑 + 启停 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(
    model: AppViewModel,
    onBack: () -> Unit,
) {
    val sites by model.sites.collectAsStateWithLifecycle()

    var draft by remember(sites) { mutableStateOf(sites.map { it.copy() }) }
    var saved by remember { mutableStateOf(false) }

    fun apply() {
        model.saveSites(draft)
        saved = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("站点来源", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { apply(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "保存并返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        saved = false
                    }) { Text("恢复默认") }
                    Spacer(Modifier.width(5.dp))
                    Button(
                        onClick = { apply(); onBack() },
                        colors = ButtonDefaults.buttonColors(containerColor = TitaGreen),
                        shape = RoundedCornerShape(20.dp),
                    ) { Text("保存") }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            SectionHeader(title = "站点来源", trailing = "已启用 ${draft.count { it.enabled }} / ${draft.size}")
            draft.forEachIndexed { idx, site ->
                SiteRow(
                    position = idx + 1,
                    site = site,
                    isLast = idx == draft.lastIndex,
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
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(6.dp))
        if (trailing.isNotBlank()) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = TitaGreen)
        }
    }
}

/** 站点单行: 编号 + 站名 + 域名内联输入 + 启停开关; 停用时整行置灰 */
@Composable
private fun SiteRow(
    position: Int,
    site: SiteConfig,
    isLast: Boolean,
    onEnabled: (Boolean) -> Unit,
    onBaseUrl: (String) -> Unit,
) {
    val alpha = if (site.enabled) 1f else 0.42f
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(position.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall, color = TitaGreen.copy(alpha = alpha), fontSize = 9.sp)
            Spacer(Modifier.width(7.dp))
            Text(site.name, style = MaterialTheme.typography.titleSmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), modifier = Modifier.width(56.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(7.dp))
            DomainField(
                value = site.baseUrl,
                onValueChange = onBaseUrl,
                textAlpha = alpha,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(2.dp))
            Box(Modifier.size(34.dp).clickable { onEnabled(!site.enabled) }, contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (site.enabled) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (site.enabled) "已启用，点击停用" else "已停用，点击启用",
                    tint = if (site.enabled) TitaGreen else Color(0xFFB0B0B0),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        if (!isLast) {
            Box(Modifier.fillMaxWidth().padding(start = 25.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

/** 域名内联输入: 固定 30dp 高、文本垂直居中(经 decorationBox 撑满居中) */
@Composable
private fun DomainField(
    value: String,
    onValueChange: (String) -> Unit,
    textAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(30.dp)
            .background(Color(0xFFF7F7F7), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                    inner()
                }
            },
        )
    }
}

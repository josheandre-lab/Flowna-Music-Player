package com.flowna.musicplayer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowna.musicplayer.R
import com.flowna.musicplayer.data.repository.LibraryRepository
import com.flowna.musicplayer.ui.components.FlownaGradientBackground
import com.flowna.musicplayer.ui.theme.FlownaBorder
import com.flowna.musicplayer.ui.theme.FlownaSurface
import com.flowna.musicplayer.ui.theme.FlownaTextMuted
import com.flowna.musicplayer.ui.theme.FlownaTextPrimary
import com.flowna.musicplayer.ui.theme.Lavender100
import com.flowna.musicplayer.ui.theme.Lavender600
import com.flowna.musicplayer.util.AppUpdateChecker
import com.flowna.musicplayer.util.AppUpdateState
import com.flowna.musicplayer.util.AppVersionProvider
import com.flowna.musicplayer.util.InstalledAppVersion
import com.flowna.musicplayer.util.PreferencesHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appUpdateInfo by AppUpdateState.info.collectAsStateWithLifecycle()
    val libraryState by LibraryRepository.state.collectAsStateWithLifecycle()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val appVersion = remember(context) { AppVersionProvider.get(context) }

    var selectedQuality by rememberSaveable { mutableStateOf(PreferencesHelper.getAudioQuality()) }
    var autoAppChecksEnabled by rememberSaveable {
        mutableStateOf(PreferencesHelper.isAutoAppUpdateCheckEnabled())
    }
    var autoYtDlpChecksEnabled by rememberSaveable {
        mutableStateOf(PreferencesHelper.isAutoYtDlpUpdateCheckEnabled())
    }
    var isUpdatingYtDlp by remember { mutableStateOf(false) }
    var isCheckingAppUpdate by remember { mutableStateOf(false) }
    var ytDlpVersionName by remember { mutableStateOf("Bilinmiyor") }
    var lastPreviewError by remember { mutableStateOf(PreferencesHelper.getLastPreviewError()) }
    var lastDownloadError by remember { mutableStateOf(PreferencesHelper.getLastDownloadError()) }

    LaunchedEffect(Unit) {
        LibraryRepository.ensureInitialized(context)
        ytDlpVersionName = runCatching {
            YoutubeDL.getInstance().versionName(context) ?: "Bilinmiyor"
        }.getOrDefault("Bilinmiyor")
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        FlownaGradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 12.dp,
                    end = 18.dp,
                    bottom = 124.dp
                )
            ) {
                item(contentType = "header") {
                    SettingsHeader()
                }

                item(contentType = "appInfo") {
                    AppInfoCard(
                        appVersion = appVersion,
                        ytDlpVersionName = ytDlpVersionName,
                        updateSummary = appUpdateInfo?.summary ?: "Açılışta günde bir kez gecikmeli kontrol edilir.",
                        updateAvailable = appUpdateInfo?.updateAvailable == true
                    )
                }

            item(contentType = "audio") {
                SettingsSectionCard(
                    title = "Ses ve İndirme",
                    subtitle = "İndirme sırasında kullanılan kalite seçenekleri.",
                    icon = Icons.Default.Tune
                ) {
                    QualitySegmentedControl(
                        selectedQuality = selectedQuality,
                        onQualitySelected = { quality ->
                            selectedQuality = quality
                            PreferencesHelper.setAudioQuality(quality)
                        }
                    )
                }
            }

            item(contentType = "updates") {
                SettingsSectionCard(
                    title = "Güncellemeler",
                    subtitle = "Uygulama ve yt-dlp kontrollerini yönet.",
                    icon = Icons.Default.SystemUpdate
                ) {
                    SettingsActionRow(
                        title = "Uygulama güncellemesi",
                        subtitle = appUpdateInfo?.summary ?: "Açılışta günde bir kez gecikmeli kontrol edilir.",
                        buttonLabel = when {
                            isCheckingAppUpdate -> "Bekleyin"
                            appUpdateInfo?.updateAvailable == true && appUpdateInfo?.hasPublishedApk == true -> "Güncelle"
                            else -> "Kontrol Et"
                        },
                        isLoading = isCheckingAppUpdate,
                        onClick = {
                            scope.launch {
                                isCheckingAppUpdate = true
                                val result = AppUpdateChecker.check(context = context, force = true)
                                isCheckingAppUpdate = false

                                result.onSuccess { info ->
                                    snackbarHostState.showSnackbar(info.summary)
                                    if (info.updateAvailable) {
                                        AppUpdateChecker.openUpdate(context, info)
                                    }
                                }.onFailure {
                                    snackbarHostState.showSnackbar(
                                        "Uygulama güncellemesi kontrol edilemedi."
                                    )
                                }
                            }
                        }
                    )

                    SectionDivider()

                    SettingsActionRow(
                        title = "yt-dlp",
                        subtitle = "Günlük kontrol sayesinde indirme tarafı güncel kalır.",
                        buttonLabel = if (isUpdatingYtDlp) "Bekleyin" else "Güncelle",
                        isLoading = isUpdatingYtDlp,
                        onClick = {
                            scope.launch {
                                isUpdatingYtDlp = true
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val status = YoutubeDL.getInstance().updateYoutubeDL(
                                            context,
                                            YoutubeDL.UpdateChannel._STABLE
                                        )
                                        PreferencesHelper.markYtDlpUpdateChecked()
                                        status to (YoutubeDL.getInstance().versionName(context) ?: "Bilinmiyor")
                                    }
                                }

                                isUpdatingYtDlp = false
                                result.onSuccess { (status, updatedVersion) ->
                                    ytDlpVersionName = updatedVersion
                                    val message = when (status ?: UpdateStatus.ALREADY_UP_TO_DATE) {
                                        UpdateStatus.DONE -> "yt-dlp güncellendi."
                                        UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp zaten güncel."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }.onFailure {
                                    snackbarHostState.showSnackbar("yt-dlp güncellenemedi.")
                                }
                            }
                        }
                    )

                    SectionDivider()

                    SettingsSwitchRow(
                        title = "Otomatik uygulama kontrolü",
                        subtitle = "Açılışta günde bir kez gecikmeli sürüm denetimi yapar.",
                        checked = autoAppChecksEnabled,
                        onCheckedChange = { enabled ->
                            autoAppChecksEnabled = enabled
                            PreferencesHelper.setAutoAppUpdateCheckEnabled(enabled)
                        }
                    )

                    SectionDivider()

                    SettingsSwitchRow(
                        title = "Günlük yt-dlp kontrolü",
                        subtitle = "İndirme altyapısını günde bir kez kontrol eder.",
                        checked = autoYtDlpChecksEnabled,
                        onCheckedChange = { enabled ->
                            autoYtDlpChecksEnabled = enabled
                            PreferencesHelper.setAutoYtDlpUpdateCheckEnabled(enabled)
                        }
                    )
                }
            }

            item(contentType = "library") {
                SettingsSectionCard(
                    title = "Kütüphane",
                    subtitle = "Tüm müzikleri önbellek üzerinden yönet.",
                    icon = Icons.Default.LibraryMusic
                ) {
                    SettingsActionRow(
                        title = "Kütüphaneyi yeniden tara",
                        subtitle = buildLibrarySubtitle(
                            totalSongs = libraryState.songs.size,
                            lastScanAt = libraryState.lastScanAt,
                            isRefreshing = libraryState.isRefreshing
                        ),
                        buttonLabel = if (libraryState.isRefreshing) "Taranıyor" else "Tara",
                        isLoading = libraryState.isRefreshing,
                        onClick = {
                            scope.launch {
                                LibraryRepository.refreshLibrary(context)
                                    .onSuccess { count ->
                                        snackbarHostState.showSnackbar("$count şarkı yeniden tarandı.")
                                    }
                                    .onFailure {
                                        snackbarHostState.showSnackbar("Kütüphane taraması başarısız oldu.")
                                    }
                            }
                        }
                    )
                }
            }

                item(contentType = "diagnostics") {
                    DiagnosticsSection(
                        appVersion = appVersion,
                        lastPreviewError = lastPreviewError,
                        lastDownloadError = lastDownloadError,
                        lastScanAt = libraryState.lastScanAt,
                        onClear = {
                            PreferencesHelper.clearDiagnostics()
                            lastPreviewError = ""
                            lastDownloadError = ""
                            scope.launch {
                                snackbarHostState.showSnackbar("Tanılama kayıtları temizlendi.")
                            }
                        }
                    )
                }

                item(contentType = "about") {
                    SettingsSectionCard(
                        title = "Hakkında",
                        subtitle = "Uygulama sürümü ve altyapı bilgileri.",
                        icon = Icons.Default.Info
                    ) {
                        SettingsInfoRow(
                            title = "Uygulama sürümü",
                            value = appVersion.name
                        )
                        SectionDivider()
                        SettingsInfoRow(
                            title = "yt-dlp sürümü",
                            value = ytDlpVersionName
                        )
                        SectionDivider()
                        SettingsInfoRow(
                            title = "Ses motoru",
                            value = "ExoPlayer (Media3)"
                        )
                        SectionDivider()
                        SettingsInfoRow(
                            title = "Önbellekteki şarkı",
                            value = libraryState.songs.size.toString()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ayarlar",
                style = MaterialTheme.typography.displayLarge,
                color = FlownaTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "İndirme, oynatma ve uygulama tercihlerini yönet.",
                style = MaterialTheme.typography.bodyMedium,
                color = FlownaTextMuted
            )
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = FlownaSurface,
            shadowElevation = 4.dp
        ) {
            Image(
                painter = painterResource(id = R.drawable.flowna_launcher_logo),
                contentDescription = "Flowna",
                modifier = Modifier
                    .size(62.dp)
                    .padding(10.dp)
            )
        }
    }
}

@Composable
private fun AppInfoCard(
    appVersion: InstalledAppVersion,
    ytDlpVersionName: String,
    updateSummary: String,
    updateAvailable: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Lavender600,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Lavender600, Lavender600.copy(alpha = 0.92f)))
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FlownaSurface.copy(alpha = 0.22f),
                    shadowElevation = 0.dp
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.flowna_launcher_logo),
                        contentDescription = "Flowna Music Player",
                        modifier = Modifier
                            .size(64.dp)
                            .padding(8.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Flowna Music Player",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = FlownaSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(updateAvailable = updateAvailable)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AppMetaRow(
                        title = "Uygulama sürümü",
                        value = appVersion.name
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppMetaRow(
                        title = "yt-dlp sürümü",
                        value = ytDlpVersionName
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = updateSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlownaSurface.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            MiniWaveDecoration(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun StatusBadge(updateAvailable: Boolean) {
    val containerColor = if (updateAvailable) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (updateAvailable) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (updateAvailable) "Yeni" else "Güncel",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun MiniWaveDecoration(modifier: Modifier = Modifier) {
    val heights = listOf(10.dp, 16.dp, 24.dp, 14.dp, 28.dp, 18.dp, 22.dp, 12.dp)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { barHeight ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = FlownaSurface,
        shadowElevation = 2.dp,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Lavender100
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = Lavender600
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun QualitySegmentedControl(
    selectedQuality: String,
    onQualitySelected: (String) -> Unit
) {
    val qualities = PreferencesHelper.getAvailableQualities()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Lavender100
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            qualities.forEach { quality ->
                val isSelected = selectedQuality == quality
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onQualitySelected(quality) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) {
                        FlownaSurface
                    } else {
                        Color.Transparent
                    }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = quality,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) Lavender600 else FlownaTextMuted
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QualityLegendItem(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.tertiary,
            text = "128K daha az yer"
        )
        QualityLegendItem(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            text = "192K dengeli kalite"
        )
        QualityLegendItem(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary,
            text = "320K yüksek kalite"
        )
    }
}

@Composable
private fun QualityLegendItem(
    modifier: Modifier = Modifier,
    color: Color,
    text: String
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        ActionPillButton(
            label = buttonLabel,
            isLoading = isLoading,
            onClick = onClick
        )
    }
}

@Composable
private fun ActionPillButton(
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Surface(
        modifier = Modifier.clip(CircleShape),
        shape = CircleShape,
            color = Lavender100
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = !isLoading, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Lavender600
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Lavender600
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun DiagnosticsSection(
    appVersion: InstalledAppVersion,
    lastPreviewError: String,
    lastDownloadError: String,
    lastScanAt: Long?,
    onClear: () -> Unit
) {
    SettingsSectionCard(
        title = "Tanılama",
        subtitle = "Son hata ve durum kayıtlarını hızlı kontrol et.",
        icon = Icons.Default.Info
    ) {
        DiagnosticInfoRow(
            title = "Paket",
            value = appVersion.display
        )
        SectionDivider()
        DiagnosticInfoRow(
            title = "Son kütüphane taraması",
            value = lastScanAt?.let(::formatTimestamp) ?: "Henüz kayıt yok"
        )
        SectionDivider()
        DiagnosticInfoRow(
            title = "Son önizleme hatası",
            value = lastPreviewError.ifBlank { "Kayıt yok" }
        )
        SectionDivider()
        DiagnosticInfoRow(
            title = "Son indirme hatası",
            value = lastDownloadError.ifBlank { "Kayıt yok" }
        )
        SectionDivider()
        SettingsActionRow(
            title = "Tanılama kayıtları",
            subtitle = "Son önizleme ve indirme hatasını temizler.",
            buttonLabel = "Temizle",
            onClick = onClear
        )
    }
}

@Composable
private fun DiagnosticInfoRow(
    title: String,
    value: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (title) {
                    "yt-dlp sürümü" -> Icons.Default.GraphicEq
                    "Önbellekteki şarkı" -> Icons.Default.LibraryMusic
                    "Ses motoru" -> Icons.Default.Settings
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppMetaRow(
    title: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
            .background(FlownaSurface.copy(alpha = 0.78f))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$title:",
            style = MaterialTheme.typography.bodyMedium,
            color = FlownaSurface.copy(alpha = 0.78f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = FlownaSurface
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    )
}

private fun buildLibrarySubtitle(
    totalSongs: Int,
    lastScanAt: Long?,
    isRefreshing: Boolean
): String {
    return when {
        isRefreshing -> "Tarama sürüyor. Mevcut liste önbellekten kullanılıyor."
        lastScanAt != null -> "$totalSongs şarkı önbellekte. Son tarama ${formatTimestamp(lastScanAt)}."
        else -> "$totalSongs şarkı önbellekte hazır."
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale("tr", "TR"))
    val localTime = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    return formatter.format(localTime)
}

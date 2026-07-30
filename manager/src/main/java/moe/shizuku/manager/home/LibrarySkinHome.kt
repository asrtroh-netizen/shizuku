package moe.shizuku.manager.home

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.KEEP_START_ON_BOOT
import moe.shizuku.manager.ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS
import moe.shizuku.manager.ShizukuSettings.WATCHDOG_ENABLED_ADB
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.receiver.BootCompleteReceiver
import moe.shizuku.manager.receiver.WifiReadyMonitor
import moe.shizuku.manager.update.UpdateChecker
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.watchdog.WatchdogService
import rikka.material.app.LocaleDelegate
import java.util.Locale

/**
 * Library V15 home: Hero → Wireless → 2×2 → Boot card → check-update (bottom-right).
 * Top-right capsules: language label + light/dark theme toggle.
 */
@Composable
fun LibrarySkinHomeBody(
    status: ServiceStatus?,
    grantedCount: Int?,
    onManageApps: () -> Unit,
    onOpenTerminal: () -> Unit,
    onStartRoot: () -> Unit,
    onRestartRoot: () -> Unit,
    onOpenWirelessGuide: () -> Unit,
    onPairWireless: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onShowAdbCommand: () -> Unit,
    onOpenAdbPermissionHelp: () -> Unit,
) {
    val context = LocalContext.current
    val resolved = status ?: ServiceStatus()
    val running = resolved.isRunning
    val rooted = EnvironmentUtils.isRooted()
    val rootRestart = running && resolved.uid == 0

    var tileDialog by remember { mutableStateOf<TileDialog?>(null) }
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }
    var showWirelessPermission by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val bootWireless = remember {
        mutableStateOf(ShizukuSettings.getPreferences().getBoolean(KEEP_START_ON_BOOT_WIRELESS, false))
    }
    val bootRoot = remember {
        mutableStateOf(ShizukuSettings.getPreferences().getBoolean(KEEP_START_ON_BOOT, false))
    }
    val watchdog = remember {
        mutableStateOf(ShizukuSettings.getPreferences().getBoolean(WATCHDOG_ENABLED_ADB, false))
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LibraryHeroCard(running = running)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || EnvironmentUtils.getAdbTcpPort() > 0) {
            WirelessStartCard(
                onGuide = onOpenWirelessGuide,
                onPair = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) onPairWireless else null,
                onStart = onStartWirelessAdb,
            )
        }

        Text(
            text = stringResource(R.string.home_quick_entry_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        val appsSub = when {
            !running -> stringResource(R.string.home_checks_apps_sub_waiting)
            grantedCount == null -> stringResource(R.string.home_checks_apps_sub_waiting)
            grantedCount < 0 -> stringResource(R.string.home_app_management_binder_unavailable)
            else -> pluralStringResource(
                R.plurals.home_app_management_authorized_apps_count,
                grantedCount,
                grantedCount,
            )
        }

        QuickGrid(
            appsSub = appsSub,
            running = running,
            rooted = rooted,
            rootRestart = rootRestart,
            permissionOk = resolved.permission,
            onApps = {
                tileDialog = TileDialog.Apps(appsSub, running && (grantedCount ?: -1) >= 0, onManageApps)
            },
            onTerminal = {
                tileDialog = TileDialog.Terminal(running && resolved.permission, onOpenTerminal)
            },
            onRoot = {
                tileDialog = if (!rooted) TileDialog.RootUnavailable
                else TileDialog.RootConfirm(rootRestart, onStartRoot, onRestartRoot)
            },
            onAdb = { tileDialog = TileDialog.Adb(onShowAdbCommand) },
        )

        // Same card chrome/size class as wireless debugging
        BootStartCard(onConfigure = { sheet = HomeSheet.Boot })

        if (running && !resolved.permission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAdbPermissionHelp),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.home_adb_is_limited_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        CheckUpdateRow(
            checking = checkingUpdate,
            onClick = {
                if (checkingUpdate) return@CheckUpdateRow
                checkingUpdate = true
                scope.launch {
                    val info = withContext(Dispatchers.IO) {
                        UpdateChecker.checkLatest(context)
                    }
                    checkingUpdate = false
                    if (info.hasUpdate && info.downloadUrl.isNotBlank()) {
                        UpdateChecker.downloadAndInstall(
                            context,
                            info.downloadUrl,
                            info.latestVersion,
                        )
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_download_started, info.latestVersion),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(context, info.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    TileDialogs(tileDialog) { tileDialog = it }
    HomeSheets(
        sheet = sheet,
        bootRoot = bootRoot.value,
        bootWireless = bootWireless.value,
        watchdog = watchdog.value,
        onDismiss = { sheet = null },
        onBootRoot = { checked ->
            bootRoot.value = checked
            if (checked) bootWireless.value = false
            saveBool(context, KEEP_START_ON_BOOT, checked)
            if (checked) saveBool(context, KEEP_START_ON_BOOT_WIRELESS, false)
            setBootReceiverEnabled(context, checked || bootWireless.value)
        },
        onBootWireless = { checked ->
            if (checked && !hasWriteSecureSettings(context)) {
                showWirelessPermission = true
            } else {
                bootWireless.value = checked
                if (checked) bootRoot.value = false
                saveBool(context, KEEP_START_ON_BOOT_WIRELESS, checked)
                if (checked) saveBool(context, KEEP_START_ON_BOOT, false)
                setBootReceiverEnabled(context, checked || bootRoot.value)
                if (checked) WifiReadyMonitor.ensureRegistered(context)
                else WifiReadyMonitor.unregister(context)
            }
        },
        onWatchdog = { checked ->
            watchdog.value = checked
            saveBool(context, WATCHDOG_ENABLED_ADB, checked)
            if (checked) {
                if (rikka.shizuku.Shizuku.pingBinder()) WatchdogService.start(context)
            } else {
                WatchdogService.stop(context)
            }
        },
    )

    if (showWirelessPermission) {
        val grantCmd =
            "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"
        AlertDialog(
            onDismissRequest = { showWirelessPermission = false },
            title = { Text(stringResource(R.string.permission_missing)) },
            text = {
                Text(
                    stringResource(R.string.wireless_boot_permission_tooltip) + "\n\n" + grantCmd,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    rikka.core.util.ClipboardUtils.put(context, grantCmd)
                    showWirelessPermission = false
                }) {
                    Text(stringResource(R.string.home_adb_dialog_view_command_copy_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWirelessPermission = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/** Top-right: fixed「Lang」chip + sun/moon theme slide toggle. */
@Composable
fun LibraryTopLangThemeCapsules(
    onLanguage: () -> Unit,
) {
    val context = LocalContext.current
    val dark = isDarkThemeActive(context)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleChip(
            modifier = Modifier,
            label = stringResource(R.string.home_lang_chip),
            selected = true,
            onClick = onLanguage,
        )
        ThemeSlideToggle(
            dark = dark,
            onToggle = { toggleLightDarkTheme(context) },
        )
    }
}

/**
 * Pill track with sun (left) / moon (right); white knob slides to the active side.
 */
@Composable
private fun ThemeSlideToggle(
    dark: Boolean,
    onToggle: () -> Unit,
) {
    val trackW = 56.dp
    val trackH = 30.dp
    val knob = 24.dp
    val pad = 3.dp
    val iconTint = Color(0xFF3A4A6B)
    val offsetX by animateDpAsState(
        targetValue = if (dark) trackW - knob - pad else pad,
        animationSpec = tween(durationMillis = 220),
        label = "themeKnob",
    )
    Box(
        modifier = Modifier
            .width(trackW)
            .height(trackH)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.LightMode,
                contentDescription = stringResource(R.string.home_theme_light),
                modifier = Modifier.size(14.dp),
                tint = iconTint.copy(alpha = if (dark) 0.35f else 0.9f),
            )
            Icon(
                Icons.Outlined.DarkMode,
                contentDescription = stringResource(R.string.home_theme_dark),
                modifier = Modifier.size(14.dp),
                tint = iconTint.copy(alpha = if (dark) 0.9f else 0.35f),
            )
        }
        Box(
            modifier = Modifier
                .offset(x = offsetX, y = pad)
                .size(knob)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = iconTint,
            )
        }
    }
}

@Composable
fun LibraryLanguageDialogHost(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val locales = remember { buildLocaleItems(context) }
    CapsuleChoiceDialog(
        title = stringResource(R.string.settings_language),
        options = locales.map { it.label },
        selectedIndex = locales.indexOfFirst { it.selected }.coerceAtLeast(0),
        onDismiss = onDismiss,
        onSelect = { index ->
            val selected = locales[index]
            val locale =
                if (selected.tag == "SYSTEM") LocaleDelegate.systemLocale
                else Locale.forLanguageTag(selected.tag)
            ShizukuSettings.getPreferences().edit { putString(ShizukuSettings.LANGUAGE, selected.tag) }
            LocaleDelegate.defaultLocale = locale
            onDismiss()
            (context as? Activity)?.recreate()
        },
    )
}

private sealed class TileDialog {
    data class Apps(val subtitle: String, val canOpen: Boolean, val open: () -> Unit) : TileDialog()
    data class Terminal(val enabled: Boolean, val open: () -> Unit) : TileDialog()
    data object RootUnavailable : TileDialog()
    data class RootConfirm(val restart: Boolean, val onStart: () -> Unit, val onRestart: () -> Unit) : TileDialog()
    data class Adb(val showCommand: () -> Unit) : TileDialog()
}

private sealed class HomeSheet {
    data object Boot : HomeSheet()
}

/** OneIMS capsule: selected shows leading 6dp dot. */
@Composable
fun CapsuleChip(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .heightIn(min = 30.dp, max = 30.dp)
            .background(container, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Box(Modifier.size(6.dp).background(content, CircleShape))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryHeroCard(running: Boolean) {
    val bg = colorResource(if (running) R.color.hero_ready_bg else R.color.hero_inactive_bg)
    val fg = colorResource(if (running) R.color.hero_ready_fg else R.color.hero_inactive_fg)
    Card(
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Image(
                    painter = painterResource(
                        if (running) R.drawable.ic_server_ok_24dp else R.drawable.ic_server_error_24dp,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    colorFilter = ColorFilter.tint(fg),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_hero_eyebrow),
                        style = MaterialTheme.typography.labelMedium,
                        color = fg.copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (running) stringResource(R.string.app_name)
                            else stringResource(R.string.home_hero_title_inactive),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = stringResource(
                                if (running) R.string.home_hero_pill_ready else R.string.home_hero_pill_inactive,
                            ),
                            modifier = Modifier
                                .background(fg.copy(alpha = 0.14f), RoundedCornerShape(percent = 50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    if (!running) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.home_hero_subtitle_inactive),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.home_hero_detail_inactive),
                            style = MaterialTheme.typography.bodySmall,
                            color = fg.copy(alpha = 0.78f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            StageStrip(ready = running, fg = fg)
        }
    }
}

@Composable
private fun StageStrip(ready: Boolean, fg: Color) {
    // Mirror home_server_status.xml: [dot+label] ——line—— [dot+label], not two labels jammed together
    val litCount = if (ready) 2 else 1
    val label1 = stringResource(R.string.home_hero_stage_inactive)
    val label2 = stringResource(R.string.home_hero_stage_ready)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        StageEndpoint(label = label1, lit = litCount >= 1, fg = fg)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .padding(top = 4.dp)
                .height(2.dp)
                .background(if (litCount >= 2) fg.copy(alpha = 0.55f) else fg.copy(alpha = 0.18f)),
        )
        StageEndpoint(label = label2, lit = litCount >= 2, fg = fg)
    }
}

@Composable
private fun StageEndpoint(label: String, lit: Boolean, fg: Color) {
    val c = if (lit) fg else fg.copy(alpha = 0.32f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(10.dp).background(c, CircleShape))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1)
    }
}

@Composable
private fun WirelessStartCard(
    onGuide: () -> Unit,
    onPair: (() -> Unit)?,
    onStart: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Wifi, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.home_wireless_adb_title_plain),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onGuide) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.home_wireless_adb_view_guide_button))
                }
                if (onPair != null) {
                    OutlinedButton(onClick = onPair) {
                        Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.adb_pairing))
                    }
                }
                Button(onClick = onStart) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.home_root_button_start))
                }
            }
        }
    }
}

@Composable
private fun BootStartCard(onConfigure: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PowerSettingsNew,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.home_capsule_boot),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                Button(onClick = onConfigure) {
                    Text(stringResource(R.string.home_boot_configure))
                }
            }
        }
    }
}

@Composable
private fun QuickGrid(
    appsSub: String,
    running: Boolean,
    rooted: Boolean,
    rootRestart: Boolean,
    permissionOk: Boolean,
    onApps: () -> Unit,
    onTerminal: () -> Unit,
    onRoot: () -> Unit,
    onAdb: () -> Unit,
) {
    // Icons copied 1:1 from thedjchi HomeQuickGridViewHolder
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickTile(
                Modifier.weight(1f),
                R.drawable.ic_settings_outline_24dp,
                stringResource(R.string.home_app_management_title),
                appsSub,
                !running,
                onApps,
            )
            QuickTile(
                Modifier.weight(1f),
                R.drawable.ic_terminal_24,
                stringResource(R.string.home_terminal_title_plain),
                stringResource(R.string.home_terminal_tile_sub),
                !(running && permissionOk),
                onTerminal,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickTile(
                Modifier.weight(1f),
                R.drawable.ic_root_24dp,
                stringResource(R.string.home_root_title_plain),
                stringResource(
                    if (rooted) {
                        if (rootRestart) R.string.home_root_button_restart else R.string.home_root_button_start
                    } else R.string.home_root_tile_unavailable,
                ),
                !rooted,
                onRoot,
            )
            QuickTile(
                Modifier.weight(1f),
                R.drawable.ic_server_ok_24dp,
                stringResource(R.string.home_adb_tile_title),
                stringResource(R.string.home_checks_adb_sub),
                false,
                onAdb,
            )
        }
    }
}

@Composable
private fun QuickTile(
    modifier: Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val iconTint = if (dimmed) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = modifier.heightIn(min = 124.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (dimmed) 0.45f else 0.85f),
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(iconTint),
            )
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dimmed) 0.45f else 0.78f),
            )
        }
    }
}

@Composable
private fun CheckUpdateRow(
    checking: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(onClick = onClick, enabled = !checking) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(
                    if (checking) R.string.home_checking_update else R.string.home_check_update,
                ),
            )
        }
    }
}

@Composable
private fun TileDialogs(dialog: TileDialog?, set: (TileDialog?) -> Unit) {
    when (val d = dialog) {
        is TileDialog.Apps -> AlertDialog(
            onDismissRequest = { set(null) },
            title = { Text(stringResource(R.string.home_app_management_title)) },
            text = { Text(d.subtitle) },
            confirmButton = {
                TextButton(onClick = {
                    set(null)
                    if (d.canOpen) d.open()
                }) {
                    Text(
                        if (d.canOpen) stringResource(R.string.home_app_management_view_authorized_apps)
                        else stringResource(android.R.string.ok),
                    )
                }
            },
            dismissButton = if (d.canOpen) {
                { TextButton(onClick = { set(null) }) { Text(stringResource(android.R.string.cancel)) } }
            } else null,
        )
        is TileDialog.Terminal -> AlertDialog(
            onDismissRequest = { set(null) },
            title = { Text(stringResource(R.string.home_terminal_title_plain)) },
            text = {
                Text(
                    if (d.enabled) stringResource(R.string.home_terminal_description)
                    else stringResource(R.string.home_status_service_not_running, stringResource(R.string.app_name)),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    set(null)
                    if (d.enabled) d.open()
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { set(null) }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        TileDialog.RootUnavailable -> AlertDialog(
            onDismissRequest = { set(null) },
            title = { Text(stringResource(R.string.home_root_title_plain)) },
            text = { Text(stringResource(R.string.home_root_tile_unavailable_detail)) },
            confirmButton = {
                TextButton(onClick = { set(null) }) { Text(stringResource(android.R.string.ok)) }
            },
        )
        is TileDialog.RootConfirm -> AlertDialog(
            onDismissRequest = { set(null) },
            title = { Text(stringResource(R.string.home_root_title_plain)) },
            text = { Text(stringResource(R.string.home_root_tile_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    set(null)
                    if (d.restart) d.onRestart() else d.onStart()
                }) {
                    Text(
                        stringResource(
                            if (d.restart) R.string.home_root_button_restart else R.string.home_root_button_start,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { set(null) }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        is TileDialog.Adb -> AlertDialog(
            onDismissRequest = { set(null) },
            title = { Text(stringResource(R.string.home_adb_tile_title)) },
            text = { Text(stringResource(R.string.home_checks_adb_sub)) },
            confirmButton = {
                TextButton(onClick = { set(null); d.showCommand() }) {
                    Text(stringResource(R.string.home_adb_button_view_command))
                }
            },
            dismissButton = {
                TextButton(onClick = { set(null) }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        null -> Unit
    }
}

@Composable
private fun HomeSheets(
    sheet: HomeSheet?,
    bootRoot: Boolean,
    bootWireless: Boolean,
    watchdog: Boolean,
    onDismiss: () -> Unit,
    onBootRoot: (Boolean) -> Unit,
    onBootWireless: (Boolean) -> Unit,
    onWatchdog: (Boolean) -> Unit,
) {
    when (sheet) {
        HomeSheet.Boot -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.home_capsule_boot)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BootSwitchRow(
                        title = stringResource(R.string.settings_start_on_boot),
                        checked = bootRoot,
                        onChecked = onBootRoot,
                    )
                    BootSwitchRow(
                        title = stringResource(R.string.settings_start_on_boot_wireless),
                        checked = bootWireless,
                        onChecked = onBootWireless,
                    )
                    BootSwitchRow(
                        title = stringResource(R.string.settings_watchdog_adb),
                        checked = watchdog,
                        onChecked = onWatchdog,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
            },
        )
        null -> Unit
    }
}

@Composable
private fun BootSwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun CapsuleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    CapsuleChip(
                        modifier = Modifier.fillMaxWidth(),
                        label = option,
                        selected = selected,
                        onClick = { onSelect(index) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private data class LocaleChoice(val tag: String, val label: String, val selected: Boolean)

private fun buildLocaleItems(context: Context): List<LocaleChoice> {
    val tags = rikka.shizuku.manager.ShizukuLocales.LOCALES
    val currentTag = ShizukuSettings.getPreferences().getString(ShizukuSettings.LANGUAGE, "SYSTEM") ?: "SYSTEM"
    return tags.mapIndexed { index, tag ->
        LocaleChoice(
            tag = tag,
            label = if (index == 0) context.getString(R.string.settings_language_system)
            else when (tag) {
                "zh-CN" -> "简体中文"
                "zh-TW" -> "繁體中文"
                "en" -> "English"
                "ja" -> "日本語"
                "ko" -> "한국어"
                else -> tag
            },
            selected = tag == currentTag,
        )
    }
}

private fun hasWriteSecureSettings(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_SECURE_SETTINGS,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun isDarkThemeActive(context: Context): Boolean {
    return when (ShizukuSettings.getNightMode()) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            night == Configuration.UI_MODE_NIGHT_YES
        }
    }
}

private fun toggleLightDarkTheme(context: Context) {
    val next = if (isDarkThemeActive(context)) {
        AppCompatDelegate.MODE_NIGHT_NO
    } else {
        AppCompatDelegate.MODE_NIGHT_YES
    }
    ShizukuSettings.getPreferences().edit { putInt(ShizukuSettings.NIGHT_MODE, next) }
    AppCompatDelegate.setDefaultNightMode(next)
    (context as? Activity)?.recreate()
}

private fun saveBool(context: Context, key: String, value: Boolean) {
    ShizukuSettings.getPreferences().edit { putBoolean(key, value) }
}

private fun setBootReceiverEnabled(context: Context, enabled: Boolean) {
    val component = ComponentName(context.packageName, BootCompleteReceiver::class.java.name)
    val state =
        if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    context.packageManager.setComponentEnabledSetting(
        component, state, PackageManager.DONT_KILL_APP,
    )
}

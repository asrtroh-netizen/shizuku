package moe.shizuku.manager.flutter

import moe.shizuku.manager.ShizukuSettings

// ───────────────────────── 纯函数（零 Android / org.json 依赖，供 JUnit 直测）─────────────────────────

/** 两个开机项的当前落盘值。 */
internal data class BootPrefs(val bootRoot: Boolean, val bootWireless: Boolean)

/**
 * Root 开机 / 无线开机互斥（与 `SettingsComposeScreen.onToggle` 同义）：
 * 开任一项 → 另一项强制关；关任一项不影响另一项。其它 key 原样返回。
 */
internal fun applyBootToggle(current: BootPrefs, key: String, checked: Boolean): BootPrefs {
    return when (key) {
        ShizukuSettings.KEEP_START_ON_BOOT -> BootPrefs(
            bootRoot = checked,
            bootWireless = if (checked) false else current.bootWireless,
        )
        ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS -> BootPrefs(
            bootRoot = if (checked) false else current.bootRoot,
            bootWireless = checked,
        )
        else -> current
    }
}

/**
 * TCP/IP 端口输入校验，与 Compose TcpIpPort 对话框的保存分支同义：
 * 去首尾空白后为空 → `null`（表示清除）；否则必须是 10..65535 的整数
 * （`R.string.dialog_adb_invalid_port` 原文即 "ranging from 10 to 65535"），否则抛 [IllegalArgumentException]。
 */
internal fun parseTcpipPort(raw: String): Int? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val value = trimmed.toIntOrNull()
    require(value != null && value in 10..65535) { "invalid port: $trimmed" }
    return value
}

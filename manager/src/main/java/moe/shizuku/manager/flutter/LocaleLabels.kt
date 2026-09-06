package moe.shizuku.manager.flutter

// ───────────────────────── 纯函数（零 Android / org.json 依赖，供 JUnit 直测）─────────────────────────

/** 语言候选，与 Compose `LocaleChoice` 同形。 */
internal data class LocaleRow(val tag: String, val label: String, val selected: Boolean)

/** 语言 tag → 显示名（R2 §R1 唯一实现；原 `SettingsChannel.localeLabel` / `buildLocaleRows` 逐字搬入）。 */
internal object LocaleLabels {

    /** 与 `SettingsComposeScreen.localeLabel` 逐字同表；未知 tag 原样返回。 */
    fun label(tag: String): String {
        return when (tag) {
            "ang" -> "Old English (ca. 450-1100)"
            "ar" -> "العربية"
            "ars" -> "العربية النجدية"
            "az" -> "Azərbaycanca"
            "bn" -> "বাংলা"
            "ca" -> "Català"
            "cs" -> "Čeština"
            "de" -> "Deutsch"
            "el" -> "Ελληνικά"
            "en" -> "English"
            "eo" -> "Esperanto"
            "es" -> "Español"
            "es-419" -> "Español (Latinoamérica)"
            "es-CL" -> "Español (Chile)"
            "et" -> "Eesti"
            "fa" -> "فارسی"
            "fil" -> "Filipino"
            "fr" -> "Français"
            "he" -> "עברית"
            "hu" -> "Magyar"
            "hy" -> "Հայերեն"
            "id" -> "Indonesia"
            "it" -> "Italiano"
            "ja" -> "日本語"
            "ka" -> "ქართული"
            "ko" -> "한국어"
            "ms" -> "Melayu"
            "nl" -> "Nederlands"
            "pl" -> "Polski"
            "pt" -> "Português"
            "pt-BR" -> "Português (Brasil)"
            "ro" -> "Română"
            "ru" -> "Русский"
            "sl" -> "Slovenščina"
            "sr" -> "Српски"
            "ta" -> "தமிழ்"
            "th" -> "ไทย"
            "tr" -> "Türkçe"
            "uk" -> "Українська"
            "vi" -> "Tiếng Việt"
            "zh-CN" -> "简体中文"
            "zh-TW" -> "繁體中文"
            else -> tag
        }
    }

    /**
     * 与 `SettingsComposeScreen.buildLocaleItems` 同义：首项（"SYSTEM"）用 [systemLabel]
     * （`R.string.settings_language_system`），其余走 [label]；`selected = tag == currentTag`。
     */
    fun rows(tags: List<String>, currentTag: String, systemLabel: String): List<LocaleRow> {
        return tags.mapIndexed { index, tag ->
            LocaleRow(
                tag = tag,
                label = if (index == 0) systemLabel else label(tag),
                selected = tag == currentTag,
            )
        }
    }
}

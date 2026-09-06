package moe.shizuku.manager.flutter

import rikka.html.text.HtmlCompat

/**
 * 去 HTML 标签、折叠空白（R2 RULEBOOK §R1 唯一实现；原 `AppsChannel.plainText` / `TerminalChannel.plainText`，
 * 同 Compose `AppsManagementComposeScreen.plainText` / `ShellTutorialComposeScreen.plainText`）。
 * `HtmlCompat.fromHtml(html)` 即 `fromHtml(html, FROM_HTML_MODE_LEGACY)`，与 Terminal 旧版显式传 LEGACY 等价。
 */
internal fun htmlToPlainText(html: String): String {
    return HtmlCompat.fromHtml(html).toString().replace(Regex("\\s+"), " ").trim()
}

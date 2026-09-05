package moe.shizuku.manager.flutter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.utils.CustomTabsHelper
import org.json.JSONObject
import rikka.html.text.HtmlCompat

/**
 * 整页快照（纯函数，零 Android 依赖，供 JUnit 直测）：`{ok, shName, dexName, copy}`。
 */
internal fun buildTerminalStateJson(
    shName: String,
    dexName: String,
    copy: Map<String, Any?>,
): Map<String, Any?> {
    return linkedMapOf(
        "ok" to true,
        "shName" to shName,
        "dexName" to dexName,
        "copy" to copy,
    )
}

/**
 * 「终端」Tab 的 MethodChannel（RULEBOOK §11）。界面在 Dart，业务逻辑与
 * 原 Compose `ShellTutorialActivity`（已于 P4 删除）同一套：导出 = 系统文档树选择 →
 * 删旧 `rish` / `rish_shizuku.dex` → 从 assets 写入；查看指南 = `Helps.RISH`。
 *
 * copy key ↔ R.string（格式参数与 `ShellTutorialComposeScreen` 逐项相同；
 * `plainText` = HtmlCompat 去标签 + 折叠空白，`mono(x)` = `<font face="monospace">x</font>`）：
 * - `title`                = `home_terminal_title`
 * - `back`                 = `action_back`
 * - `open`                 = `action_open`（Compose 里是说明卡尾部 OpenInNew 图标的 contentDescription）
 * - `rishDescription`      = plainText(`rish_description`, mono(shName))
 * - `tutorial1`            = plainText(`terminal_tutorial_1`, mono(shName), mono(dexName))
 * - `tutorial1Description` = `terminal_tutorial_1_description`（Compose 未过 plainText，保留 `\n\n`）
 * - `exportFiles`          = `terminal_export_files`
 * - `tutorial2`            = plainText(`terminal_tutorial_2`, mono(shName))
 * - `tutorial2Description` = plainText(`terminal_tutorial_2_description`, "Termux", mono("PKG"), mono("com.termux"), mono("com.termux"))
 * - `tutorial3`            = plainText(`terminal_tutorial_3`, mono("sh $shName"))
 * - `tutorial3Description` = plainText(`terminal_tutorial_3_description`, mono(shName), mono("PATH"))
 *
 * 文档树结果的接收（GAP-5 v1.2）：宿主 [FlutterHostActivity] 是 `io.flutter.embedding.android.FlutterActivity`，
 * 它继承的是 `android.app.Activity` 而不是 `ComponentActivity`，没有 `registerForActivityResult`。
 * 因此这里用 `startActivityForResult` + 公开钩子 [onActivityResult]（RULEBOOK §1.6 的生命周期钩子模式），
 * 由协调者在宿主 `onActivityResult` 里先 `super` 再转发。Intent 的构造与结果的解析仍交给
 * [ActivityResultContracts.OpenDocumentTree] 本身，与 `ShellTutorialActivity` 的 launcher 行为完全一致。
 *
 * 不设事件通道（RULEBOOK §1.1 v1.2 / GAP-15）：本页会被 `AppShell` 用 `ValueKey` 重建，
 * 新页 listen 后旧页 cancel 会把平台侧 sink 置空；页面靠 `initState` 拉取 + `resumed` 重拉即可。
 */
class TerminalChannel(private val activity: Activity, private val scope: CoroutineScope) {

    private val openDocumentTree = ActivityResultContracts.OpenDocumentTree()

    fun register(messenger: BinaryMessenger) {
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getState" -> runAction(result, "getState") { snapshotJson() }
                "exportFiles" -> runAction(result, "exportFiles") {
                    activity.startActivityForResult(
                        openDocumentTree.createIntent(activity, null),
                        REQUEST_OPEN_DOCUMENT_TREE,
                    )
                    ok()
                }
                "openGuide" -> runAction(result, "openGuide") {
                    CustomTabsHelper.launchUrlOrCopy(activity, Helps.RISH.get())
                    ok()
                }
                else -> result.notImplemented()
            }
        }
    }

    /**
     * 宿主 `onActivityResult` 转发到此；返回 true 表示请求码属于本通道、已消费。
     * 文件操作在 IO 执行（RULEBOOK §1.5）；异常 `runCatching` 吞掉（Compose 在主线程会崩——声明的差异）。
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_OPEN_DOCUMENT_TREE) return false
        val tree: Uri = openDocumentTree.parseResult(resultCode, data) ?: return true
        scope.launch(Dispatchers.IO) {
            runCatching { exportTo(tree) }
        }
        return true
    }

    /** 逐行同 `ShellTutorialActivity.openDocumentsTree` 回调：删旧同名 → 从 assets 写入。 */
    private fun exportTo(tree: Uri) {
        val cr = activity.contentResolver
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val child =
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

        cr.query(
            child,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val name = it.getString(1)
                if (name == SH_NAME || name == DEX_NAME) {
                    DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id))
                }
            }
        }

        fun writeToDocument(name: String) {
            DocumentsContract.createDocument(activity.contentResolver, doc, "application/octet-stream", name)?.runCatching {
                cr.openOutputStream(this)?.let { activity.assets.open(name).copyTo(it) }
            }
        }

        writeToDocument(SH_NAME)
        writeToDocument(DEX_NAME)
    }

    private fun runAction(result: MethodChannel.Result, name: String, block: () -> String) {
        runCatching(block).fold(
            { result.success(it) },
            { result.error(name, it.message, null) },
        )
    }

    private fun snapshotJson(): String {
        return JSONObject(buildTerminalStateJson(SH_NAME, DEX_NAME, copyMap())).toString()
    }

    private fun ok(): String = JSONObject().put("ok", true).toString()

    private fun copyMap(): Map<String, Any?> {
        val c = activity
        return linkedMapOf(
            "title" to c.getString(R.string.home_terminal_title),
            "back" to c.getString(R.string.action_back),
            "open" to c.getString(R.string.action_open),
            "rishDescription" to plainText(c.getString(R.string.rish_description, mono(SH_NAME))),
            "tutorial1" to plainText(c.getString(R.string.terminal_tutorial_1, mono(SH_NAME), mono(DEX_NAME))),
            "tutorial1Description" to c.getString(R.string.terminal_tutorial_1_description),
            "exportFiles" to c.getString(R.string.terminal_export_files),
            "tutorial2" to plainText(c.getString(R.string.terminal_tutorial_2, mono(SH_NAME))),
            "tutorial2Description" to plainText(
                c.getString(
                    R.string.terminal_tutorial_2_description,
                    "Termux",
                    mono("PKG"),
                    mono("com.termux"),
                    mono("com.termux"),
                ),
            ),
            "tutorial3" to plainText(c.getString(R.string.terminal_tutorial_3, mono("sh $SH_NAME"))),
            "tutorial3Description" to plainText(
                c.getString(R.string.terminal_tutorial_3_description, mono(SH_NAME), mono("PATH")),
            ),
        )
    }

    /** 同 `ShellTutorialComposeScreen.mono`。 */
    private fun mono(value: String): String = "<font face=\"monospace\">$value</font>"

    /** 同 `ShellTutorialComposeScreen.plainText`：去 HTML 标签、折叠空白。 */
    private fun plainText(value: String): String {
        return HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        const val CHANNEL = "shizuku/terminal"

        /** 文档树选择的请求码（"RT"）。宿主内唯一（RULEBOOK §1.6：终端 0x5254、配对 1001）。 */
        const val REQUEST_OPEN_DOCUMENT_TREE = 0x5254

        private const val SH_NAME = "rish"
        private const val DEX_NAME = "rish_shizuku.dex"
    }
}

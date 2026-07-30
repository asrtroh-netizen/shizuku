package moe.shizuku.manager.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update check against public GitHub Releases (same pattern as OneIMS).
 * Blocking — call on IO. No auth header; repo must be public with an .apk asset.
 */
object UpdateChecker {

    private const val REPO_OWNER = "asrtroh-netizen"
    private const val REPO_NAME = "shizuku"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    fun projectPageUrl(): String = "https://github.com/$REPO_OWNER/$REPO_NAME"

    private fun latestApiUrl() =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    fun checkLatest(context: Context): UpdateInfo {
        val current = BuildConfig.VERSION_NAME
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(latestApiUrl()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "Shizuku-UpdateChecker")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return fail(current, context.getString(R.string.update_http_fail, code))
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            parse(context, json, current)
        } catch (e: Throwable) {
            fail(
                current,
                context.getString(
                    R.string.update_net_fail,
                    e.message ?: context.getString(R.string.update_net_exception),
                ),
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(context: Context, json: String, current: String): UpdateInfo {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").ifBlank { obj.optString("name") }
        val notes = obj.optString("body").trim()
        val apkUrl = pickApkUrl(obj.optJSONArray("assets"))
        val latestDisplay = tag.trim().removePrefix("v").removePrefix("V")
        val newer = isRemoteNewer(tag, current)
        val msg = when {
            tag.isBlank() -> context.getString(R.string.update_no_version)
            newer && apkUrl.isNotBlank() ->
                context.getString(R.string.update_found, latestDisplay, current)
            newer -> context.getString(R.string.update_found_no_apk, latestDisplay)
            else -> context.getString(R.string.update_latest, current)
        }
        return UpdateInfo(
            hasUpdate = newer,
            currentVersion = current,
            latestVersion = latestDisplay,
            downloadUrl = apkUrl,
            releaseNotes = notes,
            message = msg,
        )
    }

    private fun pickApkUrl(assets: JSONArray?): String {
        if (assets == null || assets.length() == 0) return ""
        val preferred = mutableListOf<String>()
        val fallback = mutableListOf<String>()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = a.optString("browser_download_url")
            if (url.isBlank()) continue
            val n = name.lowercase()
            if (n.contains("shizuku") || n.contains("manager") || n.contains("release")) {
                preferred += url
            } else {
                fallback += url
            }
        }
        return preferred.firstOrNull() ?: fallback.firstOrNull().orEmpty()
    }

    private fun isRemoteNewer(remote: String, current: String): Boolean {
        val r = normalize(remote)
        val c = normalize(current)
        val n = maxOf(r.size, c.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun normalize(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

    private fun fail(current: String, message: String) =
        UpdateInfo(false, current, current, "", "", message)

    fun downloadAndInstall(context: Context, url: String, versionName: String) {
        val app = context.applicationContext
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val fileName = "Shizuku-$versionName.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Shizuku $versionName")
            .setDescription(app.getString(R.string.update_downloading))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName)
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (finishedId != downloadId) return
                runCatching {
                    val apkUri = dm.getUriForDownloadedFile(downloadId)
                    if (apkUri != null) {
                        val install = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(apkUri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        app.startActivity(install)
                    }
                }
                runCatching { app.unregisterReceiver(this) }
            }
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}

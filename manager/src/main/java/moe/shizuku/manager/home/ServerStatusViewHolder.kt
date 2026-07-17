package moe.shizuku.manager.home

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeServerStatusBinding
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.html.text.HtmlCompat
import rikka.html.text.toHtml
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants

class ServerStatusViewHolder(private val binding: HomeServerStatusBinding, root: View) :
    BaseViewHolder<ServiceStatus>(root), View.OnClickListener {

    companion object {
        val CREATOR = Creator<ServiceStatus> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeServerStatusBinding.inflate(inflater, outer.root, true)
            ServerStatusViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener(this)
    }

    private inline val textView get() = binding.text1
    private inline val summaryView get() = binding.text2
    private inline val iconView get() = binding.icon

    override fun onBind() {
        val context = itemView.context
        val status = data
        val ok = status.isRunning
        val isRoot = status.uid == 0
        val apiVersion = status.apiVersion
        val patchVersion = status.patchVersion
        if (ok) {
            iconView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_server_ok_24dp))
        } else {
            iconView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_server_error_24dp))
        }
        val user = if (isRoot) "root" else "adb"
        val versionLabel = if (ok) "${apiVersion}.${patchVersion}" else "—"
        val wifiOn = EnvironmentUtils.isWifiClientConnected(context)
        val wifiLabel = context.getString(
            if (wifiOn) R.string.home_status_wifi_on else R.string.home_status_wifi_off
        )

        val title = if (ok) {
            context.getString(R.string.home_status_service_is_running, context.getString(R.string.app_name))
        } else {
            context.getString(R.string.home_status_service_not_running, context.getString(R.string.app_name))
        }
        val summary = if (ok) {
            if (apiVersion != Shizuku.getLatestServiceVersion() || status.patchVersion != ShizukuApiConstants.SERVER_PATCH_VERSION) {
                context.getString(
                    R.string.home_status_service_version_update, user,
                    versionLabel,
                    "${Shizuku.getLatestServiceVersion()}.${ShizukuApiConstants.SERVER_PATCH_VERSION}"
                )
            } else {
                context.getString(R.string.home_status_service_version, user, versionLabel)
            }
        } else {
            context.getString(R.string.home_app_management_waiting_service)
        }
        textView.text = title.toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
        summaryView.text = summary.toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
        if (TextUtils.isEmpty(summaryView.text)) {
            summaryView.visibility = View.GONE
        } else {
            summaryView.visibility = View.VISIBLE
        }

        binding.chipMode.text = context.getString(R.string.home_status_chip_mode, if (ok) user else "—")
        binding.chipVersion.text = context.getString(R.string.home_status_chip_version, versionLabel)
        binding.chipUid.text = context.getString(
            R.string.home_status_chip_uid,
            if (ok) status.uid.toString() else "—"
        )
        binding.chipWifi.text = context.getString(R.string.home_status_chip_wifi, wifiLabel)
    }

    override fun onClick(v: View) {
        val context = v.context
        val status = data
        val ok = status.isRunning
        val user = if (status.uid == 0) "root" else "adb"
        val versionLabel = if (ok) "${status.apiVersion}.${status.patchVersion}" else "—"
        val wifiOn = EnvironmentUtils.isWifiClientConnected(context)
        val wifiLabel = context.getString(
            if (wifiOn) R.string.home_status_wifi_on else R.string.home_status_wifi_off
        )
        val stateLabel = if (ok) {
            context.getString(R.string.home_status_service_is_running, context.getString(R.string.app_name))
        } else {
            context.getString(R.string.home_status_service_not_running, context.getString(R.string.app_name))
        }
        val body = context.getString(
            R.string.home_status_detail_body,
            stateLabel,
            if (ok) user else "—",
            versionLabel,
            if (ok) status.uid.toString() else "—",
            wifiLabel,
            status.permission.toString(),
            ShizukuSettings.getTcpMode().toString(),
            ShizukuSettings.getStartOnBoot(context).toString(),
        )
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.home_status_detail_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

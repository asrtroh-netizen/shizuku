package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeActionTileBinding
import moe.shizuku.manager.databinding.HomeActionsGridBinding
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.management.ApplicationManagementActivity
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.core.util.ClipboardUtils
import rikka.html.text.HtmlCompat
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

data class HomeQuickGridPayload(
    val status: ServiceStatus,
    val grantedCount: Int,
    /** true = already running as root → show restart */
    val rootRestart: Boolean,
)

/**
 * 2×2 quick entry: Apps / Terminal / Root / PC ADB.
 * Tap → short popup → original action.
 */
class HomeQuickGridViewHolder(
    private val binding: HomeActionsGridBinding,
    root: android.view.View,
) : BaseViewHolder<HomeQuickGridPayload>(root) {

    companion object {
        val CREATOR = Creator<HomeQuickGridPayload> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeActionsGridBinding.inflate(inflater, outer.root, true)
            HomeQuickGridViewHolder(inner, outer.root)
        }
    }

    override fun onBind() {
        val context = itemView.context
        val running = data.status.isRunning
        val rooted = EnvironmentUtils.isRooted()

        val appsSub = when {
            !running || data.grantedCount == -1 -> context.getString(R.string.home_checks_apps_sub_waiting)
            data.grantedCount == -2 -> context.getString(R.string.home_app_management_binder_unavailable)
            else -> context.resources.getQuantityString(
                R.plurals.home_app_management_authorized_apps_count,
                data.grantedCount,
                data.grantedCount,
            )
        }

        bindTile(binding.tileApps, R.drawable.ic_settings_outline_24dp, R.string.home_app_management_title, subtitleText = appsSub) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.home_app_management_title)
                .setMessage(appsSub)
                .setNegativeButton(android.R.string.cancel, null)
                .apply {
                    if (running && data.grantedCount >= 0) {
                        setPositiveButton(R.string.home_app_management_view_authorized_apps) { _, _ ->
                            context.startActivity(Intent(context, ApplicationManagementActivity::class.java))
                        }
                    } else {
                        setPositiveButton(android.R.string.ok, null)
                    }
                }
                .show()
        }

        bindTile(
            binding.tileTerminal,
            R.drawable.ic_terminal_24,
            R.string.home_terminal_title,
            R.string.home_terminal_description,
            enabled = running && data.status.permission,
        ) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.home_terminal_title)
                .setMessage(
                    if (running && data.status.permission) context.getString(R.string.home_terminal_description)
                    else context.getString(R.string.home_status_service_not_running, context.getString(R.string.app_name)),
                )
                .setNegativeButton(android.R.string.cancel, null)
                .apply {
                    if (running && data.status.permission) {
                        setPositiveButton(android.R.string.ok) { _, _ ->
                            context.startActivity(Intent(context, ShellTutorialActivity::class.java))
                        }
                    } else {
                        setPositiveButton(android.R.string.ok, null)
                    }
                }
                .show()
        }

        // Always keep the 2×2 (four tiles). Non-root devices still show Root, but disabled.
        binding.tileRoot.root.isVisible = true
        val rootAction = if (data.rootRestart) R.string.home_root_button_restart else R.string.home_root_button_start
        bindTile(
            binding.tileRoot,
            R.drawable.ic_root_24dp,
            R.string.home_root_title_plain,
            subtitleRes = if (rooted) rootAction else R.string.home_root_tile_unavailable,
            enabled = rooted,
        ) {
            if (!rooted) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.home_root_title_plain)
                    .setMessage(R.string.home_root_tile_unavailable_detail)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@bindTile
            }
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.home_root_title_plain)
                .setMessage(R.string.home_root_tile_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(rootAction) { _, _ ->
                    context.startActivity(
                        Intent(context, StarterActivity::class.java).putExtra(StarterActivity.EXTRA_IS_ROOT, true),
                    )
                }
                .show()
        }

        bindTile(
            binding.tileAdb,
            R.drawable.ic_server_ok_24dp,
            R.string.home_adb_title_plain,
            R.string.home_checks_adb_sub,
        ) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.home_adb_title_plain)
                .setMessage(
                    context.getString(R.string.home_adb_description, Helps.ADB.get())
                        .toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE),
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_adb_button_view_help) { _, _ ->
                    CustomTabsHelper.launchUrlOrCopy(context, Helps.ADB.get())
                }
                .setNeutralButton(R.string.home_adb_button_view_command) { _, _ ->
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.home_adb_button_view_command)
                        .setMessage(
                            HtmlCompat.fromHtml(
                                context.getString(R.string.home_adb_dialog_view_command_message, Starter.adbCommand),
                            ),
                        )
                        .setPositiveButton(R.string.home_adb_dialog_view_command_copy_button) { _, _ ->
                            if (ClipboardUtils.put(context, Starter.adbCommand)) {
                                Toast.makeText(context, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                .show()
        }
    }

    private fun bindTile(
        tile: HomeActionTileBinding,
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int? = null,
        subtitleText: String? = null,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        tile.tileIcon.setImageResource(iconRes)
        tile.tileTitle.setText(titleRes)
        tile.tileSubtitle.text = subtitleText ?: tile.root.context.getString(subtitleRes!!)
        // Compact: hide long subtitles on quick tiles — title only look
        tile.tileSubtitle.isVisible = !subtitleText.isNullOrBlank() || subtitleRes != null
        // Wireframe: no long copy on tiles — keep one short line max
        tile.root.isEnabled = enabled
        tile.root.alpha = if (enabled) 1f else 0.45f
        tile.root.setOnClickListener { onClick() }
    }
}

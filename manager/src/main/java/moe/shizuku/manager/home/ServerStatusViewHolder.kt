package moe.shizuku.manager.home

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeServerStatusBinding
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants

/**
 * Two-state hero card: inactive (red) / ready (white + Shizuku Active).
 * Activate CTA removed — use the wireless start tile below. Autostart stays on boot/Wi‑Fi path.
 */
class ServerStatusViewHolder(
    private val binding: HomeServerStatusBinding,
    root: View,
    private val scope: CoroutineScope,
) : BaseViewHolder<ServiceStatus>(root) {

    enum class HeroState { INACTIVE, READY }

    companion object {
        fun creator(scope: CoroutineScope): Creator<ServiceStatus> {
            return Creator { inflater: LayoutInflater, parent: ViewGroup? ->
                val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
                val inner = HomeServerStatusBinding.inflate(inflater, outer.root, true)
                ServerStatusViewHolder(inner, outer.root, scope)
            }
        }
    }

    init {
        root.setOnClickListener { showDetail() }
    }

    override fun onBind() {
        val context = itemView.context
        val status = data
        val hero = resolveHeroState(status)
        val (bg, fg) = colorsFor(hero)
        (itemView as? MaterialCardView)?.apply {
            isChecked = false
            setCardBackgroundColor(bg)
            strokeWidth = 0
        }

        binding.heroIcon.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                when (hero) {
                    HeroState.INACTIVE -> R.drawable.ic_server_error_24dp
                    HeroState.READY -> R.drawable.ic_server_ok_24dp
                },
            ),
        )
        binding.heroIcon.imageTintList = ColorStateList.valueOf(fg)

        binding.heroEyebrow.setTextColor(ColorUtils.setAlphaComponent(fg, 184))
        binding.heroTitle.setTextColor(fg)
        binding.heroPill.setTextColor(fg)
        binding.heroSubtitle.setTextColor(fg)
        binding.heroDetail.setTextColor(ColorUtils.setAlphaComponent(fg, 200))

        // Ready: "Shizuku" + pill "Active". Inactive: title/pill 未激活.
        when (hero) {
            HeroState.READY -> {
                binding.heroTitle.text = context.getString(R.string.app_name)
                binding.heroPill.text = context.getString(R.string.home_hero_pill_ready)
                binding.heroSubtitle.isVisible = false
                binding.heroDetail.isVisible = false
            }
            HeroState.INACTIVE -> {
                binding.heroTitle.text = context.getString(R.string.home_hero_title_inactive)
                binding.heroPill.text = context.getString(R.string.home_hero_pill_inactive)
                binding.heroSubtitle.isVisible = true
                binding.heroDetail.isVisible = true
                binding.heroSubtitle.setText(R.string.home_hero_subtitle_inactive)
                binding.heroDetail.setText(R.string.home_hero_detail_inactive)
            }
        }

        applyStageStrip(hero, fg)
    }

    private fun resolveHeroState(status: ServiceStatus): HeroState {
        val running = status.isRunning || Shizuku.pingBinder() ||
            ShizukuStateMachine.get() == ShizukuStateMachine.State.RUNNING
        if (running) return HeroState.READY
        // Prefer activating UI still maps to inactive visually (no third CTA state).
        return HeroState.INACTIVE
    }

    private fun colorsFor(hero: HeroState): Pair<Int, Int> {
        val c = itemView.context
        return when (hero) {
            HeroState.INACTIVE ->
                ContextCompat.getColor(c, R.color.hero_inactive_bg) to
                    ContextCompat.getColor(c, R.color.hero_inactive_fg)
            HeroState.READY ->
                ContextCompat.getColor(c, R.color.hero_ready_bg) to
                    ContextCompat.getColor(c, R.color.hero_ready_fg)
        }
    }

    private fun applyStageStrip(hero: HeroState, fg: Int) {
        val litCount = if (hero == HeroState.READY) 2 else 1
        val dots = listOf(binding.stageDot1, binding.stageDot2)
        val labels = listOf(binding.stageLabel1, binding.stageLabel2)
        val line = binding.stageLine1
        dots.forEachIndexed { index, view ->
            val lit = index < litCount
            val color = if (lit) fg else ColorUtils.setAlphaComponent(fg, 82)
            view.backgroundTintList = ColorStateList.valueOf(color)
            labels[index].setTextColor(color)
        }
        line.setBackgroundColor(
            if (litCount > 1) ColorUtils.setAlphaComponent(fg, 140)
            else ColorUtils.setAlphaComponent(fg, 46),
        )
    }

    private fun showDetail() {
        val context = itemView.context
        val status = data
        val ok = status.isRunning
        val user = if (status.uid == 0) "root" else "adb"
        val versionLabel = if (ok) "${status.apiVersion}.${status.patchVersion}" else "—"
        val wifiOn = EnvironmentUtils.isWifiClientConnected(context)
        val wifiLabel = context.getString(
            if (wifiOn) R.string.home_status_wifi_on else R.string.home_status_wifi_off,
        )
        val stateLabel = when (resolveHeroState(status)) {
            HeroState.INACTIVE -> context.getString(R.string.home_hero_pill_inactive)
            HeroState.READY -> context.getString(R.string.home_hero_pill_ready)
        }
        val latest = "${Shizuku.getLatestServiceVersion()}.${ShizukuApiConstants.SERVER_PATCH_VERSION}"
        val body = context.getString(
            R.string.home_status_detail_body,
            stateLabel,
            if (ok) user else "—",
            if (ok) versionLabel else "— ($latest)",
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

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
 * OneKuku-style four-state hero card:
 * inactive (red) → activating → ready (white) → sleeping (white, no watchdog).
 */
class ServerStatusViewHolder(
    private val binding: HomeServerStatusBinding,
    root: View,
    private val scope: CoroutineScope,
) : BaseViewHolder<ServiceStatus>(root) {

    enum class HeroState { INACTIVE, ACTIVATING, READY, SLEEPING }

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
        // Whole card opens detail; primary button handles activate / detail.
        root.setOnClickListener { showDetail() }
        binding.heroAction.setOnClickListener { onPrimaryAction() }
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
                    HeroState.ACTIVATING -> R.drawable.ic_server_error_24dp
                    else -> R.drawable.ic_server_ok_24dp
                },
            ),
        )
        binding.heroIcon.imageTintList = ColorStateList.valueOf(fg)

        binding.heroEyebrow.setTextColor(ColorUtils.setAlphaComponent(fg, 184))
        binding.heroTitle.setTextColor(fg)
        binding.heroPill.setTextColor(fg)
        binding.heroSubtitle.setTextColor(fg)
        binding.heroDetail.setTextColor(ColorUtils.setAlphaComponent(fg, 200))
        binding.heroActionSub.setTextColor(ColorUtils.setAlphaComponent(fg, 184))

        val settled = hero == HeroState.READY || hero == HeroState.SLEEPING
        binding.heroTitle.text = when (hero) {
            HeroState.READY, HeroState.SLEEPING -> context.getString(R.string.app_name)
            HeroState.INACTIVE -> context.getString(R.string.home_hero_title_inactive)
            HeroState.ACTIVATING -> context.getString(R.string.home_hero_title_activating)
        }
        binding.heroPill.text = context.getString(
            when (hero) {
                HeroState.INACTIVE -> R.string.home_hero_pill_inactive
                HeroState.ACTIVATING -> R.string.home_hero_pill_activating
                HeroState.READY -> R.string.home_hero_pill_ready
                HeroState.SLEEPING -> R.string.home_hero_pill_sleeping
            },
        )
        binding.heroSubtitle.isVisible = !settled
        binding.heroDetail.isVisible = !settled
        if (!settled) {
            binding.heroSubtitle.setText(
                when (hero) {
                    HeroState.INACTIVE -> R.string.home_hero_subtitle_inactive
                    HeroState.ACTIVATING -> R.string.home_hero_subtitle_activating
                    else -> R.string.home_hero_subtitle_inactive
                },
            )
            binding.heroDetail.setText(
                when (hero) {
                    HeroState.INACTIVE -> R.string.home_hero_detail_inactive
                    HeroState.ACTIVATING -> R.string.home_hero_detail_activating
                    else -> R.string.home_hero_detail_inactive
                },
            )
        }

        applyStageStrip(resolveLitCount(hero), fg)

        val busy = hero == HeroState.ACTIVATING
        binding.heroAction.isEnabled = !busy
        binding.heroAction.text = context.getString(
            when (hero) {
                HeroState.INACTIVE -> R.string.home_hero_action_activate
                HeroState.ACTIVATING -> R.string.home_hero_action_activating
                HeroState.READY, HeroState.SLEEPING -> R.string.home_hero_action_detail
            },
        )
        binding.heroActionSub.isVisible = hero == HeroState.INACTIVE
        if (hero == HeroState.INACTIVE) {
            binding.heroActionSub.setText(R.string.home_hero_action_activate_sub)
        }
    }

    private fun resolveHeroState(status: ServiceStatus): HeroState {
        // Binder / status wins over transient STARTING — avoids blue flicker while already up.
        val running = status.isRunning || Shizuku.pingBinder() ||
            ShizukuStateMachine.get() == ShizukuStateMachine.State.RUNNING
        if (running) {
            return if (ShizukuSettings.isWatchdogRunning()) HeroState.READY else HeroState.SLEEPING
        }
        return when (ShizukuStateMachine.get()) {
            ShizukuStateMachine.State.STARTING,
            ShizukuStateMachine.State.STOPPING,
            -> HeroState.ACTIVATING
            else -> HeroState.INACTIVE
        }
    }

    private fun resolveLitCount(hero: HeroState): Int = when (hero) {
        HeroState.INACTIVE -> 1
        HeroState.ACTIVATING -> 2
        // Running (ready / sleeping) lights all four stages — READY used to stop at 3.
        HeroState.READY, HeroState.SLEEPING -> 4
    }

    private fun colorsFor(hero: HeroState): Pair<Int, Int> {
        val c = itemView.context
        // OneKuku look: red when inactive; white for activating / ready / sleeping (no blue).
        return when (hero) {
            HeroState.INACTIVE ->
                ContextCompat.getColor(c, R.color.hero_inactive_bg) to
                    ContextCompat.getColor(c, R.color.hero_inactive_fg)
            HeroState.ACTIVATING,
            HeroState.READY,
            HeroState.SLEEPING,
            ->
                ContextCompat.getColor(c, R.color.hero_ready_bg) to
                    ContextCompat.getColor(c, R.color.hero_ready_fg)
        }
    }

    private fun applyStageStrip(litCount: Int, fg: Int) {
        val dots = listOf(binding.stageDot1, binding.stageDot2, binding.stageDot3, binding.stageDot4)
        val labels = listOf(binding.stageLabel1, binding.stageLabel2, binding.stageLabel3, binding.stageLabel4)
        val lines = listOf(binding.stageLine1, binding.stageLine2, binding.stageLine3)
        dots.forEachIndexed { index, view ->
            val lit = index < litCount
            val color = if (lit) fg else ColorUtils.setAlphaComponent(fg, 82)
            view.backgroundTintList = ColorStateList.valueOf(color)
            labels[index].setTextColor(color)
        }
        lines.forEachIndexed { index, view ->
            val lit = index + 1 < litCount
            view.setBackgroundColor(
                if (lit) ColorUtils.setAlphaComponent(fg, 140)
                else ColorUtils.setAlphaComponent(fg, 46),
            )
        }
    }

    private fun onPrimaryAction() {
        val hero = resolveHeroState(data)
        when (hero) {
            HeroState.INACTIVE -> StartWirelessAdbViewHolder.start(itemView.context, scope)
            HeroState.ACTIVATING -> Unit
            HeroState.READY, HeroState.SLEEPING -> showDetail()
        }
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
            HeroState.ACTIVATING -> context.getString(R.string.home_hero_pill_activating)
            HeroState.READY -> context.getString(R.string.home_hero_pill_ready)
            HeroState.SLEEPING -> context.getString(R.string.home_hero_pill_sleeping)
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

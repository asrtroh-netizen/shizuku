package moe.shizuku.manager.home

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import moe.shizuku.manager.management.AppsViewModel
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.recyclerview.IdBasedRecyclerViewAdapter
import rikka.recyclerview.IndexCreatorPool

class HomeAdapter(private val homeModel: HomeViewModel, private val appsModel: AppsViewModel, private val scope: CoroutineScope) :
    IdBasedRecyclerViewAdapter(ArrayList()) {

    init {
        updateData()
        setHasStableIds(true)
    }

    companion object {
        private const val ID_STATUS = 0L
        private const val ID_WADB = 1L
        private const val ID_QUICK = 2L
        private const val ID_LEARN_MORE = 6L
        private const val ID_ADB_PERMISSION_LIMITED = 7L
        private const val ID_AUTOMATION = 8L
        private const val ID_STEALTH = 9L
    }

    override fun onCreateCreatorPool(): IndexCreatorPool {
        return IndexCreatorPool()
    }

    fun updateData() {
        val status = homeModel.serviceStatus.value?.data ?: return
        val grantedCount = appsModel.grantedCount.value?.data ?: 0
        val adbPermission = status.permission
        val running = status.isRunning
        val isPrimaryUser = UserHandleCompat.myUserId() == 0

        clear()
        addItem(ServerStatusViewHolder.creator(scope), status, ID_STATUS)

        // ① Independent wireless card (no long description)
        if (isPrimaryUser &&
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
                EnvironmentUtils.isTelevision() ||
                EnvironmentUtils.getAdbTcpPort() > 0)
        ) {
            addItem(StartWirelessAdbViewHolder.creator(scope), null, ID_WADB)
        }

        val appsCount = when {
            !running -> -1
            appsModel.grantedCount.value?.status == rikka.lifecycle.Status.ERROR -> -2
            else -> grantedCount
        }
        // ② Quick 2×2: Apps / Terminal / Root / PC ADB
        addItem(
            HomeQuickGridViewHolder.CREATOR,
            HomeQuickGridPayload(
                status = status,
                grantedCount = appsCount,
                rootRestart = running && status.uid == 0,
            ),
            ID_QUICK,
        )

        if (running && !adbPermission) {
            addItem(AdbPermissionLimitedViewHolder.CREATOR, status, ID_ADB_PERMISSION_LIMITED)
        }
        addItem(AutomationViewHolder.CREATOR, null, ID_AUTOMATION)
        addItem(StealthViewHolder.CREATOR, null, ID_STEALTH)
        addItem(LearnMoreViewHolder.CREATOR, null, ID_LEARN_MORE)
        notifyDataSetChanged()
    }
}

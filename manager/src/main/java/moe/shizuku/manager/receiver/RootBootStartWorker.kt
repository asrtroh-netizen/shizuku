package moe.shizuku.manager.receiver

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku

internal const val ROOT_BOOT_MAX_ATTEMPTS = 3

internal enum class RootBootAttemptOutcome {
    SUCCESS,
    RETRY,
    FAILURE,
}

internal fun rootBootAttemptOutcome(
    commandResult: RootStartResult,
    binderReady: Boolean,
    runAttemptCount: Int,
): RootBootAttemptOutcome = when {
    commandResult is RootStartResult.Success && binderReady -> RootBootAttemptOutcome.SUCCESS
    runAttemptCount + 1 < ROOT_BOOT_MAX_ATTEMPTS -> RootBootAttemptOutcome.RETRY
    else -> RootBootAttemptOutcome.FAILURE
}

internal fun shouldDisarmRecoveryForInactiveRootWork(
    rootBootEnabled: Boolean,
    wirelessBootEnabled: Boolean,
    isPrimaryUser: Boolean,
): Boolean = !isPrimaryUser || (!rootBootEnabled && !wirelessBootEnabled)

class RootBootStartWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (ShizukuSettings.getPreferences() == null) {
            ShizukuSettings.initialize(applicationContext)
        }

        val preferences = ShizukuSettings.getPreferences()
        val rootBootEnabled = preferences
            .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT, false)
        val wirelessBootEnabled = preferences
            .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)
        val isPrimaryUser = UserHandleCompat.myUserId() == 0
        if (!rootBootEnabled || !isPrimaryUser) {
            val shouldDisarmRecovery = shouldDisarmRecoveryForInactiveRootWork(
                rootBootEnabled,
                wirelessBootEnabled,
                isPrimaryUser,
            )
            if (shouldDisarmRecovery) {
                UserPresentRestartReceiver.setEnabled(applicationContext, false)
            }
            return Result.success()
        }

        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            UserPresentRestartReceiver.setEnabled(applicationContext, false)
            return Result.success()
        }

        val result = withContext(Dispatchers.IO) {
            ShizukuReceiverStarter.startRootNow()
        }
        var binderReady = false
        if (result is RootStartResult.Success) {
            Log.i(
                AppConstants.TAG,
                "root boot: command succeeded, waiting for binder " +
                    "(method=${result.method}, exit=${result.exitCode})",
            )
            binderReady = waitForBinder()
            if (!binderReady) {
                Log.w(AppConstants.TAG, "root boot: command exited 0 but binder did not arrive")
            }
        }

        val failure = result as? RootStartResult.Failure
        return when (rootBootAttemptOutcome(result, binderReady, runAttemptCount)) {
            RootBootAttemptOutcome.SUCCESS -> {
                UserPresentRestartReceiver.setEnabled(applicationContext, false)
                Result.success()
            }
            RootBootAttemptOutcome.RETRY -> {
                logAttemptFailure(result, failure)
                Result.retry()
            }
            RootBootAttemptOutcome.FAILURE -> {
                logAttemptFailure(result, failure)
                // A later lock/unlock is a fresh, user-driven opportunity for Magisk to be ready.
                UserPresentRestartReceiver.setEnabled(applicationContext, true)
                Result.failure()
            }
        }
    }

    private fun logAttemptFailure(
        result: RootStartResult,
        failure: RootStartResult.Failure?,
    ) {
        Log.w(
            AppConstants.TAG,
            "root boot: attempt ${runAttemptCount + 1} failed " +
                "(method=${result.method}, exit=${failure?.exitCode})",
            failure?.error,
        )
    }

    private suspend fun waitForBinder(): Boolean {
        repeat(BINDER_POLL_COUNT) {
            if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
                return true
            }
            delay(BINDER_POLL_INTERVAL_MS)
        }
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "root_boot_start"
        private const val BINDER_POLL_COUNT = 32
        private const val BINDER_POLL_INTERVAL_MS = 250L

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RootBootStartWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

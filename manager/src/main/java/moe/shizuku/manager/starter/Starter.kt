package moe.shizuku.manager.starter

import androidx.lifecycle.asFlow
import java.io.File
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import moe.shizuku.manager.ShizukuApplication
import moe.shizuku.manager.utils.ShizukuStateMachine

private val app = ShizukuApplication.application

object Starter {

    private val starterFile = File(app.applicationInfo.nativeLibraryDir, "libshizuku.so")

    val userCommand: String = starterFile.absolutePath
    val adbCommand = "adb shell $userCommand"
    val internalCommand = "$userCommand --apk=${app.applicationInfo.sourceDir}"

    val serviceStartedMessage = "Service started, this window will be automatically closed in 3 seconds"

    suspend fun waitForBinder(timeoutMs: Long = 60_000L, log: ((String) -> Unit)? = null) {
        try {
            log?.invoke("\nWaiting for service. This may take up to ${timeoutMs / 1000} seconds...")
            withTimeout(timeoutMs) {
                ShizukuStateMachine.asFlow()
                    .first { it == ShizukuStateMachine.State.RUNNING }
            }
            log?.invoke(serviceStartedMessage)
        } catch (e: TimeoutCancellationException) {
            throw TimeoutException("Failed to receive binder within ${timeoutMs / 1000} seconds")
        }
    }

}

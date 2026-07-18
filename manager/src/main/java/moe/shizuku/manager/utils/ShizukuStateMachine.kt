package moe.shizuku.manager.utils

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import moe.shizuku.manager.ShizukuApplication
import moe.shizuku.manager.ShizukuSettings
import rikka.shizuku.Shizuku

private val appContext = ShizukuApplication.appContext

object ShizukuStateMachine {

    enum class State { STARTING, RUNNING, STOPPING, STOPPED, CRASHED }

    private var state = AtomicReference<State>(State.STOPPED)
    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    /** ElapsedRealtime when we last entered STARTING — used to absorb brief STOPPED races in UI. */
    private val startingEnteredAt = AtomicLong(0L)
    private val leftStartingAt = AtomicLong(0L)

    init {
        Shizuku.addBinderReceivedListenerSticky(
            Shizuku.OnBinderReceivedListener { set(State.RUNNING) }
        )
        Shizuku.addBinderDeadListener(
            Shizuku.OnBinderDeadListener { setDead() }
        )
    }

    fun get(): State = state.get()

    private fun transition(transform: (State) -> State) {
        val oldState = state.getAndUpdate(transform)
        val newState = transform(oldState)
        if (oldState != newState) {
            if (newState == State.STARTING) {
                startingEnteredAt.set(SystemClock.elapsedRealtime())
            } else if (oldState == State.STARTING) {
                leftStartingAt.set(SystemClock.elapsedRealtime())
            }
            // AdbStarter / SelfStarter run off main; UI listeners must not touch views there.
            dispatchListeners(newState)
            Log.d("ShizukuStateMachine", newState.toString())
        }
    }

    private fun dispatchListeners(newState: State) {
        val notify = {
            listeners.forEach { listener ->
                runCatching { listener(newState) }
                    .onFailure { Log.w("ShizukuStateMachine", "listener failed", it) }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notify()
        } else {
            mainHandler.post(notify)
        }
    }

    fun set(newState: State) = transition { newState }

    fun setDead() = transition {
        when (it) {
            State.RUNNING -> State.CRASHED
            State.STARTING -> State.STOPPED
            State.STOPPING -> {
                try {
                    val permissionGranted = appContext.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
                    val shouldDisableUsbDebugging = permissionGranted && ShizukuSettings.getAutoDisableUsbDebugging()
                    if (shouldDisableUsbDebugging) {
                        Settings.Global.putInt(appContext.contentResolver, Settings.Global.ADB_ENABLED, 0)
                    }
                } catch (e: Exception) {
                    Log.w("ShizukuStateMachine", "Failed to disable USB debugging", e)
                }
                State.STOPPED
            }
            else -> it
        }
    }

    fun update(): State {
        val next = if (Shizuku.pingBinder()) State.RUNNING else State.STOPPED
        set(next)
        return next
    }

    fun isRunning(): Boolean = get() == State.RUNNING

    fun isDead(): Boolean = get() == State.STOPPED || get() == State.CRASHED

    /**
     * UI should show「激活中」while STARTING/STOPPING, and for a short window after leaving
     * STARTING for non-RUNNING — cancels/REPLACE used to flash INACTIVE(red) ↔ ACTIVATING.
     */
    fun preferActivatingUi(graceMs: Long = 2_000L): Boolean {
        return when (get()) {
            State.STARTING, State.STOPPING -> true
            State.RUNNING -> false
            else -> {
                val left = leftStartingAt.get()
                left > 0L && SystemClock.elapsedRealtime() - left < graceMs
            }
        }
    }

    /** True if STARTING has been stuck long enough that a user retry is warranted. */
    fun isStartingStale(staleMs: Long = 30_000L): Boolean {
        if (get() != State.STARTING) return false
        val entered = startingEnteredAt.get()
        return entered > 0L && SystemClock.elapsedRealtime() - entered >= staleMs
    }

    fun addListener(listener: (State) -> Unit) {
        listeners.add(listener)
        val current = state.get()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { listener(current) }
        } else {
            mainHandler.post { runCatching { listener(current) } }
        }
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners.remove(listener)
    }

    fun asFlow(): Flow<State> = callbackFlow {
        val listener: (State) -> Unit = { trySend(it).isSuccess }
        addListener(listener)
        awaitClose { removeListener(listener) }
    }
}

package moe.shizuku.manager.ktx

import android.content.Context
import android.os.UserManager
import moe.shizuku.manager.ShizukuApplication

val Context.application: ShizukuApplication
    get() {
        return applicationContext as ShizukuApplication
    }

fun Context.createDeviceProtectedStorageContextCompat(): Context {
    return createDeviceProtectedStorageContext()
}

fun Context.createDeviceProtectedStorageContextCompatWhenLocked(): Context {
    return if (getSystemService(UserManager::class.java)?.isUserUnlocked != true) {
        createDeviceProtectedStorageContext()
    } else {
        this
    }
}
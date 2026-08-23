package moe.shizuku.manager.management

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class GrantQuery(
    val packageName: String,
    val uid: Int,
)

internal fun packageGrantKey(packageName: String, uid: Int): String = "$packageName#$uid"

internal fun buildGrantStateMap(
    apps: List<GrantQuery>,
    granted: (packageName: String, uid: Int) -> Boolean,
): Map<String, Boolean> {
    if (apps.isEmpty()) return emptyMap()
    val next = LinkedHashMap<String, Boolean>(apps.size)
    for (app in apps) {
        next[packageGrantKey(app.packageName, app.uid)] = granted(app.packageName, app.uid)
    }
    return next
}

internal suspend fun loadGrantStates(
    apps: List<GrantQuery>,
    granted: (packageName: String, uid: Int) -> Boolean,
): Map<String, Boolean> = withContext(Dispatchers.IO) {
    buildGrantStateMap(apps, granted)
}

internal object GrantedCountCache {
    @Volatile
    var value: Int = -1
}

internal fun resolveGrantedCount(cached: Int, scan: () -> Int): Int {
    if (cached >= 0) return cached
    val fresh = scan()
    if (fresh >= 0) GrantedCountCache.value = fresh
    return fresh
}

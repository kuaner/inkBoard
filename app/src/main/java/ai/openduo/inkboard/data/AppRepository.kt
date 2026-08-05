package ai.openduo.inkboard.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    /** A small, useful first-run order; users can replace every position later. */
    private val preferredPackages = listOf(
        "ai.openduo.inkflow",
        "info.plateaukao.einkbro",
        "com.tencent.weread.eink",
        "com.kuaner.inkreader",
        "org.chromium.chrome",
        "com.github.metacubex.clash.meta"
    )

    /**
     * Resolve only the apps needed for home slots.
     *
     * The full launcher scan can take a long time on this tablet because every
     * package label and icon is loaded. Home only needs the eight saved keys.
     */
    suspend fun resolveByKeys(keys: Collection<String>): Map<String, AppInfo> =
        withContext(Dispatchers.IO) {
            if (keys.isEmpty()) return@withContext emptyMap()
            val pm = context.packageManager
            val builtIns = builtInShortcuts().associateBy { it.key }
            val resolved = LinkedHashMap<String, AppInfo>(keys.size)
            for (key in keys) {
                if (key in resolved) continue
                val builtIn = builtIns[key]
                if (builtIn != null) {
                    resolved[key] = builtIn
                    continue
                }
                val packageName = key.substringBefore('/')
                val activityName = key.substringAfter('/', missingDelimiterValue = "")
                if (packageName.isBlank() || activityName.isBlank()) continue
                runCatching {
                    val component = ComponentName(packageName, activityName)
                    val activityInfo = pm.getActivityInfo(component, 0)
                    val label = activityInfo.loadLabel(pm)?.toString()?.trim().orEmpty()
                    if (label.isEmpty()) return@runCatching
                    resolved[key] = AppInfo(
                        label = label,
                        packageName = packageName,
                        activityName = activityName,
                        icon = activityInfo.loadIcon(pm)
                    )
                }
            }
            resolved
        }

    suspend fun loadLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList: List<ResolveInfo> =
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val launchableApps = resolveList
            .mapNotNull { ri ->
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                AppInfo(
                    label = label,
                    packageName = pkg,
                    activityName = ri.activityInfo.name,
                    icon = ri.loadIcon(pm)
                )
            }
            .sortedWith(
                compareBy<AppInfo> { app ->
                    val idx = preferredPackages.indexOf(app.packageName)
                    if (idx >= 0) idx else preferredPackages.size
                }.thenBy { it.label.lowercase() }
            )
        builtInShortcuts() + launchableApps
    }

    fun defaultSlotKeys(apps: List<AppInfo>): List<String> {
        val normalApps = apps.filter { it.builtInShortcut == null }
        val byPkg = normalApps.groupBy { it.packageName }
        val picked = linkedSetOf<String>()
        for (pkg in preferredPackages) {
            val app = byPkg[pkg]?.firstOrNull() ?: continue
            picked += app.key
            if (picked.size >= PreferencesRepository.SLOT_COUNT) break
        }
        if (picked.size < PreferencesRepository.SLOT_COUNT) {
            for (app in normalApps) {
                if (!isGoodDefaultCandidate(app)) continue
                if (picked.add(app.key) && picked.size >= PreferencesRepository.SLOT_COUNT) break
            }
        }
        return picked.toList()
    }

    private fun builtInShortcuts(): List<AppInfo> {
        val shortcutPackage = "${context.packageName}.shortcut"
        return listOf(
            AppInfo(
                label = "清理后台",
                packageName = shortcutPackage,
                activityName = BuiltInShortcut.CLEAR_BACKGROUND.id,
                icon = null,
                builtInShortcut = BuiltInShortcut.CLEAR_BACKGROUND
            ),
            AppInfo(
                label = "全刷屏幕",
                packageName = shortcutPackage,
                activityName = BuiltInShortcut.FULL_REFRESH.id,
                icon = null,
                builtInShortcut = BuiltInShortcut.FULL_REFRESH
            ),
            AppInfo(
                label = "锁屏",
                packageName = shortcutPackage,
                activityName = BuiltInShortcut.LOCK_SCREEN.id,
                icon = null,
                builtInShortcut = BuiltInShortcut.LOCK_SCREEN
            )
        )
    }

    private fun isGoodDefaultCandidate(app: AppInfo): Boolean {
        val applicationFlags = runCatching {
            context.packageManager.getApplicationInfo(app.packageName, 0).flags
        }.getOrDefault(ApplicationInfo.FLAG_SYSTEM)
        return applicationFlags and ApplicationInfo.FLAG_SYSTEM == 0 &&
            !app.packageName.startsWith("com.android.") &&
            app.packageName != "com.onyx" &&
            !app.packageName.startsWith("com.onyx.") &&
            !app.packageName.startsWith("com.google.android.inputmethod")
    }

    fun launchApp(app: AppInfo): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

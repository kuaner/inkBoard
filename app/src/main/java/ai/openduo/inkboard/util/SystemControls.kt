package ai.openduo.inkboard.util

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.Surface
import android.widget.Toast
import android.widget.TextView
import ai.openduo.inkboard.admin.InkBoardDeviceAdminReceiver

/** The four rotations that can be selected directly from the launcher. */
enum class OrientationMode {
    PORTRAIT,
    LANDSCAPE,
    PORTRAIT_REVERSE,
    LANDSCAPE_REVERSE
}

object SystemControls {

    private const val YITOA_SERVER_PACKAGE = "com.yitoa.rk.zyb.server"
    private const val BROADCAST_SOURCE = "yitoa"

    private const val ACTION_ADB_ENABLE = "yitoa.adb.enable"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isAdbEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun setAdbEnabled(context: Context, enabled: Boolean): Boolean {
        return try {
            // Yitoa's privileged broadcast service owns this setting on the
            // device.  Writing Settings.Global directly requires a signature
            // permission and silently makes the control useless for a normal
            // launcher APK.
            context.sendBroadcast(
                Intent(ACTION_ADB_ENABLE)
                    .setPackage(YITOA_SERVER_PACKAGE)
                    .putExtra("enable", enabled)
                    .putExtra("source", BROADCAST_SOURCE)
            )
            true
        } catch (_: Exception) {
            showToast(context, "无法修改 ADB")
            false
        }
    }

    fun getOrientationMode(context: Context): OrientationMode {
        return try {
            when (Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 1)) {
                Surface.ROTATION_0 -> OrientationMode.PORTRAIT
                Surface.ROTATION_90 -> OrientationMode.LANDSCAPE
                Surface.ROTATION_180 -> OrientationMode.PORTRAIT_REVERSE
                Surface.ROTATION_270 -> OrientationMode.LANDSCAPE_REVERSE
                else -> OrientationMode.LANDSCAPE
            }
        } catch (_: Exception) {
            OrientationMode.LANDSCAPE
        }
    }

    fun setOrientationMode(context: Context, mode: OrientationMode): Boolean {
        val rotation = when (mode) {
            OrientationMode.PORTRAIT -> Surface.ROTATION_0
            OrientationMode.LANDSCAPE -> Surface.ROTATION_90
            OrientationMode.PORTRAIT_REVERSE -> Surface.ROTATION_180
            OrientationMode.LANDSCAPE_REVERSE -> Surface.ROTATION_270
        }

        return try {
            val rotationLocked = Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            )
            val rotationSet = Settings.System.putInt(
                context.contentResolver,
                Settings.System.USER_ROTATION,
                rotation
            )
            val success = rotationLocked && rotationSet
            if (!success) requestWriteSettings(context)
            success
        } catch (e: SecurityException) {
            requestWriteSettings(context)
            false
        } catch (e: Exception) {
            showToast(context, "设置方向失败")
            false
        }
    }

    fun openSystemSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            showToast(context, "无法打开系统设置")
        }
    }

    /**
     * Ask Android to clear only the user-installed apps that are actually
     * running in a killable background state.
     *
     * The old implementation enumerated every installed third-party package,
     * so its number meant "installed apps", not "background apps". That made
     * the launcher report the same 13 apps on every tap. ActivityManager still
     * owns the final decision: foreground services and other protected work
     * may legitimately survive this request.
     */
    fun clearBackgroundApps(context: Context): Int {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return 0
        val packageManager = context.packageManager
        val candidates = findKillableBackgroundPackages(
            context = context,
            activityManager = activityManager,
            packageManager = packageManager
        )

        if (candidates.isEmpty()) {
            showToast(context, "没有可清理的第三方后台应用")
            return 0
        }

        candidates.forEach { packageName ->
            runCatching { activityManager.killBackgroundProcesses(packageName) }
        }

        // Give ActivityManager a short chance to tear down cached processes so
        // the message reflects the current state instead of only the request.
        SystemClock.sleep(250L)
        val remaining = findKillableBackgroundPackages(
            context = context,
            activityManager = activityManager,
            packageManager = packageManager
        ).count { it in candidates }
        showToast(
            context,
            if (remaining == 0) {
                "已请求清理 ${candidates.size} 个后台应用"
            } else {
                "已请求清理 ${candidates.size} 个，仍有 ${remaining} 个由系统保留"
            }
        )
        return candidates.size
    }

    private fun findKillableBackgroundPackages(
        context: Context,
        activityManager: ActivityManager,
        packageManager: android.content.pm.PackageManager
    ): List<String> {
        return activityManager.runningAppProcesses
            .orEmpty()
            .asSequence()
            // Everything after FOREGROUND is outside the current app's
            // foreground state. Some vendor apps report a foreground service
            // as PERCEPTIBLE rather than SERVICE, so do not discard that
            // process here; killBackgroundProcesses() still makes the final
            // system-owned decision about protected work.
            .filter { process ->
                process.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
            .flatMap { process -> process.pkgList.orEmpty().asSequence() }
            .filter { packageName -> packageName != context.packageName }
            .filter { packageName ->
                isUserInstalledApp(packageManager, packageName)
            }
            .distinct()
            .toList()
    }

    /**
     * Android only permits a normal app to lock the screen through an enabled
     * device-admin policy. The first tap opens the stock one-time grant page;
     * every later tap locks immediately.
     */
    fun lockScreen(context: Context): Boolean {
        val policyManager = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        val admin = ComponentName(context, InkBoardDeviceAdminReceiver::class.java)
        if (policyManager.isAdminActive(admin)) {
            return runCatching {
                policyManager.lockNow()
                true
            }.getOrElse {
                showToast(context, "无法锁屏")
                false
            }
        }

        return try {
            context.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "InkBoard 仅使用此权限实现锁屏快捷方式。"
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            showToast(context, "首次使用请允许 InkBoard 的锁屏权限")
            false
        } catch (_: Exception) {
            showToast(context, "无法打开锁屏权限")
            false
        }
    }

    private fun showToast(context: Context, message: String) {
        mainHandler.post {
            val density = context.resources.displayMetrics.density
            val horizontalPadding = (18 * density).toInt()
            val verticalPadding = (11 * density).toInt()
            val messageView = TextView(context.applicationContext).apply {
                text = message
                setTextColor(Color.BLACK)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(247, 247, 247))
                    setStroke((density.coerceAtLeast(1f)).toInt(), Color.rgb(42, 42, 42))
                }
                elevation = 0f
            }
            Toast(context.applicationContext).apply {
                duration = Toast.LENGTH_SHORT
                setGravity(
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    0,
                    (52 * density).toInt()
                )
                @Suppress("DEPRECATION")
                view = messageView
                show()
            }
        }
    }

    private fun requestWriteSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            showToast(context, "请允许 InkBoard 修改系统设置")
        } catch (_: Exception) {
            showToast(context, "无法修改屏幕方向")
        }
    }

    private fun isUserInstalledApp(
        packageManager: android.content.pm.PackageManager,
        packageName: String
    ): Boolean {
        val appInfo = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return false
        return isUserInstalledApp(appInfo)
    }

    private fun isUserInstalledApp(appInfo: ApplicationInfo): Boolean {
        return appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
            appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    }
}

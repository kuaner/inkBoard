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
     * Request that Android reclaim third-party background processes.
     *
     * On Android 8+ a normal app's [ActivityManager.getRunningAppProcesses]
     * almost never lists other packages (privacy), so "scan running processes
     * then kill" falsely reports 0 even when KOReader / browsers are still
     * resident. The reliable approach for a launcher with only
     * [android.Manifest.permission.KILL_BACKGROUND_PROCESSES] is to call
     * [ActivityManager.killBackgroundProcesses] on every user-installed
     * package: the system only tears down processes that are safe to kill
     * (cached / empty), and leaves the true foreground + protected FGS alone.
     */
    fun clearBackgroundApps(context: Context): Int {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return 0
        val packageManager = context.packageManager
        val self = context.packageName
        val candidates = packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { isUserInstalledApp(it) }
            .map { it.packageName }
            .filter { it != self }
            .distinct()
            .toList()

        if (candidates.isEmpty()) {
            showToast(context, "没有可清理的第三方应用")
            return 0
        }

        candidates.forEach { packageName ->
            runCatching { activityManager.killBackgroundProcesses(packageName) }
        }

        // killBackgroundProcesses is asynchronous and process visibility is
        // limited; report the request, not a guessed "still running" count.
        showToast(context, "已请求清理 ${candidates.size} 个第三方应用后台")
        return candidates.size
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

    private fun isUserInstalledApp(appInfo: ApplicationInfo): Boolean {
        return appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
            appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    }
}

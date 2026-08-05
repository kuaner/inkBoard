package ai.openduo.inkboard.data

import android.graphics.drawable.Drawable

enum class BuiltInShortcut(val id: String) {
    CLEAR_BACKGROUND("clear_background"),
    FULL_REFRESH("full_refresh"),
    LOCK_SCREEN("lock_screen")
}

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable?,
    val customIconPath: String? = null,
    val builtInShortcut: BuiltInShortcut? = null
) {
    val key: String get() = "$packageName/$activityName"
}

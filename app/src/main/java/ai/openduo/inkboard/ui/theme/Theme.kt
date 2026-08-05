package ai.openduo.inkboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val InkColorScheme = lightColorScheme(
    primary = InkBlack,
    onPrimary = InkWhite,
    primaryContainer = InkDark,
    onPrimaryContainer = InkWhite,
    secondary = InkGray,
    onSecondary = InkWhite,
    secondaryContainer = InkWash,
    onSecondaryContainer = InkBlack,
    tertiary = InkInk,
    onTertiary = InkWhite,
    background = InkPaper,
    onBackground = InkBlack,
    surface = InkWhite,
    onSurface = InkBlack,
    surfaceVariant = InkWash,
    onSurfaceVariant = InkDark,
    outline = InkBlack,
    outlineVariant = InkLine,
    inverseSurface = InkBlack,
    inverseOnSurface = InkWhite,
    error = InkBlack,
    onError = InkWhite,
    scrim = Color(0x99000000)
)

@Composable
fun InkBoardTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.parseColor("#F7F7F7")
            window.navigationBarColor = android.graphics.Color.parseColor("#F7F7F7")
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(
        colorScheme = InkColorScheme,
        typography = InkTypography,
        content = content
    )
}

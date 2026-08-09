package ai.openduo.inkboard.ui.apps

import ai.openduo.inkboard.ui.components.AppGlyph
import ai.openduo.inkboard.ui.components.AppsGridColumns
import ai.openduo.inkboard.ui.components.AppsGridRows
import ai.openduo.inkboard.ui.components.AppsPageSize
import ai.openduo.inkboard.ui.components.PageHeader
import ai.openduo.inkboard.ui.components.PageNavigation
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.inkClickable
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkDark
import ai.openduo.inkboard.ui.theme.InkPaper
import ai.openduo.inkboard.data.AppInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full application catalog for launching — dense 4-column grid.
 * Shortcut slots are edited only from MENU.
 */
@Composable
internal fun AppLauncherPage(
    apps: List<AppInfo>,
    page: Int,
    onPageChange: (Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PaperFrame(modifier, bottomPadding = 16.dp) { wide ->
        val pageCount = maxOf(1, (apps.size + AppsPageSize - 1) / AppsPageSize)
        val activePage = page.coerceIn(0, pageCount - 1)
        val pageApps = apps.drop(activePage * AppsPageSize).take(AppsPageSize)

        PageHeader(
            title = "APPS.",
            meta = "${apps.size}",
            actionLabel = null,
            onBack = onBack,
            onAction = null
        )
        Spacer(Modifier.height(if (wide) 16.dp else 12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (pageApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有可启动的应用",
                        color = InkDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                for (row in 0 until AppsGridRows) {
                    if (row > 0) Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.weight(1f)) {
                        for (column in 0 until AppsGridColumns) {
                            if (column > 0) Spacer(Modifier.width(4.dp))
                            val app = pageApps.getOrNull(row * AppsGridColumns + column)
                            if (app == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                LauncherAppCell(
                                    app = app,
                                    onLaunch = { onLaunch(app) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PageNavigation(
            page = activePage,
            pageCount = pageCount,
            onPrevious = { onPageChange((activePage - 1).coerceAtLeast(0)) },
            onNext = { onPageChange((activePage + 1).coerceAtMost(pageCount - 1)) }
        )
    }
}

@Composable
private fun LauncherAppCell(
    app: AppInfo,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(InkPaper)
            .border(1.dp, InkBlack)
            .inkClickable(onClick = onLaunch)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val cellMin = min(constraints.maxWidth, constraints.maxHeight).toFloat()
        val iconDp = with(density) {
            (cellMin * 0.42f).toDp().coerceIn(28.dp, 56.dp)
        }
        val labelSp = with(density) {
            (cellMin * 0.11f).toSp().value.roundToInt().coerceIn(10, 13)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.weight(1f))
            AppGlyph(app = app, size = iconDp)
            Spacer(Modifier.weight(1f))
            Text(
                text = app.label,
                color = InkBlack,
                fontSize = labelSp.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

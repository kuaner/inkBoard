package ai.openduo.inkboard.ui.icons

import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.HeaderLink
import ai.openduo.inkboard.ui.components.PageHeader
import ai.openduo.inkboard.ui.components.PageNavigation
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.VerticalRule
import ai.openduo.inkboard.ui.components.DrawerPageSize
import ai.openduo.inkboard.ui.components.IconRows
import ai.openduo.inkboard.ui.components.ShortcutColumns

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openduo.inkboard.LauncherUiState
import ai.openduo.inkboard.SenderSnapshot
import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.BuiltInShortcut
import ai.openduo.inkboard.data.EpdDpiPreset
import ai.openduo.inkboard.data.EpdProfile
import ai.openduo.inkboard.data.EpdRefreshPreset
import ai.openduo.inkboard.data.KoboyoIcon
import ai.openduo.inkboard.data.KoboyoIconCategory
import ai.openduo.inkboard.data.KoboyoIconGroup
import ai.openduo.inkboard.ui.components.MonochromeIcon
import ai.openduo.inkboard.ui.components.inkClickable
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkDark
import ai.openduo.inkboard.ui.theme.InkLine
import ai.openduo.inkboard.ui.theme.InkMid
import ai.openduo.inkboard.ui.theme.InkPaper
import ai.openduo.inkboard.ui.theme.InkSoft
import ai.openduo.inkboard.ui.theme.InkWhite
import ai.openduo.inkboard.util.OrientationMode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun IconPicker(
    state: LauncherUiState,
    slot: Int,
    onCategory: (KoboyoIconCategory) -> Unit,
    onPage: (Int) -> Unit,
    onSelectIcon: (KoboyoIcon) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val picker = state.iconPicker
    var showingSubgroups by remember { mutableStateOf(false) }
    var subgroupPage by remember { mutableIntStateOf(0) }
    val selectedGroup = state.iconGroups.firstOrNull { it.key == picker.category.group }
        ?: state.iconGroups.first()
    val categoriesInGroup = state.iconCategories.filter {
        it.group == picker.category.group && it.subgroup != null
    }
    val subgroupPageCount = maxOf(1, (categoriesInGroup.size + DrawerPageSize - 1) / DrawerPageSize)

    LaunchedEffect(picker.category.key) {
        val selectedIndex = categoriesInGroup.indexOfFirst { it.key == picker.category.key }
        if (selectedIndex >= 0) {
            subgroupPage = selectedIndex / DrawerPageSize
        }
    }

    val activeSubgroupPage = subgroupPage.coerceIn(0, subgroupPageCount - 1)
    val visibleSubgroups = categoriesInGroup
        .drop(activeSubgroupPage * DrawerPageSize)
        .take(DrawerPageSize)

    PaperFrame(modifier, bottomPadding = 16.dp) { wide ->
        PageHeader(
            title = "ICON.",
            meta = "SLOT 0${slot + 1}",
            actionLabel = null,
            onBack = onBack,
            onAction = null
        )

        Spacer(Modifier.height(if (wide) 22.dp else 14.dp))

        val selectGroup: (KoboyoIconGroup) -> Unit = { group ->
            val childCategories = state.iconCategories.filter {
                it.group == group.key && it.subgroup != null
            }
            if (childCategories.isEmpty()) {
                // FACE and SOLID are direct sets, so never invent a fake
                // second level just to fill the navigation rail.
                showingSubgroups = false
                state.iconCategories
                    .firstOrNull { it.group == group.key && it.subgroup == null }
                    ?.let(onCategory)
            } else {
                showingSubgroups = true
                subgroupPage = 0
                childCategories.firstOrNull()?.let(onCategory)
            }
        }
        val previousSubgroupPage: () -> Unit = {
            val page = (activeSubgroupPage - 1).coerceAtLeast(0)
            subgroupPage = page
            categoriesInGroup.getOrNull(page * DrawerPageSize)?.let(onCategory)
            Unit
        }
        val nextSubgroupPage: () -> Unit = {
            val page = (activeSubgroupPage + 1).coerceAtMost(subgroupPageCount - 1)
            subgroupPage = page
            categoriesInGroup.getOrNull(page * DrawerPageSize)?.let(onCategory)
            Unit
        }

        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                IconCategoryPane(
                    groups = state.iconGroups,
                    selectedGroup = selectedGroup,
                    subgroups = visibleSubgroups,
                    selectedSubgroup = picker.category,
                    showingSubgroups = showingSubgroups,
                    subgroupPage = activeSubgroupPage,
                    subgroupPageCount = subgroupPageCount,
                    onSelectGroup = selectGroup,
                    onSelectSubgroup = onCategory,
                    onBackToGroups = { showingSubgroups = false },
                    onPreviousSubgroupPage = previousSubgroupPage,
                    onNextSubgroupPage = nextSubgroupPage,
                    modifier = Modifier
                        .weight(0.24f)
                        .fillMaxHeight()
                )
                Spacer(Modifier.width(24.dp))
                VerticalRule()
                Spacer(Modifier.width(24.dp))
                IconResultsPane(
                    picker = picker,
                    onSelectIcon = onSelectIcon,
                    onPage = onPage,
                    modifier = Modifier
                        .weight(0.76f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                IconCategoryPane(
                    groups = state.iconGroups,
                    selectedGroup = selectedGroup,
                    subgroups = visibleSubgroups,
                    selectedSubgroup = picker.category,
                    showingSubgroups = showingSubgroups,
                    subgroupPage = activeSubgroupPage,
                    subgroupPageCount = subgroupPageCount,
                    onSelectGroup = selectGroup,
                    onSelectSubgroup = onCategory,
                    onBackToGroups = { showingSubgroups = false },
                    onPreviousSubgroupPage = previousSubgroupPage,
                    onNextSubgroupPage = nextSubgroupPage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(268.dp)
                )
                Spacer(Modifier.height(18.dp))
                Hairline(thick = true)
                Spacer(Modifier.height(18.dp))
                IconResultsPane(
                    picker = picker,
                    onSelectIcon = onSelectIcon,
                    onPage = onPage,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun IconCategoryPane(
    groups: List<KoboyoIconGroup>,
    selectedGroup: KoboyoIconGroup,
    subgroups: List<KoboyoIconCategory>,
    selectedSubgroup: KoboyoIconCategory,
    showingSubgroups: Boolean,
    subgroupPage: Int,
    subgroupPageCount: Int,
    onSelectGroup: (KoboyoIconGroup) -> Unit,
    onSelectSubgroup: (KoboyoIconCategory) -> Unit,
    onBackToGroups: () -> Unit,
    onPreviousSubgroupPage: () -> Unit,
    onNextSubgroupPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showingSubgroups) {
                Text(
                    text = "← ALL",
                    color = InkBlack,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier
                        .height(48.dp)
                        .inkClickable(onBackToGroups)
                        .padding(end = 12.dp, top = 16.dp, bottom = 16.dp)
                )
                Text(
                    text = selectedGroup.label,
                    color = InkBlack,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            } else {
                Text(
                    text = "COLLECTIONS",
                    color = InkBlack,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.7.sp
                )
            }
        }
        Hairline(thick = true)
        Spacer(Modifier.height(10.dp))

        if (!showingSubgroups) {
            Column(modifier = Modifier.weight(1f)) {
                groups.forEach { group ->
                    IconCategoryRow(
                        label = group.label,
                        selected = group.key == selectedGroup.key,
                        onClick = { onSelectGroup(group) }
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                subgroups.forEach { category ->
                    IconCategoryRow(
                        label = category.label,
                        selected = category.key == selectedSubgroup.key,
                        onClick = { onSelectSubgroup(category) }
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }

        if (showingSubgroups && subgroupPageCount > 1) {
            Spacer(Modifier.height(12.dp))
            PageNavigation(
                page = subgroupPage,
                pageCount = subgroupPageCount,
                onPrevious = onPreviousSubgroupPage,
                onNext = onNextSubgroupPage
            )
        }
    }
}

@Composable
private fun IconCategoryRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(InkPaper)
            .inkClickable(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (selected) 24.dp else 1.dp)
                .background(if (selected) InkBlack else InkPaper)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label.uppercase(Locale.ROOT),
            color = InkBlack,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IconResultsPane(
    picker: ai.openduo.inkboard.data.IconPickerState,
    onSelectIcon: (KoboyoIcon) -> Unit,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = picker.category.key.uppercase(Locale.ROOT),
                    color = InkBlack,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (picker.error != null) {
                HeaderLink(text = "RETRY", onClick = { onPage(picker.page) })
            }
        }
        Hairline(thick = true)
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (picker.icons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = picker.error ?: if (picker.loading) "正在载入图标" else "此分类没有图标",
                        color = InkBlack,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    for (row in 0 until IconRows) {
                        if (row > 0) Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.weight(1f)) {
                            for (column in 0 until ShortcutColumns) {
                                if (column > 0) Spacer(Modifier.width(8.dp))
                                val icon = picker.icons.getOrNull(row * ShortcutColumns + column)
                                if (icon == null) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    IconPickerTile(
                                        icon = icon,
                                        onClick = { onSelectIcon(icon) },
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
            if (picker.loading) {
                IconLoadingNotice(
                    page = picker.page,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // This footer deliberately lives outside loading/error/content states.
        // A page flip therefore never shifts or removes its touch targets.
        PageNavigation(
            page = picker.page,
            pageCount = picker.pageCount,
            onPrevious = { onPage((picker.page - 1).coerceAtLeast(0)) },
            onNext = { onPage((picker.page + 1).coerceAtMost(picker.pageCount - 1)) }
        )
    }
}

@Composable
private fun IconLoadingNotice(page: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(InkPaper)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "正在载入第 ${page + 1} 页",
            color = InkBlack,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun IconPickerTile(
    icon: KoboyoIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(InkPaper)
            .border(1.5.dp, InkBlack)
            .inkClickable(onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        if (icon.localPath != null) {
            MonochromeIcon(
                drawable = null,
                size = 52.dp,
                svgPath = icon.localPath
            )
        } else {
            Text(
                text = "·",
                color = InkBlack,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = icon.name,
            color = InkBlack,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
    }
}

package ai.openduo.inkboard.ui.home

import ai.openduo.inkboard.ui.LauncherActions
import ai.openduo.inkboard.ui.apps.AppDrawer
import ai.openduo.inkboard.ui.components.formatCurrentTime
import ai.openduo.inkboard.ui.components.formatHomeDateParts
import ai.openduo.inkboard.util.readSystemMetrics
import ai.openduo.inkboard.ui.controls.ControlPage
import ai.openduo.inkboard.ui.epd.EpdEntryPoints
import ai.openduo.inkboard.ui.epd.EpdProfilePage
import ai.openduo.inkboard.ui.epd.EpdSection
import ai.openduo.inkboard.ui.epd.EpdTarget
import ai.openduo.inkboard.ui.epd.SystemEpdPage
import ai.openduo.inkboard.ui.icons.IconPicker
import ai.openduo.inkboard.ui.sender.SenderPage

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

// Root compositor: owns navigation state and delegates each feature to its page.
@Composable
fun LauncherHome(
    state: LauncherUiState,
    actions: LauncherActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var controls by rememberSaveable { mutableStateOf(false) }
    var sender by rememberSaveable { mutableStateOf(false) }
    var mottoEditor by rememberSaveable { mutableStateOf(false) }
    var iconPicker by rememberSaveable { mutableStateOf(false) }
    var editingSlot by rememberSaveable { mutableIntStateOf(0) }
    var drawerPage by rememberSaveable { mutableIntStateOf(0) }
    var currentTime by rememberSaveable { mutableStateOf(formatCurrentTime()) }
    var currentDateParts by remember {
        mutableStateOf(formatHomeDateParts())
    }
    var systemMetrics by remember {
        mutableStateOf(readSystemMetrics(context))
    }
    var systemEpd by rememberSaveable { mutableStateOf(false) }
    var epdTarget by remember { mutableStateOf<EpdTarget?>(null) }
    var epdDraft by remember { mutableStateOf<EpdProfile?>(null) }
    var systemEpdDraft by remember { mutableStateOf<EpdProfile?>(null) }
    var pendingInkBoardEpdApply by remember { mutableStateOf<EpdProfile?>(null) }
    var pendingSystemEpdApply by remember { mutableStateOf<EpdProfile?>(null) }
    var epdSectionName by rememberSaveable { mutableStateOf(EpdSection.REFRESH.name) }
    val epdSection = EpdSection.entries.firstOrNull { it.name == epdSectionName }
        ?: EpdSection.REFRESH

    val openEpd: (EpdTarget) -> Unit = { target ->
        epdTarget = target
        epdDraft = null
        epdSectionName = EpdSection.REFRESH.name
        actions.onLoadEpdProfile(target.packageName)
    }

    val openSystemEpd: () -> Unit = {
        systemEpd = true
        systemEpdDraft = null
        actions.onLoadSystemEpdProfile()
    }

    // One place decides whether EPD controls exist. Child pages only see
    // nullable callbacks and never re-check the product gate.
    val epd = EpdEntryPoints.of(
        enabled = state.epdCustomizationEnabled,
        openSystem = openSystemEpd,
        openApp = { app -> openEpd(EpdTarget(app.label, app.packageName, app)) },
        openInkBoard = {
            openEpd(
                EpdTarget(
                    label = "InkBoard",
                    packageName = context.packageName
                )
            )
        },
        refresh = actions.onRefreshEpdScreen
    )

    // Wait until the EPD editor has left composition before asking the vendor
    // provider to activate InkBoard's profile.  On this firmware activation
    // briefly recreates the foreground activity.
    LaunchedEffect(pendingInkBoardEpdApply) {
        pendingInkBoardEpdApply?.let { profile ->
            actions.onApplyInkBoardEpdProfile(profile)
            pendingInkBoardEpdApply = null
        }
    }

    LaunchedEffect(pendingSystemEpdApply) {
        pendingSystemEpdApply?.let { profile ->
            actions.onApplySystemEpdProfile(profile)
            pendingSystemEpdApply = null
        }
    }

    val closeEpd: () -> Unit = {
        val target = epdTarget
        val editedProfile = epdDraft
        epdTarget = null
        epdDraft = null
        if (target?.packageName == context.packageName && editedProfile != null) {
            pendingInkBoardEpdApply = editedProfile
        }
    }

    val closeSystemEpd: () -> Unit = {
        val editedProfile = systemEpdDraft
        systemEpd = false
        systemEpdDraft = null
        if (editedProfile != null) {
            pendingSystemEpdApply = editedProfile
        }
    }

    val closeSender: () -> Unit = {
        sender = false
        actions.onStopSender()
    }

    LaunchedEffect(sender) {
        if (sender) actions.onStartSender()
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime = formatCurrentTime()
            currentDateParts = formatHomeDateParts()
            systemMetrics = readSystemMetrics(context)
            val untilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(untilNextMinute.coerceAtLeast(1_000L))
        }
    }

    // Ignore stale EPD navigation if the product gate later becomes false.
    val showSystemEpd = epd.available && systemEpd
    val showAppEpd = epd.available && epdTarget != null

    BackHandler(
        enabled = sender || showSystemEpd || showAppEpd || editing || controls || mottoEditor || iconPicker
    ) {
        when {
            sender -> closeSender()
            showSystemEpd -> closeSystemEpd()
            showAppEpd -> closeEpd()
            iconPicker -> iconPicker = false
            mottoEditor -> mottoEditor = false
            editing -> editing = false
            controls -> controls = false
        }
    }

    when {
        sender -> SenderPage(
            snapshot = state.sender,
            onBack = closeSender,
            onRetry = actions.onStartSender,
            onStop = actions.onStopSender,
            modifier = modifier
        )

        showSystemEpd -> SystemEpdPage(
            profile = systemEpdDraft ?: state.systemEpdProfile,
            loading = state.systemEpdLoading,
            error = state.systemEpdError,
            onProfileChange = { profile -> systemEpdDraft = profile },
            onRefreshScreen = actions.onRefreshEpdScreen,
            onBack = closeSystemEpd,
            modifier = modifier
        )

        showAppEpd -> {
            val target = checkNotNull(epdTarget)
            EpdProfilePage(
                target = target,
                profile = state.epdProfile?.takeIf { it.packageName == target.packageName },
                loading = state.epdLoading,
                error = state.epdError,
                section = epdSection,
                onSectionChange = { epdSectionName = it.name },
                onProfileChange = { profile ->
                    epdDraft = profile
                    actions.onSaveEpdProfile(profile)
                },
                onRefreshScreen = actions.onRefreshEpdScreen,
                applyOnExit = target.packageName == context.packageName,
                onBack = closeEpd,
                modifier = modifier
            )
        }

        iconPicker -> IconPicker(
            state = state,
            slot = editingSlot,
            onCategory = { category ->
                actions.onLoadIconPage(category, 0)
            },
            onPage = { page ->
                actions.onLoadIconPage(state.iconPicker.category, page)
            },
            onSelectIcon = { icon ->
                actions.onSetSlotIcon(editingSlot, icon)
                iconPicker = false
            },
            onBack = { iconPicker = false },
            modifier = modifier
        )

        mottoEditor -> MottoEditor(
            initial = state.motto,
            onSave = { motto ->
                actions.onSetMotto(motto)
                mottoEditor = false
            },
            onBack = { mottoEditor = false },
            modifier = modifier
        )

        editing -> AppDrawer(
            slots = state.slots,
            apps = state.allApps,
            selectedSlot = editingSlot,
            onSelectSlot = { editingSlot = it },
            onLaunch = { app ->
                editing = false
                actions.onLaunch(app)
            },
            onAddShortcut = { app ->
                actions.onAssignSlot(editingSlot, app)
            },
            onClearSlot = {
                actions.onClearSlot(editingSlot)
            },
            onOpenIconPicker = {
                iconPicker = true
                actions.onLoadIconPage(state.iconPicker.category, 0)
            },
            onOpenEpd = epd.openApp,
            page = drawerPage,
            onPageChange = { drawerPage = it },
            onBack = { editing = false },
            modifier = modifier
        )

        controls -> ControlPage(
            state = state,
            actions = actions,
            epd = epd,
            onOpenMotto = { mottoEditor = true },
            onOpenApps = {
                editingSlot = 0
                drawerPage = 0
                editing = true
            },
            onBack = { controls = false },
            modifier = modifier
        )

        else -> HomeSurface(
            state = state,
            actions = actions,
            epd = epd,
            time = currentTime,
            dateParts = currentDateParts,
            metrics = systemMetrics,
            motto = state.motto,
            onEditApps = {
                editingSlot = 0
                drawerPage = 0
                editing = true
            },
            onEditSlot = {
                editingSlot = it
                drawerPage = 0
                editing = true
            },
            onOpenMotto = { mottoEditor = true },
            onOpenSender = { sender = true },
            onOpenControls = { controls = true },
            modifier = modifier
        )
    }
}

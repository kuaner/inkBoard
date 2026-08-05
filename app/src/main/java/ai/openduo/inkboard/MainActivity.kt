package ai.openduo.inkboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ai.openduo.inkboard.ui.LauncherActions
import ai.openduo.inkboard.ui.home.LauncherHome
import ai.openduo.inkboard.ui.theme.InkBoardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private var uiAttached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Do not compose an empty desktop first. Wait for the process-level
        // home bootstrap (keys + labels + monochrome icons) so the first frame
        // already has shortcuts. Theme window background covers the brief wait.
        lifecycleScope.launch {
            (application as InkBoardApp).awaitHomeBootstrap()
            setContent {
                InkBoardTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val actions = remember(viewModel) {
                        LauncherActions(
                            onLaunch = viewModel::launchApp,
                            onAssignSlot = viewModel::assignSlot,
                            onClearSlot = viewModel::clearSlot,
                            onSetMotto = viewModel::setMotto,
                            onLoadIconPage = viewModel::loadIconPage,
                            onSetSlotIcon = viewModel::setSlotIcon,
                            onLoadEpdProfile = viewModel::loadEpdProfile,
                            onSaveEpdProfile = viewModel::saveEpdProfile,
                            onApplyInkBoardEpdProfile = viewModel::applyInkBoardEpdProfile,
                            onLoadSystemEpdProfile = viewModel::loadSystemEpdProfile,
                            onApplySystemEpdProfile = viewModel::applySystemEpdProfile,
                            onRefreshEpdScreen = viewModel::refreshEpdScreen,
                            onOrientation = viewModel::setOrientation,
                            onToggleAdb = viewModel::toggleAdb,
                            onOpenSystemSettings = viewModel::openSystemSettings,
                            onStartSender = viewModel::startSender,
                            onStopSender = viewModel::stopSender
                        )
                    }
                    LauncherHome(
                        state = state,
                        actions = actions,
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                    )
                }
            }
            uiAttached = true
            viewModel.refreshStatus()
            viewModel.refreshApps()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!uiAttached) return
        viewModel.refreshStatus()
        // Home shortcuts are process-cached; refresh keeps the drawer catalog
        // current without clearing the desktop snapshot first.
        viewModel.refreshApps()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Let Compose BackHandler (for example, the app picker) consume the
        // event first. When no page-level handler is active, keep launcher
        // behavior by moving the task behind the current home surface.
        if (!onBackPressedDispatcher.hasEnabledCallbacks()) {
            moveTaskToBack(true)
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}

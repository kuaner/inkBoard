package ai.openduo.inkboard

import android.content.Intent
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ai.openduo.inkboard.ui.LauncherActions
import ai.openduo.inkboard.ui.home.LauncherHome
import ai.openduo.inkboard.ui.theme.InkBoardTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private var uiAttached = false
    private var wasInBackground = false
    private var homeRefreshJob: Job? = null

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
                            onRefreshApps = viewModel::refreshApps,
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
        val returningToHome = uiAttached && wasInBackground
        wasInBackground = false
        if (!uiAttached) return
        if (returningToHome) scheduleHomeRefresh()
        viewModel.refreshStatus()
        // Home shortcuts are process-cached; refresh keeps the drawer catalog
        // current without clearing the desktop snapshot first.
        viewModel.refreshApps()
    }

    override fun onPause() {
        super.onPause()
        wasInBackground = uiAttached
        homeRefreshJob?.cancel()
        homeRefreshJob = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A HOME intent is delivered to this singleTask launcher when the
        // system Home button brings the existing desktop task forward. The
        // onResume fallback above covers firmware that only resumes the task.
        if (intent.action == Intent.ACTION_MAIN &&
            intent.categories?.contains(Intent.CATEGORY_HOME) == true
        ) {
            scheduleHomeRefresh()
        }
    }

    private fun scheduleHomeRefresh() {
        if (!uiAttached) return
        homeRefreshJob?.cancel()
        homeRefreshJob = lifecycleScope.launch {
            // Let the launcher surface become the visible frame before asking
            // the driver to repaint it. This avoids refreshing the app that
            // was just left during the HOME transition.
            delay(HOME_REFRESH_SETTLE_MS)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                viewModel.refreshEpdScreen()
            }
        }
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

    private companion object {
        private const val HOME_REFRESH_SETTLE_MS = 120L
    }
}

package org.jisr.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.jisr.app.ui.nav.EcFloatingNavBar
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jisr.app.ui.components.EcTab
import org.jisr.app.ui.screens.HomeScreen
import org.jisr.app.ui.screens.PluginsScreen
import org.jisr.app.ui.screens.SettingsScreen
import org.jisr.app.ui.theme.JisrTheme
import org.jisr.app.ui.theme.EcColors

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        super.onCreate(savedInstanceState)
        setContent { JisrTheme { Root(viewModel) } }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHost()
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val homeList = rememberLazyListState()
    val pluginsList = rememberLazyListState()
    val pluginsExpanded = remember { mutableStateListOf<String>() }
    val settingsList = rememberLazyListState()

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val server by vm.server.collectAsState()
    val settings by vm.settings.collectAsState()
    val plugins by vm.plugins.collectAsState()
    val repositories by vm.repositories.collectAsState()
    val working by vm.working.collectAsState()
    val tmdbKeyError by vm.tmdbKeyError.collectAsState()

    val hazeState = rememberHazeState()
    Box(Modifier.fillMaxSize().background(EcColors.Background)) {
        Box(
            Modifier.fillMaxSize()
                .hazeSource(state = hazeState),
        ) {
            when (tab) {
                0 -> HomeScreen(
                    state = server,
                    pluginCount = plugins.count { it.enabled },
                    hasTmdbKey = settings.tmdbKey.isNotBlank(),
                    onStart = vm::startServer,
                    onStop = vm::stopServer,
                    onOpenSettings = { tab = 2 },
                    listState = homeList,
                )
                1 -> PluginsScreen(
                    plugins = plugins,
                    repositories = repositories,
                    working = working,
                    onToggle = vm::setPluginEnabled,
                    onSetManyEnabled = vm::setPluginsEnabled,
                    onAddRepository = vm::addRepository,
                    onRefreshAll = vm::refreshAll,
                    onRemoveRepository = vm::removeRepository,
                    onLoadSettings = vm::loadPluginSettings,
                    onSaveSettings = vm::savePluginSettings,
                    expandedRepos = pluginsExpanded,
                    listState = pluginsList,
                )
                else -> SettingsScreen(
                    settings = settings,
                    serverRunning = server is org.jisr.app.server.ServerState.Running,
                    onTmdbKey = vm::setTmdbKey,
                    tmdbKeyError = tmdbKeyError,
                    onDismissTmdbKeyError = vm::clearTmdbKeyError,
                    onPort = vm::setPort,
                    onDohProvider = vm::setDohProvider,
                    onDohCustomUrl = vm::setDohCustomUrl,
                    onLocalNetwork = vm::setLocalNetwork,
                    onBackgroundRun = vm::setBackgroundRun,
                    onConcurrency = vm::setConcurrency,
                    onResolution = vm::setResolutionEnabled,
                    onGlobalTimeout = vm::setGlobalTimeoutSec,
                    onMaxStreams = vm::setMaxStreamsPerProvider,
                    onWipe = vm::wipeEverything,
                    listState = settingsList,
                )
            }
        }
        EcFloatingNavBar(
            tabs = listOf(
                EcTab("Server", Icons.Rounded.Home, tab == 0) { tab = 0 },
                EcTab("Plugins", Icons.Rounded.Extension, tab == 1) { tab = 1 },
                EcTab("Settings", Icons.Rounded.Settings, tab == 2) { tab = 2 },
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
        )
    }
}

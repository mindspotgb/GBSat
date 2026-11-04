package app.gbsat.emulator.ui

import gb.frame.MenuState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.gbsat.emulator.Emulator
import app.gbsat.emulator.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { Library, Settings }

@Composable
fun GBSatRoot(onImmersive: (Boolean) -> Unit, onQuitApp: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    GBSatTheme {
        if (!Emulator.initialized && Emulator.initializationError == null) {
            gb.frame.AppStartScreen(app.gbsat.emulator.R.drawable.ic_launcher_foreground)
            return@GBSatTheme
        }
        if (!Emulator.initialized) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (Emulator.initializationError == null) CircularProgressIndicator()
                    gb.frame.KitText(Emulator.initializationError ?: "Opening data folder…")
                    if (Emulator.initializationError != null) gb.frame.DataFolderControls(Emulator.dataFolder)
                }
            }
            return@GBSatTheme
        }
        gb.frame.DataFolderWarning(Emulator.dataFolder)
        var screen by remember { mutableStateOf(Screen.Library) }
        var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val inGame = Emulator.running
        // Game requested by an intent (frontends, `am start`): matched to the library by file name, then started like a
        // tap on its cover (auto load included)
        val requested = gb.frame.LaunchIntent.pending.value
        LaunchedEffect(requested, Emulator.setupDone) {
            if (requested == null || !Emulator.setupDone) return@LaunchedEffect
            gb.frame.LaunchIntent.done() // changes this effect's key: the work runs in the root scope so it isn't cancelled
            scope.launch {
                val game = withContext(Dispatchers.IO) {
                    val gamesDir = Emulator.gamesDir
                    gb.frame.LaunchIntent.resolveOrRefresh(requested, Storage.cachedScan(gamesDir)) {
                        Storage.scan(gamesDir)
                    }
                }
                // Another game running: close it the way Exit does (auto save, back to the global options) first
                if (game != null && Emulator.running) Emulator.endGame()
                val err = if (game == null) gb.frame.LaunchIntent.notFound(requested) else Emulator.launch(game.path)
                err?.let { if (Emulator.running) Emulator.showToast(it) else snackbar.showSnackbar(gb.frame.localizeAppText(context, it)) }
            }
        }

        LaunchedEffect(inGame) { onImmersive(true) } // bars hide in games and with a physical controller

        Emulator.lastCrash?.let { report ->
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = { Emulator.lastCrash = null },
                title = { gb.frame.KitText("Crash report") },
                text = {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        gb.frame.KitText(report, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                    }
                },
                confirmButton = { TextButton(onClick = { Emulator.lastCrash = null }) { gb.frame.KitText("OK") } },
                dismissButton = {
                    TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(report)) }) { gb.frame.KitText("Copy") }
                },
            )
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (inGame) {
                GameScreen(onQuitApp)
            } else if (!Emulator.setupDone) {
                SetupWizard(onFinish = { Emulator.finishSetup() })
            } else {
                gb.frame.AppUpdatePrompt(UPDATE_CONFIG)
                BackHandler(screen == Screen.Settings) {
                    screen = Screen.Library
                    if (MenuState.open.value) {
                        MenuState.menuHasFocus = false
                        MenuState.menuRefocus.intValue++
                    }
                }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (screen) {
                            Screen.Library -> LibraryScreen(
                                onOpenSettings = { settingsPage = it; screen = Screen.Settings },
                                onQuit = onQuitApp,
                                launch = { path, progress -> Emulator.launch(path, onArchiveProgress = progress) },
                                launchState = { st, progress -> Emulator.launchState(st, progress) },
                                open = { path, progress -> Emulator.launch(path, useAutoLoad = false, onArchiveProgress = progress) },
                                onLaunchError = { message -> scope.launch { snackbar.showSnackbar(gb.frame.localizeAppText(context, message)) } },
                                visualCustomizationProductId = "gbsat_gold",
                            )
                            Screen.Settings -> SettingsScreen(
                                onBack = {
                                    screen = Screen.Library
                                    // LibraryScreen is rebuilt; force the selected settings tile to reclaim focus.
                                    if (MenuState.open.value) {
                                        MenuState.menuHasFocus = false
                                        MenuState.menuRefocus.intValue++
                                    }
                                },
                                toast = { msg -> scope.launch { snackbar.showSnackbar(gb.frame.localizeAppText(context, msg)) } },
                                page = settingsPage,
                            )
                        }
                    }
                }
            }
        }
    }
}

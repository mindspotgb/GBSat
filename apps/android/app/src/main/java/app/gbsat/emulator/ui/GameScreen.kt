package app.gbsat.emulator.ui

import gb.frame.*
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gbsat.emulator.Emulator
import app.gbsat.emulator.NativeBridge
import app.gbsat.emulator.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GameScreen(onQuitApp: () -> Unit) {
    // Keep the screen on while a game is open: controller input doesn't reset the screen timeout
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var menu by remember { mutableStateOf(false) }
    var fps by remember { mutableFloatStateOf(0f) }
    var stats by remember { mutableStateOf(longArrayOf(0, 320, 224, 0, 1)) }
    // Read here (the whole screen's scope): changes only when the Skip Engine starts or stops
    var skipping by remember { mutableStateOf(false) }

    BackHandler(enabled = GbSatPausePolicy.shouldHandleBack(menu)) {
        menu = true
        Emulator.pause(true)
    }
    LaunchedEffect(Unit) {
        var logged = ""
        while (true) {
            val next = NativeBridge.nativeGetStats()
            val nextSkipping = next.getOrElse(6) { 0 } == 1L
            if (nextSkipping != skipping) skipping = nextSkipping
            // The numbers are only read by the performance overlay: without it, updating them recomposed nothing useful
            if (Emulator.showFps || Emulator.showSpeed || Emulator.showCpu || Emulator.showGpu || Emulator.showRenderer ||
                Emulator.showResolution || Emulator.showFrameTime) {
                stats = next; fps = next[0] / 100f
            }
            // The options in use go into the performance log (also the ones changed in the in-game menu)
            Emulator.logSettings().let { if (it != logged) { logged = it; NativeBridge.nativeSetLogSettings(it) } }
            delay(500)
        }
    }
    LaunchedEffect(Emulator.menuRequest) {
        if (Emulator.menuRequest) {
            if (GbSatPausePolicy.shouldPauseForMenuRequest(menu)) Emulator.pause(true)
            menu = true
            Emulator.menuRequest = false
        }
    }
    // Report if the GPU (Vulkan) renderer could not start (the CPU renderer keeps running), and fall back to the plain
    // window output if OpenGL ES can't start on this device.
    LaunchedEffect(Emulator.renderer) {
        delay(1500)
        if (Emulator.renderer == 4) {
            val err = NativeBridge.nativeGetGpuError().orEmpty()
            if (err.isNotEmpty()) Emulator.showToast("GPU (Vulkan) unavailable, using the CPU renderer: $err")
        }
        if (Emulator.renderer != 0 && !Emulator.glFallback && NativeBridge.nativeGlFailed()) {
            Emulator.glFallback = true
            if (Emulator.renderer == 1) {
                Emulator.applyOptions()
                Emulator.showToast("OpenGL ES unavailable: showing the picture without filters or shaders")
            }
        }
    }
    // Resume when returning from background.
    LaunchedEffect(Unit) {
        if (GbSatPausePolicy.shouldResumeOnEntry(Emulator.paused, menu)) Emulator.pause(false)
    }

    // Auto hides touch controls while a physical controller is connected; On and Off override detection.
    val hasPad = gb.frame.rememberHasGamepad()
    val touch = Emulator.touchControlsMode == gb.frame.TouchControlsMode.ON ||
        (Emulator.touchControlsMode == gb.frame.TouchControlsMode.AUTO && !hasPad)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        val kitTouchLayout = gb.frame.TouchControlsLayoutPlanner.usesResponsiveLayout(maxWidth.value, maxHeight.value)
        val ratio = if (Emulator.aspect == 2) 16f / 9f else 4f / 3f
        val videoMod = if (Emulator.aspect == 1) Modifier.fillMaxSize() else Modifier.aspectRatio(ratio)

        if (landscape) {
            // Top: on screens narrower than the picture (e.g. 5:4, or 16:9 mode on 4:3) the bars all go below it
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                VideoSurface(videoMod.fillMaxHeight())
            }
            if (touch) TouchControls(
                Modifier.fillMaxSize(), landscape = true, responsiveLayout = kitTouchLayout,
                onMenu = { menu = true; Emulator.pause(true) }, videoRatio = ratio,
            )
        } else if (!kitTouchLayout) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (touch) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                        VideoSurface(if (Emulator.aspect == 1) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(ratio))
                    }
                    TouchControls(
                        Modifier.fillMaxWidth().weight(1f), landscape = false, responsiveLayout = false,
                        onMenu = { menu = true; Emulator.pause(true) },
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        VideoSurface(if (Emulator.aspect == 1) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(ratio))
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                // Keep the game at its normal size; the transparent touch layer overlays it instead of reserving height.
                val full = Emulator.aspect == 1
                VideoSurface(if (full) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(ratio))
                if (touch) TouchControls(
                    Modifier.fillMaxSize(), landscape = false, responsiveLayout = true,
                    onMenu = { menu = true; Emulator.pause(true) },
                )
            }
        }

        // The kit's in-game chrome: menu and fast-forward buttons, Skip Engine indicator, performance overlay,
        // achievement notifications and toasts, placed the same way in every app
        GameplayOverlayChrome(
            onMenu = { menu = true; Emulator.pause(true) },
            showFastForwardButton = Emulator.showFfButton, fastForward = Emulator.fastForward,
            onFastForward = { Emulator.toggleFastForward() },
            skipping = skipping,
            message = Emulator.toast,
            perf = { m ->
                PerfOverlay(
                    PerfOptions(Emulator.showFps, Emulator.showSpeed, Emulator.showCpu, Emulator.showGpu, Emulator.showRenderer,
                        Emulator.showResolution, Emulator.showFrameTime),
                    appName = "GBSat", fps = fps, targetFps = if (stats.getOrElse(3) { 0 } == 1L) 50f else 60f,
                    renderer = Emulator.rendererLabel,
                    width = stats.getOrElse(1) { 320 }.toInt(), height = stats.getOrElse(2) { 224 }.toInt(),
                    frameMs = stats.getOrElse(5) { 0 } / 1000f, gpuName = { NativeBridge.nativeGetGpuName() },
                    gpuLoad = { NativeBridge.nativeGetGpuLoad() },
                    modifier = m,
                )
            },
        )

        if (menu) GameMenu(
            onDismiss = { menu = false; Emulator.pause(false) },
        )
    }
}

@Composable
private fun VideoSurface(modifier: Modifier) {
    // A SurfaceView can only be fed by one kind of producer, so switching renderer recreates it.
    key(Emulator.displayRenderer) { VideoSurfaceInner(modifier) }
}

@Composable
private fun VideoSurfaceInner(modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                // Never take the key focus: it would swallow the controller's keys meant for the in-game menu
                isFocusable = false; isFocusableInTouchMode = false
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) = NativeBridge.nativeSetSurface(h.surface)
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) = NativeBridge.nativeSetSurface(null)
                })
            }
        },
    )
}

@Composable
private fun GameMenu(onDismiss: () -> Unit) {
    val savePage = remember {
        QuickMenuPage("save", "Save state") {
            StateSlots(save = true, onDone = { dismiss() })
        }
    }
    val loadPage = remember {
        QuickMenuPage("load", "Load state") {
            StateSlots(save = false, onDone = { dismiss() })
        }
    }
    val videoPage = remember {
        QuickMenuPage("video", "Video") {
            DisplayModeQuickMenuItem(
                value = Emulator.aspect,
                options = listOf(
                    DisplayModeChoice(0, "4:3"),
                    DisplayModeChoice(1, "Fullscreen"),
                    DisplayModeChoice(2, "16:9"),
                ),
            ) { mode ->
                Emulator.aspect = mode
                Emulator.savePrefs()
                Emulator.refreshCheats()
                Emulator.widescreenHack?.let { Emulator.showToast("Widescreen hack enabled: $it") }
            }
            if (Emulator.aspect in 1..2 && Emulator.widescreenHackAvailable) {
                WidescreenGameHacksItem(value = Emulator.widescreenGameHacks) {
                    Emulator.widescreenGameHacks = it
                    Emulator.savePrefs()
                    Emulator.refreshCheats()
                    if (!it) Emulator.showToast("Widescreen hack disabled")
                }
            }
            RendererQuickMenuItem(
                value = Emulator.renderer,
                options = listOf(RendererChoice(1, "CPU (OpenGL)"), RendererChoice(4, "GPU (Vulkan)")),
            ) { renderer ->
                Emulator.renderer = renderer
                Emulator.savePrefs()
                Emulator.applyOptions()
            }
            if (Emulator.renderer == 4) {
                EnhancedResolutionQuickMenuItem(
                    value = Emulator.resScale,
                    values = listOf(1, 2, 3, 4, 0),
                    labels = gb.frame.enhancedResolutionLabels("${Emulator.nativeScale}x"),
                    id = "resolution",
                ) { value ->
                    Emulator.resScale = value
                    Emulator.savePrefs()
                    Emulator.applyOptions()
                }
            }
            if (Emulator.renderer == 1 || Emulator.renderer == 4) {
                PictureFilterQuickMenuItem(value = Emulator.filter) {
                    Emulator.filter = it
                    Emulator.savePrefs()
                    NativeBridge.nativeSetDisplayOptions(Emulator.filter, Emulator.shader)
                }
                ShaderQuickMenuItem(value = Emulator.shader) {
                    Emulator.shader = it
                    Emulator.savePrefs()
                    NativeBridge.nativeSetDisplayOptions(Emulator.filter, Emulator.shader)
                }
            }
        }
    }
    val audioPage = remember {
        QuickMenuPage("audio", "Audio") {
            gb.frame.AudioQuickMenuRows(Emulator.audio) // the kit's standard rows (GBSat has no audio rows of its own)
        }
    }
    val performancePage = remember {
        QuickMenuPage("performance", "Performance") {
            AdaptiveGraphicsItem(id = "frame_skip", value = Emulator.frameSkip) { enabled ->
                Emulator.frameSkip = enabled
                Emulator.savePrefs()
                Emulator.applyOptions()
            }
            CpuRecompilerQuickMenuItem(Emulator.sh2Recompiler, id = "cpu_recompiler") { enabled ->
                Emulator.sh2Recompiler = enabled
                Emulator.savePrefs()
                Emulator.applyOptions()
            }
            CdReadSpeedQuickMenuItem(
                value = Emulator.cdSpeed,
                options = listOf(
                    CdReadSpeedOption(2, "Original"),
                    CdReadSpeedOption(4, "4x"),
                    CdReadSpeedOption(8, "8x"),
                    CdReadSpeedOption(16, "16x"),
                ),
            ) { value ->
                Emulator.cdSpeed = value
                Emulator.savePrefs()
                Emulator.applyOptions()
            }
            SkipEngineQuickMenuRows(
                skipLoadingScreens = Emulator.skipLoads,
                onSkipLoadingScreensChange = { Emulator.skipLoads = it; Emulator.savePrefs(); Emulator.applyOptions() },
                skipTransitions = Emulator.skipTransitions,
                onSkipTransitionsChange = { Emulator.skipTransitions = it; Emulator.savePrefs(); Emulator.applyOptions() },
                skipSaveWaits = Emulator.skipSaves,
                onSkipSaveWaitsChange = { Emulator.skipSaves = it; Emulator.savePrefs(); Emulator.applyOptions() },
            )
            QuickMenuItemRow(
                id = "threaded_audio", icon = Icons.Default.GraphicEq, label = "Threaded audio",
                value = when (Emulator.scspMode) { 0 -> "Auto"; 1 -> "On"; else -> "Off" },
            ) {
                Emulator.scspMode = (Emulator.scspMode + 1) % 3
                Emulator.savePrefs()
                Emulator.applyOptions()
            }
            QuickMenuItemRow(
                id = "cpu_sync", icon = Icons.Default.Memory, label = "CPU sync",
                value = if (Emulator.cpuSync == 1) "Fast" else "Accurate",
            ) {
                Emulator.cpuSync = 1 - Emulator.cpuSync
                Emulator.savePrefs()
                Emulator.applyOptions()
            }
        }
    }
    val overlaysPage = remember {
        QuickMenuPage("overlays", "Overlays") {
            MonitoringQuickMenuRows(GBSatMonitoring)
            TouchControlsQuickMenuItem(id = "touch_controls", value = Emulator.touchControlsMode) {
                Emulator.touchControlsMode = it
                Emulator.savePrefs()
            }
        }
    }
    val cheatsPage = remember {
        QuickMenuPage("cheats", "Cheats", fullScreen = true) {
            gb.frame.CheatsScreen(backend = GBSatCheatsBackend, onBack = { back() })
        }
    }
    val achievementsPage = remember {
        QuickMenuPage("achievements", "Achievements", fullScreen = true) {
            AchievementsScreen(onBack = { back() })
        }
    }
    val swapDiscPage = remember {
        QuickMenuPage("swap-disc", "Swap disc") {
            val scope = rememberCoroutineScope()
            var discs by remember { mutableStateOf<List<Game>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }
            var swapping by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Emulator.gamesDir) {
                loading = true
                discs = withContext(Dispatchers.IO) { Storage.scanAllDiscs(Emulator.gamesDir) }
                loading = false
            }
            val current = Emulator.currentGame
            val currentTitle = current?.let(Storage::fileName)?.substringBeforeLast('.')
            val currentSet = currentTitle?.let(::discSetKey)
            val candidates = discs.filter { disc ->
                disc.path != current && currentSet != null && discSetKey(disc.title) == currentSet
            }

            when {
                loading -> gb.frame.KitText("Scanning game folder…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                candidates.isEmpty() -> gb.frame.KitText(
                    "No other discs for this game were found in the library.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> candidates.forEachIndexed { index, disc ->
                    QuickMenuItemRow(
                        id = "disc-$index",
                        icon = Icons.Default.Eject,
                        label = disc.title,
                        value = if (swapping) "Loading…" else null,
                    ) {
                        if (!swapping) scope.launch {
                            swapping = true
                            error = null
                            val result = Emulator.swapDisc(disc.path)
                            swapping = false
                            if (result == null) {
                                Emulator.showToast("Disc swapped: ${disc.title}")
                                dismiss()
                            } else {
                                error = result
                            }
                        }
                    }
                }
            }
            error?.let { gb.frame.KitText(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    val gamePage = remember(cheatsPage, achievementsPage, swapDiscPage) {
        QuickMenuPage("game", "Game") {
            // Standard GBFrame destinations first; Saturn session controls follow.
            QuickMenuItemRow(
                id = "cheats", icon = Icons.Default.Tune, label = "Cheats", hasSubmenu = true,
            ) { openPage(cheatsPage, returnFocusItemId = "cheats") }
            // Keep the shared achievements screen accessible even before login or when RA is disabled;
            // it explains the current state and offers a consistent entry point across GB-family apps.
            QuickMenuItemRow(
                id = "achievements", icon = Icons.Default.EmojiEvents, label = "Achievements", hasSubmenu = true,
            ) { openPage(achievementsPage, returnFocusItemId = "achievements") }
            QuickMenuItemRow(
                id = "swap_disc", icon = Icons.Default.Eject, label = "Swap disc", hasSubmenu = true,
            ) { openPage(swapDiscPage, returnFocusItemId = "swap_disc") }
            gb.frame.ResetQuickMenuItem {
                Emulator.reset(true)
                dismiss()
            }
        }
    }
    val rootPage = remember(savePage, loadPage, videoPage, audioPage, performancePage, overlaysPage, gamePage) {
        QuickMenuPage("root", "Paused") {
            QuickMenuItemRow(id = "resume", icon = Icons.Default.PlayArrow, label = "Resume") { dismiss() }
            QuickMenuItemRow(
                id = "save", icon = Icons.Default.Save, label = "Save state", hasSubmenu = true,
            ) { openPage(savePage, returnFocusItemId = "save") }
            QuickMenuItemRow(
                id = "load", icon = Icons.Default.Restore, label = "Load state", hasSubmenu = true,
            ) { openPage(loadPage, returnFocusItemId = "load") }
            FastForwardQuickMenuAction(GBSatMonitoring, Emulator.fastForward) { Emulator.toggleFastForward() }
            QuickMenuItemRow(
                id = "video", icon = Icons.Default.Tv, label = "Video", hasSubmenu = true,
            ) { openPage(videoPage, returnFocusItemId = "video") }
            QuickMenuItemRow(
                id = "audio", icon = Icons.AutoMirrored.Filled.VolumeUp, label = "Audio", hasSubmenu = true,
            ) { openPage(audioPage, returnFocusItemId = "audio") }
            QuickMenuItemRow(
                id = "performance", icon = Icons.Default.Speed, label = "Performance", hasSubmenu = true,
            ) { openPage(performancePage, returnFocusItemId = "performance") }
            QuickMenuItemRow(
                id = "overlays", icon = Icons.Default.Layers, label = "Overlays", hasSubmenu = true,
            ) { openPage(overlaysPage, returnFocusItemId = "overlays") }
            QuickMenuItemRow(
                id = "game", icon = Icons.Default.SportsEsports, label = "Game", hasSubmenu = true,
            ) { openPage(gamePage, returnFocusItemId = "game") }
            QuickMenuItemRow(id = "exit", icon = Icons.AutoMirrored.Filled.ExitToApp, label = "Exit") {
                Emulator.quitGame()
            }
        }
    }

    QuickMenuHost(
        rootPage = rootPage,
        gameTitle = Emulator.currentGame?.substringAfterLast('/')?.substringBeforeLast('.'),
        onDismiss = onDismiss,
    )
}

/** Save state slots with screenshots (save or load). */
@Composable
private fun StateSlots(save: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var times by remember { mutableStateOf<List<Long?>>(emptyList()) }
    var thumbs by remember { mutableStateOf<List<android.graphics.Bitmap?>>(emptyList()) }
    LaunchedEffect(save) {
        times = withContext(Dispatchers.IO) { Emulator.slotTimes() }
        thumbs = withContext(Dispatchers.IO) {
            (0 until Emulator.STATE_SLOTS).map { runCatching { Emulator.slotThumbnail(it) }.getOrNull() }
        }
    }
    for (slot in 0 until Emulator.STATE_SLOTS) {
        val time = times.getOrNull(slot)
        val enabled = save || time != null
        QuickMenuStateSlot(
            id = "slot-${slot + 1}",
            label = "Slot ${slot + 1}",
            time = time,
            thumbnail = thumbs.getOrNull(slot),
            enabled = enabled,
            onClick = {
                scope.launch {
                    val error = if (save) Emulator.saveState(slot) else Emulator.loadState(slot)
                    if (error == null) onDone() else message = error
                }
            },
        )
    }
    message?.let {
        gb.frame.KitText(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
    }
}

package app.gbsat.emulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbsat.emulator.Emulator
import app.gbsat.emulator.NativeBridge
import app.gbsat.emulator.SaturnButton
import app.gbsat.emulator.R
import gb.frame.MechanicalArtworkPress
import gb.frame.MechanicalArtworkDPad
import gb.frame.MechanicalArtworkSlideToggle
import gb.frame.MechanicalArtworkToggleSpec
import gb.frame.TouchDPad
import gb.frame.TouchControlsLayoutMetrics
import gb.frame.TouchControlsOverlay
import gb.frame.TouchControlsPlacement
import gb.frame.TouchDirection
import gb.frame.SaturnControllerV12
import gb.frame.saturnControllerV12PortraitArtworkSize
import gb.frame.SaturnButton as KitSaturnButton
import gb.frame.SaturnDirection as KitSaturnDirection
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Aggregates every on-screen control into one mask so multi-touch never clobbers itself. */
private object TouchState {
    private val held = HashMap<Any, Int>()
    fun set(owner: Any, bits: Int) {
        synchronized(this) {
            if (bits == 0) held.remove(owner) else held[owner] = bits
            Emulator.setTouchMask(held.values.fold(0) { a, b -> a or b })
        }
    }
}

// Saturn pad skin: light grey body, crimson shoulder lobes, dark D-pad, grey X/Y/Z, green A, yellow B, blue C.
private val RingColor = Color(0xB32C2C2F)
private val Crimson = Color(0xFFA3173F)
private val LogoColor = Color(0xFF8A8A8A)
private val LabelColor = Color(0xFFD0D0D0)
private val GreyButton = Color(0xFF8E8E8E)
private val ControllerBodyTop = Color(0xFF292B31)
private val ControllerBodyBottom = Color(0xFF111216)

private class PadActions(
    val onLoad: () -> Unit,
    val onSave: () -> Unit,
    val onFast: () -> Unit,
    val onFilters: () -> Unit,
    val onMenu: () -> Unit,
    val onFps: () -> Unit,
)

// Measured in source pixels of saturn_touch_landscape_left.webp, so the thumb follows the real printed switch.
private val fpsLandscapeToggle = MechanicalArtworkToggleSpec(
    track = IntRect(70, 1466, 316, 1596),
    thumbOffCenter = Offset(131f, 1532f),
    thumbOnCenter = Offset(253f, 1532f),
    blankSampleCenter = Offset(233f, 1532f),
    thumbRadius = 49f,
)
private val fastLandscapeToggle = MechanicalArtworkToggleSpec(
    track = IntRect(365, 1466, 613, 1596),
    thumbOffCenter = Offset(426f, 1532f),
    thumbOnCenter = Offset(552f, 1532f),
    blankSampleCenter = Offset(528f, 1532f),
    thumbRadius = 49f,
)

/**
 * Saturn-specific button art inside GBFrame's transparent, full-window responsive overlay. Portrait controls anchor to
 * the bottom; landscape controls anchor to the lower screen edges. LOAD/SAVE use the quick-save slot.
 */
@Composable
fun TouchControls(
    modifier: Modifier,
    landscape: Boolean,
    responsiveLayout: Boolean = true,
    onMenu: () -> Unit = {},
    videoRatio: Float = 4f / 3f,
) {
    val scope = rememberCoroutineScope()
    val actions = PadActions(
        onLoad = { scope.launch { Emulator.showToast(Emulator.loadState(Emulator.QUICK_SLOT) ?: "Quick loaded") } },
        onSave = { scope.launch { Emulator.showToast(Emulator.saveState(Emulator.QUICK_SLOT) ?: "Quick saved") } },
        onFast = { Emulator.toggleFastForward() },
        onFilters = {
            Emulator.shader = gb.frame.Shaders.next(Emulator.shader)
            Emulator.savePrefs()
            NativeBridge.nativeSetDisplayOptions(Emulator.filter, Emulator.shader)
            Emulator.showToast("Shader: " + gb.frame.Shaders.name(Emulator.shader))
        },
        onMenu = onMenu,
        onFps = {
            val show = !(Emulator.showFps && Emulator.showSpeed)
            Emulator.showFps = show
            Emulator.showSpeed = show
            Emulator.savePrefs()
        },
    )
    if (responsiveLayout) {
        TouchControlsOverlay(
            modifier = modifier,
            referenceWidth = if (landscape) 720.dp else 480.dp,
            referenceHeight = if (landscape) 720.dp else 960.dp,
            videoAspectRatio = videoRatio,
        ) { layout ->
            if (landscape && layout.plan.placement == TouchControlsPlacement.LANDSCAPE_SIDES) ArtworkLandscapePad(Modifier.fillMaxSize(), videoRatio, actions)
            else if (landscape) LandscapePad(Modifier.fillMaxSize(), videoRatio, actions, layout)
            else if (layout.plan.placement == TouchControlsPlacement.PORTRAIT_BOTTOM) {
                val top = layout.plan.video.top + layout.plan.video.height
                ArtworkPortraitPad(Modifier.offset(y = top.dp).fillMaxWidth().height((layout.screenHeight.value - top).coerceAtLeast(0f).dp), actions)
            } else PortraitPad(Modifier.fillMaxSize(), actions, layout)
        }
    } else if (landscape) {
        ArtworkLandscapePad(modifier, videoRatio, actions)
    } else {
        ArtworkPortraitPad(modifier, actions)
    }
}

/** Artwork uses normalized hit regions so the same input positions follow the scaled image. */
@Composable
private fun ArtworkPortraitPad(modifier: Modifier, a: PadActions) {
    BoxWithConstraints(modifier) {
        val artSize = saturnControllerV12PortraitArtworkSize(maxWidth, maxHeight)
        SaturnControllerV12(
            modifier = Modifier.align(Alignment.TopCenter).requiredSize(artSize),
            fpsEnabled = Emulator.showFps && Emulator.showSpeed,
            fastEnabled = Emulator.fastForward,
            hapticsEnabled = Emulator.touchHapticsEnabled,
            onButton = { button, down ->
                val coreButton = when (button) {
                    KitSaturnButton.L -> SaturnButton.L
                    KitSaturnButton.R -> SaturnButton.R
                    KitSaturnButton.X -> SaturnButton.X
                    KitSaturnButton.Y -> SaturnButton.Y
                    KitSaturnButton.Z -> SaturnButton.Z
                    KitSaturnButton.A -> SaturnButton.A
                    KitSaturnButton.B -> SaturnButton.B
                    KitSaturnButton.C -> SaturnButton.C
                    KitSaturnButton.START -> SaturnButton.Start
                    else -> null
                }
                if (coreButton != null) {
                    TouchState.set(coreButton, if (down) coreButton.bit else 0)
                } else if (down) {
                    when (button) {
                        KitSaturnButton.MENU -> a.onMenu()
                        KitSaturnButton.SAVE -> a.onSave()
                        KitSaturnButton.LOAD -> a.onLoad()
                        KitSaturnButton.FILTERS -> a.onFilters()
                        else -> Unit
                    }
                }
            },
            onDirection = { direction, down ->
                val bits = if (!down) 0 else when (direction) {
                    KitSaturnDirection.UP -> SaturnButton.Up.bit
                    KitSaturnDirection.UP_RIGHT -> SaturnButton.Up.bit or SaturnButton.Right.bit
                    KitSaturnDirection.RIGHT -> SaturnButton.Right.bit
                    KitSaturnDirection.DOWN_RIGHT -> SaturnButton.Down.bit or SaturnButton.Right.bit
                    KitSaturnDirection.DOWN -> SaturnButton.Down.bit
                    KitSaturnDirection.DOWN_LEFT -> SaturnButton.Down.bit or SaturnButton.Left.bit
                    KitSaturnDirection.LEFT -> SaturnButton.Left.bit
                    KitSaturnDirection.UP_LEFT -> SaturnButton.Up.bit or SaturnButton.Left.bit
                }
                TouchState.set("saturn-v12-dpad", bits)
            },
            onFpsChange = {
                Emulator.showFps = it
                Emulator.showSpeed = it
                Emulator.savePrefs()
            },
            onFastChange = { Emulator.toggleFastForward() },
        )
    }
}

@Composable
private fun ArtworkLandscapePad(modifier: Modifier, ratio: Float, a: PadActions) {
    BoxWithConstraints(modifier) {
        val side = ((maxWidth - maxHeight * ratio) / 2).coerceAtLeast(0.dp)
        if (Emulator.aspect == 1 || side < 100.dp) {
            LegacyLandscapePad(Modifier.fillMaxSize(), ratio, a)
        } else {
            val edgeFill = Brush.verticalGradient(listOf(Color(0xFF292B31), Color(0xFF22242A), Color(0xFF292B31)))
            Box(Modifier.align(Alignment.CenterStart).width(side).fillMaxHeight().background(edgeFill)) {
                ArtworkPanel(Modifier.fillMaxSize(), R.drawable.saturn_touch_landscape_left, 1, a)
            }
            Box(Modifier.align(Alignment.CenterEnd).width(side).fillMaxHeight().background(edgeFill)) {
                ArtworkPanel(Modifier.fillMaxSize(), R.drawable.saturn_touch_landscape_right, 2, a)
            }
        }
    }
}

@Composable
private fun ArtworkPanel(modifier: Modifier, art: Int, panel: Int, a: PadActions) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        val artwork = ImageBitmap.imageResource(art)
        val sourceAspect = artwork.width.toFloat() / artwork.height
        val targetAspect = w.value / h.value
        // Keep the portrait art at its native proportions so circular controls stay circular. Landscape panels
        // show a little more horizontal artwork than a centered Crop, preserving the outer action labels.
        val crop = when (panel) {
            0 -> (targetAspect / sourceAspect).coerceIn(.1f, 1f)
            else -> maxOf((targetAspect / sourceAspect).coerceIn(.1f, 1f), .84f)
        }
        val cropStart = when (panel) {
            1 -> .08f
            2 -> .06f
            else -> (1f - crop) / 2f
        }.coerceIn(0f, 1f - crop)
        fun cx(x: Float) = ((x - cropStart) / crop).coerceIn(0f, 1f)
        fun cw(size: Float) = (size / crop).coerceIn(.04f, 1f)
        Canvas(Modifier.fillMaxSize().then(if (panel == 0) Modifier else Modifier.background(Color(0xFF15161A)))) {
            val sourceLeft = (cropStart * artwork.width).toInt().coerceIn(0, artwork.width - 1)
            val sourceWidth = (crop * artwork.width).toInt().coerceIn(1, artwork.width - sourceLeft)
            drawImage(
                image = artwork,
                srcOffset = androidx.compose.ui.unit.IntOffset(sourceLeft, 0),
                srcSize = IntSize(sourceWidth, artwork.height),
                dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            )
        }
        @Composable fun button(x: Float, y: Float, bw: Float, bh: Float, key: String, onPressed: (Boolean) -> Unit, latched: Boolean = false) {
            val bx = cx(x)
            val buttonW = cw(bw)
            PressArea(key, onPressed, Modifier.offset(x = w * (bx - buttonW / 2), y = h * (y - bh / 2)).size(w * buttonW, h * bh)) { down ->
                val amount by animateFloatAsState(
                    // Physical press is transient; toggle state is animated separately below.
                    if (down) 1f else 0f,
                    spring(dampingRatio = .68f, stiffness = 620f),
                    label = key,
                )
                val name = key.substringAfterLast('-')
                val isLoad = name == "load"
                val isSave = name == "save"
                val isPortraitStateAction = panel == 0 && (isLoad || isSave)
                val isLandscapeStateAction = panel != 0 && (isLoad || isSave)
                val isLandscapePrintedAction = panel != 0 && name in setOf("fast", "filters", "menu", "Start")
                val isStart = name == "Start"
                val pillLabel = when (name) {
                    "fps" -> "FPS"
                    "fast" -> "FAST"
                    "menu" -> "MENU"
                    "filters" -> "FILTERS"
                    "Start" -> "START"
                    else -> ""
                }
                val pillTint = if (isStart) Crimson else GreyButton

                val latchedProgress by animateFloatAsState(
                    if (latched) 1f else 0f,
                    tween(durationMillis = 180),
                    label = "$key-latched",
                )
                if (name == "fps" || name == "fast") {
                    if (panel == 0) {
                        ControllerArtworkPressEffect(Modifier.fillMaxSize(), name, amount, latchedProgress, true)
                    } else {
                        val sourceArea = Rect(
                            (x - bw / 2f) * artwork.width,
                            (y - bh / 2f) * artwork.height,
                            (x + bw / 2f) * artwork.width,
                            (y + bh / 2f) * artwork.height,
                        )
                        MechanicalArtworkSlideToggle(
                            artwork = artwork,
                            spec = if (name == "fps") fpsLandscapeToggle else fastLandscapeToggle,
                            sourceArea = sourceArea,
                            isOn = latched,
                            isPressed = down,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    MechanicalArtworkPress(artwork, x, y, bw, bh, name, amount)
                }
            }
        }
        @Composable fun saturn(x: Float, y: Float, bw: Float, bh: Float, b: SaturnButton) {
            button(x, y, bw, bh, "art-$panel-$b", { down -> TouchState.set(b, if (down) b.bit else 0) })
        }
        @Composable fun action(x: Float, y: Float, bw: Float, bh: Float, key: String, run: () -> Unit, latched: Boolean = false) {
            button(x, y, bw, bh, "art-$panel-$key", { down -> if (down) run() }, latched)
        }
        @Composable fun directions(x: Float, y: Float, size: Float) {
            var active by remember { mutableStateOf(emptySet<TouchDirection>()) }
            val dpadSize = if (panel == 0) w * (size / crop) else h * (size * sourceAspect)
            val dx = cx(x)
            Box(Modifier.offset(x = w * dx - dpadSize / 2, y = h * y - dpadSize / 2).size(dpadSize)) {
            TouchDPad(onDirectionsChanged = { dirs ->
                active = dirs
                TouchState.set("dpad", dirs.fold(0) { bits, direction -> bits or when (direction) {
                    TouchDirection.UP -> SaturnButton.Up.bit
                    TouchDirection.DOWN -> SaturnButton.Down.bit
                    TouchDirection.LEFT -> SaturnButton.Left.bit
                    TouchDirection.RIGHT -> SaturnButton.Right.bit
                } })
            }, hapticsEnabled = Emulator.touchHapticsEnabled,
                modifier = Modifier.fillMaxSize(),
                track = Color.Transparent, normal = Color.Transparent, selected = Color.Transparent,
                center = Color.Transparent, arrow = Color.Transparent, border = Color.Transparent)
            val diskScale = .71f
            MechanicalArtworkDPad(
                artwork = artwork,
                centerX = x,
                centerY = y,
                sourceSizeFraction = size * diskScale,
                activeDirections = active,
                modifier = Modifier.align(Alignment.Center).size(dpadSize * diskScale),
            )
            }
        }
        if (panel == 0) {
            saturn(.20f,.12f,.24f,.12f,SaturnButton.L); saturn(.81f,.12f,.24f,.12f,SaturnButton.R)
            action(.73f,.22f,.13f,.10f,"load",a.onLoad); action(.50f,.12f,.16f,.08f,"menu",a.onMenu); action(.28f,.22f,.13f,.10f,"save",a.onSave)
            directions(.28f,.47f,.38f)
            saturn(.58f,.44f,.12f,.10f,SaturnButton.X); saturn(.70f,.38f,.12f,.10f,SaturnButton.Y); saturn(.82f,.36f,.12f,.10f,SaturnButton.Z)
            saturn(.60f,.55f,.13f,.10f,SaturnButton.A); saturn(.73f,.49f,.13f,.10f,SaturnButton.B); saturn(.86f,.46f,.13f,.10f,SaturnButton.C)
            saturn(.50f,.70f,.16f,.08f,SaturnButton.Start)
            action(.27f,.81f,.20f,.085f,"fps",a.onFps,Emulator.showFps && Emulator.showSpeed)
            action(.50f,.81f,.19f,.085f,"filters",a.onFilters)
            action(.74f,.81f,.20f,.085f,"fast",a.onFast,Emulator.fastForward)
        } else if (panel == 1) {
            saturn(.36f,.12f,.55f,.15f,SaturnButton.L); action(.80f,.15f,.20f,.13f,"load",a.onLoad)
            directions(.55f,.63f,.65f)
            action(.202f,.93f,.275f,.10f,"fps",a.onFps,Emulator.showFps && Emulator.showSpeed)
            action(.512f,.93f,.275f,.10f,"fast",a.onFast,Emulator.fastForward)
            saturn(.81f,.93f,.24f,.09f,SaturnButton.Start)
        } else {
            saturn(.64f,.15f,.58f,.15f,SaturnButton.R); action(.21f,.15f,.20f,.13f,"save",a.onSave)
            saturn(.17f,.59f,.21f,.12f,SaturnButton.X); saturn(.39f,.52f,.21f,.12f,SaturnButton.Y); saturn(.63f,.48f,.21f,.12f,SaturnButton.Z)
            saturn(.22f,.74f,.23f,.12f,SaturnButton.A); saturn(.46f,.67f,.23f,.12f,SaturnButton.B); saturn(.72f,.63f,.23f,.12f,SaturnButton.C)
            action(.21f,.92f,.28f,.09f,"filters",a.onFilters); action(.54f,.92f,.28f,.09f,"menu",a.onMenu)
        }
    }
}

/** Adds a subtle, console-like depression over the printed buttons without covering the supplied Saturn artwork. */
@Composable
private fun ControllerArtworkPressEffect(
    modifier: Modifier,
    name: String,
    pressed: Float,
    latched: Float,
    mechanical: Boolean = false,
) {
    Canvas(modifier) {
        val press = pressed.coerceIn(0f, 1f)
        val isToggle = name == "fps" || name == "fast"
        if (isToggle) {
            val active = latched.coerceIn(0f, 1f)
            if (press > .01f || active > .01f) {
                val trackLeft = size.width * .045f
                val trackTop = size.height * .16f
                val trackWidth = size.width * .91f
                val trackHeight = size.height * .72f
                val trackTopLeft = Offset(trackLeft, trackTop)
                val trackSize = androidx.compose.ui.geometry.Size(trackWidth, trackHeight)
                val trackRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight * .5f)
                drawRoundRect(Color.Black.copy(alpha = .56f), trackTopLeft + Offset(0f, trackHeight * .08f), trackSize, trackRadius)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFF111214), Color(0xFF050607), Color(0xFF1A1B1E))),
                    trackTopLeft,
                    trackSize,
                    trackRadius,
                )
                drawRoundRect(
                    Color.White.copy(alpha = .18f + active * .22f),
                    trackTopLeft,
                    trackSize,
                    trackRadius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()),
                )
                if (active > .01f && !mechanical) {
                    drawRoundRect(
                        Color(0xFFFFB84D).copy(alpha = .28f * active),
                        trackTopLeft,
                        trackSize,
                        trackRadius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                }

                val knobRadius = trackHeight * .34f * (1f - press * .08f)
                val knobX = trackLeft + knobRadius + (trackWidth - knobRadius * 2f) * active
                val knobY = trackTop + trackHeight * .5f + trackHeight * .035f * press
                val knobCenter = Offset(knobX, knobY)
                drawCircle(Color.Black.copy(alpha = .72f), knobRadius, knobCenter + Offset(0f, trackHeight * .08f))
                drawCircle(
                    Brush.verticalGradient(listOf(Color(0xFFC5C7CC), Color(0xFF74777D), Color(0xFF36383D))),
                    knobRadius,
                    knobCenter,
                )
                drawCircle(
                    Color.White.copy(alpha = .38f),
                    knobRadius * .83f,
                    knobCenter - Offset(0f, knobRadius * .05f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )
            }
            return@Canvas
        }

        if (press <= .01f) return@Canvas
        val faceButton = name in setOf("A", "B", "C", "X", "Y", "Z")
        val stateButton = name == "load" || name == "save"
        val center = Offset(size.width * .5f, size.height * .5f + size.minDimension * .035f * press)
        when {
            faceButton || stateButton -> {
                val radius = size.minDimension * if (stateButton) .26f else .43f
                val compressedRadius = radius * (1f - .055f * press)
                drawCircle(Color.Black.copy(alpha = .64f * press), radius, center + Offset(0f, size.minDimension * .07f * press))
                drawCircle(Color.Black.copy(alpha = .31f * press), compressedRadius, center)
                drawCircle(
                    Color.White.copy(alpha = .30f * press),
                    compressedRadius * .82f,
                    center - Offset(0f, size.minDimension * .018f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.15.dp.toPx()),
                )
            }
            name == "L" || name == "R" -> {
                val inset = size.minDimension * .10f * press
                val topLeft = Offset(inset, inset + size.minDimension * .045f * press)
                val buttonSize = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f)
                val radius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .38f)
                drawRoundRect(Color.Black.copy(alpha = .62f * press), topLeft + Offset(0f, size.minDimension * .06f * press), buttonSize, radius)
                drawRoundRect(Color.Black.copy(alpha = .20f * press), topLeft, buttonSize, radius)
                drawRoundRect(Color.White.copy(alpha = .24f * press), topLeft, buttonSize, radius, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            }
            else -> {
                val insetX = size.width * .07f * press
                val insetY = size.height * .11f * press
                val topLeft = Offset(insetX, insetY + size.height * .045f * press)
                val buttonSize = androidx.compose.ui.geometry.Size(size.width - insetX * 2f, size.height - insetY * 2f)
                val radius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .36f)
                val shadow = Color.Black.copy(alpha = .64f * press)
                drawRoundRect(shadow, topLeft + Offset(0f, size.height * .06f * press), buttonSize, radius)
                drawRoundRect(Color.Black.copy(alpha = .24f * press), topLeft, buttonSize, radius)
                if (name == "Start") drawRoundRect(Crimson.copy(alpha = .23f * press), topLeft, buttonSize, radius)
                drawRoundRect(Color.White.copy(alpha = .30f * press), topLeft, buttonSize, radius, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun DPadPressEffect(modifier: Modifier, up: Float, down: Float, left: Float, right: Float) {
    androidx.compose.foundation.Canvas(modifier) {
        val min = size.minDimension
        val centerX = size.width * .5f
        val centerY = size.height * .5f
        // Directional arms depress independently, so simultaneous axes naturally form diagonals.
        // Shift the well's shadow/highlight around its center pivot to reinforce that movement.
        val horizontal = (right - left).coerceIn(-1f, 1f)
        val vertical = (down - up).coerceIn(-1f, 1f)
        val activity = maxOf(up, down, left, right).coerceIn(0f, 1f)
        if (activity > .01f) {
            val pivotOffset = Offset(horizontal * min * .025f, vertical * min * .025f)
            val wellRadius = min * .34f
            drawCircle(
                Color.Black.copy(alpha = .26f * activity),
                wellRadius,
                Offset(centerX, centerY) + pivotOffset,
            )
            drawCircle(
                Color.White.copy(alpha = .17f * activity),
                wellRadius * .92f,
                Offset(centerX, centerY) - pivotOffset,
                style = androidx.compose.ui.graphics.drawscope.Stroke(min * .018f),
            )
        }
        val arm = min * .30f
        val length = min * .34f
        fun press(progress: Float, direction: TouchDirection) {
            if (progress <= .01f) return
            val travel = min * .035f * progress
            val movement = when (direction) {
                TouchDirection.UP -> Offset(0f, travel)
                TouchDirection.DOWN -> Offset(0f, -travel)
                TouchDirection.LEFT -> Offset(travel, 0f)
                TouchDirection.RIGHT -> Offset(-travel, 0f)
            }
            val topLeft = when (direction) {
                TouchDirection.UP -> Offset(centerX - arm / 2, centerY - length) + movement
                TouchDirection.DOWN -> Offset(centerX - arm / 2, centerY) + movement
                TouchDirection.LEFT -> Offset(centerX - length, centerY - arm / 2) + movement
                TouchDirection.RIGHT -> Offset(centerX, centerY - arm / 2) + movement
            }
            val boxSize = if (direction == TouchDirection.UP || direction == TouchDirection.DOWN) {
                androidx.compose.ui.geometry.Size(arm, length)
            } else androidx.compose.ui.geometry.Size(length, arm)
            val corner = androidx.compose.ui.geometry.CornerRadius(arm * .18f)
            drawRoundRect(Color.Black.copy(alpha = .52f * progress), topLeft + movement * .35f, boxSize, corner)
            drawRoundRect(Color.Black.copy(alpha = .24f * progress), topLeft, boxSize, corner)
            drawRoundRect(
                Color.White.copy(alpha = .26f * progress),
                topLeft = topLeft - movement * .35f + Offset(1.dp.toPx(), -1.dp.toPx()),
                size = boxSize,
                cornerRadius = corner,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
        }
        press(up, TouchDirection.UP)
        press(down, TouchDirection.DOWN)
        press(left, TouchDirection.LEFT)
        press(right, TouchDirection.RIGHT)
    }
}

/** Original phone portrait arrangement: reserve a generous lower half for controls instead of shrinking them to the screen edge. */
@Composable
private fun LegacyPortraitPad(modifier: Modifier, a: PadActions) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            StateAction("LOAD", Icons.Default.Refresh, a.onLoad)
            StateAction("SAVE", Icons.Default.Download, a.onSave)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            if (maxHeight / maxWidth > 1.5f) {
                SaturnControllerPanel(Modifier.fillMaxSize(), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            }
            val s = minOf(1.15f, maxWidth / 392.dp, maxHeight / 290.dp).coerceAtLeast(.72f)
            Lobe("L", SaturnButton.L, left = true, Modifier.align(Alignment.TopStart))
            Lobe("R", SaturnButton.R, left = false, Modifier.align(Alignment.TopEnd))
            Text("SATURN", color = LogoColor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            Box(Modifier.align(Alignment.Center).offset(y = (-16).dp).size(392.dp, 290.dp).graphicsLayer { scaleX = s; scaleY = s }) {
                Box(Modifier.align(Alignment.TopStart).offset(x = 16.dp, y = 34.dp)) { DPadRing() }
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 34.dp)) { FaceRing() }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("FPS", GreyButton, a.onFps)
                    Pill("FAST", GreyButton, a.onFast, Emulator.fastForward)
                    Pill("FILTERS", GreyButton, a.onFilters)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) { StartPill() }
        }
    }
}

/** Original landscape phone layout scales from the side panels, not from the entire screen height. */
@Composable
private fun LegacyLandscapePad(modifier: Modifier, ratio: Float, a: PadActions) {
    BoxWithConstraints(modifier) {
        val full = Emulator.aspect == 1
        val side = if (full) 0.dp else ((maxWidth - maxHeight * ratio) / 2).coerceAtLeast(0.dp)
        val overlay = full || side < 120.dp
        val panelW = if (overlay) 200.dp else side
        val s = minOf(1f, panelW / 200.dp, maxHeight / 340.dp).coerceAtLeast(.62f)
        Box(Modifier.align(Alignment.CenterStart).width(panelW).fillMaxHeight()) {
            if (!overlay) SaturnControllerPanel(Modifier.fillMaxSize(), RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp))
            Lobe("L", SaturnButton.L, left = true, Modifier.align(Alignment.TopStart).padding(top = 68.dp))
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center).size(200.dp, 340.dp).graphicsLayer { scaleX = s; scaleY = s }) {
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 22.dp)) { StateAction("LOAD", Icons.Default.Refresh, a.onLoad) }
                    Box(Modifier.align(Alignment.Center).offset(y = (-28).dp)) { DPadRing() }
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MechanicalTogglePill("FPS", Emulator.showFps && Emulator.showSpeed, a.onFps)
                        MechanicalTogglePill("FAST", Emulator.fastForward, a.onFast)
                        StartPill()
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).width(panelW).fillMaxHeight()) {
            if (!overlay) SaturnControllerPanel(Modifier.fillMaxSize(), RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp))
            Lobe("R", SaturnButton.R, left = false, Modifier.align(Alignment.TopEnd).padding(top = 68.dp))
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center).size(200.dp, 340.dp).graphicsLayer { scaleX = s; scaleY = s }) {
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 22.dp)) { StateAction("SAVE", Icons.Default.Download, a.onSave) }
                    Box(Modifier.align(Alignment.Center).offset(y = (-28).dp)) { FaceRing() }
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("FILTERS", GreyButton, a.onFilters)
                        Pill("MENU", GreyButton, a.onMenu)
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitPad(modifier: Modifier, a: PadActions, layout: TouchControlsLayoutMetrics) {
    val s = layout.scale
    val compactSquare = layout.nearSquare
    val phonePortrait = layout.plan.placement == TouchControlsPlacement.PORTRAIT_BOTTOM
    val portraitLift = if (phonePortrait) 16.dp else 0.dp
    // On square screens the free area below a 4:3 game is shallow, so keep every action in one compact bottom row.
    val groupHeight = if (layout.compact) 260.dp else 290.dp
    Box(modifier) {
        if (phonePortrait) {
            val videoBottom = layout.plan.video.top + layout.plan.video.height
            val panelHeight = (layout.screenHeight.value - videoBottom).coerceAtLeast(0f)
            SaturnControllerPanel(
                Modifier.align(Alignment.TopStart).offset(y = videoBottom.dp).fillMaxWidth().height(panelHeight.dp),
                RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = layout.edgePadding + portraitLift).size(392.dp, groupHeight)
                .graphicsLayer {
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            Lobe("L", SaturnButton.L, left = true, Modifier.align(Alignment.TopStart))
            Lobe("R", SaturnButton.R, left = false, Modifier.align(Alignment.TopEnd))
            if (!compactSquare) {
                Text(
                    "SATURN", color = LogoColor, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                )
            }
            Box(Modifier.align(Alignment.TopStart).offset(x = 16.dp, y = 34.dp)) { DPadRing() }
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 34.dp)) { FaceRing() }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compactSquare) 6.dp else 14.dp),
            ) {
                Pill("FPS", GreyButton, a.onFps)
                Pill("FAST", GreyButton, a.onFast, Emulator.fastForward)
                Pill("FILTERS", GreyButton, a.onFilters)
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) { StartPill() }
        }
        if (!compactSquare) {
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = layout.edgePadding + portraitLift + groupHeight * s + 2.dp)
                    .graphicsLayer { scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StateAction("LOAD", Icons.Default.Refresh, a.onLoad)
                StateAction("SAVE", Icons.Default.Download, a.onSave)
            }
        }
    }
}

@Composable
private fun LandscapePad(modifier: Modifier, ratio: Float, a: PadActions, layout: TouchControlsLayoutMetrics) {
    // Keep the common GBFrame layer transparent; both clusters anchor to the lower screen edges.
    val full = Emulator.aspect == 1
    val side = if (full) 0.dp else ((layout.screenWidth - layout.screenHeight * ratio) / 2).coerceAtLeast(0.dp)
    val overlay = full || side < 120.dp
    val panelW = if (overlay) 200.dp else side
    val s = minOf(1f, panelW / 200.dp, layout.screenHeight / 340.dp).coerceAtLeast(0.62f)
    Box(modifier) {
        if (layout.plan.placement == TouchControlsPlacement.LANDSCAPE_SIDES) {
            val leftWidth = layout.plan.video.left.coerceAtLeast(0f)
            val rightLeft = layout.plan.video.left + layout.plan.video.width
            val rightWidth = (layout.screenWidth.value - rightLeft).coerceAtLeast(0f)
            SaturnControllerPanel(
                Modifier.align(Alignment.TopStart).width(leftWidth.dp).fillMaxHeight(),
                RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
            )
            SaturnControllerPanel(
                Modifier.align(Alignment.TopStart).offset(x = rightLeft.dp).width(rightWidth.dp).fillMaxHeight(),
                RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
            )
        }
        Box(Modifier.align(Alignment.BottomStart).width(panelW).fillMaxHeight()) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = layout.edgePadding).size(200.dp, 340.dp)
                    .graphicsLayer { scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 1f) },
            ) {
                Lobe("L", SaturnButton.L, left = true, Modifier.align(Alignment.TopStart).padding(top = 68.dp))
                Box(Modifier.align(Alignment.BottomCenter)) { StateAction("LOAD", Icons.Default.Refresh, a.onLoad) }
                Box(Modifier.align(Alignment.Center).offset(y = (-28).dp)) { DPadRing() }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MechanicalTogglePill("FPS", Emulator.showFps && Emulator.showSpeed, a.onFps)
                    MechanicalTogglePill("FAST", Emulator.fastForward, a.onFast)
                    StartPill()
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).width(panelW).fillMaxHeight()) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = layout.edgePadding).size(200.dp, 340.dp)
                    .graphicsLayer { scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 1f) },
            ) {
                Lobe("R", SaturnButton.R, left = false, Modifier.align(Alignment.TopEnd).padding(top = 68.dp))
                Box(Modifier.align(Alignment.BottomCenter)) { StateAction("SAVE", Icons.Default.Download, a.onSave) }
                Box(Modifier.align(Alignment.Center).offset(y = (-28).dp)) { FaceRing() }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("FILTERS", GreyButton, a.onFilters)
                    Pill("MENU", GreyButton, a.onMenu)
                }
            }
        }
    }
}

// ---- Parts -------------------------------------------------------------------------------------------------------

@Composable
private fun DPadRing() {
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        if (Emulator.analogPad) {
            Box(Modifier.fillMaxSize().background(RingColor, CircleShape), contentAlignment = Alignment.Center) {
                AnalogStick()
            }
        } else {
            DPad()
        }
    }
}

/** Saturn-specific graphite backplate, drawn only in GBFrame's reserved phone/side regions. */
@Composable
private fun SaturnControllerPanel(modifier: Modifier, shape: Shape) {
    Box(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(ControllerBodyTop, Color(0xFF1C1E23), ControllerBodyBottom)), shape)
            .border(1.dp, Color(0xFF646973).copy(alpha = .68f), shape),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.dp.toPx()
            drawLine(Color.White.copy(alpha = .08f), Offset(0f, size.height * .035f), Offset(size.width, size.height * .035f), stroke)
            val center = Offset(size.width * .25f, size.height * .48f)
            drawCircle(Color.Black.copy(alpha = .22f), size.minDimension * .18f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = .06f), size.minDimension * .19f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun FaceRing() {
    Box(Modifier.size(170.dp).background(RingColor, CircleShape)) {
        Face("X", SaturnButton.X, Modifier.offset(0.dp, 52.dp), GreyButton)
        Face("Y", SaturnButton.Y, Modifier.offset(52.dp, 30.dp), GreyButton)
        Face("Z", SaturnButton.Z, Modifier.offset(106.dp, 12.dp), GreyButton)
        Face("A", SaturnButton.A, Modifier.offset(12.dp, 110.dp), Color(0xFF1FA050))
        Face("B", SaturnButton.B, Modifier.offset(64.dp, 92.dp), Color(0xFFF2C21B))
        Face("C", SaturnButton.C, Modifier.offset(118.dp, 72.dp), Color(0xFF2B45C8))
    }
}

@Composable
private fun Face(label: String, b: SaturnButton, modifier: Modifier, tint: Color) =
    PressArea("face-$label", { down -> TouchState.set(b, if (down) b.bit else 0) }, modifier.size(52.dp)) { pressed ->
        val scale by animateFloatAsState(if (pressed) .88f else 1f, tween(75), label = "saturn-$label")
        val image = when (label) {
            "X" -> R.drawable.saturn_skin_x
            "Y" -> R.drawable.saturn_skin_y
            "Z" -> R.drawable.saturn_skin_z
            "A" -> R.drawable.saturn_skin_a
            "B" -> R.drawable.saturn_skin_b
            else -> R.drawable.saturn_skin_c
        }
        Image(painterResource(image), contentDescription = label,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (pressed) .8f else 1f })
    }

@Composable
private fun Lobe(label: String, b: SaturnButton, left: Boolean, modifier: Modifier) {
    val shape: Shape =
        if (left) RoundedCornerShape(topStart = 28.dp, bottomEnd = 44.dp) else RoundedCornerShape(topEnd = 28.dp, bottomStart = 44.dp)
    PressArea("lobe-$label", { down -> TouchState.set(b, if (down) b.bit else 0) }, modifier.size(width = 104.dp, height = 46.dp)) { pressed ->
        Box(
            Modifier.fillMaxSize().background(if (pressed) Color(0xFF3A3E45) else Color(0xFF626870), shape),
            contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Text(label, color = Color.Black.copy(alpha = .35f), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
        }
    }
}

@Composable
private fun StartPill() = LabeledPill("START", Crimson, "start") { down -> TouchState.set("start", if (down) SaturnButton.Start.bit else 0) }

@Composable
private fun Pill(label: String, tint: Color, onClick: () -> Unit, latched: Boolean = false) =
    LabeledPill(label, tint, "pill-$label", latched) { down -> if (down) onClick() }

@Composable
private fun MechanicalTogglePill(label: String, isOn: Boolean, onClick: () -> Unit) {
    val artwork = ImageBitmap.imageResource(R.drawable.saturn_touch_landscape_left)
    val spec = if (label == "FPS") fpsLandscapeToggle else fastLandscapeToggle
    val sourceArea = Rect(
        spec.track.left.toFloat(), spec.track.top.toFloat(),
        spec.track.right.toFloat(), spec.track.bottom.toFloat(),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = LabelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        PressArea("mechanical-toggle-$label", { down -> if (down) onClick() }, Modifier.size(58.dp, 30.dp)) { pressed ->
            MechanicalArtworkSlideToggle(
                artwork = artwork,
                spec = spec,
                sourceArea = sourceArea,
                isOn = isOn,
                isPressed = pressed,
                drawTrack = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LabeledPill(label: String, tint: Color, key: String, latched: Boolean = false, onPressed: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = LabelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        PressArea(key, onPressed, Modifier.size(width = 58.dp, height = 24.dp)) { pressed ->
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = if (pressed || latched) .94f else 1f; scaleY = if (pressed || latched) .90f else 1f; translationY = if (pressed || latched) 1.dp.toPx() else 0f }
                    .background(Brush.verticalGradient(listOf(tint.lighten(.2f), tint.darken(if (pressed || latched) .25f else 0f))), shape)
                    .border(1.dp, Color.Black.copy(alpha = .2f), shape),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (latched) Box(Modifier.padding(end = 6.dp).size(5.dp).background(Color(0xFFFFC15A), CircleShape))
            }
        }
    }
}

@Composable
private fun StateAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = GreyButton, modifier = Modifier.size(24.dp))
        Text(label, color = GreyButton, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** A touch target that reports press/release and exposes the pressed state to its skin. */
@Composable
private fun PressArea(key: Any, onPressed: (Boolean) -> Unit, modifier: Modifier, content: @Composable (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val haptics = Emulator.touchHapticsEnabled
    Box(
        modifier.pointerInput(key, haptics) {
            awaitEachGesture {
                awaitFirstDown()
                pressed = true
                onPressed(true)
                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                try {
                    var released = false
                    while (!released) released = awaitPointerEvent().changes.none { it.pressed }
                } finally {
                    pressed = false
                    onPressed(false)
                }
            }
        },
    ) { content(pressed) }
}

private fun Color.lighten(f: Float) = Color(red + (1f - red) * f, green + (1f - green) * f, blue + (1f - blue) * f, alpha)
private fun Color.darken(f: Float) = Color(red * (1f - f), green * (1f - f), blue * (1f - f), alpha)

@Composable
private fun DPad() {
    var active by remember { mutableStateOf(emptySet<TouchDirection>()) }
    val up by animateFloatAsState(if (TouchDirection.UP in active) 1f else 0f, spring(.62f, 720f), label = "dpad-up")
    val down by animateFloatAsState(if (TouchDirection.DOWN in active) 1f else 0f, spring(.62f, 720f), label = "dpad-down")
    val left by animateFloatAsState(if (TouchDirection.LEFT in active) 1f else 0f, spring(.62f, 720f), label = "dpad-left")
    val right by animateFloatAsState(if (TouchDirection.RIGHT in active) 1f else 0f, spring(.62f, 720f), label = "dpad-right")
    Box(Modifier.size(164.dp), contentAlignment = Alignment.Center) {
    // Keep GBFrame's established 8-way hit testing, ownership and haptics; replace only its visual surface.
    TouchDPad(
        onDirectionsChanged = { directions ->
            active = directions
            val mask = directions.fold(0) { bits, direction ->
                bits or when (direction) {
                    TouchDirection.UP -> SaturnButton.Up.bit
                    TouchDirection.DOWN -> SaturnButton.Down.bit
                    TouchDirection.LEFT -> SaturnButton.Left.bit
                    TouchDirection.RIGHT -> SaturnButton.Right.bit
                }
            }
            TouchState.set("dpad", mask)
        },
        hapticsEnabled = Emulator.touchHapticsEnabled,
        modifier = Modifier.size(164.dp),
        track = Color.Transparent, normal = Color.Transparent, selected = Color.Transparent,
        center = Color.Transparent, arrow = Color.Transparent, border = Color.Transparent,
    )
    Image(
        painter = painterResource(R.drawable.saturn_skin_dpad),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
    DPadPressEffect(Modifier.fillMaxSize(), up, down, left, right)
    }
}

@Composable
private fun AnalogStick() {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(170.dp).onSizeChanged { size = it }.background(Color(0xFF111112), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val radius = size.width / 2f
                    do {
                        val e = awaitPointerEvent()
                        val p = e.changes.firstOrNull() ?: break
                        var d = p.position - Offset(radius, radius)
                        val len = hypot(d.x, d.y)
                        if (len > radius) d = d * (radius / len)
                        knob = d
                        Emulator.setTouchStick(((d.x / radius + 1f) * 127.5f).toInt().coerceIn(0, 255), ((d.y / radius + 1f) * 127.5f).toInt().coerceIn(0, 255))
                    } while (e.changes.any { it.pressed })
                    knob = Offset.Zero; Emulator.setTouchStick(0x80, 0x80)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(knob.x.toInt(), knob.y.toInt()) }.size(64.dp).background(Color(0xFF55555A), CircleShape))
    }
}

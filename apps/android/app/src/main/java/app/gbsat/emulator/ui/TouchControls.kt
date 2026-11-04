package app.gbsat.emulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbsat.emulator.Emulator
import app.gbsat.emulator.NativeBridge
import app.gbsat.emulator.SaturnButton
import app.gbsat.emulator.R
import gb.frame.TouchDPad
import gb.frame.TouchControlsLayoutMetrics
import gb.frame.TouchControlsOverlay
import gb.frame.TouchControlsPlacement
import gb.frame.TouchDirection
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
    val onLoad: () -> Unit, val onSave: () -> Unit, val onFast: () -> Unit, val onFilters: () -> Unit, val onMenu: () -> Unit,
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
    )
    if (responsiveLayout) {
        TouchControlsOverlay(
            modifier = modifier,
            referenceWidth = if (landscape) 720.dp else 480.dp,
            referenceHeight = if (landscape) 720.dp else 960.dp,
            videoAspectRatio = videoRatio,
        ) { layout ->
            if (landscape) LandscapePad(Modifier.fillMaxSize(), videoRatio, actions, layout)
            else PortraitPad(Modifier.fillMaxSize(), actions, layout)
        }
    } else if (landscape) {
        LegacyLandscapePad(modifier, videoRatio, actions)
    } else {
        LegacyPortraitPad(modifier, actions)
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
                    Pill("FAST", GreyButton, a.onFast)
                    Pill("MENU", GreyButton, a.onMenu)
                    StartPill()
                }
            }
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
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("FAST", GreyButton, a.onFast)
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
                Pill("FAST", GreyButton, a.onFast)
                Pill("MENU", GreyButton, a.onMenu)
                StartPill()
            }
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
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("FAST", GreyButton, a.onFast)
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
private fun Pill(label: String, tint: Color, onClick: () -> Unit) =
    LabeledPill(label, tint, "pill-$label") { down -> if (down) onClick() }

@Composable
private fun LabeledPill(label: String, tint: Color, key: String, onPressed: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = LabelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        PressArea(key, onPressed, Modifier.size(width = 58.dp, height = 24.dp)) { pressed ->
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(tint.lighten(.2f), tint.darken(if (pressed) .25f else 0f))), shape)
                    .border(1.dp, Color.Black.copy(alpha = .2f), shape),
            )
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
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val half = size.minDimension * .5f
        val center = Offset(size.width * .5f, size.height * .5f)
        val radius = size.minDimension * .055f
        val highlight = Color.White.copy(alpha = .30f)
        if (TouchDirection.UP in active && TouchDirection.LEFT in active) drawCircle(highlight, radius, Offset(center.x - half * .46f, center.y - half * .46f))
        if (TouchDirection.UP in active) drawCircle(highlight, radius, Offset(center.x, center.y - half * .68f))
        if (TouchDirection.UP in active && TouchDirection.RIGHT in active) drawCircle(highlight, radius, Offset(center.x + half * .46f, center.y - half * .46f))
        if (TouchDirection.LEFT in active) drawCircle(highlight, radius, Offset(center.x - half * .68f, center.y))
        if (TouchDirection.RIGHT in active) drawCircle(highlight, radius, Offset(center.x + half * .68f, center.y))
        if (TouchDirection.DOWN in active && TouchDirection.LEFT in active) drawCircle(highlight, radius, Offset(center.x - half * .46f, center.y + half * .46f))
        if (TouchDirection.DOWN in active) drawCircle(highlight, radius, Offset(center.x, center.y + half * .68f))
        if (TouchDirection.DOWN in active && TouchDirection.RIGHT in active) drawCircle(highlight, radius, Offset(center.x + half * .46f, center.y + half * .46f))
    }
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

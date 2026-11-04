package app.gbsat.emulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbsat.emulator.Emulator
import app.gbsat.emulator.NativeBridge
import app.gbsat.emulator.SaturnButton
import gb.frame.TouchDPad
import gb.frame.TouchControlsLayoutMetrics
import gb.frame.TouchControlsOverlay
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
            val s = minOf(1.15f, maxWidth / 364.dp, maxHeight / 290.dp).coerceAtLeast(.72f)
            Lobe("L", SaturnButton.L, left = true, Modifier.align(Alignment.TopStart))
            Lobe("R", SaturnButton.R, left = false, Modifier.align(Alignment.TopEnd))
            Text("SATURN", color = LogoColor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            Box(Modifier.align(Alignment.Center).size(364.dp, 290.dp).graphicsLayer { scaleX = s; scaleY = s }) {
                Box(Modifier.align(Alignment.TopStart).offset(x = 8.dp, y = 34.dp)) { DPadRing() }
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 34.dp)) { FaceRing() }
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
            Lobe("L", SaturnButton.L, left = true, Modifier.align(Alignment.TopStart).padding(top = 68.dp))
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center).size(200.dp, 340.dp).graphicsLayer { scaleX = s; scaleY = s }) {
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 14.dp)) { StateAction("LOAD", Icons.Default.Refresh, a.onLoad) }
                    Box(Modifier.align(Alignment.Center).offset(y = (-16).dp)) { DPadRing() }
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("FAST", GreyButton, a.onFast)
                        StartPill()
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).width(panelW).fillMaxHeight()) {
            Lobe("R", SaturnButton.R, left = false, Modifier.align(Alignment.TopEnd).padding(top = 68.dp))
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center).size(200.dp, 340.dp).graphicsLayer { scaleX = s; scaleY = s }) {
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 14.dp)) { StateAction("SAVE", Icons.Default.Download, a.onSave) }
                    Box(Modifier.align(Alignment.Center).offset(y = (-16).dp)) { FaceRing() }
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    // On square screens the free area below a 4:3 game is shallow, so keep every action in one compact bottom row.
    val groupHeight = if (layout.compact) 260.dp else 290.dp
    Box(modifier) {
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = layout.edgePadding).size(364.dp, groupHeight)
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
            Box(Modifier.align(Alignment.TopStart).offset(x = 8.dp, y = 34.dp)) { DPadRing() }
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 34.dp)) { FaceRing() }
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
                    .padding(bottom = layout.edgePadding + groupHeight * s + 2.dp)
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
        Box(Modifier.align(Alignment.BottomStart).width(panelW).fillMaxHeight()) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = layout.edgePadding).size(200.dp, 340.dp)
                    .graphicsLayer { scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 1f) },
            ) {
                Lobe("L", SaturnButton.L, left = true, Modifier.align(Alignment.TopStart).padding(top = 68.dp))
                Box(Modifier.align(Alignment.BottomCenter)) { StateAction("LOAD", Icons.Default.Refresh, a.onLoad) }
                Box(Modifier.align(Alignment.Center).offset(y = (-16).dp)) { DPadRing() }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Box(Modifier.align(Alignment.Center).offset(y = (-16).dp)) { FaceRing() }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Box(Modifier.size(170.dp).background(RingColor, CircleShape), contentAlignment = Alignment.Center) {
        if (Emulator.analogPad) AnalogStick() else DPad()
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
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint.lighten(.25f), tint.darken(if (pressed) .25f else .08f))), CircleShape)
                .border(1.dp, Color.Black.copy(alpha = .25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = tint.darken(.45f), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }

@Composable
private fun Lobe(label: String, b: SaturnButton, left: Boolean, modifier: Modifier) {
    val shape: Shape =
        if (left) RoundedCornerShape(topStart = 28.dp, bottomEnd = 44.dp) else RoundedCornerShape(topEnd = 28.dp, bottomStart = 44.dp)
    PressArea("lobe-$label", { down -> TouchState.set(b, if (down) b.bit else 0) }, modifier.size(width = 104.dp, height = 46.dp)) { pressed ->
        Box(
            Modifier.fillMaxSize().background(if (pressed) Crimson.darken(.2f) else Crimson, shape),
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
private fun DPad() = TouchDPad(
    onDirectionsChanged = { directions ->
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
    track = Color(0xFF111112), normal = Color(0xFF1B1B1D), selected = Color(0xFF4A4A4E),
    center = Color(0xFF0A0A0B), arrow = Color(0xFF8A8A8E), border = Color.White.copy(alpha = .08f),
)

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

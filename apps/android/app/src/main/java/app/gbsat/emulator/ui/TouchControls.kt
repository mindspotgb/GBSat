package app.gbsat.emulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.gbsat.emulator.Emulator
import app.gbsat.emulator.SaturnButton
import gb.frame.TouchControlButton
import gb.frame.TouchDirection
import gb.frame.TouchDPad
import kotlin.math.abs
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

private val padColor = Color.White.copy(alpha = .22f)
private val padPressed = Color.White.copy(alpha = .5f)

@Composable
fun TouchControls(modifier: Modifier, landscape: Boolean) {
    BoxWithConstraints(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Scale the controls down on small or square screens so they never overlap each other
        val needW = 400.dp
        val needH = if (landscape) 330.dp else 290.dp
        val s = minOf(1f, maxWidth / needW, maxHeight / needH).coerceAtLeast(0.5f)
        fun Modifier.scaled(originX: Float, originY: Float) =
            graphicsLayer { scaleX = s; scaleY = s; transformOrigin = TransformOrigin(originX, originY) }
        val shoulderTop = if (landscape) 56.dp * s else 0.dp

        // Shoulders
        Row(Modifier.align(Alignment.TopStart).padding(top = shoulderTop).scaled(0f, 0f)) { Shoulder("L", SaturnButton.L) }
        Row(Modifier.align(Alignment.TopEnd).padding(top = shoulderTop).scaled(1f, 0f)) { Shoulder("R", SaturnButton.R) }

        // Left: D-pad or analog stick
        Box(Modifier.align(if (landscape) Alignment.BottomStart else Alignment.CenterStart).padding(bottom = 16.dp * s)
            .scaled(0f, if (landscape) 1f else 0.5f)) {
            if (Emulator.analogPad) AnalogStick() else DPad()
        }

        // Right: face buttons
        Box(Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.CenterEnd).padding(bottom = 16.dp * s)
            .scaled(1f, if (landscape) 1f else 0.5f).size(190.dp)) {
            Face("X", SaturnButton.X, Modifier.align(Alignment.TopStart).offset(x = 0.dp, y = 8.dp), Color(0xFFCFD5E4))
            Face("Y", SaturnButton.Y, Modifier.align(Alignment.TopCenter).offset(y = (-4).dp), Color(0xFFCFD5E4))
            Face("Z", SaturnButton.Z, Modifier.align(Alignment.TopEnd).offset(y = 8.dp), Color(0xFFCFD5E4))
            Face("A", SaturnButton.A, Modifier.align(Alignment.BottomStart).offset(y = (-8).dp), Color(0xFF16A982))
            Face("B", SaturnButton.B, Modifier.align(Alignment.BottomCenter).offset(y = 4.dp), Color(0xFFE7B832))
            Face("C", SaturnButton.C, Modifier.align(Alignment.BottomEnd).offset(y = (-8).dp), Color(0xFF3566D4))
        }

        // Start
        Box(Modifier.align(Alignment.BottomCenter).scaled(0.5f, 1f)) { StartButton() }
    }
}

@Composable
private fun Face(label: String, b: SaturnButton, modifier: Modifier, tint: Color) =
    TouchControlButton(label, { down -> TouchState.set(b, if (down) b.bit else 0) }, modifier.size(58.dp), tint, hapticsEnabled = Emulator.touchHapticsEnabled)

@Composable
private fun Shoulder(label: String, b: SaturnButton) =
    TouchControlButton(label, { down -> TouchState.set(b, if (down) b.bit else 0) }, Modifier.size(width = 96.dp, height = 44.dp), Color(0xFF30354A), RoundedCornerShape(12.dp), Emulator.touchHapticsEnabled)

@Composable
private fun StartButton() =
    TouchControlButton("START", { down -> TouchState.set("start", if (down) SaturnButton.Start.bit else 0) }, Modifier.size(width = 84.dp, height = 36.dp), Color(0xFF354F9A), RoundedCornerShape(18.dp), Emulator.touchHapticsEnabled)

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
)

@Composable
private fun AnalogStick() {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(170.dp).onSizeChanged { size = it }.background(padColor, CircleShape)
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
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(knob.x.toInt(), knob.y.toInt()) }.size(64.dp).background(padPressed, CircleShape))
    }
}

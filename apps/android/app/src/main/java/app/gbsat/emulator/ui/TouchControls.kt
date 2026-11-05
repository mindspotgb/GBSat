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
    ArtworkPanel(modifier, R.drawable.saturn_panel_portrait, 0, a)
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
                ArtworkPanel(Modifier.fillMaxSize(), R.drawable.saturn_panel_left, 1, a)
            }
            Box(Modifier.align(Alignment.CenterEnd).width(side).fillMaxHeight().background(edgeFill)) {
                ArtworkPanel(Modifier.fillMaxSize(), R.drawable.saturn_panel_right, 2, a)
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
        val crop = if (panel == 0) 1f else ((w.value / h.value) / (232f / 400f)).coerceIn(.1f, 1f)
        val cropStart = (1f - crop) / 2f
        fun cx(x: Float) = ((x - cropStart) / crop).coerceIn(0f, 1f)
        fun cw(size: Float) = (size / crop).coerceIn(.04f, .8f)
        Image(painterResource(art), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = if (panel == 0) ContentScale.FillBounds else ContentScale.Crop)
        @Composable fun button(x: Float, y: Float, bw: Float, bh: Float, key: String, onPressed: (Boolean) -> Unit, latched: Boolean = false) {
            val bx = if (panel == 0) x else cx(x)
            val buttonW = if (panel == 0) bw else cw(bw)
            PressArea(key, onPressed, Modifier.offset(x = w * (bx - buttonW / 2), y = h * (y - bh / 2)).size(w * buttonW, h * bh)) { down ->
                val amount by animateFloatAsState(if (down || latched) 1f else 0f, tween(if (down || latched) 65 else 140), label = key)
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    if (amount > 0f) {
                        val insetX = (size.width * .035f * amount).toInt()
                        val insetY = (size.height * .035f * amount).toInt()
                        drawImage(artwork,
                            srcOffset = androidx.compose.ui.unit.IntOffset(((x - bw / 2) * artwork.width).toInt(), ((y - bh / 2) * artwork.height).toInt()),
                            srcSize = IntSize((bw * artwork.width).toInt().coerceAtLeast(1), (bh * artwork.height).toInt().coerceAtLeast(1)),
                            dstOffset = androidx.compose.ui.unit.IntOffset(insetX, insetY * 2),
                            dstSize = IntSize((size.width.toInt() - insetX * 2).coerceAtLeast(1), (size.height.toInt() - insetY * 2).coerceAtLeast(1)),
                            alpha = amount)
                        drawRect(Color.Black.copy(alpha = amount * .12f))
                    }
                }
                if (panel == 0 && (key == "art-0-load" || key == "art-0-save")) {
                    Text(key.substringAfterLast('-').uppercase(), color = Color(0xFFB8BBC0), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
            val glow by animateFloatAsState(if (active.isEmpty()) 0f else 1f, tween(100), label = "saturn-direction")
            val dpadSize = if (panel == 0) w * size else h * .34f
            val dx = if (panel == 0) x else cx(x)
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
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                if (glow > 0f) {
                    val dy = size * (w.value / h.value)
                    val shiftX = ((if (TouchDirection.RIGHT in active) 1 else 0) - (if (TouchDirection.LEFT in active) 1 else 0)) * 2.dp.toPx() * glow
                    val shiftY = ((if (TouchDirection.DOWN in active) 1 else 0) - (if (TouchDirection.UP in active) 1 else 0)) * 2.dp.toPx() * glow
                    drawImage(artwork,
                        srcOffset = androidx.compose.ui.unit.IntOffset(((x - size / 2) * artwork.width).toInt(), ((y - dy / 2) * artwork.height).toInt()),
                        srcSize = IntSize((size * artwork.width).toInt().coerceAtLeast(1), (dy * artwork.height).toInt().coerceAtLeast(1)),
                        dstOffset = androidx.compose.ui.unit.IntOffset(shiftX.toInt(), shiftY.toInt()),
                        dstSize = IntSize(this.size.width.toInt().coerceAtLeast(1), this.size.height.toInt().coerceAtLeast(1)), alpha = glow)
                }
                val tint = Color.Black.copy(alpha = .42f * glow)
                val radius = this.size.minDimension * .11f
                if (TouchDirection.UP in active) drawCircle(tint, radius, Offset(this.size.width * .5f, this.size.height * .20f))
                if (TouchDirection.DOWN in active) drawCircle(tint, radius, Offset(this.size.width * .5f, this.size.height * .80f))
                if (TouchDirection.LEFT in active) drawCircle(tint, radius, Offset(this.size.width * .20f, this.size.height * .5f))
                if (TouchDirection.RIGHT in active) drawCircle(tint, radius, Offset(this.size.width * .80f, this.size.height * .5f))
            }
            }
        }
        if (panel == 0) {
            saturn(.17f,.10f,.24f,.12f,SaturnButton.L); saturn(.83f,.10f,.24f,.12f,SaturnButton.R)
            action(.36f,.14f,.18f,.055f,"load",a.onLoad); action(.64f,.14f,.18f,.055f,"save",a.onSave)
            directions(.26f,.47f,.36f)
            saturn(.58f,.43f,.12f,.10f,SaturnButton.X); saturn(.70f,.38f,.12f,.10f,SaturnButton.Y); saturn(.83f,.35f,.12f,.10f,SaturnButton.Z)
            saturn(.60f,.55f,.13f,.10f,SaturnButton.A); saturn(.74f,.49f,.13f,.10f,SaturnButton.B); saturn(.88f,.46f,.13f,.10f,SaturnButton.C)
            action(.28f,.94f,.16f,.09f,"fast",a.onFast,Emulator.fastForward); action(.50f,.94f,.16f,.09f,"menu",a.onMenu); saturn(.72f,.94f,.16f,.09f,SaturnButton.Start)
        } else if (panel == 1) {
            saturn(.36f,.07f,.55f,.12f,SaturnButton.L); action(.80f,.08f,.18f,.09f,"load",a.onLoad)
            directions(.55f,.63f,.65f)
            action(.46f,.97f,.30f,.08f,"fast",a.onFast,Emulator.fastForward); saturn(.80f,.97f,.30f,.08f,SaturnButton.Start)
        } else {
            saturn(.64f,.07f,.58f,.12f,SaturnButton.R); action(.21f,.08f,.18f,.09f,"save",a.onSave)
            saturn(.17f,.59f,.21f,.12f,SaturnButton.X); saturn(.39f,.52f,.21f,.12f,SaturnButton.Y); saturn(.63f,.48f,.21f,.12f,SaturnButton.Z)
            saturn(.22f,.74f,.23f,.12f,SaturnButton.A); saturn(.46f,.67f,.23f,.12f,SaturnButton.B); saturn(.72f,.63f,.23f,.12f,SaturnButton.C)
            action(.21f,.97f,.28f,.08f,"filters",a.onFilters); action(.54f,.97f,.28f,.08f,"menu",a.onMenu)
        }
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

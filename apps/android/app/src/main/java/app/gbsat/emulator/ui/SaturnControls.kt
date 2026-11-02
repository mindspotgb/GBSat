package app.gbsat.emulator.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import app.gbsat.emulator.Emulator
import app.gbsat.emulator.R
import app.gbsat.emulator.SaturnButton
import gb.frame.ControlButton
import gb.frame.ControlsHost
import gb.frame.HotkeyAction
import gb.frame.PadButton
import gb.frame.PadLayout
import gb.frame.TouchControlsMode
import gb.frame.padRect

/**
 * What GBSat gives the kit's standard Controls page: the Saturn control pad (drawing and buttons) and where its
 * bindings and options live. The page itself, the prompts and the key capture are the kit's (ControlsPage,
 * ControlsCapturePrompts, ControlsCapture).
 */
internal object SaturnControls : ControlsHost {
    override val console = "Saturn"
    override val pad = SaturnPad

    override val buttons: List<ControlButton> = listOf(
        SaturnButton.Up, SaturnButton.Down, SaturnButton.Left, SaturnButton.Right, SaturnButton.A, SaturnButton.B,
        SaturnButton.C, SaturnButton.X, SaturnButton.Y, SaturnButton.Z, SaturnButton.L, SaturnButton.R, SaturnButton.Start,
    ).map { b ->
        ControlButton(
            id = b.name, label = label(b), color = faceColor(b),
            badge = when (b) { SaturnButton.Start -> "ST"; else -> b.name },
            icon = when (b) {
                SaturnButton.Up -> Icons.Default.KeyboardArrowUp
                SaturnButton.Down -> Icons.Default.KeyboardArrowDown
                SaturnButton.Left -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
                SaturnButton.Right -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                else -> null
            },
        )
    }

    override fun keysFor(id: String): List<Int> = Emulator.keysFor(SaturnButton.valueOf(id))
    override fun bindKey(id: String, keyCode: Int) = Emulator.bindKey(SaturnButton.valueOf(id), keyCode)
    override var remapTarget: String?
        get() = Emulator.remapTarget?.name
        set(value) { Emulator.remapTarget = value?.let { SaturnButton.valueOf(it) } }
    override fun resetKeyMap() = Emulator.resetKeyMap()

    override val hotkeys: Map<HotkeyAction, Set<Int>> get() = Emulator.hotkeys
    override fun bindHotkey(action: HotkeyAction, keys: Set<Int>) = Emulator.bindHotkey(action, keys)
    override var hotkeyCapture: HotkeyAction?
        get() = Emulator.hotkeyCapture
        set(value) { Emulator.hotkeyCapture = value }
    override var hotkeyCaptureKeys: Set<Int>
        get() = Emulator.hotkeyCaptureKeys
        set(value) { Emulator.hotkeyCaptureKeys = value }

    override var fastForwardToggle: Boolean
        get() = Emulator.ffToggle
        set(value) { Emulator.ffToggle = value; Emulator.savePrefs(); Emulator.applyOptions() }
    override var touchControlsMode: TouchControlsMode
        get() = Emulator.touchControlsMode
        set(value) { Emulator.touchControlsMode = value; Emulator.savePrefs(); Emulator.applyOptions() }
    override var touchHapticsEnabled: Boolean
        get() = Emulator.touchHapticsEnabled
        set(value) { Emulator.touchHapticsEnabled = value; Emulator.savePrefs() }

    private fun label(b: SaturnButton) = when (b) {
        SaturnButton.Up -> "D-pad up"; SaturnButton.Down -> "D-pad down"; SaturnButton.Left -> "D-pad left"; SaturnButton.Right -> "D-pad right"
        SaturnButton.L -> "L shoulder"; SaturnButton.R -> "R shoulder"
        else -> "${b.name} button"
    }
}

private fun faceColor(b: SaturnButton) = when (b) {
    SaturnButton.A -> Color(0xFF20BE97)
    SaturnButton.B -> Color(0xFFFFD34B)
    SaturnButton.C -> Color(0xFF416FDD)
    SaturnButton.X, SaturnButton.Y, SaturnButton.Z -> Color(0xFFF1F1F1)
    SaturnButton.L, SaturnButton.R -> Color(0xFF242424)
    SaturnButton.Start -> Color(0xFF416FDD)
    else -> Color(0xFF555555)
}

/** Saturn controller artwork with transparent hit targets aligned over its printed controls. */
private val SaturnPad by lazy { PadLayout(listOf(
    PadButton("L", x = .24f, y = .155f, w = .09f, h = .07f, wOfWidth = true, shape = CircleShape, color = Color.Transparent, drawOverlay = false),
    PadButton("R", x = .76f, y = .155f, w = .09f, h = .07f, wOfWidth = true, shape = CircleShape, color = Color.Transparent, drawOverlay = false),
    PadButton("Up", x = .265f, y = .405f, w = .13f, h = .13f, shape = padRect(8), color = Color.Transparent, drawOverlay = false),
    PadButton("Down", x = .265f, y = .59f, w = .13f, h = .13f, shape = padRect(8), color = Color.Transparent, drawOverlay = false),
    PadButton("Left", x = .215f, y = .49f, w = .13f, h = .13f, shape = padRect(8), color = Color.Transparent, drawOverlay = false),
    PadButton("Right", x = .315f, y = .49f, w = .13f, h = .13f, shape = padRect(8), color = Color.Transparent, drawOverlay = false),
    PadButton("Start", x = .50f, y = .64f, w = .15f, h = .14f, shape = padRect(8), color = Color.Transparent, drawOverlay = false),
    PadButton("X", x = .635f, y = .455f, w = .13f, h = .13f, shape = CircleShape, color = Color.Transparent, drawOverlay = false),
    PadButton("Y", x = .713f, y = .384f, w = .13f, h = .13f, shape = CircleShape, color = Color.Transparent, drawOverlay = false),
    PadButton("Z", x = .795f, y = .324f, w = .13f, h = .13f, shape = CircleShape, color = Color.Transparent, drawOverlay = false),
    PadButton("A", x = .668f, y = .635f, w = .13f, h = .13f, shape = CircleShape, color = Color.Transparent, drawOverlay = false),
    PadButton("B", x = .755f, y = .552f, w = .13f, h = .13f, shape = CircleShape, color = Color.Transparent, drawOverlay = false),
    PadButton("C", x = .845f, y = .49f, w = .13f, h = .13f, shape = CircleShape, color = Color.Transparent, drawOverlay = false),
), aspectRatio = 1602f / 854f, artwork = { painterResource(R.drawable.saturn_controller) }) }

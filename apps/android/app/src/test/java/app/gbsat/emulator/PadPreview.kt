package app.gbsat.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.gbsat.emulator.ui.TouchControls
import org.junit.Test
import org.robolectric.annotation.Config

/** Temporary: renders the touch pad skin. */
class PadPreviewLandscape : ScreenTest() {
    override val out = "build/pad-preview"
    @Test fun landscape() = shot("pad-landscape", { Box(Modifier.fillMaxSize().background(Color(0xFF223344))) { TouchControls(Modifier.fillMaxSize(), landscape = true) } })
}

@Config(qualifiers = "w411dp-h891dp-port-xhdpi")
class PadPreviewPortrait : ScreenTest() {
    override val out = "build/pad-preview"
    @Test fun portrait() = shot("pad-portrait", { Box(Modifier.fillMaxSize().background(Color.Black)) { androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().weight(0.45f).background(Color(0xFF223344)))
        TouchControls(Modifier.fillMaxSize().weight(0.55f), landscape = false) } } })
}

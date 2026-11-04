package app.gbsat.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.gbsat.emulator.ui.TouchControls
import org.junit.Test
import org.robolectric.annotation.Config

/** Renders the touch pad skin (not part of the README screenshots). */
@Config(qualifiers = "w891dp-h411dp-land-xhdpi")
class PadPreviewLandscape : ScreenTest() {
    override val out = "build/pad-preview"
    @Test fun landscape() = shot("pad-landscape", { Box(Modifier.fillMaxSize().background(Color.Black)) { Box(Modifier.align(androidx.compose.ui.Alignment.Center).fillMaxHeight().aspectRatio(4f / 3f).background(Color(0xFF223344))); TouchControls(Modifier.fillMaxSize(), landscape = true) } })
}

@Config(qualifiers = "w411dp-h891dp-port-xhdpi")
class PadPreviewPortrait : ScreenTest() {
    override val out = "build/pad-preview"
    @Test fun portrait() = shot("pad-portrait", { Box(Modifier.fillMaxSize().background(Color.Black)) { androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().weight(0.45f).background(Color(0xFF223344)))
        TouchControls(Modifier.fillMaxSize().weight(0.55f), landscape = false) } } })
}

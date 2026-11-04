package app.gbsat.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.gbsat.emulator.ui.TouchControls
import org.junit.Test
import org.robolectric.annotation.Config

/** Renders the touch pad skin (not part of the README screenshots). */
@Config(qualifiers = "w873dp-h491dp-land-xhdpi")
class PadPreviewLandscape : ScreenTest() {
    override val out = "build/pad-preview"
    @Test fun landscape16x9() = shot("pad-landscape-16x9", {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.align(androidx.compose.ui.Alignment.Center).fillMaxHeight().aspectRatio(4f / 3f).background(Color(0xFF223344)))
            TouchControls(Modifier.fillMaxSize(), landscape = true)
        }
    })
}

@Config(qualifiers = "w640dp-h480dp-land-xhdpi")
class PadPreviewFourThreeFullscreen : ScreenTest() {
    override val out = "build/pad-preview"
    @Test fun fullscreen4x3() {
        Emulator.aspect = 1
        shot("pad-landscape-4x3-fullscreen", {
            Box(Modifier.fillMaxSize().background(Color(0xFF223344))) {
                TouchControls(Modifier.fillMaxSize(), landscape = true, videoRatio = 4f / 3f)
            }
        })
    }
}

@Config(qualifiers = "w480dp-h480dp-land-xhdpi")
class PadPreviewSquare : ScreenTest() {
    override val out = "build/pad-preview"
    @Test fun squareRotate() = shot("pad-square-rotate", {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.align(androidx.compose.ui.Alignment.TopCenter).fillMaxWidth().aspectRatio(4f / 3f).background(Color(0xFF223344)))
            TouchControls(Modifier.fillMaxSize(), landscape = false)
        }
    })
}

@Config(qualifiers = "w393dp-h873dp-port-xhdpi")
class PadPreviewPortrait : ScreenTest() {
    override val out = "build/pad-preview"
    @Test fun portrait16x9() = shot("pad-portrait-16x9", {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.align(androidx.compose.ui.Alignment.TopCenter).fillMaxWidth().aspectRatio(4f / 3f).background(Color(0xFF223344)))
            TouchControls(Modifier.fillMaxSize(), landscape = false)
        }
    })
}

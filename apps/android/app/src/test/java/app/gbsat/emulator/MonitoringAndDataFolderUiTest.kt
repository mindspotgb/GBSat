package app.gbsat.emulator

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import gb.frame.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MonitoringAndDataFolderUiTest {
    @get:Rule val compose = createComposeRule()
    private class Settings : MonitoringSettings {
        override var options by mutableStateOf(PerfOptions())
        override var showFfButton by mutableStateOf(false)
        override fun update(options: PerfOptions, showFfButton: Boolean) { this.options = options; this.showFfButton = showFfButton }
    }
    private class Audio : AudioSettings {
        override var volume by mutableIntStateOf(50)
        override var fastForwardAudio by mutableIntStateOf(FastForwardAudio.DEFAULT)
        var saves = 0
        override fun save() { saves++ }
    }
    @Test fun fastForwardActionDisappearsWhenOverlayButtonIsEnabled() {
        val settings = Settings()
        compose.setContent {
            val page = remember { QuickMenuPage("test", "Paused") {
                MonitoringQuickMenuRows(settings)
                FastForwardQuickMenuAction(settings, false) {}
            } }
            QuickMenuHost(page, "Game", {})
        }
        compose.onNodeWithText("Fast-forward", useUnmergedTree = true).assertExists()
        compose.runOnIdle { settings.set("fastForwardButton", true) }
        compose.onNodeWithText("Fast-forward", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Show fast-forward button", useUnmergedTree = true).assertExists()
    }
    @Test fun sharedWizardIncludesOptionalDataFolderAndChooseAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = AppDataFolder(context, "Test", mapOf("saves" to java.io.File(context.filesDir, "folder-ui-test")))
        compose.setContent { SetupWizard(listOf(dataFolderWizardStep(folder)), {}) }
        compose.onAllNodesWithText("Data folder", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("Choose", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Start", useUnmergedTree = true).assertExists()
    }

    @Test fun systemUsesTheCommonFolderCapabilityAndStartupOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = AppDataFolder(context, "Test", mapOf("saves" to java.io.File(context.filesDir, "system-test")))
        val toggle = SystemToggleSetting(true, {})
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SystemSettings(folder,
                    SystemFolderSetting(null, "Choose game images", {}),
                    SystemBiosFolderSetting(null, "Choose firmware files", listOf(SystemBiosFile("boot.bin", false)), {}),
                    false, false, {}, {},
                    startup = SystemStartupOptions(toggle, toggle, toggle, toggle),
                    hleBios = { Text("HLE BIOS") }, appFileSettings = { Text("Cartridge ROMs") },
                    recommendedSettingsPerGame = toggle,
                    cdReadSpeed = SystemCdReadSpeedSetting(
                        4,
                        listOf(CdReadSpeedOption(2, "Original"), CdReadSpeedOption(4, "4x")),
                        {},
                        "Disc speed",
                    ),
                    coreSettings = { Text("Custom option") })
            }
        }
        fun texts(node: SemanticsNode): List<String> = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::texts)
        val all = texts(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        val expected = listOf("DATA", "Data folder", "Games folder", "Bios folder", "HLE BIOS",
            "SAVE STATES", "Auto save", "Auto load", "PER-GAME PROFILES", "Recommended settings per game",
            "LOADING TIMES HACKS", "Fast boot", "Skip loading screens", "Skip transitions", "Skip save waits",
            "CD read speed", "Custom option", "Cartridge ROMs")
        expected.zipWithNext().forEach { (before, after) ->
            org.junit.Assert.assertTrue("$before must precede $after: $all", all.indexOf(before) >= 0 && all.indexOf(before) < all.indexOf(after))
        }
        compose.onNodeWithText("Getting started", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Check access").assertDoesNotExist()
        compose.onNodeWithText("Choose game images").assertExists()
        compose.onNodeWithText("Choose firmware files\nboot.bin — Missing").assertExists()
    }

    @Test fun sharedAudioSettingsVolumeButtonsAdjustAndSaveByTenPercent() {
        val audio = Audio()
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) { AudioSettingsGroups(audio) }
        }
        compose.onNodeWithContentDescription("Increase volume by 10%").assertIsDisplayed().performClick()
        compose.onNodeWithText("60%").assertExists()
        compose.onNodeWithContentDescription("Decrease volume by 10%").assertIsDisplayed().performClick()
        compose.onNodeWithText("50%").assertExists()
        org.junit.Assert.assertEquals(2, audio.saves)
    }

    @Test fun systemOmitsUnsupportedBiosEmulationAndStartupFeatures() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = AppDataFolder(context, "Test", mapOf("saves" to java.io.File(context.filesDir, "system-test")))
        compose.setContent {
            Column { SystemSettings(folder, SystemFolderSetting("SD/Games", "Choose games", {}),
                SystemBiosFolderSetting("SD/Data/bios", "Choose firmware", listOf(SystemBiosFile("bios.bin", true)), {}), false, false, {}, {},
                startup = SystemStartupOptions(SystemToggleSetting(true, {}))) }
        }
        compose.onNodeWithText("HLE BIOS").assertDoesNotExist()
        compose.onNodeWithText("Fast boot").assertExists() // mandatory in every app
        compose.onNodeWithText("Skip loading screens").assertDoesNotExist()
        compose.onNodeWithText("SD/Games").assertExists()
        compose.onNodeWithText("SD/Data/bios\nbios.bin — Loaded").assertExists()
    }
}

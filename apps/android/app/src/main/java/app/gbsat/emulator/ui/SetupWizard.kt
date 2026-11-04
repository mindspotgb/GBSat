package app.gbsat.emulator.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.gbsat.emulator.Emulator
import app.gbsat.emulator.Storage
import gb.frame.WizardStatus
import gb.frame.WizardStep
import kotlinx.coroutines.launch

/** First-run setup (shared wizard from the UI kit): welcome, data folder, BIOS, games folder, performance preset. */
@Composable
fun SetupWizard(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { message = Emulator.importBios(ctx, it) ?: "BIOS loaded" } }
    }
    val gamesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { Storage.takeFolder(it); Emulator.gamesDir = it.toString(); Emulator.savePrefs(); message = null }
    }
    val dataPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { selected -> scope.launch { Emulator.dataFolder.choose(selected) } }
    }
    val chooseDataFolder = {
        val start = Emulator.dataFolder.folder?.let(Uri::parse)
            ?: gb.frame.StorageDefaults.treePickerUri(gb.frame.StorageDefaults.EMULATORS_DIRECTORY)
        dataPicker.launch(start)
    }
    val chooseGamesFolder = {
        val start = Emulator.gamesDir.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: gb.frame.StorageDefaults.treePickerUri(gb.frame.StorageDefaults.SATURN_GAMES_DIRECTORY)
        gamesPicker.launch(start)
    }
    // First run starts with the default settings; the device's recommended preset is only applied by the wizard's button
    LaunchedEffect(Unit) { if (!Emulator.setupDone) Emulator.applyDefaultSettings() }
    gb.frame.SetupWizard(
        steps = listOf(
            WizardStep(Icons.Default.SportsEsports, "Welcome to GBSat", listOf(Color(0xFF8B7CFF), Color(0xFFE85CC4)),
                "A Saturn emulator for Android. A few steps and you're ready to play."),
            gb.frame.dataFolderWizardStep(Emulator.dataFolder, chooseDataFolder),
            WizardStep(Icons.Default.Memory, "Saturn BIOS", listOf(Color(0xFFFFB347), Color(0xFFFF6B6B)),
                "GBSat needs your own Saturn BIOS (IPL ROM, 512 KiB). None is included.") {
                Button(onClick = { biosPicker.launch(arrayOf("*/*")) }) { gb.frame.KitText(if (Emulator.iplLoaded) "Replace BIOS" else "Choose BIOS file") }
                if (Emulator.iplLoaded) WizardStatus("BIOS loaded")
            },
            gb.frame.gamesFolderWizardStep(
                gb.frame.StorageDefaults.SATURN_GAMES_DIRECTORY,
                "Pick the folder that holds your Saturn disc images (CUE/BIN, CHD, ISO, MDS, CCD). GBSat only reads that folder.",
                { Emulator.gamesDir.takeIf { it.isNotBlank() }?.let(Storage::folderLabel) },
                chooseGamesFolder,
            ),
            gb.frame.performanceWizardStep(Emulator.recommendedPreset.label,
                Emulator.presetWizardOptions(Emulator.recommendedPreset),
                onApply = { Emulator.applyPreset(Emulator.recommendedPreset) }),
        ),
        onFinish = onFinish, message = message, onStepChange = { message = null },
    )
}

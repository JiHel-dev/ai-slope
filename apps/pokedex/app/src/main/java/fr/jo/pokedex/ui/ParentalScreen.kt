package fr.jo.pokedex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.jo.pokedex.R
import fr.jo.pokedex.domain.PokedexData
import fr.jo.pokedex.kiosk.KioskManager
import fr.jo.pokedex.speech.RecognitionMode
import fr.jo.pokedex.ui.theme.PokedexColors
import kotlinx.coroutines.delay

data class ParentalStatus(
    val data: PokedexData?,
    val uiState: UiState,
    val recognitionMode: RecognitionMode,
    val kioskMode: KioskManager.Mode,
    val kioskLocked: Boolean,
    val maintenance: Boolean,
    val micGranted: Boolean,
    val isDefaultPin: Boolean,
)

class ParentalActions(
    val onRequestMic: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onModeChange: (RecognitionMode) -> Unit,
    val onStartMaintenance: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onChangePin: () -> Unit,
    val onReload: () -> Unit,
    val onRemoveOwner: () -> Unit,
    val onClose: () -> Unit,
)

private const val INACTIVITY_CLOSE_MS = 3 * 60_000L

@Composable
fun ParentalScreen(status: ParentalStatus, actions: ParentalActions, resumed: Boolean) {
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmRemove by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(actions.onClose)

    LaunchedEffect(lastInteraction, resumed) {
        if (!resumed) return@LaunchedEffect
        delay(INACTIVITY_CLOSE_MS)
        close()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PokedexColors.Screen)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteraction = System.currentTimeMillis()
                    }
                }
            }
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.parental_title), style = MaterialTheme.typography.headlineMedium)

        if (status.isDefaultPin) {
            Banner(stringResource(R.string.parental_default_pin))
        }

        Section(stringResource(R.string.parental_section_kiosk)) {
            Text(
                stringResource(
                    if (status.kioskMode == KioskManager.Mode.MANAGED) R.string.parental_kiosk_managed else R.string.parental_kiosk_pinning,
                ),
            )
            Text(
                stringResource(
                    R.string.parental_kiosk_locked,
                    stringResource(if (status.kioskLocked) R.string.yes else R.string.no),
                ),
            )
            if (status.maintenance) Banner(stringResource(R.string.parental_maintenance_on))
        }

        Section(stringResource(R.string.parental_section_data)) {
            val data = status.data
            if (data != null) {
                Text(
                    stringResource(
                        R.string.parental_data_summary,
                        data.pokemon.size,
                        data.missingImages.size,
                        data.missingAudio.size,
                    ),
                )
                val missing = (data.missingImages + data.missingAudio).sorted()
                if (missing.isNotEmpty()) {
                    Text(
                        stringResource(R.string.parental_data_missing, missing.take(40).joinToString() + if (missing.size > 40) "…" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                    )
                }
            }
            val engine = status.uiState
            Text(
                if (engine is UiState.Unavailable) {
                    stringResource(R.string.parental_engine_error, engine.reason)
                } else {
                    stringResource(R.string.parental_engine_ok)
                },
                color = if (engine is UiState.Unavailable) PokedexColors.Red else PokedexColors.Green,
            )
            OutlinedButton(onClick = actions.onReload) { Text(stringResource(R.string.parental_reload)) }
        }

        Section(stringResource(R.string.parental_section_mic)) {
            Text(stringResource(if (status.micGranted) R.string.parental_mic_granted else R.string.parental_mic_denied))
            if (!status.micGranted) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions.onRequestMic) { Text(stringResource(R.string.parental_mic_request)) }
                    OutlinedButton(onClick = actions.onOpenAppSettings) { Text(stringResource(R.string.parental_open_app_settings)) }
                }
            }
        }

        Section(stringResource(R.string.parental_section_recognition)) {
            ModeOption(RecognitionMode.GRAMMAR, R.string.parental_mode_grammar, status.recognitionMode, actions.onModeChange)
            ModeOption(RecognitionMode.FREE, R.string.parental_mode_free, status.recognitionMode, actions.onModeChange)
        }

        Section(stringResource(R.string.parental_section_actions)) {
            if (!status.maintenance) {
                OutlinedButton(onClick = actions.onStartMaintenance) { Text(stringResource(R.string.parental_maintenance_start)) }
            }
            OutlinedButton(onClick = actions.onOpenSettings) { Text(stringResource(R.string.parental_open_settings)) }
            OutlinedButton(onClick = actions.onChangePin) { Text(stringResource(R.string.parental_change_pin)) }
            if (status.kioskMode == KioskManager.Mode.MANAGED) {
                if (confirmRemove) Banner(stringResource(R.string.parental_remove_owner_confirm))
                Button(
                    onClick = {
                        if (confirmRemove) actions.onRemoveOwner() else confirmRemove = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PokedexColors.DarkRed),
                ) { Text(stringResource(R.string.parental_remove_owner)) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = actions.onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PokedexColors.Red),
        ) { Text(stringResource(R.string.parental_close), style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = PokedexColors.Blue)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun Banner(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .background(PokedexColors.Yellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}

@Composable
private fun ModeOption(
    mode: RecognitionMode,
    label: Int,
    selected: RecognitionMode,
    onSelect: (RecognitionMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = mode == selected, onClick = { onSelect(mode) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
        Text(stringResource(label))
    }
}

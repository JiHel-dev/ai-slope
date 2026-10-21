package fr.jo.pokedex.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.jo.pokedex.R
import fr.jo.pokedex.ui.theme.PokedexColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 3_000L
private const val ARMED_WINDOW_MS = 6_000L
private const val REQUIRED_TAPS = 3

/**
 * Invisible corners: hold the top-left corner for 3 s, then tap the top-right corner 3 times.
 * Nothing is shown to the child while doing so.
 */
@Composable
fun BoxScope.ParentalHotspots(onUnlockRequested: () -> Unit) {
    val onUnlock by rememberUpdatedState(onUnlockRequested)
    var armedUntil by remember { mutableLongStateOf(0L) }
    var taps by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .align(Alignment.TopStart)
            .size(88.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val released = withTimeoutOrNull(LONG_PRESS_MS) { waitForUpOrCancellation() }
                    if (released == null) {
                        armedUntil = SystemClock.elapsedRealtime() + ARMED_WINDOW_MS
                        taps = 0
                    }
                }
            },
    )
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .size(88.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (SystemClock.elapsedRealtime() > armedUntil) {
                        taps = 0
                        return@detectTapGestures
                    }
                    taps++
                    if (taps >= REQUIRED_TAPS) {
                        taps = 0
                        armedUntil = 0
                        onUnlock()
                    }
                }
            },
    )
}

/** Full-screen PIN keypad drawn inside the activity, so the system bars stay hidden. */
@Composable
fun PinPad(
    title: String,
    onSubmit: suspend (String) -> String?,
    onCancel: () -> Unit,
    maxAttempts: Int = 5,
    lockoutSeconds: Int = 60,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableIntStateOf(0) }
    var lockedSeconds by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lockedSeconds > 0) {
        while (lockedSeconds > 0) {
            delay(1_000)
            lockedSeconds--
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0222222))
            .clickable(enabled = false) {}
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(
                        Modifier
                            .size(18.dp)
                            .background(if (i < pin.length) Color.White else Color.Gray, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val message = when {
                lockedSeconds > 0 -> stringResource(R.string.pin_locked, lockedSeconds)
                else -> error
            }
            Text(message.orEmpty(), color = PokedexColors.Yellow, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
            keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        val enabled = !busy && lockedSeconds == 0
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1.6f)
                                .background(
                                    if (key == "OK") PokedexColors.Green else Color(0xFF444444),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable(enabled = enabled) {
                                    when (key) {
                                        "⌫" -> pin = pin.dropLast(1)
                                        "OK" -> {
                                            busy = true
                                            scope.launch {
                                                val result = onSubmit(pin)
                                                busy = false
                                                pin = ""
                                                if (result != null) {
                                                    error = result
                                                    failures++
                                                    if (failures >= maxAttempts) {
                                                        failures = 0
                                                        lockedSeconds = lockoutSeconds
                                                    }
                                                }
                                            }
                                        }
                                        else -> if (pin.length < 8) pin += key
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.pin_cancel), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

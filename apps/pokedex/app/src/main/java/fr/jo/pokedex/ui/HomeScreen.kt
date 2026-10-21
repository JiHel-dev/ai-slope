package fr.jo.pokedex.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.jo.pokedex.R
import fr.jo.pokedex.ui.theme.PokedexColors

@Composable
fun HomeScreen(
    state: UiState,
    onListen: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(PokedexColors.Red)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Header()
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(PokedexColors.Ink, RoundedCornerShape(28.dp))
                .padding(10.dp)
                .background(PokedexColors.Screen, RoundedCornerShape(20.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = state,
                contentKey = { it.javaClass },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { current -> ScreenContent(current) }
        }
        Spacer(Modifier.height(16.dp))
        val showing = state as? UiState.Showing
        if (showing != null) {
            BigButton(
                text = stringResource(R.string.button_replay),
                icon = PokedexIcons.VolumeUp,
                color = PokedexColors.Blue,
                enabled = !showing.playing,
                height = 84,
                onClick = onReplay,
            )
            Spacer(Modifier.height(12.dp))
        }
        BigButton(
            text = stringResource(R.string.button_recognize),
            icon = PokedexIcons.Mic,
            color = PokedexColors.Yellow,
            contentColor = PokedexColors.Ink,
            enabled = state.canListen,
            height = if (showing != null) 100 else 140,
            onClick = onListen,
        )
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(56.dp)
                .border(5.dp, Color.White, CircleShape)
                .padding(5.dp)
                .background(Color(0xFF29B6F6), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        listOf(Color(0xFFFF5252), PokedexColors.Yellow, Color(0xFF66BB6A)).forEach {
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(16.dp)
                    .background(it, CircleShape)
                    .border(2.dp, PokedexColors.Ink, CircleShape),
            )
        }
        Spacer(Modifier.weight(1f))
        Text("POKÉDEX", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.width(72.dp))
    }
}

@Composable
private fun ScreenContent(state: UiState) {
    when (state) {
        UiState.Loading -> Message(stringResource(R.string.state_loading)) {
            Pokeball(Modifier.size(160.dp), spinning = true)
        }
        is UiState.Unavailable -> Message(stringResource(R.string.unavailable_title), detail = state.reason) {
            StateIcon(Icons.Filled.Warning, PokedexColors.Red)
        }
        UiState.Idle -> Message(stringResource(R.string.state_idle)) {
            Pokeball(Modifier.size(200.dp))
        }
        UiState.Listening -> Message(stringResource(R.string.state_listening)) {
            ListeningAnimation(Modifier.size(240.dp))
        }
        UiState.Processing -> Message(stringResource(R.string.state_processing)) {
            Pokeball(Modifier.size(160.dp), spinning = true)
        }
        is UiState.Showing -> PokemonCard(state.pokemon, state.hasImage, state.playing)
        is UiState.Failure -> Message(stringResource(state.kind.message())) {
            StateIcon(
                if (state.kind == FailureKind.MICROPHONE) PokedexIcons.MicOff else PokedexIcons.QuestionMark,
                PokedexColors.DarkRed,
            )
        }
        UiState.MicPermissionNeeded -> Message(stringResource(R.string.mic_permission_needed)) {
            StateIcon(PokedexIcons.MicOff, PokedexColors.DarkRed)
        }
    }
}

private fun FailureKind.message() = when (this) {
    FailureKind.NOT_UNDERSTOOD -> R.string.failure_not_understood
    FailureKind.AMBIGUOUS -> R.string.failure_ambiguous
    FailureKind.SILENCE -> R.string.failure_silence
    FailureKind.MICROPHONE -> R.string.failure_microphone
}

@Composable
private fun Message(text: String, detail: String? = null, visual: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        visual()
        Spacer(Modifier.height(32.dp))
        Text(
            text,
            style = MaterialTheme.typography.headlineMedium,
            color = PokedexColors.Ink,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Spacer(Modifier.height(12.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StateIcon(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(180.dp)
            .background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(120.dp))
    }
}

@Composable
private fun BigButton(
    text: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    height: Int,
    onClick: () -> Unit,
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = contentColor,
            disabledContainerColor = color.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.6f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .border(4.dp, PokedexColors.Ink, RoundedCornerShape(32.dp)),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

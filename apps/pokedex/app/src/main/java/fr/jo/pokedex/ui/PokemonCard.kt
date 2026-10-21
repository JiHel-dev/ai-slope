package fr.jo.pokedex.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.jo.pokedex.R
import fr.jo.pokedex.domain.Pokemon
import fr.jo.pokedex.ui.theme.PokedexColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PokemonCard(pokemon: Pokemon, hasImage: Boolean, playing: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = if (hasImage) rememberAssetImage(pokemon.imagePath) else null
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = pokemon.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MissingImage(pokemon)
            }
        }
        Spacer(Modifier.height(8.dp))
        ShrinkToFitText(pokemon.name.uppercase(), MaterialTheme.typography.displayLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                text = pokemon.number,
                style = MaterialTheme.typography.headlineMedium,
                color = PokedexColors.Blue,
            )
            if (playing) {
                Spacer(Modifier.width(16.dp))
                Icon(
                    PokedexIcons.VolumeUp,
                    contentDescription = stringResource(R.string.state_playing),
                    tint = PokedexColors.Green,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

/** Single line that gets smaller instead of being cut, so long names stay readable. */
@Composable
private fun ShrinkToFitText(text: String, style: TextStyle) {
    var scale by remember(text) { mutableFloatStateOf(1f) }
    var fits by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        color = PokedexColors.Ink,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent { if (fits) drawContent() },
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && scale > 0.5f) scale *= 0.9f else fits = true
        },
    )
}

@Composable
private fun MissingImage(pokemon: Pokemon) {
    Box(
        Modifier
            .padding(24.dp)
            .fillMaxSize()
            .background(PokedexColors.Yellow.copy(alpha = 0.35f), RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Pokeball(Modifier.size(140.dp))
            Spacer(Modifier.height(16.dp))
            Text("%03d".format(pokemon.id), style = MaterialTheme.typography.displayLarge, color = PokedexColors.Ink)
        }
    }
}

@Composable
private fun rememberAssetImage(path: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return bitmap
}

package fr.jo.pokedex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PokedexColors {
    val Red = Color(0xFFDC0A2D)
    val DarkRed = Color(0xFF8B0000)
    val Yellow = Color(0xFFFFCB05)
    val Blue = Color(0xFF3B4CCA)
    val Ink = Color(0xFF222222)
    val Screen = Color(0xFFF4F7F2)
    val Green = Color(0xFF2E9E4F)
}

private val Rounded = FontFamily.SansSerif

private val PokedexTypography = Typography(
    displayLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 50.sp),
    headlineMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Rounded, fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontSize = 15.sp, lineHeight = 20.sp),
)

@Composable
fun PokedexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PokedexColors.Red,
            onPrimary = Color.White,
            secondary = PokedexColors.Blue,
            onSecondary = Color.White,
            tertiary = PokedexColors.Yellow,
            background = PokedexColors.Red,
            surface = PokedexColors.Screen,
            onSurface = PokedexColors.Ink,
        ),
        typography = PokedexTypography,
        content = content,
    )
}

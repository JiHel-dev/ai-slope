package fr.jo.pokedex.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few Material icons the app needs, without pulling the whole extended icon library. */
object PokedexIcons {
    val Mic = icon(
        "Mic",
        "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
            "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28" +
            "c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
    )

    val MicOff = icon(
        "MicOff",
        "M19,11h-1.7c0,0.74 -0.16,1.43 -0.43,2.05l1.23,1.23c0.56,-0.98 0.9,-2.09 0.9,-3.28z" +
            "M14.98,11.17c0,-0.06 0.02,-0.11 0.02,-0.17L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v0.18l5.98,5.99z" +
            "M4.27,3L3,4.27l6.01,6.01L9.01,11c0,1.66 1.33,3 2.99,3 0.22,0 0.44,-0.03 0.65,-0.08l1.66,1.66" +
            "c-0.71,0.33 -1.5,0.52 -2.31,0.52 -2.76,0 -5.3,-2.1 -5.3,-5.1L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2" +
            "v-3.28c0.91,-0.13 1.77,-0.45 2.54,-0.9L19.73,21 21,19.73 4.27,3z",
    )

    val VolumeUp = icon(
        "VolumeUp",
        "M3,9v6h4l5,5L12,4L7,9L3,9z" +
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02z" +
            "M14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77" +
            "s-2.99,-7.86 -7,-8.77z",
    )

    val QuestionMark = icon(
        "QuestionMark",
        "M11.07,12.85c0.77,-1.39 2.25,-2.21 3.11,-3.44c0.91,-1.29 0.4,-3.7 -2.18,-3.7" +
            "c-1.69,0 -2.52,1.28 -2.87,2.34L6.54,6.96C7.25,4.83 9.18,3 11.99,3c2.35,0 3.96,1.07 4.78,2.41" +
            "c0.7,1.15 1.11,3.3 0.03,4.9c-1.2,1.77 -2.35,2.31 -2.97,3.45c-0.25,0.46 -0.35,0.76 -0.35,2.24" +
            "h-2.89C10.58,15.22 10.46,13.95 11.07,12.85z" +
            "M14,20c0,1.1 -0.9,2 -2,2s-2,-0.9 -2,-2c0,-1.1 0.9,-2 2,-2S14,18.9 14,20z",
    )

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()
}

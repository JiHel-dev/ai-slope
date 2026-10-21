package fr.jo.pokedex.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PokedexFile(
    val version: Int,
    val pokemon: List<PokemonJson>,
)

@Serializable
data class PokemonJson(
    val id: Int,
    val name: String,
    val aliases: List<String> = emptyList(),
    val image: String? = null,
    val audio: String? = null,
) {
    val imagePath: String get() = image ?: PokedexFiles.defaultImagePath(id)
    val audioPath: String get() = audio ?: PokedexFiles.defaultAudioPath(id)
}

object PokedexFiles {
    const val ASSET_PATH = "pokemon/pokedex.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): PokedexFile = json.decodeFromString(PokedexFile.serializer(), text)

    fun defaultImagePath(id: Int) = "pokemon/images/%04d.png".format(id)

    fun defaultAudioPath(id: Int) = "pokemon/audio/%04d.wav".format(id)
}

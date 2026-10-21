package fr.jo.pokedex.domain

data class PokedexData(
    val pokemon: List<Pokemon>,
    val resolver: PokemonResolver,
    /** Phrases (names and aliases) the recognizer should favour. */
    val grammarPhrases: List<String>,
    val missingImages: Set<Int>,
    val missingAudio: Set<Int>,
) {
    private val byId = pokemon.associateBy { it.id }

    fun pokemon(id: Int): Pokemon? = byId[id]

    fun hasImage(pokemon: Pokemon) = pokemon.id !in missingImages

    fun hasAudio(pokemon: Pokemon) = pokemon.id !in missingAudio
}

interface PokedexSource {
    suspend fun load(): PokedexData
}

package fr.jo.pokedex

import fr.jo.pokedex.data.PokedexFile
import fr.jo.pokedex.data.PokedexFiles
import fr.jo.pokedex.domain.PokemonResolver
import java.io.File

object TestData {
    val pokedexText: String by lazy { File("src/main/assets/pokemon/pokedex.json").readText() }

    val pokedex: PokedexFile by lazy { PokedexFiles.parse(pokedexText) }

    val resolver: PokemonResolver by lazy {
        PokemonResolver(
            pokedex.pokemon.flatMap { entry ->
                listOf(PokemonResolver.Entry(entry.id, entry.name)) +
                    entry.aliases.map { PokemonResolver.Entry(entry.id, it) }
            },
        )
    }

    fun idOf(name: String): Int = pokedex.pokemon.first { it.name == name }.id
}

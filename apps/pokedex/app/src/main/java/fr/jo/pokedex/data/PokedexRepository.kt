package fr.jo.pokedex.data

import android.content.Context
import android.util.Log
import fr.jo.pokedex.domain.NameNormalizer
import fr.jo.pokedex.domain.Pokemon
import fr.jo.pokedex.domain.PokedexData
import fr.jo.pokedex.domain.PokedexSource
import fr.jo.pokedex.domain.PokemonResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PokedexRepository(
    private val context: Context,
    private val dao: PokemonDao,
) : PokedexSource {

    override suspend fun load(): PokedexData = withContext(Dispatchers.IO) {
        seedIfNeeded()

        val pokemon = dao.allPokemon().map { Pokemon(it.id, it.name, it.imagePath, it.audioPath) }
        val aliases = dao.allAliases()

        val entries = pokemon.map { PokemonResolver.Entry(it.id, it.name) } +
            aliases.map { PokemonResolver.Entry(it.pokemonId, it.alias) }

        val phrases = (pokemon.map { it.name } + aliases.map { it.alias })
            .map(NameNormalizer::spokenForm)
            .filter { it.isNotEmpty() }
            .distinct()

        PokedexData(
            pokemon = pokemon,
            resolver = PokemonResolver(entries),
            grammarPhrases = phrases,
            missingImages = pokemon.filterNot { assetExists(it.imagePath) }.map { it.id }.toSet(),
            missingAudio = pokemon.filterNot { assetExists(it.audioPath) }.map { it.id }.toSet(),
        )
    }

    private suspend fun seedIfNeeded() {
        val text = context.assets.open(PokedexFiles.ASSET_PATH).bufferedReader().use { it.readText() }
        val file = PokedexFiles.parse(text)
        val key = PokedexSeeder.sourceKey(file, text)
        if (dao.metadata(PokemonDao.KEY_SOURCE) == key) return

        PokedexSeeder.validate(file).forEach { Log.w(TAG, it) }
        val (pokemon, aliases) = PokedexSeeder.toEntities(file)
        dao.replaceAll(pokemon, aliases, key)
        Log.i(TAG, "Imported ${pokemon.size} Pokémon and ${aliases.size} aliases (version ${file.version})")
    }

    private val listings = HashMap<String, Set<String>>()

    private fun assetExists(path: String): Boolean {
        val dir = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val files = listings.getOrPut(dir) { context.assets.list(dir)?.toSet().orEmpty() }
        return name in files
    }

    private companion object {
        const val TAG = "PokedexRepository"
    }
}

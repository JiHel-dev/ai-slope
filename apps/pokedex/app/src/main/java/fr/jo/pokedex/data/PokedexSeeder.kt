package fr.jo.pokedex.data

import fr.jo.pokedex.domain.NameNormalizer
import java.security.MessageDigest

/** Turns pokedex.json into database rows. Pure, so it can be unit tested. */
object PokedexSeeder {
    const val EXPECTED_COUNT = 151

    fun sourceKey(file: PokedexFile, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return "${file.version}:" + digest.joinToString("") { "%02x".format(it) }
    }

    fun toEntities(file: PokedexFile): Pair<List<PokemonEntity>, List<AliasEntity>> {
        val unique = file.pokemon.distinctBy { it.id }
        val pokemon = unique.map { PokemonEntity(it.id, it.name, it.imagePath, it.audioPath) }
        val aliases = unique.flatMap { entry ->
            entry.aliases
                .map { alias -> alias to NameNormalizer.normalize(alias) }
                .filter { (_, normalized) -> normalized.isNotEmpty() }
                .distinctBy { (_, normalized) -> normalized }
                .map { (alias, normalized) -> AliasEntity(pokemonId = entry.id, alias = alias, normalized = normalized) }
        }
        return pokemon to aliases
    }

    /** Human-readable problems in the file; empty when it is valid. */
    fun validate(file: PokedexFile): List<String> {
        val problems = mutableListOf<String>()
        val ids = file.pokemon.map { it.id }
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            problems += "Numéro en double : $it"
        }
        val missing = (1..EXPECTED_COUNT).toSet() - ids.toSet()
        if (missing.isNotEmpty()) problems += "Numéros manquants : ${missing.sorted().joinToString()}"
        val extra = ids.filter { it !in 1..EXPECTED_COUNT }
        if (extra.isNotEmpty()) problems += "Numéros hors Kanto : ${extra.joinToString()}"
        file.pokemon.filter { it.name.isBlank() }.forEach { problems += "Nom vide pour le n° ${it.id}" }
        file.pokemon.forEach { entry ->
            entry.aliases.filter { NameNormalizer.normalize(it).isEmpty() }.forEach {
                problems += "Alias inutilisable pour ${entry.name} : \"$it\""
            }
        }
        return problems
    }
}

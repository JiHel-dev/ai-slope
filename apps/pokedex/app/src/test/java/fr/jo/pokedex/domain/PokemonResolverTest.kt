package fr.jo.pokedex.domain

import fr.jo.pokedex.TestData
import fr.jo.pokedex.domain.PokemonResolver.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonResolverTest {
    private val resolver = TestData.resolver

    private fun found(vararg heard: String): Int {
        val result = resolver.resolve(heard.toList())
        assertTrue("Expected a match for ${heard.toList()}, got $result", result is Resolution.Found)
        return (result as Resolution.Found).pokemonId
    }

    @Test
    fun `exact names with or without accents`() {
        assertEquals(25, found("pikachu"))
        assertEquals(4, found("salamèche"))
        assertEquals(4, found("salameche"))
        assertEquals(122, found("m mime"))
        assertEquals(151, found("mew"))
        assertEquals(150, found("mewtwo"))
    }

    @Test
    fun `aliases map to the official Pokémon`() {
        assertEquals(1, found("bulle bizarre"))
        assertEquals(25, found("pika chu"))
        assertEquals(6, found("draco feu"))
        assertEquals(89, found("gros tas de morve"))
        assertEquals(123, found("un sécateur"))
    }

    @Test
    fun `fillers and sentences`() {
        assertEquals(25, found("c'est pikachu"))
        assertEquals(7, found("je crois que carapuce"))
    }

    @Test
    fun `approximate transcriptions`() {
        assertEquals(25, found("pikatchu"))
        assertEquals(9, found("tortanque"))
        assertEquals(143, found("ronflexe"))
    }

    @Test
    fun `later alternatives are used when the first one is unknown`() {
        assertEquals(7, found("bonjour", "carapuce"))
    }

    @Test
    fun `shared spoken name is ambiguous`() {
        val result = resolver.resolve(listOf("nidoran"))
        assertTrue(result is Resolution.Ambiguous)
        assertEquals(listOf(29, 32), (result as Resolution.Ambiguous).candidates)
        assertEquals(29, found("nidoran fille"))
        assertEquals(32, found("nido ran garçon"))
    }

    @Test
    fun `close fuzzy candidates are ambiguous instead of guessed`() {
        val result = resolver.resolve(listOf("nidorin"))
        assertTrue("got $result", result is Resolution.Ambiguous)
    }

    @Test
    fun `unknown words never return a Pokémon`() {
        listOf("bonjour maman", "voiture", "[unk]", "", "a").forEach {
            val result = resolver.resolve(listOf(it))
            assertTrue("'$it' gave $result", result is Resolution.NotFound)
        }
        assertTrue(resolver.resolve(emptyList()) is Resolution.NotFound)
    }

    @Test
    fun `only the two Nidoran share a key`() {
        assertEquals(setOf("nidoran"), resolver.collisions.keys)
    }

    @Test
    fun `every name and alias resolves to its own Pokémon`() {
        for (entry in TestData.pokedex.pokemon) {
            for (text in listOf(entry.name) + entry.aliases) {
                val result = resolver.resolve(listOf(text))
                if (NameNormalizer.normalize(text) == "nidoran") {
                    assertTrue(result is Resolution.Ambiguous)
                } else {
                    assertEquals("'$text'", entry.id, (result as? Resolution.Found)?.pokemonId)
                }
            }
        }
    }

    @Test
    fun `levenshtein distance`() {
        assertEquals(0, PokemonResolver.levenshtein("abc", "abc"))
        assertEquals(1, PokemonResolver.levenshtein("abc", "abd"))
        assertEquals(3, PokemonResolver.levenshtein("", "abc"))
        assertEquals(2, PokemonResolver.levenshtein("flaw", "lawn"))
    }
}

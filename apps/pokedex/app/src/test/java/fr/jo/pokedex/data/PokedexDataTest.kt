package fr.jo.pokedex.data

import fr.jo.pokedex.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokedexDataTest {
    private val file = TestData.pokedex

    @Test
    fun `contains exactly the 151 Kanto Pokémon`() {
        assertEquals(151, file.pokemon.size)
        assertEquals((1..151).toList(), file.pokemon.map { it.id }.sorted())
        assertEquals("Bulbizarre", file.pokemon.first { it.id == 1 }.name)
        assertEquals("Pikachu", file.pokemon.first { it.id == 25 }.name)
        assertEquals("Mew", file.pokemon.first { it.id == 151 }.name)
    }

    @Test
    fun `file is valid`() {
        assertEquals(emptyList<String>(), PokedexSeeder.validate(file))
    }

    @Test
    fun `default resource paths use four digits`() {
        val bulbizarre = file.pokemon.first { it.id == 1 }
        assertEquals("pokemon/images/0001.png", bulbizarre.imagePath)
        assertEquals("pokemon/audio/0001.wav", bulbizarre.audioPath)
        assertEquals("pokemon/audio/0151.wav", file.pokemon.first { it.id == 151 }.audioPath)
    }

    @Test
    fun `explicit resource paths override the convention`() {
        val custom = PokemonJson(id = 7, name = "Carapuce", image = "pokemon/images/carapuce.webp", audio = "pokemon/audio/carapuce.ogg")
        assertEquals("pokemon/images/carapuce.webp", custom.imagePath)
        assertEquals("pokemon/audio/carapuce.ogg", custom.audioPath)
    }

    @Test
    fun `every Pokémon has its image and description in the assets`() {
        for (entry in file.pokemon) {
            assertTrue(entry.imagePath, java.io.File("src/main/assets/${entry.imagePath}").isFile)
            assertTrue(entry.audioPath, java.io.File("src/main/assets/${entry.audioPath}").isFile)
        }
    }

    @Test
    fun `seeder builds one row per Pokémon and normalized aliases`() {
        val (pokemon, aliases) = PokedexSeeder.toEntities(file)
        assertEquals(151, pokemon.size)
        assertTrue(aliases.isNotEmpty())
        assertTrue(aliases.all { it.normalized.isNotEmpty() && it.normalized == it.normalized.lowercase() })
        assertTrue(aliases.any { it.pokemonId == 1 && it.normalized == "bullebizarre" })
    }

    @Test
    fun `validation reports duplicates and gaps`() {
        val broken = PokedexFile(1, listOf(PokemonJson(1, "A"), PokemonJson(1, "B"), PokemonJson(200, "")))
        val problems = PokedexSeeder.validate(broken)
        assertTrue(problems.any { it.contains("double") })
        assertTrue(problems.any { it.contains("manquants") })
        assertTrue(problems.any { it.contains("hors Kanto") })
        assertTrue(problems.any { it.contains("Nom vide") })
    }

    @Test
    fun `source key changes with the content`() {
        val a = PokedexSeeder.sourceKey(file, TestData.pokedexText)
        val b = PokedexSeeder.sourceKey(file, TestData.pokedexText + " ")
        assertNotEquals(a, b)
        assertTrue(a.startsWith("${file.version}:"))
    }
}

package fr.jo.pokedex.speech

import fr.jo.pokedex.TestData
import fr.jo.pokedex.domain.NameNormalizer
import fr.jo.pokedex.domain.PokemonResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ModelVocabularyTest {
    private val modelDir = File("src/main/assets/model-fr")

    @Test
    fun `filters phrases with unknown words`() {
        val vocabulary = setOf("draco", "bulle", "bizarre")
        assertEquals(
            listOf("draco", "bulle bizarre"),
            ModelVocabulary.usablePhrases(listOf("draco", "draco losse", "bulle bizarre"), vocabulary),
        )
        assertEquals(listOf("draco losse"), ModelVocabulary.usablePhrases(listOf("draco losse"), null))
    }

    @Test
    fun `missing model gives no vocabulary`() {
        assertNull(ModelVocabulary.read(File("does-not-exist")))
    }

    @Test
    fun `reads the vocabulary of the bundled model`() {
        assumeTrue("Vosk model not downloaded", File(modelDir, "graph/Gr.fst").isFile)
        val vocabulary = ModelVocabulary.read(modelDir)!!
        assertTrue(vocabulary.size > 100_000)
        assertTrue("pikachu" in vocabulary)
        assertFalse("losse" in vocabulary)
    }

    @Test
    fun `every Pokémon stays reachable through the filtered grammar`() {
        assumeTrue("Vosk model not downloaded", File(modelDir, "graph/Gr.fst").isFile)
        val vocabulary = ModelVocabulary.read(modelDir)!!
        for (entry in TestData.pokedex.pokemon) {
            val phrases = (listOf(entry.name) + entry.aliases).map(NameNormalizer::spokenForm)
            val usable = ModelVocabulary.usablePhrases(phrases, vocabulary)
            assertTrue("${entry.name} has no recognizable phrase", usable.isNotEmpty())
            for (phrase in usable) {
                val result = TestData.resolver.resolve(listOf(phrase))
                if (NameNormalizer.normalize(phrase) != "nidoran") {
                    assertEquals(phrase, entry.id, (result as? PokemonResolver.Resolution.Found)?.pokemonId)
                }
            }
        }
    }
}

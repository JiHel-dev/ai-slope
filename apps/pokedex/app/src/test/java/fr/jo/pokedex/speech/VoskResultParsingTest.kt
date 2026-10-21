package fr.jo.pokedex.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoskResultParsingTest {
    @Test
    fun `parses alternatives in order and drops unknown`() {
        val json = """
            {"alternatives": [
              {"confidence": 210.3, "text": "pikachu"},
              {"confidence": 190.1, "text": "[unk]"},
              {"confidence": 150.0, "text": "raichu"}
            ]}
        """.trimIndent()
        assertEquals(listOf("pikachu", "raichu"), VoskSpeechEngine.parseAlternatives(json))
    }

    @Test
    fun `parses single text results`() {
        assertEquals(listOf("carapuce"), VoskSpeechEngine.parseAlternatives("""{"text": " carapuce "}"""))
        assertEquals(emptyList<String>(), VoskSpeechEngine.parseAlternatives("""{"text": ""}"""))
    }

    @Test
    fun `tolerates invalid input`() {
        assertEquals(emptyList<String>(), VoskSpeechEngine.parseAlternatives(null))
        assertEquals(emptyList<String>(), VoskSpeechEngine.parseAlternatives("not json"))
        assertNull(VoskSpeechEngine.parsePartial("{}"))
        assertEquals("pika", VoskSpeechEngine.parsePartial("""{"partial": "pika"}"""))
    }

    @Test
    fun `grammar lists phrases once plus unknown`() {
        assertEquals("""["pikachu","bulle bizarre","[unk]"]""", VoskSpeechEngine.grammar(listOf("pikachu", "bulle bizarre", "pikachu")))
    }
}

package fr.jo.pokedex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrenchPhoneticTest {
    private fun key(text: String) = FrenchPhonetic.encode(NameNormalizer.normalize(text))

    @Test
    fun `spellings that sound the same get the same key`() {
        assertEquals(key("Salamèche"), key("salamaiche"))
        assertEquals(key("Tortank"), key("tortanque"))
        assertEquals(key("Kokiyas"), key("cokiyas"))
        assertEquals(key("Ronflex"), key("ronflecs"))
        assertEquals(key("Psykokwak"), key("psikokwak"))
    }

    @Test
    fun `close pronunciations stay close`() {
        val similarity = PokemonResolver.similarity(key("Pikachu"), key("pikachou"))
        assertTrue("similarity=$similarity", similarity >= 0.8)
    }

    @Test
    fun `different names keep different keys`() {
        assertNotEquals(key("Abra"), key("Abo"))
        assertNotEquals(key("Nidorina"), key("Nidorino"))
    }
}

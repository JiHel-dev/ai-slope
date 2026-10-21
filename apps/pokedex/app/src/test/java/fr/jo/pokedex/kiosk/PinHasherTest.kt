package fr.jo.pokedex.kiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun `matches only the original pin`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("2468", salt)
        assertTrue(PinHasher.matches("2468", salt, hash))
        assertFalse(PinHasher.matches("2469", salt, hash))
        assertFalse(PinHasher.matches("2468", PinHasher.newSalt(), hash))
    }

    @Test
    fun `validates pin format`() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}

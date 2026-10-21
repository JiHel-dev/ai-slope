package fr.jo.pokedex.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NameNormalizerTest {
    @Test
    fun `removes case accents and separators`() {
        assertEquals("salameche", NameNormalizer.normalize("Salamèche"))
        assertEquals("electrode", NameNormalizer.normalize("ÉLECTRODE"))
        assertEquals("mmime", NameNormalizer.normalize("M. Mime"))
        assertEquals("pikachu", NameNormalizer.normalize("pika-chu"))
    }

    @Test
    fun `drops gender symbols and ligatures`() {
        assertEquals("nidoran", NameNormalizer.normalize("Nidoran♀"))
        assertEquals("noeunoeuf", NameNormalizer.normalize("Nœunœuf"))
    }

    @Test
    fun `ignores filler words around the name`() {
        assertEquals("pikachu", NameNormalizer.normalize("c'est Pikachu"))
        assertEquals("carapuce", NameNormalizer.normalize("euh le carapuce"))
        assertEquals("", NameNormalizer.normalize("[unk]"))
    }

    @Test
    fun `spoken form keeps accents for the grammar`() {
        assertEquals("salamèche", NameNormalizer.spokenForm("Salamèche"))
        assertEquals("m mime", NameNormalizer.spokenForm("M. Mime"))
        assertEquals("nidoran", NameNormalizer.spokenForm("Nidoran♂"))
        assertEquals("dard d'artagnan", NameNormalizer.spokenForm("dard d'artagnan"))
    }
}

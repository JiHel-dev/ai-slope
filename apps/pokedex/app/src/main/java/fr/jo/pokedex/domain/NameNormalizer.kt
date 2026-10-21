package fr.jo.pokedex.domain

import java.text.Normalizer
import java.util.Locale

object NameNormalizer {
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val NON_SPOKEN = Regex("[^\\p{L}' ]+")
    private val SPACES = Regex("\\s+")

    /** Words a child or the recognizer may add around a name ("c'est Pikachu", "euh..."). */
    private val FILLERS = setOf(
        "le", "la", "les", "l", "un", "une", "c", "cest", "est", "ce",
        "pokemon", "euh", "heu", "hum", "alors", "je", "veux", "voir",
        "dis", "montre", "moi", "unk",
    )

    fun words(text: String): List<String> {
        val lower = text.lowercase(Locale.FRENCH).replace("œ", "oe").replace("æ", "ae")
        val folded = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(DIACRITICS, "")
        return folded.split(NON_ALPHANUMERIC).filter { it.isNotEmpty() && it !in FILLERS }
    }

    fun normalize(text: String): String = words(text).joinToString("")

    /** Lowercase phrase with accents kept, as expected by the Vosk grammar. */
    fun spokenForm(text: String): String =
        text.lowercase(Locale.FRENCH).replace(NON_SPOKEN, " ").replace(SPACES, " ").trim()
}

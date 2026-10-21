package fr.jo.pokedex.domain

/**
 * Coarse French phonetic key, so that spellings which sound alike ("salamaiche", "salamèche")
 * end up close to each other. Input must already be normalized (lowercase a-z0-9, no accents).
 * Uppercase letters in the output stand for single sounds (S = "ch", U = "ou", A/O/I = nasals...).
 */
object FrenchPhonetic {
    private const val VOWEL_SOUNDS = "aeiouyAEIOU"

    private val GROUPS = listOf(
        "eaux" to "o", "eau" to "o", "aux" to "o", "au" to "o",
        "sch" to "S", "ch" to "S", "sh" to "S",
        "ph" to "f", "qu" to "k", "ck" to "k", "gn" to "N",
        "oeu" to "E", "eu" to "E", "oe" to "E",
        "ou" to "U", "oo" to "U",
        "oi" to "wa", "oy" to "wa",
        "ai" to "e", "ei" to "e", "ay" to "e",
        "tion" to "sion",
    )

    private val NASAL_DIPHTHONGS = Regex("(ain|ein|aim|eim)(?![aeiouynm])")

    private val NASALS = listOf(
        Regex("(in|im|yn|ym|un|um)(?![$VOWEL_SOUNDS" + "nm])") to "I",
        Regex("(an|am|en|em)(?![$VOWEL_SOUNDS" + "nm])") to "A",
        Regex("(on|om)(?![$VOWEL_SOUNDS" + "nm])") to "O",
    )

    private val CONSONANTS = listOf(
        Regex("c(?=[eiy])") to "s",
        Regex("g(?=[eiy])") to "j",
        Regex("c") to "k",
        Regex("q") to "k",
        Regex("x") to "ks",
        Regex("z") to "s",
        Regex("y") to "i",
        Regex("h") to "",
    )

    private val SILENT_ENDINGS = Regex("[estdx]+$")

    fun encode(normalized: String): String {
        var s = normalized.replace(NASAL_DIPHTHONGS, "I")
        for ((from, to) in GROUPS) s = s.replace(from, to)
        for ((regex, to) in NASALS) s = s.replace(regex, to)
        for ((regex, to) in CONSONANTS) s = s.replace(regex, to)
        s = collapseRepeats(s)
        val trimmed = s.replace(SILENT_ENDINGS, "")
        return if (trimmed.length >= 2) trimmed else s
    }

    private fun collapseRepeats(s: String): String {
        val out = StringBuilder(s.length)
        for (c in s) if (out.isEmpty() || out.last() != c) out.append(c)
        return out.toString()
    }
}

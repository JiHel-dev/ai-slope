package fr.jo.pokedex.domain

import kotlin.math.max

/**
 * Maps recognizer transcriptions to a Pokémon id. Never guesses: when two Pokémon are
 * plausible, the result is [Resolution.Ambiguous] and the UI treats it as a failure.
 */
class PokemonResolver(
    entries: List<Entry>,
    private val config: Config = Config(),
) {
    /** [text] is a name or alias as written; it is normalized internally. */
    data class Entry(val pokemonId: Int, val text: String)

    data class Config(
        /** Minimum similarity (0..1) for an approximate match. */
        val minSimilarity: Double = 0.78,
        /** Minimum gap between the best and the second best Pokémon. */
        val minMargin: Double = 0.08,
        /** Penalty applied per rank of recognizer alternative. */
        val alternativePenalty: Double = 0.03,
        /** Inputs shorter than this are never matched approximately. */
        val minFuzzyLength: Int = 3,
        /** Longest group of words searched inside a sentence. */
        val maxWindowWords: Int = 4,
    )

    sealed interface Resolution {
        val heard: String

        data class Found(val pokemonId: Int, val score: Double, override val heard: String) : Resolution
        data class Ambiguous(val candidates: List<Int>, override val heard: String) : Resolution
        data class NotFound(override val heard: String) : Resolution
    }

    private class Key(val pokemonId: Int, val text: String, val phonetic: String)

    private val exact: Map<String, Set<Int>>
    private val keys: List<Key>

    init {
        val map = HashMap<String, MutableSet<Int>>()
        val keyList = ArrayList<Key>()
        for (entry in entries) {
            val normalized = NameNormalizer.normalize(entry.text)
            if (normalized.isEmpty()) continue
            if (map.getOrPut(normalized) { mutableSetOf() }.add(entry.pokemonId)) {
                keyList += Key(entry.pokemonId, normalized, FrenchPhonetic.encode(normalized))
            }
        }
        exact = map
        keys = keyList
    }

    /** Normalized keys shared by several Pokémon (always resolved as ambiguous). */
    val collisions: Map<String, Set<Int>> get() = exact.filterValues { it.size > 1 }

    fun resolve(alternatives: List<String>): Resolution {
        val utterances = alternatives.map { NameNormalizer.words(it) }.filter { it.isNotEmpty() }
        val heard = alternatives.firstOrNull { it.isNotBlank() }.orEmpty()
        if (utterances.isEmpty()) return Resolution.NotFound(heard)

        for (words in utterances) {
            val ids = exact[words.joinToString("")] ?: continue
            return if (ids.size == 1) {
                Resolution.Found(ids.first(), 1.0, heard)
            } else {
                Resolution.Ambiguous(ids.sorted(), heard)
            }
        }

        fuzzy(utterances, heard)?.let { return it }
        window(utterances, heard)?.let { return it }
        return Resolution.NotFound(heard)
    }

    private fun fuzzy(utterances: List<List<String>>, heard: String): Resolution? {
        val bestById = HashMap<Int, Double>()
        utterances.forEachIndexed { rank, words ->
            val text = words.joinToString("")
            if (text.length < config.minFuzzyLength) return@forEachIndexed
            val phonetic = FrenchPhonetic.encode(text)
            val penalty = rank * config.alternativePenalty
            for (key in keys) {
                val score = max(similarity(text, key.text), similarity(phonetic, key.phonetic)) - penalty
                if (score > (bestById[key.pokemonId] ?: Double.NEGATIVE_INFINITY)) {
                    bestById[key.pokemonId] = score
                }
            }
        }
        val ranked = bestById.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull() ?: return null
        if (best.value < config.minSimilarity) return null
        val second = ranked.getOrNull(1)
        if (second != null && best.value - second.value < config.minMargin) {
            val close = ranked.takeWhile { best.value - it.value < config.minMargin }.map { it.key }
            return Resolution.Ambiguous(close.sorted(), heard)
        }
        return Resolution.Found(best.key, best.value, heard)
    }

    /** Exact match on a group of consecutive words, e.g. "je crois que c'est carapuce". */
    private fun window(utterances: List<List<String>>, heard: String): Resolution? {
        for (words in utterances) {
            val longest = minOf(config.maxWindowWords, words.size - 1)
            for (size in longest downTo 1) {
                val ids = (0..words.size - size)
                    .mapNotNull { start -> exact[words.subList(start, start + size).joinToString("")] }
                    .flatten()
                    .toSortedSet()
                when {
                    ids.size == 1 -> return Resolution.Found(ids.first(), 1.0, heard)
                    ids.size > 1 -> return Resolution.Ambiguous(ids.toList(), heard)
                }
            }
        }
        return null
    }

    companion object {
        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() && b.isEmpty()) return 1.0
            return 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
        }

        fun levenshtein(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }
    }
}

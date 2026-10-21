package fr.jo.pokedex.speech

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream

/**
 * Words known by a Vosk model. Vosk silently drops unknown words from grammar phrases, so
 * "draco losse" would become "draco" (another Pokémon): such phrases must not be used.
 */
object ModelVocabulary {
    private const val FST_MAGIC = 2125659606
    private const val SYMBOL_TABLE_MAGIC = 2125658996
    private const val HAS_INPUT_SYMBOLS = 1

    /** Reads `graph/words.txt` or the symbol table embedded in `graph/Gr.fst`; null if neither is usable. */
    fun read(modelDir: File): Set<String>? {
        val wordsTxt = File(modelDir, "graph/words.txt")
        if (wordsTxt.isFile) {
            return wordsTxt.useLines { lines ->
                lines.mapNotNull { it.substringBefore(' ').takeIf(String::isNotEmpty) }.toSet()
            }
        }
        val fst = File(modelDir, "graph/Gr.fst")
        if (!fst.isFile) return null
        return runCatching { fst.inputStream().use(::readFstSymbols) }.getOrNull()
    }

    fun readFstSymbols(input: InputStream): Set<String>? {
        val reader = LittleEndianReader(input)
        if (reader.int() != FST_MAGIC) return null
        reader.string()
        reader.string()
        reader.int()
        val flags = reader.int()
        reader.skip(8 * 4)
        if (flags and HAS_INPUT_SYMBOLS == 0) return null
        if (reader.int() != SYMBOL_TABLE_MAGIC) return null
        reader.string()
        reader.long()
        val size = reader.long()
        val words = HashSet<String>(size.toInt() * 2)
        repeat(size.toInt()) {
            words += reader.string()
            reader.long()
        }
        return words
    }

    /** Keeps the phrases whose words are all known; keeps everything when the vocabulary is unknown. */
    fun usablePhrases(phrases: List<String>, vocabulary: Set<String>?): List<String> =
        if (vocabulary == null) phrases else phrases.filter { phrase -> phrase.split(' ').all { it in vocabulary } }

    private class LittleEndianReader(input: InputStream) {
        private val data = DataInputStream(BufferedInputStream(input, 1 shl 16))
        private val buffer = ByteArray(8)

        fun int(): Int {
            data.readFully(buffer, 0, 4)
            return (buffer[0].toInt() and 0xff) or
                ((buffer[1].toInt() and 0xff) shl 8) or
                ((buffer[2].toInt() and 0xff) shl 16) or
                ((buffer[3].toInt() and 0xff) shl 24)
        }

        fun long(): Long {
            val low = int().toLong() and 0xffffffffL
            val high = int().toLong()
            return (high shl 32) or low
        }

        fun string(): String {
            val length = int()
            if (length < 0) throw EOFException("Invalid string length")
            val bytes = ByteArray(length)
            data.readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        fun skip(count: Int) {
            data.readFully(ByteArray(count))
        }
    }
}

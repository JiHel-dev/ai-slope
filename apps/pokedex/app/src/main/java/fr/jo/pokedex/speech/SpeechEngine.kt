package fr.jo.pokedex.speech

enum class RecognitionMode {
    /** Recognizer restricted to the Pokémon names and aliases. */
    GRAMMAR,

    /** Open French dictation, matched afterwards. */
    FREE,
}

sealed interface ListenResult {
    data class Heard(val alternatives: List<String>) : ListenResult
    data object Silence : ListenResult
    data class Failed(val reason: String) : ListenResult
}

class SpeechUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface SpeechEngine {
    /** Loads the model if needed and configures the recognizer. Throws [SpeechUnavailableException]. */
    suspend fun prepare(phrases: List<String>, mode: RecognitionMode)

    /**
     * Opens the microphone for at most [timeoutMs] and returns after the first utterance.
     * Cancelling the calling coroutine stops the capture immediately.
     */
    suspend fun listen(timeoutMs: Long): ListenResult

    fun release()
}

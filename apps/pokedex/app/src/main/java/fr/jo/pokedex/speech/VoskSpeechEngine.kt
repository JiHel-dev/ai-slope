package fr.jo.pokedex.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VoskSpeechEngine(private val context: Context) : SpeechEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var vocabulary: Set<String>? = null
    private val prepareLock = Mutex()

    override suspend fun prepare(phrases: List<String>, mode: RecognitionMode) = prepareLock.withLock {
        val loaded = model ?: loadModel().also { model = it }
        val usable = if (mode == RecognitionMode.GRAMMAR) usablePhrases(phrases) else phrases
        val created = withContext(Dispatchers.Default) {
            try {
                val rec = when (mode) {
                    RecognitionMode.GRAMMAR -> Recognizer(loaded, SAMPLE_RATE, grammar(usable))
                    RecognitionMode.FREE -> Recognizer(loaded, SAMPLE_RATE)
                }
                rec.setMaxAlternatives(MAX_ALTERNATIVES)
                rec.setWords(false)
                rec
            } catch (e: Exception) {
                throw SpeechUnavailableException("Impossible de configurer la reconnaissance vocale", e)
            }
        }
        recognizer?.close()
        recognizer = created
    }

    override suspend fun listen(timeoutMs: Long): ListenResult {
        val rec = recognizer ?: return ListenResult.Failed("Reconnaissance non initialisée")
        rec.reset()
        val service = try {
            SpeechService(rec, SAMPLE_RATE)
        } catch (e: Exception) {
            Log.w(TAG, "Microphone unavailable", e)
            return ListenResult.Failed("Micro indisponible")
        }

        return suspendCancellableCoroutine { cont ->
            var lastPartial = ""

            fun finish(result: ListenResult) {
                if (!cont.isActive) return
                service.cancel()
                service.shutdown()
                cont.resume(result)
            }

            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    parsePartial(hypothesis)?.let { lastPartial = it }
                }

                override fun onResult(hypothesis: String?) {
                    val alternatives = parseAlternatives(hypothesis)
                    if (alternatives.isNotEmpty()) finish(ListenResult.Heard(alternatives))
                }

                override fun onFinalResult(hypothesis: String?) {
                    val alternatives = parseAlternatives(hypothesis)
                    finish(if (alternatives.isEmpty()) ListenResult.Silence else ListenResult.Heard(alternatives))
                }

                override fun onError(exception: Exception?) {
                    Log.w(TAG, "Recognition error", exception)
                    finish(ListenResult.Failed(exception?.message ?: "Erreur de reconnaissance"))
                }

                override fun onTimeout() {
                    val alternatives = parseAlternatives(rec.finalResult)
                    finish(
                        when {
                            alternatives.isNotEmpty() -> ListenResult.Heard(alternatives)
                            lastPartial.isNotBlank() -> ListenResult.Heard(listOf(lastPartial))
                            else -> ListenResult.Silence
                        },
                    )
                }
            }

            cont.invokeOnCancellation {
                mainHandler.post {
                    service.cancel()
                    service.shutdown()
                }
            }

            if (!service.startListening(listener, timeoutMs.toInt())) {
                finish(ListenResult.Failed("Écoute déjà en cours"))
            }
        }
    }

    override fun release() {
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
    }

    private suspend fun usablePhrases(phrases: List<String>): List<String> {
        val words = vocabulary ?: withContext(Dispatchers.IO) {
            ModelVocabulary.read(File(context.getExternalFilesDir(null), "$MODEL_TARGET_DIR/$MODEL_ASSET_DIR"))
        }.also { vocabulary = it }
        val usable = ModelVocabulary.usablePhrases(phrases, words)
        Log.i(TAG, "Grammar: ${usable.size} of ${phrases.size} phrases fully known by the model")
        if (usable.isEmpty()) throw SpeechUnavailableException("Aucun nom reconnaissable par le modèle vocal")
        return usable
    }

    private suspend fun loadModel(): Model {
        val files = withContext(Dispatchers.IO) { context.assets.list(MODEL_ASSET_DIR) }
        if (files.isNullOrEmpty()) {
            throw SpeechUnavailableException("Modèle vocal absent de l'application (assets/$MODEL_ASSET_DIR)")
        }
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        Log.i(TAG, "Loading model")
        return suspendCancellableCoroutine { cont ->
            StorageService.unpack(
                context,
                MODEL_ASSET_DIR,
                MODEL_TARGET_DIR,
                { model -> if (cont.isActive) cont.resume(model) else model.close() },
                { error ->
                    if (cont.isActive) {
                        cont.resumeWithException(SpeechUnavailableException("Modèle vocal illisible", error))
                    }
                },
            )
        }
    }

    companion object {
        private const val TAG = "VoskSpeechEngine"
        private const val SAMPLE_RATE = 16000f
        private const val MAX_ALTERNATIVES = 3
        const val MODEL_ASSET_DIR = "model-fr"
        private const val MODEL_TARGET_DIR = "model"
        private const val UNKNOWN = "[unk]"

        private val json = Json { ignoreUnknownKeys = true }

        fun grammar(phrases: List<String>): String =
            JsonArray((phrases + UNKNOWN).distinct().map(::JsonPrimitive)).toString()

        /** Accepts both `{"text": ...}` and `{"alternatives": [{"text": ...}]}` results. */
        fun parseAlternatives(hypothesis: String?): List<String> {
            val root = parse(hypothesis) ?: return emptyList()
            val texts = root["alternatives"]?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                ?: listOfNotNull(root["text"]?.jsonPrimitive?.contentOrNull)
            return texts.map { it.trim() }.filter { it.isNotEmpty() && it != UNKNOWN }.distinct()
        }

        fun parsePartial(hypothesis: String?): String? =
            parse(hypothesis)?.get("partial")?.jsonPrimitive?.contentOrNull?.trim()

        private fun parse(hypothesis: String?): JsonObject? =
            hypothesis?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }
}

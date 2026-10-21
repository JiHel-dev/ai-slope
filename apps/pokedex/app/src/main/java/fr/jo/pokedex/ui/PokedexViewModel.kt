package fr.jo.pokedex.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.jo.pokedex.AppContainer
import fr.jo.pokedex.audio.DescriptionAudio
import fr.jo.pokedex.audio.Feedback
import fr.jo.pokedex.audio.FeedbackSounds
import fr.jo.pokedex.audio.PlaybackResult
import fr.jo.pokedex.audio.Sound
import fr.jo.pokedex.domain.PokedexData
import fr.jo.pokedex.domain.PokedexSource
import fr.jo.pokedex.domain.Pokemon
import fr.jo.pokedex.domain.PokemonResolver.Resolution
import fr.jo.pokedex.kiosk.RecognitionSettings
import fr.jo.pokedex.speech.ListenResult
import fr.jo.pokedex.speech.RecognitionMode
import fr.jo.pokedex.speech.SpeechEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PokedexViewModel(
    private val source: PokedexSource,
    private val speech: SpeechEngine,
    private val audio: DescriptionAudio,
    private val feedback: Feedback,
    private val settings: RecognitionSettings,
    private val timing: Timing = Timing(),
) : ViewModel() {

    data class Timing(
        val listenTimeoutMs: Long = 6_000,
        /** Lets the "listening" beep finish so it is not captured by the microphone. */
        val beforeListenMs: Long = 400,
        val processingMinMs: Long = 400,
        val failureDisplayMs: Long = 4_000,
        val showingIdleMs: Long = 90_000,
    )

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _data = MutableStateFlow<PokedexData?>(null)
    val data: StateFlow<PokedexData?> = _data.asStateFlow()

    private val _mode = MutableStateFlow(RecognitionMode.GRAMMAR)
    val mode: StateFlow<RecognitionMode> = _mode.asStateFlow()

    /** Current search, playback or message; cancelled by any interruption. */
    private var job: Job? = null

    /** Loading of the data and recognizer; survives interruptions so the app never stays stuck loading. */
    private var loadJob: Job? = null

    init {
        loadJob = viewModelScope.launch { initialize(modeOverride = null) }
    }

    fun onListenPressed(hasMicPermission: Boolean) {
        if (!_state.value.canListen) return
        job?.cancel()
        audio.stop()
        if (!hasMicPermission) {
            _state.value = UiState.MicPermissionNeeded
            feedback.play(Sound.ERROR)
            job = viewModelScope.launch {
                delay(timing.failureDisplayMs)
                _state.value = UiState.Idle
            }
            return
        }
        _state.value = UiState.Listening
        job = viewModelScope.launch { search() }
    }

    fun onReplayPressed() {
        val showing = _state.value as? UiState.Showing ?: return
        if (showing.playing) return
        job?.cancel()
        job = viewModelScope.launch { present(showing.pokemon, showing.hasImage, playSuccess = false) }
    }

    /** Called when the activity is paused: no capture or playback may survive an interruption. */
    fun onInterrupted() {
        job?.cancel()
        job = null
        audio.stop()
        when (_state.value) {
            UiState.Loading, is UiState.Unavailable, UiState.Idle -> Unit
            else -> _state.value = UiState.Idle
        }
    }

    fun setRecognitionMode(mode: RecognitionMode) {
        if (mode == _mode.value && _state.value !is UiState.Unavailable) return
        reload(mode)
    }

    fun reload(mode: RecognitionMode? = null) {
        job?.cancel()
        loadJob?.cancel()
        audio.stop()
        loadJob = viewModelScope.launch {
            if (mode != null) settings.setRecognitionMode(mode)
            initialize(modeOverride = mode)
        }
    }

    private suspend fun initialize(modeOverride: RecognitionMode?) {
        _state.value = UiState.Loading
        try {
            val data = source.load()
            _data.value = data
            Log.i(TAG, "Loaded ${data.pokemon.size} Pokémon")
            val mode = modeOverride ?: settings.recognitionMode.first()
            _mode.value = mode
            Log.i(TAG, "Preparing recognizer ($mode)")
            speech.prepare(data.grammarPhrases, mode)
            Log.i(TAG, "Recognizer ready")
            _state.value = UiState.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            _state.value = UiState.Unavailable(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun search() {
        feedback.play(Sound.LISTEN)
        delay(timing.beforeListenMs)
        when (val result = speech.listen(timing.listenTimeoutMs)) {
            is ListenResult.Heard -> {
                _state.value = UiState.Processing
                val data = _data.value ?: return fail(FailureKind.MICROPHONE)
                val resolution = data.resolver.resolve(result.alternatives)
                Log.i(TAG, "Heard ${result.alternatives} -> $resolution")
                delay(timing.processingMinMs)
                when (resolution) {
                    is Resolution.Found -> {
                        val pokemon = data.pokemon(resolution.pokemonId) ?: return fail(FailureKind.NOT_UNDERSTOOD)
                        present(pokemon, data.hasImage(pokemon), playSuccess = true)
                    }
                    is Resolution.Ambiguous -> fail(FailureKind.AMBIGUOUS)
                    is Resolution.NotFound -> fail(FailureKind.NOT_UNDERSTOOD)
                }
            }
            ListenResult.Silence -> fail(FailureKind.SILENCE)
            is ListenResult.Failed -> fail(FailureKind.MICROPHONE)
        }
    }

    private suspend fun present(pokemon: Pokemon, hasImage: Boolean, playSuccess: Boolean) {
        _state.value = UiState.Showing(pokemon, hasImage, playing = true)
        if (playSuccess) {
            feedback.play(Sound.SUCCESS)
            delay(SUCCESS_SOUND_MS)
        }
        val data = _data.value
        val path = if (data == null || data.hasAudio(pokemon)) pokemon.audioPath else FeedbackSounds.MISSING_DESCRIPTION
        if (audio.play(path) == PlaybackResult.FAILED && path != FeedbackSounds.MISSING_DESCRIPTION) {
            audio.play(FeedbackSounds.MISSING_DESCRIPTION)
        }
        _state.value = UiState.Showing(pokemon, hasImage, playing = false)
        delay(timing.showingIdleMs)
        _state.value = UiState.Idle
    }

    private suspend fun fail(kind: FailureKind) {
        _state.value = UiState.Failure(kind)
        feedback.play(Sound.ERROR)
        delay(timing.failureDisplayMs)
        _state.value = UiState.Idle
    }

    override fun onCleared() {
        audio.stop()
    }

    companion object {
        private const val TAG = "PokedexViewModel"
        private const val SUCCESS_SOUND_MS = 600L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PokedexViewModel(
                    source = container.repository,
                    speech = container.speech,
                    audio = container.descriptionPlayer,
                    feedback = container.feedback,
                    settings = container.settings,
                )
            }
        }
    }
}

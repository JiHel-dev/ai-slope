package fr.jo.pokedex.ui

import fr.jo.pokedex.audio.DescriptionAudio
import fr.jo.pokedex.audio.Feedback
import fr.jo.pokedex.audio.FeedbackSounds
import fr.jo.pokedex.audio.PlaybackResult
import fr.jo.pokedex.audio.Sound
import fr.jo.pokedex.domain.PokedexData
import fr.jo.pokedex.domain.PokedexSource
import fr.jo.pokedex.domain.Pokemon
import fr.jo.pokedex.domain.PokemonResolver
import fr.jo.pokedex.kiosk.RecognitionSettings
import fr.jo.pokedex.speech.ListenResult
import fr.jo.pokedex.speech.RecognitionMode
import fr.jo.pokedex.speech.SpeechEngine
import fr.jo.pokedex.speech.SpeechUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PokedexViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val timing = PokedexViewModel.Timing(
        listenTimeoutMs = 6_000,
        beforeListenMs = 100,
        processingMinMs = 100,
        failureDisplayMs = 1_000,
        showingIdleMs = 5_000,
    )

    private val pikachu = Pokemon(25, "Pikachu", "pokemon/images/0025.png", "pokemon/audio/0025.wav")
    private val carapuce = Pokemon(7, "Carapuce", "pokemon/images/0007.png", "pokemon/audio/0007.wav")

    private val speech = FakeSpeech()
    private val audio = FakeAudio()
    private val feedback = FakeFeedback()
    private val settings = FakeSettings()
    private var loadFailure: Exception? = null

    private val source = object : PokedexSource {
        override suspend fun load(): PokedexData {
            loadFailure?.let { throw it }
            val all = listOf(carapuce, pikachu)
            return PokedexData(
                pokemon = all,
                resolver = PokemonResolver(
                    all.map { PokemonResolver.Entry(it.id, it.name) } +
                        listOf(PokemonResolver.Entry(29, "Nidoran"), PokemonResolver.Entry(32, "Nidoran")),
                ),
                grammarPhrases = listOf("carapuce", "pikachu"),
                missingImages = setOf(7),
                missingAudio = setOf(7),
            )
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.ready(): PokedexViewModel {
        val vm = PokedexViewModel(source, speech, audio, feedback, settings, timing)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `starts idle with the grammar prepared`() = runTest(dispatcher) {
        val vm = ready()
        assertEquals(UiState.Idle, vm.state.value)
        assertEquals(listOf("carapuce", "pikachu"), speech.preparedPhrases)
        assertEquals(RecognitionMode.GRAMMAR, speech.preparedMode)
        assertFalse(speech.active)
    }

    @Test
    fun `recognized name shows the card and plays its description`() = runTest(dispatcher) {
        val vm = ready()
        speech.next = ListenResult.Heard(listOf("pikachu"))

        vm.onListenPressed(hasMicPermission = true)
        assertEquals(UiState.Listening, vm.state.value)

        advanceTimeBy(timing.beforeListenMs + FakeSpeech.LISTEN_MS + timing.processingMinMs + 700)
        assertEquals(UiState.Showing(pikachu, hasImage = true, playing = true), vm.state.value)
        assertEquals(listOf("pokemon/audio/0025.wav"), audio.played)

        advanceTimeBy(FakeAudio.PLAY_MS)
        assertEquals(UiState.Showing(pikachu, hasImage = true, playing = false), vm.state.value)
        assertEquals(listOf(Sound.LISTEN, Sound.SUCCESS), feedback.sounds)

        advanceTimeBy(timing.showingIdleMs + 1)
        assertEquals(UiState.Idle, vm.state.value)
        assertFalse(speech.active)
    }

    @Test
    fun `button triggers a single listening session`() = runTest(dispatcher) {
        val vm = ready()
        vm.onListenPressed(true)
        vm.onListenPressed(true)
        runCurrent()
        vm.onListenPressed(true)
        advanceUntilIdle()
        assertEquals(1, speech.listenCount)
    }

    @Test
    fun `unknown name shows a failure and never a card`() = runTest(dispatcher) {
        val vm = ready()
        speech.next = ListenResult.Heard(listOf("voiture"))
        val states = mutableListOf<UiState>()
        vm.onListenPressed(true)
        repeat(100) {
            advanceTimeBy(50)
            states += vm.state.value
        }
        assertTrue(states.contains(UiState.Failure(FailureKind.NOT_UNDERSTOOD)))
        assertTrue(states.none { it is UiState.Showing })
        assertEquals(UiState.Idle, vm.state.value)
        assertTrue(audio.played.isEmpty())
    }

    @Test
    fun `ambiguous name is a failure`() = runTest(dispatcher) {
        val vm = ready()
        speech.next = ListenResult.Heard(listOf("nidoran"))
        vm.onListenPressed(true)
        advanceTimeBy(timing.beforeListenMs + FakeSpeech.LISTEN_MS + timing.processingMinMs + 10)
        assertEquals(UiState.Failure(FailureKind.AMBIGUOUS), vm.state.value)
    }

    @Test
    fun `silence and microphone errors are failures`() = runTest(dispatcher) {
        val vm = ready()
        speech.next = ListenResult.Silence
        vm.onListenPressed(true)
        advanceTimeBy(timing.beforeListenMs + FakeSpeech.LISTEN_MS + 10)
        assertEquals(UiState.Failure(FailureKind.SILENCE), vm.state.value)

        advanceUntilIdle()
        speech.next = ListenResult.Failed("busy")
        vm.onListenPressed(true)
        advanceTimeBy(timing.beforeListenMs + FakeSpeech.LISTEN_MS + 10)
        assertEquals(UiState.Failure(FailureKind.MICROPHONE), vm.state.value)
    }

    @Test
    fun `missing microphone permission never opens the microphone`() = runTest(dispatcher) {
        val vm = ready()
        vm.onListenPressed(hasMicPermission = false)
        assertEquals(UiState.MicPermissionNeeded, vm.state.value)
        advanceUntilIdle()
        assertEquals(0, speech.listenCount)
        assertEquals(UiState.Idle, vm.state.value)
    }

    @Test
    fun `replay plays the description again`() = runTest(dispatcher) {
        val vm = ready()
        speech.next = ListenResult.Heard(listOf("pikachu"))
        vm.onListenPressed(true)
        advanceTimeBy(timing.beforeListenMs + FakeSpeech.LISTEN_MS + timing.processingMinMs + 700 + FakeAudio.PLAY_MS)
        assertEquals(UiState.Showing(pikachu, hasImage = true, playing = false), vm.state.value)

        vm.onReplayPressed()
        runCurrent()
        assertEquals(UiState.Showing(pikachu, hasImage = true, playing = true), vm.state.value)
        assertEquals(2, audio.played.size)
    }

    @Test
    fun `missing description falls back to a neutral sound`() = runTest(dispatcher) {
        val vm = ready()
        speech.next = ListenResult.Heard(listOf("carapuce"))
        vm.onListenPressed(true)
        advanceTimeBy(timing.beforeListenMs + FakeSpeech.LISTEN_MS + timing.processingMinMs + 700)
        assertEquals(UiState.Showing(carapuce, hasImage = false, playing = true), vm.state.value)
        assertEquals(listOf(FeedbackSounds.MISSING_DESCRIPTION), audio.played)
    }

    @Test
    fun `interruption stops the capture and returns to the home screen`() = runTest(dispatcher) {
        val vm = ready()
        vm.onListenPressed(true)
        advanceTimeBy(timing.beforeListenMs + 10)
        assertTrue(speech.active)

        vm.onInterrupted()
        runCurrent()
        assertFalse(speech.active)
        assertEquals(UiState.Idle, vm.state.value)
        advanceUntilIdle()
        assertEquals(UiState.Idle, vm.state.value)
    }

    @Test
    fun `interruption while loading does not block the app`() = runTest(dispatcher) {
        speech.prepareDelayMs = 1_000
        val vm = PokedexViewModel(source, speech, audio, feedback, settings, timing)
        runCurrent()
        assertEquals(UiState.Loading, vm.state.value)

        vm.onInterrupted()
        advanceUntilIdle()
        assertEquals(UiState.Idle, vm.state.value)
    }

    @Test
    fun `engine failure makes the app unavailable`() = runTest(dispatcher) {
        speech.failure = SpeechUnavailableException("Modèle vocal absent")
        val vm = ready()
        val state = vm.state.value
        assertTrue(state is UiState.Unavailable)
        assertFalse(state.canListen)
        vm.onListenPressed(true)
        advanceUntilIdle()
        assertEquals(0, speech.listenCount)
    }

    @Test
    fun `changing the recognition mode prepares the engine again`() = runTest(dispatcher) {
        val vm = ready()
        vm.setRecognitionMode(RecognitionMode.FREE)
        advanceUntilIdle()
        assertEquals(RecognitionMode.FREE, speech.preparedMode)
        assertEquals(RecognitionMode.FREE, settings.recognitionMode.value)
        assertEquals(UiState.Idle, vm.state.value)
    }

    @Test
    fun `dozens of searches in a row`() = runTest(dispatcher) {
        val vm = ready()
        repeat(60) { i ->
            speech.next = if (i % 3 == 0) ListenResult.Heard(listOf("voiture")) else ListenResult.Heard(listOf("pikachu"))
            vm.onListenPressed(true)
            advanceTimeBy(timing.beforeListenMs + FakeSpeech.LISTEN_MS + timing.processingMinMs + 50)
            vm.onInterrupted()
            advanceTimeBy(10)
        }
        assertEquals(60, speech.listenCount)
        assertFalse(speech.active)
        assertEquals(UiState.Idle, vm.state.value)
    }

    private class FakeSpeech : SpeechEngine {
        var next: ListenResult = ListenResult.Silence
        var failure: Exception? = null
        var listenCount = 0
        var active = false
        var preparedPhrases: List<String> = emptyList()
        var preparedMode: RecognitionMode? = null
        var prepareDelayMs = 0L

        override suspend fun prepare(phrases: List<String>, mode: RecognitionMode) {
            delay(prepareDelayMs)
            failure?.let { throw it }
            preparedPhrases = phrases
            preparedMode = mode
        }

        override suspend fun listen(timeoutMs: Long): ListenResult {
            listenCount++
            active = true
            try {
                delay(LISTEN_MS)
                return next
            } finally {
                active = false
            }
        }

        override fun release() = Unit

        companion object {
            const val LISTEN_MS = 1_000L
        }
    }

    private class FakeAudio : DescriptionAudio {
        val played = mutableListOf<String>()

        override suspend fun play(assetPath: String): PlaybackResult {
            played += assetPath
            delay(PLAY_MS)
            return PlaybackResult.COMPLETED
        }

        override fun stop() = Unit

        companion object {
            const val PLAY_MS = 2_000L
        }
    }

    private class FakeFeedback : Feedback {
        val sounds = mutableListOf<Sound>()

        override fun play(sound: Sound) {
            sounds += sound
        }
    }

    private class FakeSettings : RecognitionSettings {
        override val recognitionMode = MutableStateFlow(RecognitionMode.GRAMMAR)

        override suspend fun setRecognitionMode(mode: RecognitionMode) {
            recognitionMode.value = mode
        }
    }
}

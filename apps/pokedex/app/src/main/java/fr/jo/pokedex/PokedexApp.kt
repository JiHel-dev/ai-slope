package fr.jo.pokedex

import android.app.Application
import android.content.Context
import fr.jo.pokedex.audio.DescriptionPlayer
import fr.jo.pokedex.audio.FeedbackSounds
import fr.jo.pokedex.data.PokedexDatabase
import fr.jo.pokedex.data.PokedexRepository
import fr.jo.pokedex.kiosk.KioskManager
import fr.jo.pokedex.kiosk.ParentalSettings
import fr.jo.pokedex.speech.SpeechEngine
import fr.jo.pokedex.speech.VoskSpeechEngine

class PokedexApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val database by lazy { PokedexDatabase.create(context) }
    val repository by lazy { PokedexRepository(context, database.pokemonDao()) }
    val speech: SpeechEngine by lazy { VoskSpeechEngine(context) }
    val descriptionPlayer by lazy { DescriptionPlayer(context) }
    val feedback by lazy { FeedbackSounds(context) }
    val settings by lazy { ParentalSettings(context) }
    val kiosk by lazy { KioskManager(context) }
}

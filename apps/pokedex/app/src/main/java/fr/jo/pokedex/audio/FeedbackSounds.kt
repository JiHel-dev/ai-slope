package fr.jo.pokedex.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

enum class Sound(val asset: String) {
    LISTEN("sounds/listen.wav"),
    SUCCESS("sounds/success.wav"),
    ERROR("sounds/error.wav"),
}

interface Feedback {
    fun play(sound: Sound)
}

class FeedbackSounds(context: Context) : Feedback {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<Sound, Int> = Sound.entries.mapNotNull { sound ->
        runCatching { context.assets.openFd(sound.asset).use { sound to pool.load(it, 1) } }
            .onFailure { Log.w(TAG, "Missing sound ${sound.asset}", it) }
            .getOrNull()
    }.toMap()

    override fun play(sound: Sound) {
        ids[sound]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    companion object {
        private const val TAG = "FeedbackSounds"

        /** Played instead of a description that has not been recorded yet. */
        const val MISSING_DESCRIPTION = "sounds/missing.wav"
    }
}

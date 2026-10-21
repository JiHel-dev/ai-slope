package fr.jo.pokedex.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class PlaybackResult { COMPLETED, INTERRUPTED, FAILED }

interface DescriptionAudio {
    /** Plays an asset and suspends until it ends. Cancelling stops playback. */
    suspend fun play(assetPath: String): PlaybackResult

    fun stop()
}

/** Must be used from the main thread. */
class DescriptionPlayer(context: Context) : DescriptionAudio {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val player = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    override suspend fun play(assetPath: String): PlaybackResult = suspendCancellableCoroutine { cont ->
        val listener = object : Player.Listener {
            fun done(result: PlaybackResult) {
                player.removeListener(this)
                if (cont.isActive) cont.resume(result)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) done(PlaybackResult.COMPLETED)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Cannot play $assetPath", error)
                done(PlaybackResult.FAILED)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    player.stop()
                    done(PlaybackResult.INTERRUPTED)
                }
            }
        }

        player.stop()
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri("asset:///$assetPath"))
        player.prepare()
        player.playWhenReady = true

        cont.invokeOnCancellation {
            mainHandler.post {
                player.removeListener(listener)
                player.stop()
            }
        }
    }

    override fun stop() {
        player.stop()
    }

    private companion object {
        const val TAG = "DescriptionPlayer"
    }
}

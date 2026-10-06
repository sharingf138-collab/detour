package com.kartik.detour.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.kartik.detour.MainActivity
import com.kartik.detour.data.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Hosts the player so podcasts keep going with the screen off, with lock-screen controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true, // pause for calls and other apps
            )
            .setHandleAudioBecomingNoisy(true) // pause when earphones come out
            .build()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

data class AudioState(
    val podcast: Podcast? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val sleepAtMs: Long? = null,      // wall-clock time the sleep timer fires
)

/** App-side handle on the playback service: play, seek, sleep timer, resume position. */
class Audio(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var sleepJob: Job? = null
    private val positions = context.getSharedPreferences("podcast_positions", Context.MODE_PRIVATE)

    private suspend fun connect(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val c = suspendCancellableCoroutine { cont ->
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        })
        controller = c
        return c
    }

    fun play(p: Podcast) {
        scope.launch(Dispatchers.Main) {
            val c = connect()
            if (_state.value.podcast?.id != p.id) {
                savePosition()
                val item = MediaItem.Builder()
                    .setMediaId(p.id)
                    .setUri(p.audioUrl)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(p.title).setArtist(p.show).build())
                    .build()
                // Resume where you left off; otherwise skip the intro/sponsor read when the show notes allow.
                c.setMediaItem(item, positions.getLong(p.id, (p.startSec ?: 0) * 1000L))
                c.prepare()
                _state.value = _state.value.copy(podcast = p)
            }
            c.play()
            startTicker()
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekBy(ms: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + ms).coerceAtLeast(0))
        sync()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        sync()
    }

    /** Pause after [minutes]; null cancels. */
    fun sleepIn(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _state.value = _state.value.copy(sleepAtMs = null)
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _state.value = _state.value.copy(sleepAtMs = at)
        sleepJob = scope.launch(Dispatchers.Main) {
            delay(minutes * 60_000L)
            controller?.pause()
            savePosition()
            _state.value = _state.value.copy(sleepAtMs = null)
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch(Dispatchers.Main) {
            var n = 0
            while (isActive) {
                sync()
                if (++n % 10 == 0) savePosition()
                delay(1000)
            }
        }
    }

    private fun sync() {
        val c = controller ?: return
        _state.value = _state.value.copy(
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0,
        )
    }

    private fun savePosition() {
        val c = controller ?: return
        val id = _state.value.podcast?.id ?: return
        positions.edit { putLong(id, c.currentPosition) }
    }
}

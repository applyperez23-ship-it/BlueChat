package com.example.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerState(
    val playingPath: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

class AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun play(filePath: String, onCompletion: (() -> Unit)? = null) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e("AudioPlayerManager", "Audio file does not exist: $filePath")
            return
        }

        // If clicking same file and currently playing, pause it
        if (_playerState.value.playingPath == filePath && _playerState.value.isPlaying) {
            pause()
            return
        }

        stop()

        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    stop()
                    onCompletion?.invoke()
                }
                start()
            }
            mediaPlayer = player

            val duration = player.duration
            _playerState.value = PlayerState(
                playingPath = filePath,
                isPlaying = true,
                currentPositionMs = 0,
                durationMs = duration
            )

            startProgressTicker()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to start audio playback", e)
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Pause error", e)
        }
        progressJob?.cancel()
    }

    fun stop() {
        progressJob?.cancel()
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _playerState.value = PlayerState()
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer != null && _playerState.value.isPlaying) {
                try {
                    val pos = mediaPlayer?.currentPosition ?: 0
                    val dur = mediaPlayer?.duration ?: 0
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                } catch (_: Exception) {}
                delay(100)
            }
        }
    }
}

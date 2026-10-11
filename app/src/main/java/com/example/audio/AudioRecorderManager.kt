package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

data class AudioRecordingResult(
    val file: File,
    val durationSeconds: Int,
    val fileSizeBytes: Long
)

class AudioRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTimeMs: Long = 0
    var isRecording: Boolean = false
        private set

    @Suppress("DEPRECATION")
    fun startRecording(): Boolean {
        if (isRecording) return false

        try {
            val audioDir = File(context.cacheDir, "audio_notes")
            if (!audioDir.exists()) audioDir.mkdirs()

            val timestamp = System.currentTimeMillis()
            val file = File(audioDir, "rec_$timestamp.m4a")
            currentOutputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000) // 16kHz for lightweight voice transmission
                setAudioEncodingBitRate(24000) // 24kbps keeps mesh packets small
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingStartTimeMs = System.currentTimeMillis()
            isRecording = true
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Failed to start audio recording", e)
            cleanup()
            return false
        }
    }

    fun stopRecording(): AudioRecordingResult? {
        if (!isRecording) return null

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val file = currentOutputFile
            if (file != null && file.exists() && file.length() > 0) {
                val durationMs = System.currentTimeMillis() - recordingStartTimeMs
                val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)
                AudioRecordingResult(
                    file = file,
                    durationSeconds = durationSec,
                    fileSizeBytes = file.length()
                )
            } else {
                cleanup()
                null
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error stopping recording", e)
            cleanup()
            null
        }
    }

    fun cancelRecording() {
        if (!isRecording) return
        cleanup()
    }

    private fun cleanup() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
        currentOutputFile?.let {
            if (it.exists()) it.delete()
        }
        currentOutputFile = null
    }
}

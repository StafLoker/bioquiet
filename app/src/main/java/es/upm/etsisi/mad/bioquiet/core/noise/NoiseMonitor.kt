package es.upm.etsisi.mad.bioquiet.core.noise

import android.annotation.SuppressLint
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.log10

/**
 * Manages the device microphone via [MediaRecorder].
 */
@Suppress("DEPRECATION")
class NoiseMonitor(private val cacheDirPath: String) {

    companion object {
        private const val LOG_TAG = "NoiseMonitor"
        private const val DUMMY_FILE = "dummy_audio.3gp"
        private const val DB_CORRECTION = 15.0
    }

    private var recorder: MediaRecorder? = null
    val isCapturing: Boolean get() = recorder != null

    @SuppressLint("MissingPermission")
    fun start() {
        if (isCapturing) return
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile("$cacheDirPath/$DUMMY_FILE")
                prepare()
                start()
            }
            Log.d(LOG_TAG, "MediaRecorder started.")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to start MediaRecorder: ${e.message}", e)
            release()
        }
    }

    fun readDb(): Double {
        val amplitude = recorder?.maxAmplitude ?: 0
        return if (amplitude > 0) 20.0 * log10(amplitude.toDouble()) - DB_CORRECTION else 0.0
    }

    fun stop() {
        if (!isCapturing) return
        try {
            recorder?.stop()
            Log.d(LOG_TAG, "MediaRecorder stopped.")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error stopping MediaRecorder: ${e.message}", e)
        } finally {
            release()
        }
    }

    private fun release() {
        recorder?.release()
        recorder = null
    }
}

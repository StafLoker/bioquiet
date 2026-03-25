package es.upm.etsisi.mad.bioquiet.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.card.MaterialCardView
import es.upm.etsisi.mad.bioquiet.model.Zepa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10

class NoiseMonitor(
    private val activity: FragmentActivity,
    private val noiseCard: MaterialCardView,
    private val noiseText: TextView,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val getCurrentZepa: () -> Zepa?,
    private val onWarning: (Zepa) -> Unit,
    private val getNoiseLevel: (String, Int) -> String
) {
    companion object {
        const val LOG_TAG = "NoiseMonitor"
        const val WARNING_WINDOW = 5
        const val WARNING_THRESHOLD = 3
    }

    fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    @Volatile private var capturing = false
    @Volatile private var latestDb = 0.0
    private var mediaRecorder: MediaRecorder? = null

    private var monitorJob: Job? = null
    private var alertShown = false
    private val warningHistory = ArrayDeque<Boolean>(WARNING_WINDOW)
    private val storageManager = NoiseStorageManager(activity)

    @RequiresApi(Build.VERSION_CODES.S)
    fun start() {
        if (!hasPermission()) {
            Log.e(LOG_TAG, "Cannot start: RECORD_AUDIO permission not granted.")
            return
        }

        Log.d(LOG_TAG, "Starting NoiseMonitor loop.")
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(500)
            while (isActive) {
                delay(1000)

                val zepa = withContext(Dispatchers.Main) { getCurrentZepa() }

                // If user is outside any ZEPA zone
                if (zepa == null) {
                    if (capturing) {
                        Log.d(LOG_TAG, "User left ZEPA zone. Stopping microphone.")
                    }
                    stopListening()
                    withContext(Dispatchers.Main) { noiseCard.visibility = View.GONE }
                    alertShown = false
                    warningHistory.clear()
                    continue
                }

                // If user is inside a ZEPA zone, ensure microphone is actively listening
                startListening()

                // Calculate decibels from MediaRecorder's max amplitude
                val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                val db = if (maxAmplitude > 0) {
                    20.0 * log10(maxAmplitude.toDouble())
                } else {
                    0.0
                }
                latestDb = db

                Log.d(
                    LOG_TAG,
                    "Tracking noise inside ZEPA '${zepa.name}': Amplitude=$maxAmplitude -> ${db.toInt()} dB"
                )

                // Save record to CSV
                storageManager.saveRecord(zepa.name, db.toInt())

                // Track whether this tick exceeded warning threshold
                val isWarningTick = db >= zepa.noiseThresholds.dbWarning
                if (warningHistory.size >= WARNING_WINDOW) warningHistory.removeFirst()
                warningHistory.addLast(isWarningTick)

                // Sustained warning: enough ticks above threshold in the window
                val sustained = warningHistory.count { it } >= WARNING_THRESHOLD

                withContext(Dispatchers.Main) {
                    noiseCard.visibility = View.VISIBLE
                    noiseText.text = getNoiseLevel("noise_level_db", db.toInt())

                    when {
                        db >= zepa.noiseThresholds.dbWarning ->
                            noiseCard.setCardBackgroundColor(Color.RED)
                        db >= zepa.noiseThresholds.dbSafe ->
                            noiseCard.setCardBackgroundColor(Color.YELLOW)
                        else ->
                            noiseCard.setCardBackgroundColor(Color.GREEN)
                    }

                    // Alert only on sustained noise
                    if (sustained && !alertShown) {
                        Log.d(LOG_TAG, "Sustained high noise detected! Triggering UI warning.")
                        alertShown = true
                        onWarning(zepa)
                    }
                    if (!sustained) {
                        alertShown = false
                    }
                }
            }
        }
    }

    fun pause() {
        Log.d(LOG_TAG, "Pausing NoiseMonitor.")
        monitorJob?.cancel()
        monitorJob = null
        stopListening()
        warningHistory.clear()
        alertShown = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun resume() {
        if (!capturing) {
            Log.d(LOG_TAG, "Resuming NoiseMonitor.")
            start()
        }
    }

    fun stop() {
        Log.d(LOG_TAG, "Stopping NoiseMonitor entirely.")
        pause()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (capturing) return

        try {
            val dummyFile = "${activity.cacheDir.absolutePath}/dummy_audio.3gp"
            Log.d(LOG_TAG, "Initializing MediaRecorder with dummy file: $dummyFile")

            mediaRecorder = MediaRecorder(activity).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(dummyFile)
                prepare()
                start()
            }
            capturing = true
            Log.d(LOG_TAG, "MediaRecorder started successfully.")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "MediaRecorder failed to start: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            capturing = false
        }
    }

    private fun stopListening() {
        if (!capturing) return

        try {
            mediaRecorder?.stop()
            Log.d(LOG_TAG, "MediaRecorder stopped.")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "MediaRecorder failed during stop: ${e.message}", e)
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            capturing = false
            latestDb = 0.0
            Log.d(LOG_TAG, "MediaRecorder released safely.")
        }
    }
}

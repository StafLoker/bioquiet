package es.upm.etsisi.mad.bioquiet.util

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.card.MaterialCardView
import es.upm.etsisi.mad.bioquiet.model.Zepa
import kotlin.math.log10
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun amplitude2db(amplitude: Double): Double = if (amplitude > 0) 20 * log10(amplitude) else 0.0

class NoiseMonitor(
    private val activity: FragmentActivity,
    private val noiseCard: MaterialCardView,
    private val noiseText: TextView,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val getCurrentZepa: () -> Zepa?,
    private val onWarning: (Zepa) -> Unit,
    val onPermissionDenied: () -> Unit,
    private val getNoiseLevel: (String, Int) -> String
) {
    companion object {
        const val LOG_TAG = "NoiseMonitor"
        const val PERMISSION_CODE = 3
    }

    fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun requestPermission() {
        Log.d(LOG_TAG, "Requesting audio permission")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_CODE
        )
    }

    private var recorder: MediaRecorder? = null
    private var monitorJob: Job? = null
    private var isRecording = false
    private var alertShown = false

    fun start() {
        if (!hasPermission()) {
            requestPermission()
            return
        }
        monitorJob = lifecycleScope.launch {
            while (true) {
                delay(1000)
                val zepa = getCurrentZepa()
                if (zepa == null) {
                    Log.d(LOG_TAG, "User outside ZEPA, stopping recorder")
                    stopRecorder()
                    noiseCard.visibility = View.GONE
                    alertShown = false
                    continue
                }
                startRecorder()
                val db = amplitude2db(recorder?.maxAmplitude?.toDouble() ?: 0.0)
                Log.d(
                    LOG_TAG,
                    "Inside ZEPA '${zepa.name}': ${db.toInt()} dB (safe=${zepa.noiseThresholds.dbSafe}, warning=${zepa.noiseThresholds.dbWarning})"
                )

                noiseCard.visibility = View.VISIBLE
                noiseText.text = getNoiseLevel("noise_level_db", db.toInt())

                when {
                    db >= zepa.noiseThresholds.dbWarning -> {
                        Log.w(LOG_TAG, "Noise level exceeded warning threshold in '${zepa.name}'")
                        noiseCard.setCardBackgroundColor(Color.RED)
                        if (!alertShown) {
                            alertShown = true
                            onWarning(zepa)
                        }
                    }

                    db >= zepa.noiseThresholds.dbSafe -> {
                        alertShown = false
                        noiseCard.setCardBackgroundColor(Color.YELLOW)
                    }

                    else -> {
                        alertShown = false
                        noiseCard.setCardBackgroundColor(Color.GREEN)
                    }
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        stopRecorder()
    }

    private fun startRecorder() {
        if (isRecording) return
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile("/dev/null")
            }
            recorder?.prepare()
            recorder?.start()
            isRecording = true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to start recorder (no microphone?): ${e.message}")
            recorder?.release()
            recorder = null
        }
    }

    private fun stopRecorder() {
        if (!isRecording) return
        recorder?.stop()
        recorder?.release()
        recorder = null
        isRecording = false
    }
}

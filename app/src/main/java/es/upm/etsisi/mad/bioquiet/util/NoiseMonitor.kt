package es.upm.etsisi.mad.bioquiet.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.view.View
import android.widget.TextView
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
import kotlin.math.sqrt

private const val SAMPLE_RATE = 44100
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val MAX_16BIT = 32767.0
private const val DBFS_TO_SPL_OFFSET = 90.0

private fun rmsToDb(rms: Double): Double {
    if (rms <= 0) return 0.0
    val dbFs = 20.0 * log10(rms / MAX_16BIT)
    return (dbFs + DBFS_TO_SPL_OFFSET).coerceAtLeast(0.0)
}

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
    private var captureThread: Thread? = null

    private var monitorJob: Job? = null
    private var alertShown = false
    private val warningHistory = ArrayDeque<Boolean>(WARNING_WINDOW)
    private val storageManager = NoiseStorageManager(activity)

    fun start() {
        if (!hasPermission()) return
        startCapture()
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(500)
            while (isActive) {
                delay(1000)

                val zepa = withContext(Dispatchers.Main) { getCurrentZepa() }
                if (zepa == null) {
                    withContext(Dispatchers.Main) { noiseCard.visibility = View.GONE }
                    alertShown = false
                    warningHistory.clear()
                    continue
                }

                val db = latestDb
                Log.d(
                    LOG_TAG,
                    "Inside ZEPA '${zepa.name}': ${db.toInt()} dB (safe=${zepa.noiseThresholds.dbSafe}, warning=${zepa.noiseThresholds.dbWarning})"
                )

                // Saving into a csv
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
        monitorJob?.cancel()
        monitorJob = null
        stopCapture()
        warningHistory.clear()
        alertShown = false
    }

    fun resume() {
        if (!capturing) start()
    }

    fun stop() {
        pause()
    }

    @SuppressLint("MissingPermission")
    private fun startCapture() {
        if (capturing) return

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            Log.e(LOG_TAG, "Bad AudioRecord min buffer size: $minBuf")
            return
        }

        val ar = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
                minBuf * 2
            )
        } catch (e: Exception) {
            Log.e(LOG_TAG, "AudioRecord init failed: ${e.message}", e)
            return
        }

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "AudioRecord not initialized, releasing")
            ar.release()
            return
        }

        capturing = true
        ar.startRecording()
        Log.d(LOG_TAG, "AudioRecord capture started")

        val readSize = SAMPLE_RATE / 10 // ~100ms of audio
        captureThread = Thread({
            val buf = ShortArray(readSize)
            while (capturing) {
                val n = ar.read(buf, 0, buf.size)
                if (n > 0) {
                    var sum = 0.0
                    for (i in 0 until n) {
                        val s = buf[i].toDouble()
                        sum += s * s
                    }
                    latestDb = rmsToDb(sqrt(sum / n))
                }
            }
            try { ar.stop() } catch (_: Exception) {}
            ar.release()
            Log.d(LOG_TAG, "AudioRecord capture stopped and released")
        }, "NoiseCapture").also { it.start() }
    }

    private fun stopCapture() {
        if (!capturing) return
        capturing = false
        captureThread?.join(3000)
        captureThread = null
        latestDb = 0.0
    }
}

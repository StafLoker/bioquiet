package es.upm.etsisi.mad.bioquiet.data.repository

import android.content.Context
import android.util.Log
import es.upm.etsisi.mad.bioquiet.model.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists noise records to a CSV file and computes aggregated [Statistics].
 */
class NoiseRepository(context: Context) {

    companion object {
        private const val LOG_TAG = "NoiseRepository"
        private const val FILE_NAME = "noise_records.csv"
        private const val CSV_HEADER = "Date,ZEPA_Zone,Decibels\n"
        private const val WARNING_AVG_DB = 60
    }

    private val file: File = File(context.filesDir, FILE_NAME).also { f ->
        if (!f.exists()) {
            try {
                FileWriter(f, true).use { it.append(CSV_HEADER) }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error creating CSV: ${e.message}")
            }
        }
    }

    fun saveRecord(zepaName: String, decibels: Int) {
        try {
            val timestamp =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            FileWriter(file, true).use { it.append("$timestamp,\"$zepaName\",$decibels\n") }
            Log.d(LOG_TAG, "Saved: $decibels dB in $zepaName")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error writing to CSV: ${e.message}")
        }
    }

    suspend fun getStatistics(): Statistics? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            var maxDb = 0
            var sumDb = 0
            var count = 0

            file.useLines { lines ->
                lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
                    val db = line.substringAfterLast(",").trim().toIntOrNull() ?: 0
                    if (db > maxDb) maxDb = db
                    sumDb += db
                    count++
                }
            }

            if (count == 0) return@withContext null

            val avgDb = sumDb / count
            val feedback = if (avgDb > WARNING_AVG_DB) {
                "Warning! High average noise level."
            } else {
                "Good job keeping it quiet!"
            }

            Statistics(totalRecords = count, maxDb = maxDb, averageDb = avgDb, feedback = feedback)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error reading CSV: ${e.message}")
            null
        }
    }
}

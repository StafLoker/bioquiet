package es.upm.etsisi.mad.bioquiet.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoiseStorageManager(context: Context) {
    private val fileName = "noise_records.csv"
    private val file: File = File(context.filesDir, fileName)

    init {
        // If the file doesn't exist, create it and add the CSV header
        if (!file.exists()) {
            try {
                FileWriter(file, true).use { writer ->
                    writer.append("Date,ZEPA_Zone,Decibels\n")
                }
            } catch (e: Exception) {
                Log.e("NoiseStorage", "Error creating CSV: ${e.message}")
            }
        }
    }

    fun saveRecord(zepaName: String, decibels: Int) {
        try {
            // Get the exact time of the record
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // Write the new comma-separated line to the CSV
            FileWriter(file, true).use { writer ->
                writer.append("$timestamp,\"$zepaName\",$decibels\n")
            }
            Log.d("NoiseStorage", "Saved to CSV: $decibels dB in $zepaName")
        } catch (e: Exception) {
            Log.e("NoiseStorage", "Error writing to CSV: ${e.message}")
        }
    }

    // Convert to 'suspend' to safely run on a background thread
    suspend fun getStatsSummary(): String = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext "No noise data registered yet."

        try {
            // Using useLines is much more memory-efficient for large files
            // than readLines() because it reads line by line instead of loading all into RAM.
            var maxDb = 0
            var sumDb = 0
            var count = 0

            file.useLines { lines ->
                // Skip the header line
                val dataLines = lines.drop(1)

                for (line in dataLines) {
                    if (line.isNotBlank()) {
                        // Safe extraction: get everything after the LAST comma
                        val dbString = line.substringAfterLast(",")
                        val db = dbString.trim().toIntOrNull() ?: 0

                        if (db > maxDb) maxDb = db
                        sumDb += db
                        count++
                    }
                }
            }

            if (count == 0) return@withContext "No valid data found."

            val avgDb = sumDb / count

            // Prepare the final text
            val feedback = if (avgDb > 60) "Warning! High average noise level." else "Good job keeping it quiet!"

            return@withContext "ZEPA Noise Summary:\n\n" +
                    "Total records: $count\n" +
                    "Max Noise: $maxDb dB\n" +
                    "Average Noise: $avgDb dB\n\n" +
                    feedback

        } catch (e: Exception) {
            Log.e("NoiseStorage", "Error reading CSV: ${e.message}")
            return@withContext "Error reading statistics."
        }
    }
}

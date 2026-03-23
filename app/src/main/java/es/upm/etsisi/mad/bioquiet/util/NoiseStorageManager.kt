package es.upm.etsisi.mad.bioquiet.util

import android.content.Context
import android.util.Log
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

    fun getStatsSummary(): String {
        if (!file.exists()) return "No noise data registered yet."

        return try {
            val lines = file.readLines()
            if (lines.size <= 1) return "Not enough data registered."

            var maxDb = 0
            var sumDb = 0
            var count = 0

            // Skip line 0 because it is the header (Date,ZEPA_Zone,Decibels)
            for (i in 1 until lines.size) {
                val parts = lines[i].split(",")
                if (parts.size >= 3) {
                    // Get the decibels part and convert it to a number
                    val db = parts[2].trim().toIntOrNull() ?: 0
                    if (db > maxDb) maxDb = db
                    sumDb += db
                    count++
                }
            }

            if (count == 0) return "No valid data found."

            val avgDb = sumDb / count

            // Prepare the final text to be displayed on screen
            val feedback = if (avgDb > 60) "Warning! High average noise level." else "Good job keeping it quiet!"

            "ZEPA Noise Summary:\n\n" +
                    "Total records: $count\n" +
                    "Max Noise: $maxDb dB\n" +
                    "Average Noise: $avgDb dB\n\n" +
                    feedback

        } catch (e: Exception) {
            Log.e("NoiseStorage", "Error reading CSV: ${e.message}")
            "Error reading statistics."
        }
    }
}

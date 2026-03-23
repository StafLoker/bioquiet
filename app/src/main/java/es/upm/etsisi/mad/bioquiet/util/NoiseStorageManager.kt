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
}

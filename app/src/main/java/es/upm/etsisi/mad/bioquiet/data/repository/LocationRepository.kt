package es.upm.etsisi.mad.bioquiet.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Abstracts [LocationManager] updates into a [StateFlow].
 */
class LocationRepository(private val locationManager: LocationManager) {

    companion object {
        private const val LOG_TAG = "LocationRepository"
        private const val UPDATE_INTERVAL_MS = 5_000L
        private const val UPDATE_DISTANCE_M = 5f
    }

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val listener = LocationListener { loc ->
        Log.d(LOG_TAG, "Location updated: [${loc.latitude}, ${loc.longitude}]")
        _location.value = loc
    }

    fun hasPermission(context: android.content.Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startUpdates(context: android.content.Context) {
        if (!hasPermission(context)) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.requestLocationUpdates(
                    provider,
                    UPDATE_INTERVAL_MS,
                    UPDATE_DISTANCE_M,
                    listener
                )
            }
        }
        Log.d(LOG_TAG, "Location updates started.")
    }

    fun stopUpdates() {
        locationManager.removeUpdates(listener)
        Log.d(LOG_TAG, "Location updates stopped.")
    }
}

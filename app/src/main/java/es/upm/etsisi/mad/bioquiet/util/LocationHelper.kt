package es.upm.etsisi.mad.bioquiet.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity

class LocationHelper(
    private val activity: FragmentActivity,
    private val locationManager: LocationManager
) {
    companion object {
        const val LOG_TAG = "LocationHelper"
        const val UPDATE_INTERVAL_MS = 5000L
        const val UPDATE_DISTANCE_M = 5f
    }

    fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    activity, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startUpdates(listener: LocationListener) {
        if (!hasPermission()) return
        Log.d(LOG_TAG, "Starting location updates")
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
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Location? {
        if (!hasPermission()) return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers.mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }
}

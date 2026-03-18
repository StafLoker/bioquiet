package es.upm.etsisi.mad.bioquiet

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import es.upm.etsisi.mad.bioquiet.components.buildUserLocationOverlay
import es.upm.etsisi.mad.bioquiet.util.LocationHelper
import es.upm.etsisi.mad.bioquiet.util.NoiseMonitor
import es.upm.etsisi.mad.bioquiet.util.ZepaMapManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var map: MapView
    private lateinit var locationHelper: LocationHelper
    private lateinit var zepaMapManager: ZepaMapManager
    private lateinit var noiseMonitor: NoiseMonitor
    private var currentUserPoint: GeoPoint? = null

    companion object {
        const val LOG_TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOG_TAG, "Activity is being created")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Location
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationHelper = LocationHelper(this, locationManager)

        // OSM config
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance()
            .load(applicationContext, getSharedPreferences("osm", MODE_PRIVATE))

        // Map setup
        Log.d(LOG_TAG, "Setup map")
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setBuiltInZoomControls(false)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)

        if (locationHelper.hasPermission()) {
            locationHelper.startUpdates(this)
            setupLocationOnMap()
        } else {
            locationHelper.requestPermissions()
        }

        // ZepaMapManager
        val statusDot = findViewById<View>(R.id.statusDot)
        val loadingIndicator = findViewById<ProgressBar>(R.id.loadingIndicator)
        zepaMapManager = ZepaMapManager(
            map = map,
            statusDot = statusDot,
            loadingIndicator = loadingIndicator,
            lifecycleScope = lifecycleScope,
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        )
        zepaMapManager.fetchAndDraw()

        map.addMapListener(object : MapListener {
            override fun onScroll(p0: ScrollEvent?): Boolean {
                zepaMapManager.fetchAndDraw()
                return true
            }

            override fun onZoom(p0: ZoomEvent?): Boolean {
                zepaMapManager.fetchAndDraw()
                return true
            }
        })

        // FAB
        Log.d(LOG_TAG, "Setup my location button")
        findViewById<FloatingActionButton>(R.id.fabMyLocation).setOnClickListener {
            if (currentUserPoint != null) {
                map.controller.animateTo(currentUserPoint)
            } else {
                Toast.makeText(this, "No podemos encontrar tu localizacion", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        // NoiseMonitor
        noiseMonitor = NoiseMonitor(
            activity = this,
            noiseCard = findViewById(R.id.noiseCard),
            noiseText = findViewById(R.id.noiseLevel),
            lifecycleScope = lifecycleScope,
            getCurrentZepa = { zepaMapManager.getCurrentZepa(currentUserPoint) },
            onWarning = { zepa ->
                Snackbar.make(
                    findViewById(R.id.main),
                    "Estás en ${zepa.name}. ¡Por favor, reduce el ruido!",
                    Snackbar.LENGTH_LONG
                ).show()
            },
            onPermissionDenied = {
                Toast.makeText(this, "Sin permiso de micrófono no se puede monitorizar el ruido", Toast.LENGTH_LONG).show()
            },
            getNoiseLevel = { _, db -> getString(R.string.noise_level_db, db) }
        )
        noiseMonitor.start()
    }

    private fun setupLocationOnMap() {
        Log.d(LOG_TAG, "Fetch user start location")
        val bundle = intent.getBundleExtra("locationBundle")
        @Suppress("DEPRECATION")
        val location: Location? =
            bundle?.getParcelable("location") ?: locationHelper.getLastKnownLocation()
        if (location != null) {
            Log.d(LOG_TAG, "Start location: [${location.latitude}][${location.longitude}]")
            currentUserPoint = GeoPoint(location.latitude, location.longitude)
            map.controller.setCenter(currentUserPoint)
        } else {
            Log.e(LOG_TAG, "Failed to obtain user location")
            Toast.makeText(this, "No podemos encontrar tu localizacion", Toast.LENGTH_LONG).show()
        }
        map.overlays.add(buildUserLocationOverlay(this, map))
        map.invalidate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LocationHelper.PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationHelper.startUpdates(this)
                    setupLocationOnMap()
                }
            }
            NoiseMonitor.PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(LOG_TAG, "Audio permission granted, starting noise monitor")
                    noiseMonitor.start()
                } else {
                    Log.w(LOG_TAG, "Audio permission denied")
                    noiseMonitor.onPermissionDenied()
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d(LOG_TAG, "Location updated: [${location.latitude}][${location.longitude}]")
        currentUserPoint = GeoPoint(location.latitude, location.longitude)
        map.controller.animateTo(currentUserPoint)
    }

    override fun onDestroy() {
        super.onDestroy()
        noiseMonitor.stop()
    }
}

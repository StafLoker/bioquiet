package es.upm.etsisi.mad.bioquiet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import java.io.File

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var map: MapView
    private lateinit var locationHelper: LocationHelper
    private lateinit var zepaMapManager: ZepaMapManager
    private lateinit var noiseMonitor: NoiseMonitor
    private var currentUserPoint: GeoPoint? = null
    private var locationOverlayAdded = false

    companion object {
        const val LOG_TAG = "MainActivity"
        const val PERMISSION_CODE = 1
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
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
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationHelper = LocationHelper(this, locationManager)

        // OSM config
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        osmConfig.osmdroidBasePath = cacheDir
        osmConfig.osmdroidTileCache = File(cacheDir, "osmdroid")
        osmConfig.load(applicationContext, getSharedPreferences("osm", MODE_PRIVATE))

        // Map setup
        Log.d(LOG_TAG, "Setup map")
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)

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
            getNoiseLevel = { _, db -> getString(R.string.noise_level_db, db) }
        )

        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            onAllPermissionsGranted()
        } else {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_CODE
            )
        }
    }

    private fun onAllPermissionsGranted() {
        if (locationHelper.hasPermission()) {
            locationHelper.startUpdates(this)
            setupLocationOnMap()
        }
        if (noiseMonitor.hasPermission()) {
            noiseMonitor.start()
        }
    }

    private fun setupLocationOnMap() {
        Log.d(LOG_TAG, "Fetch user start location")
        val bundle = intent.getBundleExtra("locationBundle")

        val bundleLocation: Location? = bundle?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable("location", Location::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable("location")
            }
        }
        val location: Location? = bundleLocation ?: locationHelper.getLastKnownLocation()
        if (location != null) {
            Log.d(LOG_TAG, "Start location: [${location.latitude}][${location.longitude}]")
            currentUserPoint = GeoPoint(location.latitude, location.longitude)
            map.controller.setCenter(currentUserPoint)
        } else {
            Log.w(LOG_TAG, "No cached location yet, waiting for first GPS fix")
        }
        if (!locationOverlayAdded) {
            map.overlays.add(buildUserLocationOverlay(this, map))
            locationOverlayAdded = true
        }
        map.invalidate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_CODE) return

        val denied = mutableListOf<String>()
        permissions.forEachIndexed { i, perm ->
            if (perm != null && grantResults.getOrNull(i) != PackageManager.PERMISSION_GRANTED) {
                denied.add(perm)
            }
        }

        onAllPermissionsGranted()

        if (denied.isNotEmpty()) {
            Log.w(LOG_TAG, "Permissions denied: $denied")
            val permanentlyDenied = denied.filter {
                !ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }
            if (permanentlyDenied.isNotEmpty()) {
                val names = permanentlyDenied.map { perm ->
                    when (perm) {
                        Manifest.permission.RECORD_AUDIO -> "Micrófono"
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION -> "Ubicación"

                        else -> perm
                    }
                }.distinct().joinToString(", ")
                AlertDialog.Builder(this)
                    .setTitle("Permisos necesarios")
                    .setMessage("La app necesita acceso a: $names. Actívalos desde Ajustes.")
                    .setPositiveButton("Ir a Ajustes") { _, _ ->
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        })
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d(LOG_TAG, "Location updated: [${location.latitude}][${location.longitude}]")
        val wasNull = currentUserPoint == null
        currentUserPoint = GeoPoint(location.latitude, location.longitude)
        if (wasNull) {
            map.controller.setCenter(currentUserPoint)
        } else {
            map.controller.animateTo(currentUserPoint)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        noiseMonitor.stop()
    }
}

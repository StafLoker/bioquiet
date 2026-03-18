package es.upm.etsisi.mad.bioquiet

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.api.IGeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import android.Manifest
import es.upm.etsisi.mad.bioquiet.model.GeoJsonGeometry
import es.upm.etsisi.mad.bioquiet.data.remote.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.json.JSONArray
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.overlay.Polygon

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var map: MapView
    private lateinit var locationManager: LocationManager
    private var fetchJob: Job? = null

    companion object {
        const val LOG_TAG: String = "MainActivity"
        const val LOCATION_PERMISSION_CODE: Int = 2
        const val LOCATION_UPDATE_INTERVAL_MS: Long = 5000L
        const val LOCATION_UPDATE_DISTANCE_M: Float = 5f

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

        Log.d(LOG_TAG, "Check and request location permissions")
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_CODE
            )
        } else {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_INTERVAL_MS,
                LOCATION_UPDATE_DISTANCE_M,
                this
            )
        }

        Log.d(LOG_TAG, "Setup osm")
        Configuration.getInstance().userAgentValue = "es.upm.etsisi.mad.bioquiet"
        Configuration.getInstance()
            .load(applicationContext, getSharedPreferences("osm", MODE_PRIVATE))

        Log.d(LOG_TAG, "Fetch user start location")
        val bundle = intent.getBundleExtra("locationBundle")
        val location: Location? = bundle?.getParcelable("location")

        val startUserPoint: IGeoPoint? = if (location != null) {
            Log.d(
                LOG_TAG,
                "Start user location point at: [${location.altitude}][${location.latitude}][${location.longitude}]"
            )
            GeoPoint(location.latitude, location.longitude)
        } else {
            Log.e(LOG_TAG, "Failed to obtain user location")
            Toast.makeText(this, "No podemos encontrar tu localizacion", Toast.LENGTH_LONG).show()
            null
        }

        Log.d(LOG_TAG, "Setup map")
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.controller.setZoom(10.0)
        map.controller.setCenter(startUserPoint)

        map.addMapListener(object : MapListener {
            override fun onScroll(p0: ScrollEvent?): Boolean {
                fetchAndDrawZepas()
                return true
            }

            override fun onZoom(p0: ZoomEvent?): Boolean {
                fetchAndDrawZepas()
                return true
            }
        })

        Log.d(LOG_TAG, "Setup my location button")
        val fabMyLocation = findViewById<FloatingActionButton>(R.id.fabMyLocation)
        fabMyLocation.setOnClickListener {
            if (startUserPoint != null) {
                map.controller.animateTo(startUserPoint)
            } else {
                Toast.makeText(this, "No podemos encontrar tu localizacion", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        LOCATION_UPDATE_INTERVAL_MS,
                        LOCATION_UPDATE_DISTANCE_M,
                        this
                    )
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d(LOG_TAG, "Location updated: [${location.altitude}][${location.latitude}][${location.longitude}]")
    }

    fun fetchAndDrawZepas() {
        fetchJob?.cancel()
        fetchJob = lifecycleScope.launch {
            delay(300)
            val bbox = map.boundingBox
            val zepaList = ApiClient.fetchNearsZepa(
                bbox.lonWest, bbox.latSouth,
                bbox.lonEast, bbox.latNorth
            )
            map.overlays.removeAll(map.overlays.filterIsInstance<Polygon>())
            for (zepa in zepaList) {
                drawZepa(zepa.geometry, zepa.name)
            }
            map.invalidate()
        }
    }

    private fun drawZepa(zone: GeoJsonGeometry, zoneName: String) {
        val coords = zone.coordinates as? JSONArray ?: return

        val rings: List<JSONArray> = when (zone.type) {
            "Polygon" -> listOf(coords.getJSONArray(0))
            "MultiPolygon" -> (0 until coords.length()).map { i ->
                coords.getJSONArray(i).getJSONArray(0)
            }
            else -> return
        }

        for (ring in rings) {
            val geoPoints = (0 until ring.length()).map { j ->
                val point = ring.getJSONArray(j)
                GeoPoint(point.getDouble(1), point.getDouble(0))
            }
            val polygon = Polygon().apply {
                points = geoPoints
                fillPaint.color = 0x3300AA00
                outlinePaint.color = 0xFF006400.toInt()
                outlinePaint.strokeWidth = 2f
                title = zoneName
            }
            map.overlays.add(polygon)
        }
    }
}
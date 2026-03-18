package es.upm.etsisi.mad.bioquiet

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import es.upm.etsisi.mad.bioquiet.data.remote.ApiClient
import es.upm.etsisi.mad.bioquiet.model.Zepa
import es.upm.etsisi.mad.bioquiet.util.amplitude2db
import es.upm.etsisi.mad.bioquiet.util.isPointInPolygon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var map: MapView
    private lateinit var locationManager: LocationManager

    private var recorder: MediaRecorder? = null
    private var fetchJob: Job? = null
    private var noiseJob: Job? = null
    private var currentUserPoint: GeoPoint? = null
    private val zepaPolygons: MutableList<Pair<Polygon, Zepa>> = mutableListOf()
    private var alertShown = false
    private var isRecording = false

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
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

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
            ?: getLastKnownLocation()

        if (location != null) {
            Log.d(
                LOG_TAG,
                "Start user location point at: [${location.altitude}][${location.latitude}][${location.longitude}]"
            )
            currentUserPoint = GeoPoint(location.latitude, location.longitude)
        } else {
            Log.e(LOG_TAG, "Failed to obtain user location")
            Toast.makeText(this, "No podemos encontrar tu localizacion", Toast.LENGTH_LONG).show()
        }

        Log.d(LOG_TAG, "Setup map")
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.controller.setZoom(15.0)
        map.controller.setCenter(currentUserPoint)

        val density = resources.displayMetrics.density
        val sizePx = (16 * density).toInt()
        val borderPx = (2 * density)
        val dot = createBitmap(sizePx, sizePx)
        Canvas(dot).apply {
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt() }
            val r = sizePx / 2f
            drawCircle(r, r, r, white)
            drawCircle(r, r, r - borderPx, blue)
        }

        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation()
        locationOverlay.setPersonIcon(dot)
        locationOverlay.setPersonAnchor(0.5f, 0.5f)
        locationOverlay.setDirectionIcon(dot)
        locationOverlay.setDirectionAnchor(0.5f, 0.5f)
        map.overlays.add(locationOverlay)

        fetchAndDrawZepas()

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
            if (currentUserPoint != null) {
                map.controller.animateTo(currentUserPoint)
            } else {
                Toast.makeText(this, "No podemos encontrar tu localizacion", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        startNoiseMonitoring()
    }

    private fun startRecorder() {
        if (isRecording) return
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile("/dev/null")
            }
            recorder?.prepare()
            recorder?.start()
            isRecording = true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to start recorder (no microphone?): ${e.message}")
            recorder?.release()
            recorder = null
        }
    }

    private fun stopRecorder() {
        if (!isRecording) return
        recorder?.stop()
        recorder?.release()
        recorder = null
        isRecording = false
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
        Log.d(
            LOG_TAG,
            "Location updated: [${location.altitude}][${location.latitude}][${location.longitude}]"
        )
        currentUserPoint = GeoPoint(location.latitude, location.longitude)
        map.controller.animateTo(currentUserPoint)
    }

    private fun fetchAndDrawZepas() {
        val loadingIndicator = findViewById<ProgressBar>(R.id.loadingIndicator)
        fetchJob?.cancel()
        fetchJob = lifecycleScope.launch {
            delay(300)
            loadingIndicator.visibility = View.VISIBLE
            val bbox = map.boundingBox
            Log.d(LOG_TAG, "Fetching ZEPAs for bbox: [${bbox.latSouth}, ${bbox.lonWest}] - [${bbox.latNorth}, ${bbox.lonEast}]")
            val zepaList = ApiClient.fetchNearsZepa(
                bbox.lonWest, bbox.latSouth,
                bbox.lonEast, bbox.latNorth
            )
            Log.d(LOG_TAG, "Drawing ${zepaList.size} ZEPAs on map")
            map.overlays.removeAll(map.overlays.filterIsInstance<Polygon>())
            zepaPolygons.clear()
            for (zepa in zepaList) {
                drawZepa(zepa)
            }
            map.invalidate()
            loadingIndicator.visibility = View.GONE
        }
    }

    private fun drawZepa(zepa: Zepa) {
        val coords = zepa.geometry.coordinates as? JSONArray ?: return

        val rings: List<JSONArray> = when (zepa.geometry.type) {
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
                title = zepa.name
            }
            map.overlays.add(polygon)
            zepaPolygons.add(Pair(polygon, zepa))
        }
    }

    private fun getCurrentZepa(): Zepa? {
        val point = currentUserPoint ?: return null
        for((polygon, zepa) in zepaPolygons) {
            if(isPointInPolygon(point, polygon)) return zepa
        }
        return null
    }

    private fun startNoiseMonitoring() {
        val noiseCard = findViewById<MaterialCardView>(R.id.noiseCard)
        val noiseText = findViewById<TextView>(R.id.noiseLevel)
        noiseJob = lifecycleScope.launch {
            while (true) {
                delay(1000)
                val zepa = getCurrentZepa()
                if (zepa == null) {
                    Log.d(LOG_TAG, "User outside ZEPA, stopping recorder")
                    stopRecorder()
                    noiseCard.visibility = View.GONE
                    alertShown = false
                    continue
                }
                startRecorder()
                val db = amplitude2db(recorder?.maxAmplitude?.toDouble() ?: 0.0)
                Log.d(LOG_TAG, "Inside ZEPA '${zepa.name}': ${db.toInt()} dB (safe=${zepa.noiseThresholds.dbSafe}, warning=${zepa.noiseThresholds.dbWarning})")

                noiseCard.visibility = View.VISIBLE
                noiseText.text = getString(R.string.noise_level_db, db.toInt())

                when {
                    db >= zepa.noiseThresholds.dbWarning -> {
                        Log.w(LOG_TAG, "Noise level exceeded warning threshold in '${zepa.name}'")
                        noiseCard.setCardBackgroundColor(Color.RED)
                        if (!alertShown) {
                            alertShown = true
                            Snackbar.make(
                                findViewById(R.id.main),
                                "Estás en ${zepa.name}. ¡Por favor, reduce el ruido!",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                    db >= zepa.noiseThresholds.dbSafe -> {
                        alertShown = false
                        noiseCard.setCardBackgroundColor(Color.YELLOW)
                    }
                    else -> {
                        alertShown = false
                        noiseCard.setCardBackgroundColor(Color.GREEN)
                    }
                }
            }
        }
    }

    private fun getLastKnownLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return null
        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }

    override fun onDestroy() {
        super.onDestroy()
        noiseJob?.cancel()
        stopRecorder()
    }
}

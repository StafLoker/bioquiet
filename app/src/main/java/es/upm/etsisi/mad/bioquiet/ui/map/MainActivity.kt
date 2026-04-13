package es.upm.etsisi.mad.bioquiet.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import es.upm.etsisi.mad.bioquiet.R
import es.upm.etsisi.mad.bioquiet.core.map.buildUserLocationOverlay
import es.upm.etsisi.mad.bioquiet.core.map.ZepaMapManager
import es.upm.etsisi.mad.bioquiet.core.navigation.NavigationManager
import es.upm.etsisi.mad.bioquiet.core.noise.NoiseLevel
import es.upm.etsisi.mad.bioquiet.core.noise.NoiseMonitor
import es.upm.etsisi.mad.bioquiet.data.repository.LocationRepository
import es.upm.etsisi.mad.bioquiet.data.repository.NoiseRepository
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOG_TAG = "MainActivity"
        private const val PERMISSION_CODE = 1
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private val viewModel: MainViewModel by viewModels()

    private lateinit var map: MapView
    private lateinit var zepaMapManager: ZepaMapManager
    private lateinit var locationRepository: LocationRepository
    private lateinit var bottomNavigation: com.google.android.material.bottomnavigation.BottomNavigationView

    private var locationOverlayAdded = false
    private var firstLocationSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOG_TAG, "onCreate")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupMap()
        setupNavigation()
        setupFab()
        observeState()
        observeEvents()
        checkPermissions()
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private fun setupMap() {
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir
            osmdroidTileCache = File(cacheDir, "osmdroid")
            load(applicationContext, getSharedPreferences("osm", MODE_PRIVATE))
        }

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)

        zepaMapManager = ZepaMapManager(
            map = map,
            statusDot = findViewById(R.id.statusDot),
            loadingIndicator = findViewById(R.id.loadingIndicator),
            lifecycleScope = lifecycleScope,
            onZepaListUpdated = {
                viewModel.onZepaListUpdated { point -> zepaMapManager.findZepaAt(point) }
            },
            onError = { viewModel.onMapError() }
        )
        zepaMapManager.fetchAndDraw()

        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { zepaMapManager.fetchAndDraw(); return true }
            override fun onZoom(event: ZoomEvent?): Boolean { zepaMapManager.fetchAndDraw(); return true }
        })
    }

    private fun setupNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        NavigationManager(
            context = this,
            bottomNavigationView = bottomNavigation,
            selectedItemId = R.id.nav_map
        )
    }

    // Called when activity is reused via FLAG_ACTIVITY_SINGLE_TOP
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        bottomNavigation.selectedItemId = R.id.nav_map
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabMyLocation).setOnClickListener {
            val point = viewModel.uiState.value.userLocation
            if (point != null) {
                map.controller.animateTo(point)
            } else {
                Toast.makeText(this, "No podemos encontrar tu localización", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Observers ───────────────────────────────────────────────────────────

    private fun observeState() {
        val noiseCard = findViewById<MaterialCardView>(R.id.noiseCard)
        val noiseText = findViewById<TextView>(R.id.noiseLevel)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderLocation(state.userLocation)
                    renderNoiseCard(noiseCard, noiseText, state)
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is MainEvent.ShowWarning ->
                            Snackbar.make(
                                findViewById(R.id.main),
                                "Estás en ${event.zepaName}. ¡Por favor, reduce el ruido!",
                                Snackbar.LENGTH_LONG
                            ).show()
                        is MainEvent.ShowError ->
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ─── Render helpers ──────────────────────────────────────────────────────

    private fun renderLocation(point: GeoPoint?) {
        point ?: return
        if (!locationOverlayAdded) {
            map.overlays.add(buildUserLocationOverlay(this, map))
            locationOverlayAdded = true
        }
        if (!firstLocationSet) {
            map.controller.setCenter(point)
            firstLocationSet = true
        } else {
            map.controller.animateTo(point)
        }
    }

    private fun renderNoiseCard(card: MaterialCardView, text: TextView, state: MainUiState) {
        card.visibility = if (state.noiseCardVisible) View.VISIBLE else View.GONE
        text.text = getString(R.string.noise_level_db, state.noiseDb.toInt())
        card.setCardBackgroundColor(
            when (state.noiseLevel) {
                NoiseLevel.WARNING -> Color.RED
                NoiseLevel.CAUTION -> Color.YELLOW
                NoiseLevel.SAFE    -> Color.GREEN
            }
        )
    }

    // ─── Permissions ─────────────────────────────────────────────────────────

    private fun checkPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) onAllPermissionsGranted()
        else ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_CODE)
    }

    private fun onAllPermissionsGranted() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationRepository = LocationRepository(locationManager)

        if (locationRepository.hasPermission(this)) {
            locationRepository.startUpdates(this)
            extractBundleLocation()?.let { loc ->
                viewModel.onInitialLocation(GeoPoint(loc.latitude, loc.longitude))
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    locationRepository.location.collect { loc ->
                        loc ?: return@collect
                        val point = GeoPoint(loc.latitude, loc.longitude)
                        viewModel.onLocationChanged(point) { zepaMapManager.findZepaAt(it) }
                    }
                }
            }
            if (!locationOverlayAdded) {
                map.overlays.add(buildUserLocationOverlay(this, map))
                locationOverlayAdded = true
                map.invalidate()
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.initialize(
                monitor = NoiseMonitor(cacheDir.absolutePath),
                repository = NoiseRepository(this)
            )
            viewModel.startNoiseMonitor()
        }
    }

    private fun extractBundleLocation(): Location? {
        val bundle = intent.getBundleExtra("locationBundle") ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable("location", Location::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelable("location")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_CODE) return

        val denied = permissions.filterIndexed { i, _ ->
            grantResults.getOrNull(i) != PackageManager.PERMISSION_GRANTED
        }.filterNotNull()

        onAllPermissionsGranted()

        if (denied.isNotEmpty()) {
            Log.w(LOG_TAG, "Permissions denied: $denied")
            val permanentlyDenied = denied.filter { !ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
            if (permanentlyDenied.isNotEmpty()) showPermissionSettingsDialog(permanentlyDenied)
        }
    }

    private fun showPermissionSettingsDialog(denied: List<String>) {
        val names = denied.map { perm ->
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
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        viewModel.pauseNoiseMonitor()
        map.onPause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeNoiseMonitor()
        map.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationRepository.isInitialized) locationRepository.stopUpdates()
    }
}

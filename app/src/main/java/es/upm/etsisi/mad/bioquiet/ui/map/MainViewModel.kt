package es.upm.etsisi.mad.bioquiet.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.upm.etsisi.mad.bioquiet.core.noise.NoiseLevel
import es.upm.etsisi.mad.bioquiet.core.noise.NoiseMonitor
import es.upm.etsisi.mad.bioquiet.core.noise.NoiseTracker
import es.upm.etsisi.mad.bioquiet.data.repository.NoiseRepository
import es.upm.etsisi.mad.bioquiet.model.Zepa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

data class MainUiState(
    val userLocation: GeoPoint? = null,
    val currentZepa: Zepa? = null,
    val noiseDb: Double = 0.0,
    val noiseLevel: NoiseLevel = NoiseLevel.SAFE,
    val noiseCardVisible: Boolean = false
)

sealed class MainEvent {
    data class ShowWarning(val zepaName: String) : MainEvent()
    data class ShowError(val message: String) : MainEvent()
}

class MainViewModel : ViewModel() {

    companion object {
        private const val LOG_TAG = "MainViewModel"
        private const val NOISE_POLL_INTERVAL_MS = 1_000L
        private const val NOISE_START_DELAY_MS = 500L
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainEvent>()
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    private val noiseTracker = NoiseTracker()
    private var noiseMonitor: NoiseMonitor? = null
    private var noiseRepository: NoiseRepository? = null
    private var noiseJob: Job? = null
    private var alertShown = false

    // ─── Initialisation ─────────────────────────────────────────────────────

    /**
     * Called once from the View after permissions are granted.
     * Provides platform dependencies without leaking Activity into the ViewModel.
     */
    fun initialize(monitor: NoiseMonitor, repository: NoiseRepository) {
        noiseMonitor = monitor
        noiseRepository = repository
    }

    // ─── Location ───────────────────────────────────────────────────────────

    fun onLocationChanged(point: GeoPoint, currentZepaFinder: (GeoPoint) -> Zepa?) {
        _uiState.update { it.copy(userLocation = point, currentZepa = currentZepaFinder(point)) }
    }

    fun onInitialLocation(point: GeoPoint) {
        _uiState.update { it.copy(userLocation = point) }
    }

    // ─── ZEPA ────────────────────────────────────────────────────────────────

    fun onZepaListUpdated(currentZepaFinder: (GeoPoint) -> Zepa?) {
        val location = _uiState.value.userLocation ?: return
        _uiState.update { it.copy(currentZepa = currentZepaFinder(location)) }
    }

    fun onMapError() {
        viewModelScope.launch {
            _events.emit(MainEvent.ShowError("Error al conectar con el servidor"))
        }
    }

    // ─── Noise monitoring ───────────────────────────────────────────────────

    fun startNoiseMonitor() {
        noiseJob?.cancel()
        noiseJob = viewModelScope.launch(Dispatchers.IO) {
            delay(NOISE_START_DELAY_MS)
            while (isActive) {
                delay(NOISE_POLL_INTERVAL_MS)
                tick()
            }
        }
    }

    private suspend fun tick() {
        val zepa = withContext(Dispatchers.Main) { _uiState.value.currentZepa }

        if (zepa == null) {
            noiseMonitor?.stop()
            noiseTracker.reset()
            withContext(Dispatchers.Main) {
                alertShown = false
                _uiState.update { it.copy(noiseCardVisible = false, noiseDb = 0.0) }
            }
            return
        }

        if (noiseMonitor?.isCapturing == false) noiseMonitor?.start()

        val db = noiseMonitor?.readDb() ?: 0.0
        Log.d(LOG_TAG, "Noise in '${zepa.name}': ${db.toInt()} dB")

        noiseRepository?.saveRecord(zepa.name, db.toInt())

        val level = noiseTracker.record(db, zepa.noiseThresholds)
        val sustained = noiseTracker.isSustainedWarning

        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(noiseDb = db, noiseLevel = level, noiseCardVisible = true) }
            if (sustained && !alertShown) {
                alertShown = true
                _events.emit(MainEvent.ShowWarning(zepa.name))
            }
            if (!sustained) alertShown = false
        }
    }

    fun pauseNoiseMonitor() {
        noiseJob?.cancel()
        noiseJob = null
        noiseMonitor?.stop()
        noiseTracker.reset()
        alertShown = false
    }

    fun resumeNoiseMonitor() {
        if (noiseMonitor?.isCapturing == false) startNoiseMonitor()
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        pauseNoiseMonitor()
    }
}

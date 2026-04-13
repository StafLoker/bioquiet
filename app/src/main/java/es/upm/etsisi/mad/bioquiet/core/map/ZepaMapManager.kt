package es.upm.etsisi.mad.bioquiet.core.map

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.lifecycle.LifecycleCoroutineScope
import es.upm.etsisi.mad.bioquiet.core.map.ZepaLabelOverlay
import es.upm.etsisi.mad.bioquiet.data.remote.ApiClient
import es.upm.etsisi.mad.bioquiet.model.Zepa
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

class ZepaMapManager(
    private val map: MapView,
    private val statusDot: View,
    private val loadingIndicator: ProgressBar,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onZepaListUpdated: (List<Zepa>) -> Unit,
    private val onError: () -> Unit
) {
    companion object {
        private const val LOG_TAG = "ZepaMapManager"
        private const val FETCH_DEBOUNCE_MS = 300L
    }

    // Internal map from drawn Polygon → its domain Zepa, used only for hit-testing inside this class
    private val polygonZepaMap: MutableMap<Polygon, Zepa> = mutableMapOf()
    private var fetchJob: Job? = null

    fun fetchAndDraw() {
        fetchJob?.cancel()
        fetchJob = lifecycleScope.launch {
            delay(FETCH_DEBOUNCE_MS)
            setLoadingState(true)
            val bbox = map.boundingBox
            Log.d(
                LOG_TAG,
                "Fetching ZEPAs for bbox: [${bbox.latSouth}, ${bbox.lonWest}] - [${bbox.latNorth}, ${bbox.lonEast}]"
            )
            try {
                val zepaList = ApiClient.fetchNearsZepa(
                    bbox.lonWest,
                    bbox.latSouth,
                    bbox.lonEast,
                    bbox.latNorth
                )
                Log.d(LOG_TAG, "Fetched ${zepaList.size} ZEPAs")
                redrawPolygons(zepaList)
                statusDot.backgroundTintList = ColorStateList.valueOf(0xFF4CAF50.toInt())
                onZepaListUpdated(zepaList)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to fetch ZEPAs: ${e.message}")
                statusDot.backgroundTintList = ColorStateList.valueOf(Color.RED)
                onError()
            } finally {
                setLoadingState(false)
            }
        }
    }

    /**
     * Returns the [Zepa] whose polygon contains [point], or null if outside all zones.
     * Uses ray-casting algorithm. Stays here because only this class owns the drawn polygons.
     */
    fun findZepaAt(point: GeoPoint): Zepa? =
        polygonZepaMap.entries.firstOrNull { (polygon, _) ->
            isPointInPolygon(
                point,
                polygon
            )
        }?.value

    private fun isPointInPolygon(point: GeoPoint, polygon: Polygon): Boolean {
        val pts = polygon.points
        if (pts.size < 3) return false
        var inside = false
        var j = pts.size - 1
        for (i in pts.indices) {
            val xi = pts[i].longitude
            val yi = pts[i].latitude
            val xj = pts[j].longitude
            val yj = pts[j].latitude
            if ((yi > point.latitude) != (yj > point.latitude) &&
                point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi
            ) inside = !inside
            j = i
        }
        return inside
    }

    private fun redrawPolygons(zepaList: List<Zepa>) {
        clearOverlays()
        zepaList.forEach { zepa -> drawZepa(zepa) }
        map.invalidate()
    }

    private fun clearOverlays() {
        map.overlays.removeAll(map.overlays.filterIsInstance<Polygon>().toSet())
        map.overlays.removeAll(map.overlays.filterIsInstance<ZepaLabelOverlay>().toSet())
        polygonZepaMap.clear()
    }

    private fun drawZepa(zepa: Zepa) {
        val coords = zepa.geometry.coordinates as? JSONArray ?: return
        val rings: List<JSONArray> = when (zepa.geometry.type) {
            "Polygon" -> listOf(coords.getJSONArray(0))
            "MultiPolygon" -> (0 until coords.length()).map {
                coords.getJSONArray(it).getJSONArray(0)
            }

            else -> return
        }

        val allPoints = mutableListOf<GeoPoint>()
        for (ring in rings) {
            val geoPoints = (0 until ring.length()).map { j ->
                val pt = ring.getJSONArray(j)
                GeoPoint(pt.getDouble(1), pt.getDouble(0))
            }
            val polygon = Polygon().apply {
                points = geoPoints
                fillPaint.color = 0x3300AA00
                outlinePaint.color = 0xFF006400.toInt()
                outlinePaint.strokeWidth = 2f
                title = zepa.name
                infoWindow = null
            }
            map.overlays.add(polygon)
            polygonZepaMap[polygon] = zepa
            allPoints.addAll(geoPoints)
        }

        if (allPoints.isNotEmpty()) {
            val center = GeoPoint(
                allPoints.map { it.latitude }.average(),
                allPoints.map { it.longitude }.average()
            )
            map.overlays.add(ZepaLabelOverlay(center, zepa.name))
        }
    }

    private fun setLoadingState(loading: Boolean) {
        loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        statusDot.visibility = if (loading) View.GONE else View.VISIBLE
    }
}

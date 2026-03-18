package es.upm.etsisi.mad.bioquiet.util

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.lifecycle.LifecycleCoroutineScope
import es.upm.etsisi.mad.bioquiet.components.ZepaLabelOverlay
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

/**
 * Determines whether a geographic point is inside a polygon using the ray-casting algorithm.
 *
 * @param point the geographic point to test
 * @param polygon the polygon to check against
 * @return true if the point is inside the polygon, false otherwise
 */
private fun isPointInPolygon(point: GeoPoint, polygon: Polygon): Boolean {
    var inside = false
    val x = point.longitude
    val y = point.latitude
    var j = polygon.points.size - 1

    for (i in polygon.points.indices) {
        val xi = polygon.points[i].longitude
        val yi = polygon.points[i].latitude
        val xj = polygon.points[j].longitude
        val yj = polygon.points[j].latitude

        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
            inside = !inside
        }
        j = i
    }
    return inside
}

class ZepaMapManager(
    private val map: MapView,
    private val statusDot: View,
    private val loadingIndicator: ProgressBar,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onError: (String) -> Unit
) {
    companion object {
        const val LOG_TAG = "ZepaMapManager"
    }

    private var fetchJob: Job? = null
    private val zepaPolygons: MutableList<Pair<Polygon, Zepa>> = mutableListOf()

    fun fetchAndDraw() {
        fetchJob?.cancel()
        fetchJob = lifecycleScope.launch {
            delay(300)
            statusDot.visibility = View.GONE
            loadingIndicator.visibility = View.VISIBLE
            val bbox = map.boundingBox
            Log.d(
                LOG_TAG,
                "Fetching ZEPAs for bbox: [${bbox.latSouth}, ${bbox.lonWest}] - [${bbox.latNorth}, ${bbox.lonEast}]"
            )
            try {
                val zepaList = ApiClient.fetchNearsZepa(
                    bbox.lonWest, bbox.latSouth,
                    bbox.lonEast, bbox.latNorth
                )
                Log.d(LOG_TAG, "Drawing ${zepaList.size} ZEPAs on map")
                statusDot.backgroundTintList = ColorStateList.valueOf(0xFF4CAF50.toInt())
                clearOverlays()
                for (zepa in zepaList) {
                    drawZepa(zepa)
                }
                map.invalidate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to fetch ZEPAs: ${e.message}")
                statusDot.backgroundTintList = ColorStateList.valueOf(Color.RED)
                onError("Error al conectar con el servidor")
            } finally {
                loadingIndicator.visibility = View.GONE
                statusDot.visibility = View.VISIBLE
            }
        }
    }

    fun getCurrentZepa(userPoint: GeoPoint?): Zepa? {
        val point = userPoint ?: return null
        for ((polygon, zepa) in zepaPolygons) {
            if (isPointInPolygon(point, polygon)) return zepa
        }
        return null
    }

    private fun clearOverlays() {
        map.overlays.removeAll(map.overlays.filterIsInstance<Polygon>())
        map.overlays.removeAll(map.overlays.filterIsInstance<ZepaLabelOverlay>())
        zepaPolygons.clear()
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
                infoWindow = null
            }
            map.overlays.add(polygon)
            zepaPolygons.add(Pair(polygon, zepa))

            val centerLat = geoPoints.map { it.latitude }.average()
            val centerLon = geoPoints.map { it.longitude }.average()
            map.overlays.add(ZepaLabelOverlay(GeoPoint(centerLat, centerLon), zepa.name))
        }
    }
}

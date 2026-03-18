package es.upm.etsisi.mad.bioquiet.util

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon

/**
 * Determines whether a geographic point is inside a polygon using the ray-casting algorithm.
 *
 * @param point the geographic point to test
 * @param polygon the polygon to check against
 * @return true if the point is inside the polygon, false otherwise
 */
fun isPointInPolygon(point: GeoPoint, polygon: Polygon): Boolean {
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

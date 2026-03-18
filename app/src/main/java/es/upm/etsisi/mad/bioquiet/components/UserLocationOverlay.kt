package es.upm.etsisi.mad.bioquiet.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

fun buildUserLocationOverlay(context: Context, map: MapView): MyLocationNewOverlay {
    val density = context.resources.displayMetrics.density
    val sizePx = (16 * density).toInt()
    val borderPx = 2 * density
    val dot = createBitmap(sizePx, sizePx)
    Canvas(dot).apply {
        val r = sizePx / 2f
        drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        drawCircle(r, r, r - borderPx, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt() })
    }
    return MyLocationNewOverlay(GpsMyLocationProvider(context), map).apply {
        enableMyLocation()
        setPersonIcon(dot)
        setPersonAnchor(0.5f, 0.5f)
        setDirectionIcon(dot)
        setDirectionAnchor(0.5f, 0.5f)
    }
}

package es.upm.etsisi.mad.bioquiet.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class ZepaLabelOverlay(
    private val position: GeoPoint,
    private val text: String
) : Overlay() {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B5E20.toInt()
        typeface = Typeface.DEFAULT_BOLD
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val zoom = mapView.zoomLevelDouble
        if (zoom < 10.0) return

        val textSize = (14f + (zoom - 10.0) * 3f).toFloat().coerceIn(14f, 28f)
        textPaint.textSize = textSize

        val point = mapView.projection.toPixels(position, null)

        val padding = 4f
        val textWidth = textPaint.measureText(text)
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        val left = point.x - textWidth / 2 - padding
        val top = point.y - textHeight / 2 - padding
        val right = point.x + textWidth / 2 + padding
        val bottom = point.y + textHeight / 2 + padding

        canvas.drawRoundRect(RectF(left, top, right, bottom), 4f, 4f, bgPaint)
        canvas.drawText(
            text,
            point.x - textWidth / 2,
            point.y - fm.ascent / 2 - fm.descent / 2,
            textPaint
        )
    }
}

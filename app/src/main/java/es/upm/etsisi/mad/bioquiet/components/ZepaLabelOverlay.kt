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
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B5E20.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        // Solo mostrar etiquetas a partir de zoom 10
        if (mapView.zoomLevelDouble < 10.0) return

        val point = mapView.projection.toPixels(position, null)

        val padding = 8f
        val textWidth = textPaint.measureText(text)
        val textHeight = textPaint.textSize

        val left = point.x - textWidth / 2 - padding
        val top = point.y - textHeight / 2 - padding
        val right = point.x + textWidth / 2 + padding
        val bottom = point.y + textHeight / 2 + padding

        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
        canvas.drawRoundRect(rect, 6f, 6f, strokePaint)
        canvas.drawText(
            text,
            point.x - textWidth / 2,
            point.y + textHeight / 2 - padding / 2,
            textPaint
        )
    }
}

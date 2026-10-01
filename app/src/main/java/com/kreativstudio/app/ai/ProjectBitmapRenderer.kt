package com.kreativstudio.app.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.kreativstudio.app.model.CanvasElement
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.model.ToolType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object ProjectBitmapRenderer {
    fun render(project: KreativProject, maxEdge: Int = 1280): Bitmap {
        val sourceWidth = project.widthPx.coerceAtLeast(1)
        val sourceHeight = project.heightPx.coerceAtLeast(1)
        val scale = (maxEdge.toFloat() / max(sourceWidth, sourceHeight)).coerceAtMost(1f)
        val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(project.backgroundArgb.toInt())
        canvas.scale(scale, scale)

        val visibleLayers = project.layers.filter { it.isVisible }.associateBy { it.id }
        project.elements.forEach { element ->
            val layer = visibleLayers[element.layerId] ?: return@forEach
            drawElement(canvas, element, layer.opacity)
        }
        return bitmap
    }

    private fun drawElement(canvas: Canvas, element: CanvasElement, layerOpacity: Float) {
        if (element.points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = element.colorArgb.toInt()
            alpha = (255f * element.opacity * layerOpacity).roundToInt().coerceIn(0, 255)
            strokeWidth = element.width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        when (element.tool) {
            ToolType.WATERCOLOR -> paint.alpha = (paint.alpha * .45f).roundToInt()
            ToolType.CHARCOAL -> paint.alpha = (paint.alpha * .8f).roundToInt()
            ToolType.MARKER -> paint.alpha = (paint.alpha * .72f).roundToInt()
            ToolType.SMUDGE -> paint.alpha = (paint.alpha * .18f).roundToInt()
            else -> Unit
        }

        val first = element.points.first()
        val last = element.points.last()
        when (element.tool) {
            ToolType.LINE, ToolType.ARROW -> {
                canvas.drawLine(first.x, first.y, last.x, last.y, paint)
                if (element.tool == ToolType.ARROW) drawArrowHead(canvas, first, last, paint)
            }
            ToolType.RECTANGLE -> canvas.drawRect(normalizedRect(first, last), paint)
            ToolType.ELLIPSE -> canvas.drawOval(normalizedRect(first, last), paint)
            ToolType.TRIANGLE -> {
                val rect = normalizedRect(first, last)
                val path = Path().apply {
                    moveTo(rect.centerX(), rect.top)
                    lineTo(rect.right, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            ToolType.POLYGON -> drawRegularPolygon(canvas, normalizedRect(first, last), 6, paint)
            ToolType.STAR -> drawStar(canvas, normalizedRect(first, last), paint)
            ToolType.ARC -> canvas.drawArc(normalizedRect(first, last), 200f, 300f, false, paint)
            ToolType.TEXT -> {
                paint.style = Paint.Style.FILL
                paint.textSize = element.width.coerceAtLeast(12f)
                val lineHeight = paint.fontSpacing
                (element.text ?: "").lineSequence().forEachIndexed { index, line ->
                    canvas.drawText(line, first.x, first.y + lineHeight * index, paint)
                }
            }
            ToolType.FILL, ToolType.SELECT, ToolType.ERASER -> Unit
            else -> drawStroke(canvas, element, paint)
        }
    }

    private fun drawStroke(canvas: Canvas, element: CanvasElement, paint: Paint) {
        if (element.points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(element.points[0].x, element.points[0].y, element.width / 2f, paint)
            return
        }
        val path = Path().apply {
            moveTo(element.points.first().x, element.points.first().y)
            element.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
        if (element.tool == ToolType.CHARCOAL) {
            val grain = Paint(paint).apply {
                alpha = (alpha * .28f).roundToInt()
                strokeWidth = (element.width * .15f).coerceAtLeast(1f)
            }
            element.points.forEachIndexed { index, point ->
                val offset = sin(index * 1.71).toFloat() * element.width * .28f
                canvas.drawPoint(point.x + offset, point.y - offset * .35f, grain)
            }
        }
    }

    private fun normalizedRect(a: StrokePoint, b: StrokePoint) = RectF(
        minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y),
    )

    private fun drawRegularPolygon(canvas: Canvas, rect: RectF, sides: Int, paint: Paint) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val path = Path()
        repeat(sides) { index ->
            val angle = -PI / 2.0 + 2.0 * PI * index / sides
            val x = rect.centerX() + cos(angle).toFloat() * rect.width() / 2f
            val y = rect.centerY() + sin(angle).toFloat() * rect.height() / 2f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawStar(canvas: Canvas, rect: RectF, paint: Paint) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val path = Path()
        repeat(10) { index ->
            val radius = if (index % 2 == 0) 1f else .43f
            val angle = -PI / 2.0 + PI * index / 5.0
            val x = rect.centerX() + cos(angle).toFloat() * rect.width() / 2f * radius
            val y = rect.centerY() + sin(angle).toFloat() * rect.height() / 2f * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawArrowHead(canvas: Canvas, a: StrokePoint, b: StrokePoint, paint: Paint) {
        val angle = kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
        val size = (paint.strokeWidth * 4f).coerceAtLeast(18f)
        val left = angle + PI * .82
        val right = angle - PI * .82
        canvas.drawLine(b.x, b.y, b.x + cos(left).toFloat() * size, b.y + sin(left).toFloat() * size, paint)
        canvas.drawLine(b.x, b.y, b.x + cos(right).toFloat() * size, b.y + sin(right).toFloat() * size, paint)
    }
}

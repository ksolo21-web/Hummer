package com.kreativstudio.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import com.kreativstudio.app.model.CanvasElement
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.ToolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

class ProjectExporter(private val context: Context) {
    private val json = Json { encodeDefaults = true; prettyPrint = false }

    suspend fun exportProject(project: KreativProject, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "w")?.use {
            it.write(json.encodeToString(project).encodeToByteArray())
        } ?: error("Could not open the selected file.")
    }

    suspend fun importProject(uri: Uri): KreativProject = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not read the selected project.")
        json.decodeFromString<KreativProject>(raw)
    }

    suspend fun exportPng(project: KreativProject, uri: Uri, maxSide: Int = 4096) = withContext(Dispatchers.IO) {
        val ratio = minOf(1f, maxSide.toFloat() / max(project.widthPx, project.heightPx).toFloat())
        val width = (project.widthPx * ratio).toInt().coerceAtLeast(1)
        val height = (project.heightPx * ratio).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(project.backgroundArgb.toInt())
        canvas.scale(ratio, ratio)
        renderProject(canvas, project)
        context.contentResolver.openOutputStream(uri, "w")?.use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG export failed." }
        } ?: error("Could not open the selected export file.")
        bitmap.recycle()
    }

    private fun renderProject(canvas: Canvas, project: KreativProject) {
        val visible = project.layers.filter { it.isVisible }.associateBy { it.id }
        project.elements.forEach { element ->
            val layer = visible[element.layerId] ?: return@forEach
            drawElement(canvas, element, layer.opacity)
        }
    }

    private fun drawElement(canvas: Canvas, element: CanvasElement, layerOpacity: Float) {
        if (element.points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = element.colorArgb.toInt()
            alpha = (255f * element.opacity * layerOpacity).toInt().coerceIn(0, 255)
            strokeWidth = element.width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        when (element.tool) {
            ToolType.ERASER -> {
                paint.color = Color.WHITE
                paint.alpha = 255
            }
            ToolType.WATERCOLOR -> paint.alpha = (paint.alpha * .48f).toInt()
            ToolType.MARKER -> paint.alpha = (paint.alpha * .72f).toInt()
            ToolType.CHARCOAL -> paint.alpha = (paint.alpha * .82f).toInt()
            else -> Unit
        }
        val a = element.points.first()
        val b = element.points.last()
        when (element.tool) {
            ToolType.LINE, ToolType.ARROW -> {
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
                if (element.tool == ToolType.ARROW) drawArrowHead(canvas, a.x, a.y, b.x, b.y, paint)
            }
            ToolType.RECTANGLE -> canvas.drawRect(normalizedRect(a.x, a.y, b.x, b.y), paint)
            ToolType.ELLIPSE -> canvas.drawOval(normalizedRect(a.x, a.y, b.x, b.y), paint)
            ToolType.TRIANGLE -> {
                val rect = normalizedRect(a.x, a.y, b.x, b.y)
                val path = Path().apply {
                    moveTo(rect.centerX(), rect.top)
                    lineTo(rect.right, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            ToolType.POLYGON -> drawRegularPolygon(canvas, normalizedRect(a.x, a.y, b.x, b.y), 6, paint)
            ToolType.STAR -> drawStar(canvas, normalizedRect(a.x, a.y, b.x, b.y), paint)
            ToolType.ARC -> canvas.drawArc(normalizedRect(a.x, a.y, b.x, b.y), 200f, 300f, false, paint)
            ToolType.TEXT -> {
                paint.style = Paint.Style.FILL
                paint.textSize = element.width.coerceAtLeast(12f)
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
                val lineHeight = paint.fontSpacing
                (element.text ?: "").lineSequence().forEachIndexed { index, line ->
                    canvas.drawText(line, a.x, a.y + lineHeight * index, paint)
                }
            }
            else -> {
                if (element.points.size == 1) {
                    canvas.drawCircle(a.x, a.y, element.width / 2f, paint.apply { style = Paint.Style.FILL })
                } else {
                    for (i in 1 until element.points.size) {
                        val p0 = element.points[i - 1]
                        val p1 = element.points[i]
                        paint.strokeWidth = element.width * ((p0.pressure + p1.pressure) / 2f).coerceIn(.15f, 1.5f)
                        canvas.drawLine(p0.x, p0.y, p1.x, p1.y, paint)
                    }
                }
            }
        }
    }

    private fun normalizedRect(ax: Float, ay: Float, bx: Float, by: Float) = RectF(
        minOf(ax, bx), minOf(ay, by), maxOf(ax, bx), maxOf(ay, by)
    )

    private fun drawRegularPolygon(canvas: Canvas, rect: RectF, sides: Int, paint: Paint) {
        val path = Path()
        repeat(sides) { index ->
            val angle = -Math.PI / 2.0 + 2.0 * Math.PI * index / sides
            val x = rect.centerX() + kotlin.math.cos(angle).toFloat() * rect.width() / 2f
            val y = rect.centerY() + kotlin.math.sin(angle).toFloat() * rect.height() / 2f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawStar(canvas: Canvas, rect: RectF, paint: Paint) {
        val path = Path()
        repeat(10) { index ->
            val scale = if (index % 2 == 0) 1f else .43f
            val angle = -Math.PI / 2.0 + Math.PI * index / 5.0
            val x = rect.centerX() + kotlin.math.cos(angle).toFloat() * rect.width() / 2f * scale
            val y = rect.centerY() + kotlin.math.sin(angle).toFloat() * rect.height() / 2f * scale
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawArrowHead(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float, paint: Paint) {
        val angle = kotlin.math.atan2((by - ay).toDouble(), (bx - ax).toDouble())
        val size = (paint.strokeWidth * 4f).coerceAtLeast(18f)
        val left = angle + Math.PI * .82
        val right = angle - Math.PI * .82
        canvas.drawLine(bx, by, bx + (kotlin.math.cos(left) * size).toFloat(), by + (kotlin.math.sin(left) * size).toFloat(), paint)
        canvas.drawLine(bx, by, bx + (kotlin.math.cos(right) * size).toFloat(), by + (kotlin.math.sin(right) * size).toFloat(), paint)
    }
}

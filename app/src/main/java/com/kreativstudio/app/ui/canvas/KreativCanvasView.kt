package com.kreativstudio.app.ui.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.kreativstudio.app.model.CanvasElement
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.model.ToolType
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

class KreativCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var project: KreativProject? = null
        set(value) {
            field = value
            invalidate()
        }
    var activeTool: ToolType = ToolType.PENCIL
        set(value) {
            if (field == value) return
            field = value
            if (value != ToolType.SELECT) clearSelection()
            invalidate()
        }
    var activeColorArgb: Long = 0xFF21182E
    var brushWidth: Float = 10f
    var brushOpacity: Float = 1f
    var stabilization: Float = .25f
    var symmetryEnabled: Boolean = false
    var perspectiveGridEnabled: Boolean = false
    var palmRejectionEnabled: Boolean = true
    var shapeSnapEnabled: Boolean = true
    var replayProgress: Float = 1f
    var onElementsFinished: (List<CanvasElement>) -> Unit = {}
    var onEraseGesture: (List<StrokePoint>, Float) -> Unit = { _, _ -> }
    var onFillRequested: (Long) -> Unit = {}
    var onTextPlacementRequested: (StrokePoint) -> Unit = {}
    var onElementTransformed: (CanvasElement) -> Unit = {}
    var onInputStatus: (String) -> Unit = {}

    private val transform = Matrix()
    private val inverseTransform = Matrix()
    private val pageCorners = FloatArray(8)
    private val pageShadowBounds = RectF()
    private val currentPoints = mutableListOf<StrokePoint>()
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outsidePaint = Paint().apply { color = Color.rgb(18, 14, 24) }
    private val pageShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(96, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 132, 96, 190)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(216, 171, 92)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var rotation = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var transforming = false
    private var stylusDown = false
    private var hoverX: Float? = null
    private var hoverY: Float? = null
    private var selectedElementId: String? = null
    private var selectionOriginal: CanvasElement? = null
    private var selectionPreview: CanvasElement? = null
    private var selectionAnchor: StrokePoint? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            transforming = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(.25f, 8f)
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            transforming = false
        }
    })

    init {
        isFocusable = true
        isClickable = true
    }

    fun resetView() {
        zoom = 1f
        panX = 0f
        panY = 0f
        rotation = 0f
        invalidate()
    }

    fun rotateCanvas(deltaDegrees: Float) {
        rotation = (rotation + deltaDegrees) % 360f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), outsidePaint)
        val p = project ?: return
        updateTransform(p)

        pageCorners[0] = 0f
        pageCorners[1] = 0f
        pageCorners[2] = p.widthPx.toFloat()
        pageCorners[3] = 0f
        pageCorners[4] = p.widthPx.toFloat()
        pageCorners[5] = p.heightPx.toFloat()
        pageCorners[6] = 0f
        pageCorners[7] = p.heightPx.toFloat()
        transform.mapPoints(pageCorners)
        pageShadowBounds.set(
            minOf(pageCorners[0], pageCorners[2], pageCorners[4], pageCorners[6]) - 10f,
            minOf(pageCorners[1], pageCorners[3], pageCorners[5], pageCorners[7]) + 8f,
            maxOf(pageCorners[0], pageCorners[2], pageCorners[4], pageCorners[6]) + 10f,
            maxOf(pageCorners[1], pageCorners[3], pageCorners[5], pageCorners[7]) + 24f,
        )
        canvas.drawRoundRect(
            pageShadowBounds,
            12f,
            12f,
            pageShadow,
        )

        canvas.save()
        canvas.concat(transform)
        canvas.clipRect(0f, 0f, p.widthPx.toFloat(), p.heightPx.toFloat())
        canvas.drawColor(p.backgroundArgb.toInt())
        if (perspectiveGridEnabled) drawPerspectiveGrid(canvas, p)
        if (symmetryEnabled) drawSymmetryGuide(canvas, p)

        val visibleLayers = p.layers.filter { it.isVisible }.associateBy { it.id }
        val count = (p.elements.size * replayProgress.coerceIn(0f, 1f)).toInt()
        p.elements.take(count).forEach { element ->
            val layer = visibleLayers[element.layerId] ?: return@forEach
            val rendered = selectionPreview?.takeIf { it.id == element.id } ?: element
            drawElement(canvas, rendered, layer.opacity)
        }
        if (currentPoints.isNotEmpty() && activeTool != ToolType.SELECT) drawPreview(canvas, p)
        if (activeTool == ToolType.SELECT) {
            val selected = selectionPreview ?: p.elements.firstOrNull { it.id == selectedElementId }
            if (selected != null && visibleLayers.containsKey(selected.layerId)) drawSelection(canvas, selected)
        }
        canvas.restore()

        drawHoverCursor(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val isPalmCancellation = Build.VERSION.SDK_INT >= 33 &&
            event.flags and MotionEvent.FLAG_CANCELED != 0
        if (palmRejectionEnabled && isPalmCancellation) {
            currentPoints.clear()
            selectionOriginal = null
            selectionPreview = null
            selectionAnchor = null
            stylusDown = false
            parent?.requestDisallowInterceptTouchEvent(false)
            invalidate()
            return true
        }
        if (palmRejectionEnabled && stylusDown && !isStylus) return true

        if (event.pointerCount >= 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    transforming = true
                    currentPoints.clear()
                    lastTouchX = averageX(event)
                    lastTouchY = averageY(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = averageX(event)
                    val y = averageY(event)
                    panX += x - lastTouchX
                    panY += y - lastTouchY
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> transforming = false
            }
            return true
        }

        if (transforming || scaleDetector.isInProgress) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stylusDown = isStylus
                parent?.requestDisallowInterceptTouchEvent(true)
                requestFocus()
                currentPoints.clear()
                if (activeTool == ToolType.FILL) {
                    onFillRequested(activeColorArgb)
                    return true
                }
                if (activeTool == ToolType.SELECT) {
                    beginSelection(event)
                    onInputStatus(if (selectedElementId == null) "Select • tap an object" else "Select • drag to move")
                    invalidate()
                    return true
                }
                addMotionPoint(event, 0)
                onInputStatus(
                    if (isStylus) "Active pen • pressure ${"%.2f".format(event.pressure)}"
                    else "Touch precision mode"
                )
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTool == ToolType.SELECT) {
                    updateSelection(event)
                } else {
                    for (h in 0 until event.historySize) addHistoricalPoint(event, h)
                    addMotionPoint(event, 0)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (activeTool == ToolType.SELECT) {
                    finishSelection(event)
                } else {
                    addMotionPoint(event, 0)
                    finishElement()
                }
                stylusDown = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                currentPoints.clear()
                selectionOriginal = null
                selectionPreview = null
                selectionAnchor = null
                stylusDown = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                    hoverX = event.x
                    hoverY = event.y
                    onInputStatus("S Pen hover • tilt ${"%.0f".format(Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT).toDouble()))}°")
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    hoverX = null
                    hoverY = null
                }
            }
            invalidate()
            return true
        }
        return super.onHoverEvent(event)
    }

    private fun addHistoricalPoint(event: MotionEvent, historyIndex: Int) {
        val screen = floatArrayOf(event.getHistoricalX(0, historyIndex), event.getHistoricalY(0, historyIndex))
        inverseTransform.mapPoints(screen)
        addPoint(
            screen[0],
            screen[1],
            event.getHistoricalPressure(0, historyIndex),
            event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, 0, historyIndex),
            event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, 0, historyIndex),
            event.getHistoricalEventTime(historyIndex),
        )
    }

    private fun addMotionPoint(event: MotionEvent, pointerIndex: Int) {
        val screen = floatArrayOf(event.getX(pointerIndex), event.getY(pointerIndex))
        inverseTransform.mapPoints(screen)
        addPoint(
            screen[0],
            screen[1],
            event.getPressure(pointerIndex).coerceAtLeast(.08f),
            event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex),
            event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex),
            event.eventTime,
        )
    }

    private fun addPoint(
        rawX: Float,
        rawY: Float,
        pressure: Float,
        tilt: Float,
        orientation: Float,
        time: Long,
    ) {
        val p = project ?: return
        val x = rawX.coerceIn(0f, p.widthPx.toFloat())
        val y = rawY.coerceIn(0f, p.heightPx.toFloat())
        val last = currentPoints.lastOrNull()
        val smooth = (1f - stabilization.coerceIn(0f, .95f) * .82f).coerceAtLeast(.08f)
        val sx = if (last == null || isShapeTool(activeTool)) x else last.x + (x - last.x) * smooth
        val sy = if (last == null || isShapeTool(activeTool)) y else last.y + (y - last.y) * smooth
        val point = StrokePoint(
            x = sx,
            y = sy,
            pressure = pressure.coerceIn(.08f, 1.5f),
            tilt = tilt,
            orientation = orientation,
            timeMillis = time,
        )
        if (isShapeTool(activeTool) && currentPoints.isNotEmpty()) {
            if (currentPoints.size == 1) currentPoints.add(point) else currentPoints[currentPoints.lastIndex] = point
        } else if (last == null || hypot((point.x - last.x).toDouble(), (point.y - last.y).toDouble()) >= .5) {
            currentPoints.add(point)
        }
    }

    private fun finishElement() {
        val p = project ?: return
        if (currentPoints.isEmpty()) return
        val points = if (isShapeTool(activeTool)) snappedShapePoints(currentPoints) else currentPoints.toList()
        if (activeTool == ToolType.ERASER) {
            onEraseGesture(points, brushWidth)
            currentPoints.clear()
            invalidate()
            return
        }
        if (activeTool == ToolType.TEXT) {
            onTextPlacementRequested(currentPoints.last())
            currentPoints.clear()
            invalidate()
            return
        }
        if (activeTool == ToolType.SELECT) {
            currentPoints.clear()
            invalidate()
            return
        }
        val element = CanvasElement(
            id = UUID.randomUUID().toString(),
            layerId = p.activeLayerId,
            tool = activeTool,
            points = points,
            colorArgb = activeColorArgb,
            width = brushWidth,
            opacity = brushOpacity,
            stabilization = stabilization,
        )
        val results = mutableListOf(element)
        if (symmetryEnabled && activeTool !in setOf(ToolType.FILL, ToolType.SELECT, ToolType.TEXT)) {
            val mirrored = element.copy(
                id = UUID.randomUUID().toString(),
                points = element.points.map { it.copy(x = p.widthPx - it.x) },
            )
            results += mirrored
        }
        onElementsFinished(results)
        currentPoints.clear()
        invalidate()
    }

    private fun beginSelection(event: MotionEvent) {
        val point = eventCanvasPoint(event)
        val hit = findElementAt(point)
        selectedElementId = hit?.id
        selectionOriginal = hit
        selectionPreview = hit
        selectionAnchor = point
    }

    private fun updateSelection(event: MotionEvent) {
        val original = selectionOriginal ?: return
        val anchor = selectionAnchor ?: return
        val point = eventCanvasPoint(event)
        val dx = point.x - anchor.x
        val dy = point.y - anchor.y
        selectionPreview = original.copy(points = original.points.map { it.copy(x = it.x + dx, y = it.y + dy) })
    }

    private fun finishSelection(event: MotionEvent) {
        updateSelection(event)
        val original = selectionOriginal
        val updated = selectionPreview
        if (original != null && updated != null && updated.points != original.points) {
            onElementTransformed(updated)
        }
        selectionOriginal = null
        selectionAnchor = null
        selectionPreview = updated
        invalidate()
    }

    private fun clearSelection() {
        selectedElementId = null
        selectionOriginal = null
        selectionPreview = null
        selectionAnchor = null
    }

    private fun eventCanvasPoint(event: MotionEvent): StrokePoint {
        val screen = floatArrayOf(event.x, event.y)
        inverseTransform.mapPoints(screen)
        val p = project
        return StrokePoint(
            x = if (p == null) screen[0] else screen[0].coerceIn(0f, p.widthPx.toFloat()),
            y = if (p == null) screen[1] else screen[1].coerceIn(0f, p.heightPx.toFloat()),
            pressure = event.pressure.coerceAtLeast(.08f),
            tilt = event.getAxisValue(MotionEvent.AXIS_TILT),
            orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION),
            timeMillis = event.eventTime,
        )
    }

    private fun findElementAt(point: StrokePoint): CanvasElement? {
        val p = project ?: return null
        val visibleLayers = p.layers.filter { it.isVisible && !it.isLocked }.map { it.id }.toSet()
        val padding = (38f / currentScale()).coerceAtLeast(8f)
        return p.elements.asReversed().firstOrNull { element ->
            element.layerId in visibleLayers && elementBounds(element).apply { inset(-padding, -padding) }.contains(point.x, point.y)
        }
    }

    private fun elementBounds(element: CanvasElement): RectF {
        if (element.points.isEmpty()) return RectF()
        if (element.tool == ToolType.TEXT) {
            val anchor = element.points.first()
            val size = element.width.coerceAtLeast(12f)
            val lines = (element.text ?: "").lines().ifEmpty { listOf("") }
            val widest = lines.maxOfOrNull { it.length } ?: 1
            return RectF(
                anchor.x,
                anchor.y - size,
                anchor.x + widest.coerceAtLeast(1) * size * .62f,
                anchor.y + (lines.size - 1) * size * 1.25f + size * .3f,
            )
        }
        val xs = element.points.map { it.x }
        val ys = element.points.map { it.y }
        val half = (element.width / 2f).coerceAtLeast(3f)
        return RectF(
            (xs.minOrNull() ?: 0f) - half,
            (ys.minOrNull() ?: 0f) - half,
            (xs.maxOrNull() ?: 0f) + half,
            (ys.maxOrNull() ?: 0f) + half,
        )
    }

    private fun drawSelection(canvas: Canvas, element: CanvasElement) {
        val bounds = elementBounds(element)
        val inset = (12f / currentScale()).coerceAtLeast(3f)
        bounds.inset(-inset, -inset)
        selectionPaint.strokeWidth = (2f / currentScale()).coerceAtLeast(.6f)
        selectionPaint.pathEffect = android.graphics.DashPathEffect(
            floatArrayOf(12f / currentScale(), 8f / currentScale()),
            0f,
        )
        canvas.drawRect(bounds, selectionPaint)
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(216, 171, 92)
            style = Paint.Style.FILL
        }
        val radius = (6f / currentScale()).coerceAtLeast(2f)
        canvas.drawCircle(bounds.left, bounds.top, radius, handlePaint)
        canvas.drawCircle(bounds.right, bounds.top, radius, handlePaint)
        canvas.drawCircle(bounds.left, bounds.bottom, radius, handlePaint)
        canvas.drawCircle(bounds.right, bounds.bottom, radius, handlePaint)
    }

    private fun snappedShapePoints(source: List<StrokePoint>): List<StrokePoint> {
        if (source.size < 2 || !shapeSnapEnabled || activeTool !in setOf(ToolType.LINE, ToolType.ARROW)) return source
        val a = source.first()
        val b = source.last()
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val step = PI / 12.0
        val angle = round(atan2(dy.toDouble(), dx.toDouble()) / step) * step
        return listOf(a, b.copy(x = a.x + cos(angle).toFloat() * length, y = a.y + sin(angle).toFloat() * length))
    }

    private fun drawPreview(canvas: Canvas, p: KreativProject) {
        val preview = CanvasElement(
            layerId = p.activeLayerId,
            tool = activeTool,
            points = if (isShapeTool(activeTool)) snappedShapePoints(currentPoints) else currentPoints,
            colorArgb = activeColorArgb,
            width = brushWidth,
            opacity = brushOpacity,
            stabilization = stabilization,
        )
        drawElement(canvas, preview, 1f)
    }

    private fun drawElement(canvas: Canvas, element: CanvasElement, layerOpacity: Float) {
        if (element.points.isEmpty()) return
        val paint = previewPaint.apply {
            reset()
            isAntiAlias = true
            color = element.colorArgb.toInt()
            alpha = (255f * element.opacity * layerOpacity).toInt().coerceIn(0, 255)
            strokeWidth = element.width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        when (element.tool) {
            ToolType.WATERCOLOR -> paint.alpha = (paint.alpha * .42f).toInt()
            ToolType.MARKER -> paint.alpha = (paint.alpha * .7f).toInt()
            ToolType.CHARCOAL -> paint.alpha = (paint.alpha * .78f).toInt()
            ToolType.SMUDGE -> paint.alpha = (paint.alpha * .18f).toInt()
            else -> Unit
        }
        val a = element.points.first()
        val b = element.points.last()
        when (element.tool) {
            ToolType.LINE, ToolType.ARROW -> {
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
                if (element.tool == ToolType.ARROW) drawArrowHead(canvas, a, b, paint)
            }
            ToolType.RECTANGLE -> canvas.drawRect(normalizedRect(a, b), paint)
            ToolType.ELLIPSE -> canvas.drawOval(normalizedRect(a, b), paint)
            ToolType.TRIANGLE -> {
                val rect = normalizedRect(a, b)
                val path = Path().apply {
                    moveTo(rect.centerX(), rect.top)
                    lineTo(rect.right, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            ToolType.POLYGON -> drawRegularPolygon(canvas, normalizedRect(a, b), 6, paint)
            ToolType.STAR -> drawStar(canvas, normalizedRect(a, b), paint)
            ToolType.ARC -> canvas.drawArc(normalizedRect(a, b), 200f, 300f, false, paint)
            ToolType.TEXT -> {
                paint.style = Paint.Style.FILL
                paint.textSize = element.width.coerceAtLeast(12f)
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
                val lineHeight = paint.fontSpacing
                (element.text ?: "").lineSequence().forEachIndexed { index, line ->
                    canvas.drawText(line, a.x, a.y + lineHeight * index, paint)
                }
            }
            ToolType.WATERCOLOR -> drawWatercolor(canvas, element, paint)
            ToolType.CHARCOAL -> drawCharcoal(canvas, element, paint)
            else -> drawVariableStroke(canvas, element, paint)
        }
    }

    private fun drawVariableStroke(canvas: Canvas, element: CanvasElement, paint: Paint) {
        if (element.points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(element.points[0].x, element.points[0].y, element.width / 2f, paint)
            return
        }
        for (i in 1 until element.points.size) {
            val a = element.points[i - 1]
            val b = element.points[i]
            val pressure = ((a.pressure + b.pressure) / 2f).coerceIn(.12f, 1.5f)
            val tiltBoost = 1f + ((a.tilt + b.tilt) / 2f).coerceIn(0f, 1.4f) *
                if (element.tool == ToolType.PENCIL) 1.4f else .35f
            paint.strokeWidth = element.width * pressure * tiltBoost
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }

    private fun drawWatercolor(canvas: Canvas, element: CanvasElement, paint: Paint) {
        val originalAlpha = paint.alpha
        val originalWidth = element.width
        repeat(3) { pass ->
            paint.alpha = (originalAlpha * (0.52f - pass * .11f)).toInt().coerceAtLeast(8)
            paint.strokeWidth = originalWidth * (1f + pass * .22f)
            drawVariableStroke(canvas, element.copy(width = paint.strokeWidth), paint)
        }
    }

    private fun drawCharcoal(canvas: Canvas, element: CanvasElement, paint: Paint) {
        drawVariableStroke(canvas, element, paint)
        paint.alpha = (paint.alpha * .35f).toInt()
        paint.strokeWidth = (element.width * .18f).coerceAtLeast(1f)
        element.points.forEachIndexed { index, point ->
            val jitter = sin(index * 1.73).toFloat() * element.width * .32f
            canvas.drawPoint(point.x + jitter, point.y - jitter * .4f, paint)
        }
    }

    private fun drawRegularPolygon(canvas: Canvas, rect: RectF, sides: Int, paint: Paint) {
        if (rect.width() <= 0f || rect.height() <= 0f || sides < 3) return
        val path = Path()
        val radiusX = rect.width() / 2f
        val radiusY = rect.height() / 2f
        repeat(sides) { index ->
            val angle = -PI / 2.0 + 2.0 * PI * index / sides
            val x = rect.centerX() + cos(angle).toFloat() * radiusX
            val y = rect.centerY() + sin(angle).toFloat() * radiusY
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawStar(canvas: Canvas, rect: RectF, paint: Paint) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val path = Path()
        val outerX = rect.width() / 2f
        val outerY = rect.height() / 2f
        repeat(10) { index ->
            val radiusScale = if (index % 2 == 0) 1f else .43f
            val angle = -PI / 2.0 + PI * index / 5.0
            val x = rect.centerX() + cos(angle).toFloat() * outerX * radiusScale
            val y = rect.centerY() + sin(angle).toFloat() * outerY * radiusScale
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawArrowHead(canvas: Canvas, a: StrokePoint, b: StrokePoint, paint: Paint) {
        val angle = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
        val size = (paint.strokeWidth * 4f).coerceAtLeast(18f)
        val left = angle + PI * .82
        val right = angle - PI * .82
        canvas.drawLine(b.x, b.y, b.x + cos(left).toFloat() * size, b.y + sin(left).toFloat() * size, paint)
        canvas.drawLine(b.x, b.y, b.x + cos(right).toFloat() * size, b.y + sin(right).toFloat() * size, paint)
    }

    private fun drawPerspectiveGrid(canvas: Canvas, p: KreativProject) {
        val horizon = p.heightPx * .42f
        guidePaint.strokeWidth = (1f / currentScale()).coerceAtLeast(.3f)
        canvas.drawLine(0f, horizon, p.widthPx.toFloat(), horizon, guidePaint)
        val leftVp = p.widthPx * .12f
        val rightVp = p.widthPx * .88f
        for (i in 0..12) {
            val x = p.widthPx * i / 12f
            canvas.drawLine(leftVp, horizon, x, p.heightPx.toFloat(), guidePaint)
            canvas.drawLine(rightVp, horizon, x, p.heightPx.toFloat(), guidePaint)
        }
        for (i in 1..8) {
            val t = i / 9f
            val y = horizon + (p.heightPx - horizon) * t * t
            canvas.drawLine(0f, y, p.widthPx.toFloat(), y, guidePaint)
        }
    }

    private fun drawSymmetryGuide(canvas: Canvas, p: KreativProject) {
        guidePaint.strokeWidth = (1.5f / currentScale()).coerceAtLeast(.4f)
        canvas.drawLine(p.widthPx / 2f, 0f, p.widthPx / 2f, p.heightPx.toFloat(), guidePaint)
    }

    private fun drawHoverCursor(canvas: Canvas) {
        val x = hoverX ?: return
        val y = hoverY ?: return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            alpha = 210
        }
        val radius = (brushWidth * currentScale() / 2f).coerceIn(4f, 100f)
        canvas.drawCircle(x, y, radius, p)
        p.color = Color.BLACK
        p.alpha = 130
        p.strokeWidth = 1f
        canvas.drawCircle(x, y, radius + 2f, p)
    }

    private fun updateTransform(p: KreativProject) {
        val scale = currentScale()
        transform.reset()
        transform.postTranslate(-p.widthPx / 2f, -p.heightPx / 2f)
        transform.postScale(scale, scale)
        transform.postRotate(rotation)
        transform.postTranslate(width / 2f + panX, height / 2f + panY)
        transform.invert(inverseTransform)
    }

    private fun currentScale(): Float {
        val p = project ?: return 1f
        val base = min(
            width.toFloat() / p.widthPx.coerceAtLeast(1),
            height.toFloat() / p.heightPx.coerceAtLeast(1),
        ) * .9f
        return base * zoom
    }

    private fun normalizedRect(a: StrokePoint, b: StrokePoint) = RectF(
        minOf(a.x, b.x),
        minOf(a.y, b.y),
        maxOf(a.x, b.x),
        maxOf(a.y, b.y),
    )

    private fun isShapeTool(tool: ToolType) = tool in setOf(
        ToolType.LINE,
        ToolType.RECTANGLE,
        ToolType.ELLIPSE,
        ToolType.TRIANGLE,
        ToolType.POLYGON,
        ToolType.STAR,
        ToolType.ARC,
        ToolType.ARROW,
    )

    private fun averageX(event: MotionEvent): Float = (0 until event.pointerCount).map { event.getX(it) }.average().toFloat()
    private fun averageY(event: MotionEvent): Float = (0 until event.pointerCount).map { event.getY(it) }.average().toFloat()
}

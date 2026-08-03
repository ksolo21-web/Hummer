package com.kreativstudio.app.ui

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.ui.canvas.KreativCanvasView

class AdaptiveCanvasController internal constructor() {
    internal var frame: AdaptiveCanvasFrame? = null

    fun fit() {
        frame?.canvasView?.resetView()
    }

    fun rotate(degrees: Float = 15f) {
        frame?.canvasView?.rotateCanvas(degrees)
    }
}

@Composable
fun rememberAdaptiveCanvasController(): AdaptiveCanvasController = remember { AdaptiveCanvasController() }

@Composable
fun AdaptiveCanvasArea(
    viewModel: KreativViewModel,
    project: KreativProject,
    controller: AdaptiveCanvasController,
    modifier: Modifier = Modifier,
    onTextPlacement: (StrokePoint) -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                AdaptiveCanvasFrame(context).also { frame ->
                    controller.frame = frame
                    frame.onCanvasFailure = viewModel::showMessage
                    frame.canvasView.onElementsFinished = viewModel::addElements
                    frame.canvasView.onEraseGesture = viewModel::erase
                    frame.canvasView.onFillRequested = viewModel::fillBackground
                    frame.canvasView.onTextPlacementRequested = onTextPlacement
                    frame.canvasView.onElementTransformed = viewModel::transformElement
                    frame.canvasView.onInputStatus = { viewModel.inputStatus = it }
                }
            },
            update = { frame ->
                controller.frame = frame
                runCatching {
                    frame.canvasView.project = project
                    frame.canvasView.activeTool = viewModel.activeTool
                    frame.canvasView.activeColorArgb = viewModel.activeColorArgb
                    frame.canvasView.brushWidth = viewModel.brushWidth
                    frame.canvasView.brushOpacity = viewModel.brushOpacity
                    frame.canvasView.stabilization = viewModel.stabilization
                    frame.canvasView.symmetryEnabled = settings.symmetryEnabled
                    frame.canvasView.perspectiveGridEnabled = settings.perspectiveGridEnabled
                    frame.canvasView.palmRejectionEnabled = settings.palmRejectionEnabled
                    frame.canvasView.shapeSnapEnabled = settings.shapeSnapEnabled
                    frame.canvasView.replayProgress = viewModel.replayProgress
                    frame.canvasView.invalidate()
                }.onFailure(frame::reportFailure)
            },
        )
        overlay()
    }
}

internal class AdaptiveCanvasFrame(context: Context) : FrameLayout(context) {
    val canvasView = KreativCanvasView(context)
    var onCanvasFailure: (String) -> Unit = {}

    private var failure: String? = null
    private var reported = false
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 34f
    }

    init {
        clipChildren = true
        clipToPadding = true
        addView(
            canvasView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && oldw > 0 && oldh > 0 && (w != oldw || h != oldh)) {
            // The drawing view now receives the exact measured workspace. Refit only
            // after Compose has finished the Fold, rotation, keyboard, or split-screen
            // layout so no part of the page is hidden beyond the real viewport.
            post { canvasView.resetView() }
        }
    }

    fun reportFailure(error: Throwable) {
        if (failure != null) return
        failure = error.message ?: error.javaClass.simpleName
        canvasView.visibility = View.INVISIBLE
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        invalidate()
        reportOnce()
    }

    override fun dispatchDraw(canvas: AndroidCanvas) {
        if (failure != null) {
            drawFallback(canvas)
            return
        }
        try {
            super.dispatchDraw(canvas)
        } catch (error: Throwable) {
            reportFailure(error)
            drawFallback(canvas)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (failure != null) return true
        return try {
            super.dispatchTouchEvent(event)
        } catch (error: Throwable) {
            reportFailure(error)
            true
        }
    }

    private fun drawFallback(canvas: AndroidCanvas) {
        canvas.drawColor(AndroidColor.rgb(18, 18, 22))
        fallbackPaint.textSize = 36f
        canvas.drawText("Canvas safe mode", 36f, 68f, fallbackPaint)
        fallbackPaint.textSize = 23f
        canvas.drawText("The studio stayed open after a device rendering error.", 36f, 110f, fallbackPaint)
        canvas.drawText("Return to the Atelier and reopen this project.", 36f, 146f, fallbackPaint)
    }

    private fun reportOnce() {
        if (reported) return
        reported = true
        post { onCanvasFailure("Canvas entered safe mode instead of closing: ${failure ?: "unknown render error"}") }
    }
}

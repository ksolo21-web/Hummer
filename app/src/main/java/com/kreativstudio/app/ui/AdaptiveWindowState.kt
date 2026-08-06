package com.kreativstudio.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.collectLatest

data class KreativWindowState(
    val widthDp: Int,
    val heightDp: Int,
    val isCompact: Boolean,
    val isExpanded: Boolean,
    val isTabletop: Boolean,
    val isBookPosture: Boolean,
    val isSeparating: Boolean,
    val hingeOccludes: Boolean,
    val signature: String,
)

@Composable
fun rememberKreativWindowState(activity: Activity): KreativWindowState {
    val configuration = LocalConfiguration.current
    var layoutInfo by remember(activity) { mutableStateOf<WindowLayoutInfo?>(null) }

    LaunchedEffect(activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collectLatest { layoutInfo = it }
    }

    val foldingFeature = layoutInfo
        ?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()

    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val tabletop = foldingFeature?.orientation == FoldingFeature.Orientation.HORIZONTAL &&
        foldingFeature.state == FoldingFeature.State.HALF_OPENED
    val book = foldingFeature?.orientation == FoldingFeature.Orientation.VERTICAL &&
        (foldingFeature.isSeparating || foldingFeature.state == FoldingFeature.State.HALF_OPENED)
    val separating = foldingFeature?.isSeparating == true
    val occluding = foldingFeature?.occlusionType == FoldingFeature.OcclusionType.FULL
    val foldSignature = foldingFeature?.let {
        "${it.bounds.left}:${it.bounds.top}:${it.bounds.right}:${it.bounds.bottom}:${it.state}:${it.orientation}:${it.isSeparating}:${it.occlusionType}"
    } ?: "flat"

    return remember(width, height, foldSignature) {
        KreativWindowState(
            widthDp = width,
            heightDp = height,
            isCompact = width < 600,
            isExpanded = width >= 840,
            isTabletop = tabletop,
            isBookPosture = book,
            isSeparating = separating,
            hingeOccludes = occluding,
            signature = "$width:$height:$foldSignature",
        )
    }
}

package com.mystudycompanion.app.ui.adaptive

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_LARGE_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

enum class AdaptiveWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    LARGE,
    EXTRA_LARGE;

    companion object {
        fun fromWidthDp(widthDp: Int): AdaptiveWidthClass = when {
            widthDp >= WIDTH_DP_EXTRA_LARGE_LOWER_BOUND -> EXTRA_LARGE
            widthDp >= WIDTH_DP_LARGE_LOWER_BOUND -> LARGE
            widthDp >= WIDTH_DP_EXPANDED_LOWER_BOUND -> EXPANDED
            widthDp >= WIDTH_DP_MEDIUM_LOWER_BOUND -> MEDIUM
            else -> COMPACT
        }
    }
}

data class AdaptiveLayoutSpec(
    val widthClass: AdaptiveWidthClass,
    val isTabletop: Boolean,
    val hasSeparatingVerticalHinge: Boolean,
) {
    val useBottomNavigation: Boolean = widthClass == AdaptiveWidthClass.COMPACT
    val useNavigationRail: Boolean = !useBottomNavigation

    /**
     * The home screen deliberately changes structure instead of stretching phone cards.
     * Fold-unfolded and tablet windows receive two panes; very large windows receive a
     * third supporting pane.
     */
    val homePaneCount: Int = when {
        isTabletop -> 2
        widthClass >= AdaptiveWidthClass.LARGE -> 3
        widthClass >= AdaptiveWidthClass.MEDIUM || hasSeparatingVerticalHinge -> 2
        else -> 1
    }

    val outerPaddingDp: Int = when (widthClass) {
        AdaptiveWidthClass.COMPACT -> 16
        AdaptiveWidthClass.MEDIUM -> 20
        AdaptiveWidthClass.EXPANDED -> 24
        AdaptiveWidthClass.LARGE -> 28
        AdaptiveWidthClass.EXTRA_LARGE -> 32
    }

    val contentMaxWidthDp: Int = when (widthClass) {
        AdaptiveWidthClass.COMPACT -> 720
        AdaptiveWidthClass.MEDIUM -> 1_000
        AdaptiveWidthClass.EXPANDED -> 1_280
        AdaptiveWidthClass.LARGE -> 1_520
        AdaptiveWidthClass.EXTRA_LARGE -> 1_840
    }
}

fun WindowAdaptiveInfo.toStudyLayoutSpec(): AdaptiveLayoutSpec {
    val sizeClass = windowSizeClass
    val widthClass = when {
        sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> AdaptiveWidthClass.EXTRA_LARGE
        sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_LARGE_LOWER_BOUND) -> AdaptiveWidthClass.LARGE
        sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> AdaptiveWidthClass.EXPANDED
        sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> AdaptiveWidthClass.MEDIUM
        else -> AdaptiveWidthClass.COMPACT
    }
    return AdaptiveLayoutSpec(
        widthClass = widthClass,
        isTabletop = windowPosture.isTabletop,
        hasSeparatingVerticalHinge = windowPosture.separatingVerticalHingeBounds.isNotEmpty(),
    )
}

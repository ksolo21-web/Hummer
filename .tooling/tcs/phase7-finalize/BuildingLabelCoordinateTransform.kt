package com.koenterprises.territorycardstudio.core

/** BuildingGeometry and its label angles are top-origin (y-down), including frozen
 * catalog observations. PDF text matrices rotate in y-up coordinates. Reflect only
 * the angle at this boundary; source geometry, label origins, hashes and work rules
 * are not rewritten. Every rebuilt candidate still needs a fresh artifact review. */
object BuildingLabelCoordinateTransform {
    fun toPdf(item: BuildingLabelItem): PdfBuildingLabelItem {
        require(item.center.x.isFinite() && item.center.y.isFinite() &&
            item.angleDeg.isFinite() && item.fontSizePt.isFinite() && item.fontSizePt > 0.0 &&
            (item.origin == null || item.origin.x.isFinite() && item.origin.y.isFinite())) {
            "Building label has non-finite or invalid source coordinates"
        }
        return PdfBuildingLabelItem(item.text, item.center.x, item.center.y,
            item.origin?.x, item.origin?.y,
            if (item.angleDeg == 0.0) 0.0 else -item.angleDeg, item.fontSizePt)
    }
}

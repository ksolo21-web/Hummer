package com.koenterprises.territorycardstudio.core

/** A historical document supplies labels/work rules only. The footprint remains current-source geometry. */
data class SupplementalBuildingReference(
    val referenceSha256: String,
    val currentSourceSha256: String,
    val buildingContentSha256: String,
    val referenceLocation: String,
    val correspondenceEvidence: String,
    val confirmed: Boolean = false
)

object SupplementalBuildingReferenceContract {
    private val hash = Regex("[0-9a-f]{64}")
    fun validate(r: SupplementalBuildingReference) {
        require(listOf(r.referenceSha256,r.currentSourceSha256,r.buildingContentSha256).all { hash.matches(it) })
        require(r.referenceSha256 != r.currentSourceSha256) { "Supplemental reference cannot replace the current source" }
        listOf(r.referenceLocation,r.correspondenceEvidence).forEach {
            require(it == it.trim() && it.length in 8..1000 && it.all { c -> c.code in 32..126 }) { "Describe the reference location and visible footprint correspondence" }
        }
    }
    /** Every editable building property is bound, including member order and label placement. */
    fun contentSha256(b: BuildingGeometry): String {
        val bytes=java.io.ByteArrayOutputStream()
        val out=java.io.DataOutputStream(bytes)
        fun text(s:String) { val v=s.toByteArray(Charsets.UTF_8);out.writeInt(v.size);out.write(v) }
        fun point(p:Point2D) {require(p.x.isFinite() && p.y.isFinite());out.writeDouble(p.x);out.writeDouble(p.y)}
        text("supplemental-building-match-v1");text(b.buildingId);text(b.label);text(b.housingType);out.writeBoolean(b.assigned);text(b.attachedGroup)
        out.writeInt(b.sourceMembers.size);b.sourceMembers.forEach(::text)
        out.writeInt(b.polygon.size);b.polygon.forEach(::point)
        out.writeInt(b.labelItems.size);b.labelItems.forEach {text(it.text);point(it.center);out.writeBoolean(it.origin!=null);it.origin?.let(::point);require(it.angleDeg.isFinite() && it.fontSizePt.isFinite());out.writeDouble(it.angleDeg);out.writeDouble(it.fontSizePt)}
        out.flush();return BundleIntegrity.sha256(bytes.toByteArray().inputStream())
    }
    fun failures(r:SupplementalBuildingReference,b:BuildingGeometry,currentSourceSha256:String,lockedReferenceSha256:String):List<String> {
        validate(r)
        return buildList {
            if(!r.confirmed)add("SUPPLEMENTAL_MATCH_UNCONFIRMED")
            if(r.referenceSha256!=lockedReferenceSha256)add("SUPPLEMENTAL_REFERENCE_MISMATCH")
            if(r.currentSourceSha256!=currentSourceSha256)add("SUPPLEMENTAL_CURRENT_SOURCE_CHANGED")
            if(r.buildingContentSha256!=contentSha256(b))add("SUPPLEMENTAL_BUILDING_CHANGED")
        }
    }
}

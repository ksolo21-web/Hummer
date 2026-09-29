package com.koenterprises.territorycardstudio.core

import kotlin.math.*

/** Uses the locked PDF's actual widths and the existing label engine. No estimated font widths. */
class NativeRoadLabelProducer(template:ByteArray,private val style:LabelStyleContract) {
    private val first:Int
    private val widths:List<Double>
    init {
        require(BundleIntegrity.sha256(template.inputStream())==LockedPdfRendererAuthorityHashes.TEMPLATE_PDF_SHA256)
        val raw=template.toString(Charsets.ISO_8859_1)
        val font=requireNotNull(Regex("(?:^|\\n)8 0 obj(.*?)endobj",RegexOption.DOT_MATCHES_ALL).find(raw)).groupValues[1]
        first=requireNotNull(Regex("/FirstChar\\s+(\\d+)").find(font)).groupValues[1].toInt()
        val body=requireNotNull(Regex("/Widths\\s*\\[(.*?)]",RegexOption.DOT_MATCHES_ALL).find(font)).groupValues[1]
        widths=Regex("[-+]?[0-9]*\\.?[0-9]+").findAll(body).map {it.value.toDouble()}.toList()
        require(widths.size>=128)
    }
    fun buildingLabel(text:String,center:Point2D):BuildingLabelItem {
        val size=9.0;val width=text.sumOf {ch->require(ch.code-first in widths.indices);widths[ch.code-first]/1000.0*size}
        return BuildingLabelItem(text,center,Point2D(center.x-width/2,center.y+size*0.26),0.0,size)
    }
    fun buildingLabels(members:List<String>,polygon:List<Point2D>):List<BuildingLabelItem> =
        NativeBuildingLabelPlacement.place(members,polygon) {text,size ->
            text.sumOf {ch ->
                require(ch.code-first in widths.indices) { "Building label has unsupported locked-font text" }
                widths[ch.code-first]/1000.0*size
            }
        }
    fun labels(a:CurrentAuthoritativeAssignmentState):List<VerifiedLabelRenderOutput> {
        require(a.layoutMode=="full_map") {"Native initial placement requires the full map layout"}
        val engine=RoadLabelPlacementEngine(style);val result=mutableListOf<VerifiedLabelRenderOutput>()
        val obstacles=mutableListOf<LabelObstacle>()
        fun box(points:List<Point2D>,pad:Double=0.0)=AxisAlignedRect(points.minOf{it.x}-pad,points.minOf{it.y}-pad,points.maxOf{it.x}+pad,points.maxOf{it.y}+pad)
        a.buildings.forEach {obstacles+=LabelObstacle(it.buildingId,LabelObstacleKind.BUILDING,box(it.polygon))}
        for(road in a.roads) {
            val size=style.streetFont.normalSizePt
            val width=road.name.sumOf {ch->require(ch.code-first in widths.indices);widths[ch.code-first]/1000.0*size}
            val id="native-label-${road.segmentId}"
            val lock=LabelNavigationLock(id,road.name,if(road.role=="perimeter")"perimeter_run" else "road_run",road.segmentId,
                "Explicit native source reconciliation",a.authoritySha256,if(road.role=="perimeter")road.insideSide else null,
                if(road.role=="perimeter")if(road.insideSide=="left")"right" else "left" else null)
            val others=a.roads.filter {it.segmentId!=road.segmentId}.map {LabelObstacle(it.segmentId,LabelObstacleKind.ROAD,box(it.points,it.widthPt/2),it.status)}
            val request=StreetLabelRequest(id,road,lock,
                StreetLabelTextMetrics(width,size,size,style.streetFont.family,TextMetricsProvenance.ACTUAL_PDF_FONT_WIDTHS),
                size,AxisAlignedRect(176.0,20.0,747.0,363.0),LabelLayoutScale(1.0),obstacles+others,
                if(road.role=="perimeter")if(road.insideSide=="left")1 else -1 else null)
            val decision=engine.place(request)
            require(decision is LabelPlacementDecision.Placed) {"Label for ${road.name} needs layout review: $decision"}
            val p=decision.placement
            // Keep all geometry and side evidence in the engine's top-origin space.
            // Only the PDF text rotation uses the opposite angle convention.
            val rotation=-p.angleDeg
            val mapped=p.copy(angleDeg=rotation)
            val angle=Math.toRadians(rotation)
            val baselineX=p.center.x-cos(angle)*width/2+sin(angle)*size*0.26
            val baselineY=p.center.y+sin(angle)*width/2+cos(angle)*size*0.26
            result+=VerifiedLabelRenderOutput(id,road.segmentId,road.name,baselineX,baselineY,rotation,size,
                if(p.mode==LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT)0.0 else p.roadGapGeometryUnits,LabelLayoutScale(1.0),mapped)
            obstacles+=LabelObstacle(id,LabelObstacleKind.STREET_LABEL,p.bounds)
        }
        return result
    }
}

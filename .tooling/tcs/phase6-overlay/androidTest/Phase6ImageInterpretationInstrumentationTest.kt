package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

@RunWith(AndroidJUnit4::class)
class Phase6ImageInterpretationInstrumentationTest {
    @Test fun representativeMapRetainsConnectedNumberedFootprints() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val pdf=File(context.cacheDir,"read-only-reference-map.pdf")
        val source=File(context.cacheDir,"read-only-reference-map.png")
        instrumentation.context.assets.open("reference300.pdf").use {input->pdf.outputStream().use {input.copyTo(it)}}
        android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(pdf,android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use {renderer->
            renderer.openPage(0).use {page->
                val scale=minOf(1400.0/page.width,1400.0/page.height)
                val bitmap=android.graphics.Bitmap.createBitmap((page.width*scale).toInt(),(page.height*scale).toInt(),android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                source.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
        }
        try {
            val sha=source.inputStream().use(BundleIntegrity::sha256)
            val result=AndroidMapImageInterpreter().interpret(source,sha,"apartment") {text,center->BuildingLabelItem(text,center,center,0.0,9.0)}
            val report=JSONObject().put("sourceSha256",sha).put("analysis",JSONObject(NativeImageReview.from(result).analysisJson))
                .put("roads",JSONArray(result.roads.map {JSONObject().put("name",it.name).put("status",it.status).put("points",JSONArray(it.points.map {p->JSONArray(listOf(p.x,p.y))}))}))
                .put("buildings",JSONArray(result.buildings.map {JSONObject().put("id",it.buildingId).put("assigned",it.assigned).put("members",JSONArray(it.sourceMembers)).put("polygon",JSONArray(it.polygon.map {p->JSONArray(listOf(p.x,p.y))}))}))
            File(context.filesDir,"phase6-reference-image-analysis.json").writeText(report.toString(2))
            val assigned=result.buildings.filter {it.assigned}
            assertEquals("Three connected assigned footprints must survive image interpretation",3,assigned.size)
            fun normalized(value:String)=value.replace(Regex("\\s+"),"").replace('–','-').replace('—','-').replace('−','-')
            fun inventory(assigned:Boolean)=result.buildings.filter {it.assigned==assigned}.map {b->b.sourceMembers.map(::normalized).toSet()}.toSet()
            assertEquals("Every assigned member must remain associated with its source footprint",setOf(
                setOf("500E","481-495"),setOf("445-475"),setOf("488","490","492","494")),inventory(true))
            assertEquals("Eight excluded connected footprints must survive image interpretation",8,result.buildings.count {!it.assigned})
            result.buildings.forEach {b->assertEquals("Duplicate member in footprint ${b.buildingId}",b.sourceMembers.size,b.sourceMembers.map(::normalized).toSet().size)}
            val excluded=setOf(setOf("415-439"),setOf("401-409"),setOf("440-456"),setOf("424-434","401B"),setOf("447-459"),setOf("433-445"),setOf("417-425"),setOf("418"))
            assertEquals("Every excluded footprint must survive without becoming a road",excluded,inventory(false))
            assertFalse("Direction annotations are not street names",result.roads.any {it.name.startsWith("To ",true)})
            assertTrue("Concave footprint lost",assigned.any {it.polygon.size>=6})
            assertTrue(result.roads.any {it.name.contains("Elizabeth",true)})
            assertTrue(result.roads.any {it.name.contains("Miller",true)})
        } finally {source.delete();pdf.delete()}
    }
}

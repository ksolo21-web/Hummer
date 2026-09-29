package com.koenterprises.territorycardstudio.core

import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Phase7FinalizeContractTest {
    private val root=generateSequence(File(System.getProperty("user.dir")).absoluteFile){it.parentFile}
        .first {File(it,"app/src/main/assets/territory").isDirectory}
    private val assets=File(root,"app/src/main/assets/territory")
    private val template=File(assets,"render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()
    private val producer=NativeRoadLabelProducer(template,File(assets,"R48-Canonical-Style-Tokens.json").reader().use(LabelStyleContractLoader::load))
    private fun poly(x:Double=260.0)=listOf(Point2D(x,130.0),Point2D(x+75,130.0),Point2D(x+75,200.0),Point2D(x,200.0))

    @Test fun ancillaryIsExplicitAndNeverInferredFromUnassignedResidence() {
        val ancillary=NativeBuildingWorkSemantics.createSourceItem("garage","apartment",false,true,emptyList(),poly(),producer::buildingLabels)
        val excluded=NativeBuildingWorkSemantics.createSourceItem("excluded","apartment",false,false,emptyList(),poly(360.0),producer::buildingLabels)
        assertTrue(NativeBuildingWorkSemantics.isContextOnly(ancillary))
        assertFalse(NativeBuildingWorkSemantics.isContextOnly(excluded))
        assertThrows(IllegalArgumentException::class.java) {
            NativeBuildingWorkSemantics.createSourceItem("bad","apartment",true,true,emptyList(),poly(),producer::buildingLabels)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeBuildingWorkSemantics.createSourceItem("bad2","apartment",false,true,listOf("101"),poly(),producer::buildingLabels)
        }
    }

    @Test fun buildingLabelsRemainSourceBoundAndPdfAngleIsReflectedOnce() {
        val rotated=listOf(Point2D(300.0,120.0),Point2D(360.0,80.0),Point2D(395.0,132.0),Point2D(335.0,172.0))
        val labels=producer.buildingLabels(listOf("510-524","528-542"),rotated)
        assertEquals(2,labels.size)
        assertTrue(labels.all {it.fontSizePt==9.0 && BuildingValidationEngine.labelFits(rotated,it)})
        labels.forEach { source ->
            val pdf=BuildingLabelCoordinateTransform.toPdf(source)
            assertEquals(if(source.angleDeg==0.0)0.0 else -source.angleDeg,pdf.rotationDegrees,1e-9)
            assertEquals(source.origin?.x,pdf.originX)
            assertEquals(source.origin?.y,pdf.originTopY)
        }
    }

    @Test fun contextPdfUsesGrayAndExactValidatorChecksItsGeometry() {
        val identity=TerritoryIdentity.parse("A998")
        val ancillary=NativeBuildingWorkSemantics.createSourceItem("garage","apartment",false,true,emptyList(),poly(),producer::buildingLabels)
        val spec=CandidatePdfRenderSpec(identity,"NOT FOR FIELD USE","9/29/2026",
            listOf("Directions: Synthetic Phase 7 component check. NOT FOR FIELD USE."),
            buildings=listOf(NativeBuildingWorkSemantics.toPdf(ancillary)),nonFieldFixture=true)
        val rendered=CandidatePdfRenderer.renderNonFieldFixture(template,spec)
        assertTrue(rendered.exactValidation.passed)
        val raw=rendered.pdfBytes.toString(Charsets.ISO_8859_1)
        assertTrue(raw.contains("% TCS_BUILDING_WORK_STATUS garage context"))
        assertTrue(raw.contains("0.8902 0.9059 0.9137 rg"))
        assertTrue(raw.contains("0.5216 0.5451 0.5804 RG"))
        val tampered=raw.replace("0.5216 0.5451 0.5804 RG","1 0.0784 0.2078 RG").toByteArray(Charsets.ISO_8859_1)
        assertFalse(CandidatePdfExactValidator.validate(tampered,spec,spec.canonicalSha256()).passed)
    }

    @Test fun legacyResidentialPdfShapeDefaultsRemainRedSemantics() {
        val legacy=PdfBuildingShape("legacy","",poly().map {it.x to it.y},assigned=false)
        assertFalse(legacy.contextOnly)
    }
}

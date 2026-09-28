package com.koenterprises.territorycardstudio

import androidx.test.core.app.ApplicationProvider
import com.koenterprises.territorycardstudio.core.*
import java.io.File
import java.time.Instant

/** Isolated identities and deterministic transport only; never supplies prepared input or approval. */
internal class Phase6NativeFixture(val mode:WorkspaceMode,val fresh:Boolean=true,val multiUnit:Boolean=false):AutoCloseable {
    val app=ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
    val original=app.services
    val identity=TerritoryIdentity(if(mode==WorkspaceMode.TELEPHONE)998 else 999,if(mode==WorkspaceMode.TELEPHONE)TerritoryClass.Telephone else if(multiUnit)TerritoryClass.Apartment else TerritoryClass.Residential,'a')
    val id=identity.displayId
    private val old=original.knowledgeBase.assignments.getValue("273")
    val slot=old.copy(housingType=if(multiUnit)"condo" else old.housingType,status="needs_new_card",displayId=id,identity=identity,baseNumber=identity.baseNumber,cardClass=identity.territoryClass.token,suffix="a",slot=id,canonicalFilename=identity.canonicalFilename,needsNewCard=true,newCardApproved=false,fieldReleaseAllowedForExactArtifact=false,legacyReferenceFile="SYNTHETIC-NOT-FOR-FIELD-USE")
    val kb=original.knowledgeBase.copy(assignments=original.knowledgeBase.assignments-"273"+(id to slot),referenceRoles=original.knowledgeBase.referenceRoles+(slot.referenceFile to original.knowledgeBase.referenceRoles.getValue(slot.referenceFile).copy(displayId=id,fieldReleaseAllowed=false)))
    val root=File(app.noBackupFilesDir,"phase6-synthetic-${mode.name}${if(multiUnit)"-multiunit" else ""}").apply {if(fresh)deleteRecursively();mkdirs()}
    val sources=SourceMapIntakeStore(app).also {if(fresh)it.clear(id)}
    val template=app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use {it.readBytes()}
    val artifacts=AndroidPdfArtifactService(kb,root,template)
    val drafts=AndroidNativeDraftStore(app,kb,sources,File(root,"native"))
    val models=AndroidRenderModelService(kb,drafts.ledger)
    val coordinator=AndroidBuildWorkflowCoordinator(kb,models,artifacts,sources)
    val preview=AndroidPdfPreviewService(coordinator,File(root,"preview"),artifacts)
    val review=AndroidCandidateReviewService(coordinator,File(root,"review"))
    val lifecycle=AndroidCandidateLifecycleService(kb,coordinator,sources,File(root,"lifecycle"))
    val style=app.assets.open("territory/R48-Canonical-Style-Tokens.json").bufferedReader().use(LabelStyleContractLoader::load)
    val authoring=AndroidNativeAuthoringService(kb,original.activePolicy,drafts,sources,coordinator,NativeRoadLabelProducer(template,style),::evidence)
    val output=AndroidFinalOutputService(kb,original.activePolicy,coordinator,lifecycle,File(root,"output"),::evidence,nativeEligibility=drafts.ledger)
    val services=object:ProductionWorkflowServices by original {
        override val knowledgeBase=this@Phase6NativeFixture.kb
        override val buildWorkflow=coordinator
        override val pdfPreview=preview
        override val candidateReview=review
        override val candidateLifecycle=lifecycle
        override val nativeDrafts=drafts
        override val nativeAuthoring=authoring
        override val finalOutput=output
    }
    fun evidence(r:LiveGeometryVerificationRequest):List<ProviderVerificationEvidence> {
        val ids=r.roadTargets.map {it.targetId}.toSet()
        return (listOf("oakland_county_roads","census_tigerweb_transportation")+if(multiUnit)listOf("oakland_county_site_addresses","oakland_county_buildings") else emptyList()).map {provider->ProviderVerificationEvidence(provider,r.requestFingerprint,true,Instant.now().toString(),BundleIntegrity.sha256("$provider|${r.requestFingerprint}".byteInputStream()),if(provider=="oakland_county_buildings")"synthetic-fixture-2026-09-28" else null,if(provider in setOf("oakland_county_roads","census_tigerweb_transportation"))ids else emptySet(),if(provider in setOf("oakland_county_roads","census_tigerweb_transportation"))ids else emptySet(),emptySet(),emptySet(),emptySet(),if(provider=="oakland_county_site_addresses")true else null,if(provider=="oakland_county_buildings")true else null,null)}
    }
    fun sourcePdf():ByteArray {
        val doc=android.graphics.pdf.PdfDocument();val page=doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(768,480,1).create())
        val p=android.graphics.Paint().apply {textSize=12f;color=android.graphics.Color.BLACK}
        page.canvas.drawColor(android.graphics.Color.WHITE)
        page.canvas.drawText("SYNTHETIC CURRENT ASSIGNMENT - NOT FOR FIELD USE",30f,30f,p)
        page.canvas.drawText("Territory $id: explicit test source; no real congregation facts",30f,50f,p)
        listOf(Triple("Alpha Rd: inside RIGHT",115f,android.graphics.Color.YELLOW),Triple("Beta Dr: BOTH sides",200f,android.graphics.Color.GREEN),Triple("Gamma Ct: DO NOT WORK",285f,android.graphics.Color.RED)).forEach {(name,y,color)->
            p.color=color;p.strokeWidth=4f;page.canvas.drawLine(225f,y,650f,y,p);p.color=android.graphics.Color.BLACK;page.canvas.drawText(name,330f,y-10,p)
        }
        page.canvas.drawText("Alpha endpoints: junctions. Beta/Gamma endpoints: terminations.",40f,350f,p)
        if(multiUnit) {
            p.color=android.graphics.Color.rgb(81,199,43);page.canvas.drawRect(550f,220f,620f,260f,p)
            p.color=android.graphics.Color.BLACK;page.canvas.drawText("9001 / 9002",552f,244f,p)
            page.canvas.drawText("Assigned condo footprint: members 9001 and 9002",40f,370f,p)
            p.color=android.graphics.Color.rgb(255,20,53);p.style=android.graphics.Paint.Style.STROKE;p.strokeWidth=1f
            page.canvas.drawRect(430f,220f,500f,260f,p);p.style=android.graphics.Paint.Style.FILL;p.color=android.graphics.Color.BLACK
            page.canvas.drawText("9010",449f,244f,p);page.canvas.drawText("Excluded footprint: 9010 - DO NOT WORK",40f,390f,p)
        }
        doc.finishPage(page);val out=java.io.ByteArrayOutputStream();doc.writeTo(out);doc.close();return out.toByteArray()
    }
    fun sourcePng():ByteArray {
        val file=File.createTempFile("phase6-source-",".pdf",app.cacheDir)
        try {file.writeBytes(sourcePdf())
            android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use {pdf->
                pdf.openPage(0).use {page->
                    val bitmap=android.graphics.Bitmap.createBitmap(1400,875,android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return out.toByteArray()
                }
            }
        } finally {file.delete()}
    }
    override fun close() { /* Evidence persists for restart assertions; next fresh fixture resets only its synthetic identity. */ }
}

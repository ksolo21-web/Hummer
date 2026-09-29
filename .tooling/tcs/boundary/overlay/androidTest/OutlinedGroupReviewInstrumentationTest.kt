package com.koenterprises.territorycardstudio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.floor

@RunWith(AndroidJUnit4::class)
class OutlinedGroupReviewInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private fun source():File {
        val i=InstrumentationRegistry.getInstrumentation()
        val file=File(i.targetContext.cacheDir,"outlined-group-exact-a265.jpg")
        i.context.assets.open("phase7-03-26175.jpg").use {input->file.outputStream().use {input.copyTo(it)}}
        assertEquals("8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d",file.inputStream().use(BundleIntegrity::sha256))
        return file
    }
    private fun cell(p:OutlinedRoadProposal)=floor(p.road.points.first().x/96).toInt() to floor(p.road.points.first().y/96).toInt()
    private fun contained(p:OutlinedRoadProposal):Boolean {val c=cell(p);return p.road.points.all {floor(it.x/96).toInt()==c.first && floor(it.y/96).toInt()==c.second}}
    @Test fun exactSourceGroupsKeepIndividualAccountingAndRejectInvalidOmissions() {
        val file=source();val sha=file.inputStream().use(BundleIntegrity::sha256)
        val draft=AndroidMapImageInterpreter().interpretOutlined(file,sha)
        val review=OutlinedNativeReview.from(draft)
        val group=review.extraction.roads.filter(::contained).groupBy(::cell).values.first {it.size>=2}.take(2)
        val c=cell(group.first());val ids=group.map {it.road.id}.toSet()
        val (next,roads)=reviewOutlinedGroup(review,draft.roads,ids,c.first,c.second,OutlinedSpanDisposition.NON_ROAD,"MAP_SYMBOL","Test-only disposition to verify accounting; not approved card evidence")
        assertEquals(review.extraction,next.extraction)
        assertEquals(ids,next.spans.map {it.candidateId}.toSet())
        assertEquals(review.extraction.roads.size,next.extraction.roads.size)
        assertEquals(next,OutlinedNativeReview(next.document))
        assertFalse(next.complete(roads))
        val other=review.extraction.roads.first {it.road.id !in ids}.road.id
        assertTrue(next.coverageFailures(roads).contains("UNACCOUNTED_CANDIDATE:$other"))
        assertEquals(draft.roads.filterNot {it.segmentId in ids},roads)
        fun rejected(r:OutlinedNativeReview=review,selected:Set<String> = ids,x:Int=c.first,y:Int=c.second,kind:OutlinedSpanDisposition=OutlinedSpanDisposition.NON_ROAD) {
            assertTrue(runCatching {reviewOutlinedGroup(r,draft.roads,selected,x,y,kind,"MAP_SYMBOL","Test rejection must leave original immutable")}.isFailure)
        }
        rejected(r=next);rejected(selected=setOf("not-a-source-id"));rejected(x=c.first+1);rejected(kind=OutlinedSpanDisposition.ROAD)
        rejected(selected=review.extraction.roads.take(13).map {it.road.id}.toSet())
        val partial=review.reviewSpan(group.first().road.id,0.0,0.5,OutlinedSpanDisposition.NON_ROAD,"Partial test disposition",draft.roads,null)
        rejected(r=partial)
        val crossing=review.extraction.roads.first { !contained(it) };val crossCell=cell(crossing)
        rejected(selected=setOf(crossing.road.id),x=crossCell.first,y=crossCell.second)
        val interior=review.extraction.roads.first {contained(it) && it.relation==BoundaryRoadRelation.INTERIOR};val innerCell=cell(interior)
        rejected(selected=setOf(interior.road.id),x=innerCell.first,y=innerCell.second,kind=OutlinedSpanDisposition.OUTSIDE_CONTEXT)
        val mixed=review.extraction.roads.filter(::contained).groupBy(::cell).values.first {rows->rows.any {it.relation==BoundaryRoadRelation.INTERIOR} && rows.any {it.relation==BoundaryRoadRelation.EXTERIOR}}
        val mixedIds=setOf(mixed.first {it.relation==BoundaryRoadRelation.INTERIOR}.road.id,mixed.first {it.relation==BoundaryRoadRelation.EXTERIOR}.road.id)
        val mixedCell=cell(mixed.first())
        rejected(selected=mixedIds,x=mixedCell.first,y=mixedCell.second,kind=OutlinedSpanDisposition.OUTSIDE_CONTEXT)
        assertTrue(review.spans.isEmpty())
        assertEquals(sha,file.inputStream().use(BundleIntegrity::sha256))
    }
    @Test fun exactSourceSelectedCropIsVisibleAndCannotRecordWithoutEvidence() {
        val file=source();val sha=file.inputStream().use(BundleIntegrity::sha256)
        val draft=AndroidMapImageInterpreter().interpretOutlined(file,sha)
        val review=OutlinedNativeReview.from(draft)
        val first=review.extraction.roads.filter(::contained).sortedWith(compareBy({cell(it).second},{cell(it).first})).first()
        rule.setContent {Column(Modifier.verticalScroll(rememberScrollState())) {OutlinedGroupedReview(review,draft.roads,file){_,_->error("Unreviewed group must not be recorded")}}}
        rule.onNodeWithTag("outlined-group-open").performClick()
        rule.onNodeWithTag("outlined-group-select-${first.road.id}").performScrollTo().performClick()
        rule.onNodeWithTag("outlined-group-record").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("outlined-group-crop").performScrollTo()
        rule.waitUntil(30000){rule.onAllNodesWithContentDescription("Exact source region with selected traces").fetchSemanticsNodes().isNotEmpty()}
        rule.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation()
        requireNotNull(i.uiAutomation.takeScreenshot()).let {b->File(i.targetContext.filesDir,"outlined-group-a265-selected.png").outputStream().use {assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
        assertEquals(sha,file.inputStream().use(BundleIntegrity::sha256))
    }
}

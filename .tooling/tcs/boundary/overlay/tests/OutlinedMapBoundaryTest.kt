package com.koenterprises.territorycardstudio.core

import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class OutlinedMapBoundaryTest {
    private val root=generateSequence(File(System.getProperty("user.dir")).absoluteFile){it.parentFile}.first {File(it,"app/src/main/assets/territory").isDirectory}
    private fun source()=ImageIO.read(File(root,"core/src/test/resources/boundary/source-26435.jpg"))
    @Test fun exactUserMapPreservesConcaveOutlineAndNarrowStem() {
        val image=source();val p=IntArray(image.width*image.height);image.getRGB(0,0,image.width,image.height,p,0,image.width)
        val b=OutlinedMapBoundaryDetector.detect(image.width,image.height,p)
        assertEquals(1079,b.width);assertEquals(547,b.height)
        assertTrue(b.enclosedPixels in 48000..51000)
        assertTrue(OutlinedMapBoundaryDetector.inside(Point2D(580.0,120.0),b.polygon))
        assertTrue(OutlinedMapBoundaryDetector.inside(Point2D(500.0,400.0),b.polygon))
        assertTrue(OutlinedMapBoundaryDetector.inside(Point2D(700.0,420.0),b.polygon))
        assertFalse(OutlinedMapBoundaryDetector.inside(Point2D(500.0,200.0),b.polygon))
        assertFalse(OutlinedMapBoundaryDetector.inside(Point2D(660.0,250.0),b.polygon))
        assertFalse(OutlinedMapBoundaryDetector.inside(Point2D(550.0,465.0),b.polygon))
        assertFalse(OutlinedMapBoundaryDetector.inside(Point2D(750.0,250.0),b.polygon))
        val out=File(root.parentFile,"evidence/boundary-analysis.json");out.parentFile.mkdirs()
        out.writeText("""{"width":${b.width},"height":${b.height},"enclosedPixels":${b.enclosedPixels},"polygon":[${b.polygon.joinToString(","){"[${it.x},${it.y}]"}}]}""")
    }
    @Test fun brokenUserOutlineIsNotSilentlyClosed() {
        val image=source();val p=IntArray(image.width*image.height);image.getRGB(0,0,image.width,image.height,p,0,image.width)
        for(y in 82..92)for(x in 584..590)p[y*image.width+x]=0xFFFFFFFF.toInt()
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(image.width,image.height,p)}
    }
    @Test fun competingAndClippedOutlinesAreRejected() {
        val w=400;val h=240;val p=IntArray(w*h){0xFFDDDDDD.toInt()}
        fun rect(l:Int,t:Int,r:Int,b:Int){for(x in l..r){p[t*w+x]=0xFF000000.toInt();p[b*w+x]=0xFF000000.toInt()};for(y in t..b){p[y*w+l]=0xFF000000.toInt();p[y*w+r]=0xFF000000.toInt()}}
        rect(20,20,170,200);rect(220,20,370,200)
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(w,h,p)}
        p.fill(0xFFDDDDDD.toInt());rect(0,20,170,200)
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(w,h,p)}
    }
    @Test fun neutralColoredAreasDoNotEstablishBoundaryOrWorkStatus() {
        val w=300;val h=200;val p=IntArray(w*h){when(it%w/75){0->0xFF51C72B.toInt();1->0xFFFF1435.toInt();2->0xFF22CCFF.toInt();else->0xFFFFDC18.toInt()}}
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(w,h,p)}
    }
}

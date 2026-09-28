package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.koenterprises.territorycardstudio.core.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/** Side-by-side original and proposed connection. Decoding never writes to the source. */
@Composable internal fun BoundaryRepairPreview(file:File,sourceSha256:String,repair:BoundaryGapRepair,onReadable:(Boolean)->Unit) {
    var bitmap by remember(file.path,sourceSha256,repair){mutableStateOf<Bitmap?>(null)}
    var origin by remember(file.path,sourceSha256,repair){mutableStateOf(Point2D(0.0,0.0))}
    var error by remember(file.path,sourceSha256,repair){mutableStateOf<String?>(null)}
    LaunchedEffect(file.path,sourceSha256,repair) {
        onReadable(false)
        runCatching {withContext(Dispatchers.IO) {
            require(file.inputStream().use(BundleIntegrity::sha256)==sourceSha256){"The source changed. Import it again before reviewing this connection."}
            val source=AndroidMapImageInterpreter().decode(file)
            try {
                val left=max(0,min(repair.start.x,repair.end.x).toInt()-40)
                val top=max(0,min(repair.start.y,repair.end.y).toInt()-40)
                val right=min(source.width,max(repair.start.x,repair.end.x).toInt()+41)
                val bottom=min(source.height,max(repair.start.y,repair.end.y).toInt()+41)
                val crop=Bitmap.createBitmap(source,left,top,right-left,bottom-top)
                (if(crop===source)requireNotNull(crop.copy(Bitmap.Config.ARGB_8888,false))else crop) to Point2D(left.toDouble(),top.toDouble())
            } finally {source.recycle()}
        }}.onSuccess {(b,p)->bitmap=b;origin=p;onReadable(true)}.onFailure {error=it.message}
    }
    DisposableEffect(bitmap){val old=bitmap;onDispose {old?.recycle()}}
    Row(Modifier.fillMaxWidth().testTag("boundary-repair-closeup"),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(false,true).forEach {proposal->Column(Modifier.weight(1f)) {
            Text(if(proposal)"Suggested connection" else "Original image")
            Box(Modifier.fillMaxWidth().height(190.dp)) {
                bitmap?.let {b->
                    Image(b.asImageBitmap(),if(proposal)"Proposed boundary connection closeup" else "Original boundary gap closeup",Modifier.fillMaxSize())
                    if(proposal)Canvas(Modifier.fillMaxSize()) {
                        val scale=min(size.width/b.width,size.height/b.height)
                        val dx=(size.width-b.width*scale)/2;val dy=(size.height-b.height*scale)/2
                        fun at(p:Point2D)=Offset(dx+((p.x-origin.x)*scale).toFloat(),dy+((p.y-origin.y)*scale).toFloat())
                        drawLine(Color(0xFFFF8800),at(repair.start),at(repair.end),2.dp.toPx())
                        drawCircle(Color(0xFF0077FF),3.dp.toPx(),at(repair.start));drawCircle(Color(0xFF0077FF),3.dp.toPx(),at(repair.end))
                    }
                }
            }
        }}
    }
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
}

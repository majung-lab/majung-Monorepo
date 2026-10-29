package com.majung.vehicle.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ponytail: Figma 목업 지도를 레이어 그대로 깐다 (값은 dp, Figma 좌표). 지도 SDK 붙일 때 교체.
class MapLayer(val res: Int, val x: Float, val y: Float, val w: Float, val h: Float)
class MapText(val text: String, val x: Float, val y: Float, val w: Float, val size: Int, val bold: Boolean, val color: Color)
class MapScene(
    val background: Color,
    val canvasX: Float,
    val canvasY: Float,
    val canvasW: Float,
    val canvasH: Float,
    val layers: List<MapLayer>,
    val texts: List<MapText>,
)

/** onPan이 있으면 끌어서 지도를 움직인다 (핀은 화면 쪽에서 고정) */
@Composable
fun MockMap(scene: MapScene, modifier: Modifier = Modifier, onPan: (() -> Unit)? = null) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val drag = if (onPan == null) Modifier else Modifier.pointerInput(Unit) {
        detectDragGestures { change, delta ->
            change.consume()
            pan += with(density) { Offset(delta.x.toDp().value, delta.y.toDp().value) }
            onPan()
        }
    }
    Box(modifier.clipToBounds().background(scene.background).then(drag)) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset((scene.canvasX + pan.x).dp, (scene.canvasY + pan.y).dp)
                .requiredSize(scene.canvasW.dp, scene.canvasH.dp),
        ) {
            scene.layers.forEach { l ->
                Image(painterResource(l.res), null, Modifier.offset(l.x.dp, l.y.dp).requiredSize(l.w.dp, l.h.dp))
            }
            scene.texts.forEach { t ->
                Box(Modifier.offset(t.x.dp, t.y.dp).size(t.w.dp, (t.size * 1.4f).dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        t.text,
                        Modifier.wrapContentWidth(unbounded = true),
                        t.color, t.size.sp,
                        fontWeight = if (t.bold) FontWeight.Bold else FontWeight.Normal,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

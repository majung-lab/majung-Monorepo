package com.majung.vehicle.ui.proposal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.R
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.MapLayer
import com.majung.vehicle.ui.theme.MapScene
import com.majung.vehicle.ui.theme.MapText
import com.majung.vehicle.ui.theme.MockMap
import com.majung.vehicle.ui.theme.SecondaryButton
import com.majung.vehicle.ui.theme.card

/** Figma C10 지도: 스타필드 하남 출입구 4곳 + 지금 핀(서문) */
internal val STARFIELD_MAP = MapScene(
    background = Color(0xFF1A2129),
    canvasX = 110f, canvasY = -190f, canvasW = 1000f, canvasH = 900f,
    layers = listOf(
        MapLayer(R.drawable.map_c10_base, 0f, 0f, 1000f, 900f),
        MapLayer(R.drawable.map_c10_park, 40f, 640f, 200f, 200f),
        MapLayer(R.drawable.map_c10_road_h_top, 0f, 156f, 1000f, 28f),
        MapLayer(R.drawable.map_c10_road_h_bottom, 0f, 708f, 1000f, 24f),
        MapLayer(R.drawable.map_c10_road_v, 187f, 0f, 26f, 900f),
        MapLayer(R.drawable.map_c10_road_v, 807f, 0f, 26f, 900f),
        MapLayer(R.drawable.map_c10_road_loop, 200f, 170f, 620f, 550f),
        MapLayer(R.drawable.map_c10_building, 299f, 249f, 402f, 352f),
        MapLayer(R.drawable.map_c10_access_point, 491f, 241f, 18f, 18f),
        MapLayer(R.drawable.map_c10_access_point, 291f, 421f, 18f, 18f),
        MapLayer(R.drawable.map_c10_access_point, 691f, 421f, 18f, 18f),
        MapLayer(R.drawable.map_c10_access_point, 611f, 591f, 18f, 18f),
        MapLayer(R.drawable.map_c10_pin_now, 284f, 382f, 32f, 48.1f),
        MapLayer(R.drawable.map_c10_pin_hole, 294f, 392f, 12f, 12f),
        MapLayer(R.drawable.map_c10_tag_now, 256f, 350f, 88f, 26f),
    ),
    texts = listOf(
        MapText("스타필드 하남", 448f, 414f, 104f, 18, bold = true, color = Ink.sub),
        MapText("북문 로비", 472f, 272f, 56f, 14, bold = false, color = Ink.sub),
        MapText("서문 로비", 318f, 456f, 56f, 14, bold = false, color = Ink.sub),
        MapText("동문 로비", 628f, 456f, 56f, 14, bold = false, color = Ink.sub),
        MapText("B1 승하차장", 584f, 566f, 72f, 14, bold = false, color = Ink.sub),
        MapText("지금", 288f, 354f, 26f, 14, bold = true, color = Ink.bg),
    ),
)

internal val PinTag = Color(0xFFFF7A45)

// C10 운전자가 위치 바꾸기 — 지도를 끌어 핀을 맞추고 제안 → 가족 응답 대기
// ponytail: 핀 → 가까운 출입구 계산은 시설 데이터 붙일 때. 지금은 Figma 예시값.
@Composable
fun MovePinScreen(
    current: String,
    driving: Boolean = false,
    onBack: () -> Unit = {},
    onPropose: (String) -> Unit = {},
    onCancel: () -> Unit = {},
) {
    var moved by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    val target = if (moved) "북문 로비" else current
    // 대기 중 뒤로 = 제안 취소 (제안이 조용히 남지 않게)
    val back = if (waiting) onCancel else onBack
    BackHandler(onBack = back)
    val distM = if (moved) 120 else 0

    Row(Modifier.fillMaxSize().background(Ink.bg)) {
        Box(Modifier.width(820.dp).fillMaxHeight()) {
            MockMap(STARFIELD_MAP, Modifier.fillMaxSize(), onPan = if (waiting || driving) null else ({ moved = true }))
            Text(
                "지도를 움직여 핀을 맞춰 주세요",
                Modifier.padding(16.dp).card(Ink.surface, 21).padding(horizontal = 15.dp, vertical = 9.dp),
                Ink.text, 16.sp,
            )
            Text(
                "여기로 바꿀까요?",
                Modifier.offset(340.dp, 152.dp).size(140.dp, 30.dp).clip(RoundedCornerShape(16.dp)).background(PinTag).padding(top = 5.dp),
                Ink.bg, 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Image(painterResource(R.drawable.ic_pin_target), null, Modifier.offset(390.dp, 192.dp))
        }
        Column(Modifier.weight(1f).padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 26.dp)) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Ink.surface).clickable(onClick = back),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_back), "뒤로", tint = Color.Unspecified)
            }
            Text("지금 픽업 장소 · $current", Modifier.padding(top = 16.dp), Ink.sub, 16.sp)
            Text(target, Modifier.padding(top = 14.dp), Ink.text, 30.sp, fontWeight = FontWeight.Bold)
            Text("확인된 승하차 지점", Modifier.padding(top = 14.dp), Ink.ok, 16.sp)
            Text("지금 위치에서 ${distM}m", Modifier.padding(top = 16.dp), Ink.sub, 16.sp)
            Spacer(Modifier.weight(1f))
            when {
                waiting -> {
                    Text("가족 응답 기다리는 중 · 10분 지나면 그대로예요", color = Ink.sub, fontSize = 16.sp)
                    SecondaryButton("제안 취소", onCancel, Modifier.padding(top = 10.dp).fillMaxWidth().height(68.dp))
                }
                driving -> Box(
                    Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(14.dp)).background(Ink.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("정차 후 조정할 수 있어요", color = Ink.sub, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                moved -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.accent)
                        .clickable { waiting = true; onPropose(target) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("여기로 조정하기", color = Ink.onAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                else -> Box(
                    Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(14.dp)).background(Ink.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("핀을 옮겨 주세요", color = Ink.sub, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun MovePinPreview() = MovePinScreen("서문 로비")

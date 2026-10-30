package com.majung.vehicle.ui.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.R
import com.majung.vehicle.domain.model.VisitMode
import com.majung.vehicle.ui.theme.BackHeader
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.card

private data class ModeItem(val mode: VisitMode, val icon: Int, val title: String, val desc: String)

private val MODES = listOf(
    ModeItem(VisitMode.PICKUP, R.drawable.ic_mode_pickup, "픽업하기", "가족을 태울 곳을 정하고, 링크로 차 위치를 알려요"),
    ModeItem(VisitMode.PARK, R.drawable.ic_mode_park, "그냥가기", "주차할 곳을 선택해요"),
    ModeItem(VisitMode.DROPOFF, R.drawable.ic_mode_dropoff, "내려주기", "가족이 내릴 곳을 정해요 · 링크 없이 바로 출발"),
)

// C03 모드 선택 — 픽업하기를 기본 강조 (핵심 시나리오)
@Composable
fun ModeScreen(
    placeName: String,
    onBack: () -> Unit = {},
    onSelect: (VisitMode) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(Ink.bg).padding(start = 40.dp, end = 40.dp, top = 30.dp, bottom = 28.dp)) {
        BackHeader("어떻게 가세요?", onBack, subtitle = placeName)
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(21.dp)) {
            MODES.forEach { item ->
                val primary = item.mode == VisitMode.PICKUP
                val shape = RoundedCornerShape(18.dp)
                val box = if (primary) {
                    Modifier.clip(shape).background(Ink.accentSurface).border(2.dp, Ink.accent, shape)
                } else {
                    Modifier.card(Ink.surface, 18)
                }
                Column(
                    box.weight(1f).fillMaxHeight().clickable { onSelect(item.mode) }.padding(26.dp),
                ) {
                    Icon(painterResource(item.icon), null, tint = Color.Unspecified)
                    Spacer(Modifier.weight(1f))
                    Text(item.title, color = Ink.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        item.desc,
                        Modifier.padding(top = 4.dp),
                        if (primary) Ink.subOnAccent else Ink.sub, 16.sp, lineHeight = 24.sp,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun ModePreview() = ModeScreen("스타필드 하남")

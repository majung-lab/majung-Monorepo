package com.majung.vehicle.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.R
import com.majung.vehicle.domain.model.AccessPoint
import com.majung.vehicle.domain.model.SAMPLE_ACCESS_POINTS
import com.majung.vehicle.domain.model.SAMPLE_WEATHER
import com.majung.vehicle.domain.model.VisitMode
import com.majung.vehicle.ui.theme.BackHeader
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.PrimaryButton
import com.majung.vehicle.ui.theme.Tag
import com.majung.vehicle.ui.theme.card

// C04 추천 / 선택 — 날씨 기준 추천 지점을 먼저 골라 둔다
@Composable
fun PlaceScreen(
    placeName: String,
    mode: VisitMode,
    onBack: () -> Unit = {},
    onConfirm: (AccessPoint) -> Unit = {},
) {
    val points = SAMPLE_ACCESS_POINTS
    var selected by remember { mutableStateOf(points.firstOrNull { it.recommended } ?: points.first()) }
    val title = when (mode) {
        VisitMode.PICKUP -> "어디서 태울까요?"
        VisitMode.DROPOFF -> "어디서 내려줄까요?"
        VisitMode.PARK -> "어디에 세울까요?"
    }

    Column(Modifier.fillMaxSize().background(Ink.bg).padding(start = 40.dp, end = 40.dp, top = 30.dp, bottom = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { BackHeader(title, onBack, subtitle = placeName) }
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(Ink.info).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_rain), null, tint = Color.Unspecified)
                Text(SAMPLE_WEATHER, Modifier.padding(start = 10.dp), Ink.text, 16.sp)
            }
        }
        Row(Modifier.padding(top = 78.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            points.forEach { p -> PointCard(p, p == selected, Modifier.weight(1f)) { selected = p } }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("다음", { onConfirm(selected) }, Modifier.align(Alignment.End).width(316.dp))
    }
}

@Composable
private fun PointCard(point: AccessPoint, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val box = if (selected) {
        modifier.clip(shape).background(Ink.accentSurface).border(2.dp, Ink.accent, shape)
    } else {
        modifier.card(Ink.surface, 18)
    }
    Column(box.height(168.dp).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(point.name, Modifier.weight(1f), Ink.text, 26.sp, fontWeight = FontWeight.Bold)
            if (point.recommended) {
                Text(
                    "추천",
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.accent).padding(horizontal = 10.dp, vertical = 4.dp),
                    Ink.onAccent, 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            point.reason,
            Modifier.padding(top = 6.dp),
            if (selected) Ink.accentBody else Ink.sub, 18.sp, lineHeight = 26.sp,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            point.tags.forEach { Tag(it, if (selected) Ink.accentTag else Ink.tag) }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun PlacePreview() = PlaceScreen("스타필드 하남", VisitMode.PICKUP)

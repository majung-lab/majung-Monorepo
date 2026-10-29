package com.majung.vehicle.ui.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.R
import com.majung.vehicle.domain.model.Place
import com.majung.vehicle.domain.model.SAMPLE_PLACES
import com.majung.vehicle.ui.theme.BackHeader
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.Tag
import com.majung.vehicle.ui.theme.card
import com.majung.vehicle.ui.theme.dashedCard

// C02 목적지 입력
@Composable
fun DestinationScreen(
    initialQuery: String = "",
    onBack: () -> Unit = {},
    onSelect: (Place) -> Unit = {},
) {
    var query by remember { mutableStateOf(initialQuery) }
    val results = SAMPLE_PLACES.filter { query.isNotBlank() && it.name.contains(query.trim()) }

    Column(Modifier.fillMaxSize().background(Ink.bg).padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        BackHeader("목적지 입력", onBack)
        Row(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.raised)
                .border(1.dp, Ink.accent, RoundedCornerShape(14.dp))
                .padding(horizontal = 23.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), null, Modifier.size(24.dp), tint = Color.Unspecified)
            BasicTextField(
                query,
                { query = it },
                Modifier.padding(start = 14.dp).fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = Ink.text, fontSize = 20.sp),
                cursorBrush = SolidColor(Ink.accent),
            )
        }
        LazyColumn(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(results) { PlaceRow(it, onSelect) }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onSelect: (Place) -> Unit) {
    val base = Modifier.fillMaxWidth().height(92.dp)
    val box = if (place.registered) base.card(Ink.surface, 14).clickable { onSelect(place) } else base.dashedCard(14)
    Row(box.padding(horizontal = 23.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                place.name,
                color = if (place.registered) Ink.text else Ink.sub,
                fontSize = 20.sp,
                fontWeight = if (place.registered) FontWeight.Medium else FontWeight.Normal,
            )
            Text(
                if (place.registered) "${place.address} · ${place.distanceKm}km" else "아직 확인 안 된 시설 · 일반 길안내만 가능",
                Modifier.padding(top = 2.dp),
                Ink.sub, 16.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (place.pickupZoneVerified) Tag("승하차 구역 확인됨")
            if (place.indoorParking) Tag("실내 주차")
        }
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun DestinationPreview() = DestinationScreen(initialQuery = "스타필드")

package com.majung.vehicle.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.domain.model.SAMPLE_RECORDS
import com.majung.vehicle.domain.model.VisitRecord
import com.majung.vehicle.domain.model.label
import com.majung.vehicle.ui.theme.BackHeader
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.Tag
import com.majung.vehicle.ui.theme.card

// C09 목적지 기록
@Composable
fun HistoryScreen(
    records: List<VisitRecord> = SAMPLE_RECORDS,
    onBack: () -> Unit = {},
    onRevisit: (VisitRecord) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(Ink.bg).padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        BackHeader("목적지 기록", onBack)
        LazyColumn(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(records) { r ->
                Row(
                    Modifier.fillMaxWidth().height(92.dp).card(Ink.surface, 14).padding(start = 23.dp, end = 23.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(r.place, color = Ink.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text("${r.date} · ${r.mode.label}", Modifier.padding(start = 10.dp, bottom = 1.dp), Ink.sub, 16.sp)
                        }
                        Text(r.outcome, Modifier.padding(top = 2.dp), Ink.subOnAccent, 16.sp)
                    }
                    Tag(r.weatherTime)
                    Box(
                        Modifier
                            .padding(start = 20.dp)
                            .width(118.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.accentSurface)
                            .border(1.dp, Ink.accent, RoundedCornerShape(12.dp))
                            .clickable { onRevisit(r) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("다시 가기", color = Ink.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun HistoryPreview() = HistoryScreen()

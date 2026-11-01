package com.majung.vehicle.ui.proposal

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.domain.model.LatLng
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.card

/** 위치 변경 제안 중 기존 목적지·제안 목적지·둘 다를 골라 본다 */
@Composable
fun ProposalMapViews(from: LatLng, to: LatLng, onView: (List<LatLng>) -> Unit, modifier: Modifier = Modifier) {
    val views = listOf("기존 목적지" to listOf(from), "변경제안 목적지" to listOf(to), "전체" to listOf(from, to))
    var selected by remember { mutableIntStateOf(2) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        views.forEachIndexed { index, (label, points) ->
            val border = if (index == selected) Modifier.border(2.dp, Ink.accent, RoundedCornerShape(12.dp)) else Modifier
            Text(
                label,
                Modifier.card(Ink.surface, 12).then(border)
                    .clickable { selected = index; onView(points.toList()) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                Ink.text, 16.sp,
            )
        }
    }
}

package com.majung.vehicle.ui.review

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.R
import com.majung.vehicle.ui.theme.BrandBar
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.SecondaryButton

enum class Rating(val label: String, val icon: Int) {
    GOOD("좋았어요", R.drawable.ic_rate_good),
    OK("보통", R.drawable.ic_rate_ok),
    BAD("별로", R.drawable.ic_rate_bad),
}

private val REASONS = listOf("찾기 쉬웠어요", "오래 안 기다렸어요", "비 안 맞았어요", "차 세우기 어려웠어요", "사람이 많았어요")

/** 선택 가능한 카드 배경: 선택 시 주황 */
private fun Modifier.selectable(selected: Boolean, shape: Shape): Modifier =
    clip(shape)
        .background(if (selected) Ink.accentSurface else Ink.surface)
        .border(if (selected) 2.dp else 1.dp, if (selected) Ink.accent else Ink.line, shape)

// C08 목적지 평가 — 평가 하나 고르면 완료 활성, 이유 태그는 선택
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    context: String,
    pointName: String,
    summary: String,
    kicker: String = "픽업 장소 평가",
    onDone: (Rating, List<String>) -> Unit = { _, _ -> },
    onSkip: () -> Unit = {},
) {
    var rating by remember { mutableStateOf<Rating?>(null) }
    val reasons = remember { mutableStateListOf<String>() }

    Row(Modifier.fillMaxSize().background(Ink.bg).padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 28.dp)) {
        Column(Modifier.weight(1f)) {
            BrandBar(context)
            Text(kicker, Modifier.padding(top = 20.dp), Ink.sub, 18.sp)
            Text("$pointName 어땠어요?", Modifier.padding(top = 8.dp), Ink.text, 38.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp)
            Row(Modifier.padding(top = 14.dp).width(812.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Rating.entries.forEach { r ->
                    Row(
                        Modifier.weight(1f).height(72.dp).selectable(rating == r, RoundedCornerShape(16.dp)).clickable { rating = r },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(r.icon), null, tint = Color.Unspecified)
                        Text(r.label, Modifier.padding(start = 12.dp), Ink.text, 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("어떤 점이 그랬나요? (여러 개 선택)", Modifier.padding(top = 16.dp), Ink.sub, 14.sp)
            FlowRow(
                Modifier.padding(top = 16.dp).width(812.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                REASONS.forEach { tag ->
                    val on = tag in reasons
                    Text(
                        tag,
                        Modifier
                            .selectable(on, RoundedCornerShape(50))
                            .clickable { if (on) reasons.remove(tag) else reasons.add(tag) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        Ink.text, 18.sp,
                    )
                }
            }
        }
        Column(Modifier.width(360.dp).padding(top = 134.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().height(120.dp).selectable(false, RoundedCornerShape(16.dp)).padding(horizontal = 21.dp, vertical = 18.dp)) {
                Text("이번 방문", color = Ink.sub, fontSize = 14.sp)
                Text(summary, Modifier.padding(top = 6.dp), Ink.text, 18.sp, fontWeight = FontWeight.Medium)
                Text("평가는 목적지 기록에 남고, 다음 추천에 반영돼요", Modifier.padding(top = 4.dp), Ink.sub, 14.sp)
            }
            val done = rating
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (done != null) Ink.accent else Ink.accentMuted)
                    .clickable(enabled = done != null) { done?.let { onDone(it, reasons.toList()) } },
                contentAlignment = Alignment.Center,
            ) {
                Text("평가 완료", color = if (done != null) Ink.onAccent else Ink.onAccentMuted, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            SecondaryButton("건너뛰기", onSkip, Modifier.fillMaxWidth().height(58.dp), fontSize = 18)
        }
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun ReviewPreview() = ReviewScreen("스타필드 하남 · 픽업하기", "북문 로비", "비 · 14:48 도착 · 가족 2명 탑승")

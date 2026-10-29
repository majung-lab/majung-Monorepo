package com.majung.vehicle.ui.arrival

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.R
import com.majung.vehicle.ui.theme.BrandBar
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.SecondaryButton
import com.majung.vehicle.ui.theme.card

// C07 목적지 도착 — 모드별 세 화면이 같은 틀을 쓴다 (왼쪽 질문 + 선택, 오른쪽 안내 + 완료 버튼)

private class Info(val label: String, val title: String, val sub: String, val icon: Int? = null)

@Composable
private fun ArrivalLayout(
    context: String,
    kicker: String,
    question: String,
    hint: String,
    info: Info,
    primary: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    choices: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize().background(Ink.bg).padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        Column(Modifier.weight(1f)) {
            BrandBar(context)
            Spacer(Modifier.height(104.dp))
            Text(kicker, color = Ink.sub, fontSize = 18.sp)
            Text(question, Modifier.padding(top = 10.dp), Ink.text, 44.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp)
            Text(hint, Modifier.padding(top = 8.dp), Ink.sub, 18.sp)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = choices)
        }
        Column(Modifier.width(380.dp).padding(top = 92.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().height(124.dp).card(Ink.surface, 16).padding(horizontal = 21.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    info.icon?.let {
                        Box(Modifier.size(18.dp).padding(start = 1.dp, top = 2.dp)) {
                            Icon(painterResource(it), null, tint = Color.Unspecified)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(info.label, color = Ink.sub, fontSize = 14.sp)
                }
                Text(info.title, Modifier.padding(top = 8.dp), Ink.text, 20.sp, fontWeight = FontWeight.Medium)
                Text(info.sub, Modifier.padding(top = 4.dp), Ink.sub, 14.sp)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (primaryEnabled) Ink.accent else Ink.accentMuted)
                    .clickable(enabled = primaryEnabled, onClick = onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    primary,
                    color = if (primaryEnabled) Ink.onAccent else Ink.onAccentMuted,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                )
            }
            SecondaryButton(secondary, onSecondary, Modifier.fillMaxWidth().height(58.dp), fontSize = 18)
        }
    }
}

/** 선택지 칩 (층, 탄 사람). selectedColor로 강조색을 바꾼다 */
@Composable
private fun Choice(
    text: String,
    selected: Boolean,
    width: Int,
    onClick: () -> Unit,
    selectedLine: Color = Ink.accent,
    selectedBg: Color = Ink.accentSurface,
    icon: Int? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val box = if (selected) Modifier.clip(shape).background(selectedBg).border(2.dp, selectedLine, shape) else Modifier.card(Ink.surface, 14)
    Row(
        box.width(width.dp).height(60.dp).clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(painterResource(it), null, Modifier.padding(end = 10.dp), tint = Color.Unspecified) }
        Text(text, color = Ink.text, fontSize = 20.sp)
    }
}

// ponytail: 층 목록·안내 문구는 시설 데이터 붙기 전 Figma 값.
private val FLOORS = listOf("B1", "B2", "B3", "B4")

/** C07-1 주차 (그냥가기) */
@Composable
fun ParkArrivalScreen(
    context: String,
    lotName: String,
    onParked: (floor: String?) -> Unit = {},
    onStillLooking: () -> Unit = {},
) {
    var floor by remember { mutableStateOf<String?>("B2") }
    ArrivalLayout(
        context = context,
        kicker = "${lotName}에 도착했어요",
        question = "어디에 세웠나요?",
        hint = "층을 남겨 두면 목적지 기록에서 다시 볼 수 있어요 (선택)",
        info = Info("비 와요 · 12°C", "${floor ?: "B2"} → 북문 엘리베이터 2분", "실내 통로로 가면 비 안 맞아요", R.drawable.ic_rain_small),
        primary = "주차 완료",
        primaryEnabled = true,
        onPrimary = { onParked(floor) },
        secondary = "아직 자리 찾는 중",
        onSecondary = onStillLooking,
    ) {
        FLOORS.forEach { f -> Choice(f, f == floor, 84, { floor = if (floor == f) null else f }) }
    }
}

/** C07-1 하차 (내려주기) */
@Composable
fun DropoffArrivalScreen(
    context: String,
    pointName: String,
    onDone: () -> Unit = {},
    onStillGettingOff: () -> Unit = {},
) {
    ArrivalLayout(
        context = context,
        kicker = "${pointName}에 도착했어요",
        question = "가족이 모두 내렸나요?",
        hint = "오른쪽 뒷문으로 내리면 바로 로비예요",
        info = Info("다음", "하차 완료하면 홈으로 가요", "이번 방문은 목적지 기록에 남아요"),
        primary = "하차 완료",
        primaryEnabled = true,
        onPrimary = onDone,
        secondary = "아직 내리는 중",
        onSecondary = onStillGettingOff,
    ) {
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Ink.warnLine, RoundedCornerShape(12.dp)).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_clock), null, tint = Color.Unspecified)
            Text("잠깐 정차 구역 · 3분 안에 출발해 주세요", Modifier.padding(start = 8.dp), Ink.accent, 18.sp)
        }
    }
}

/** C07-1 픽업 (픽업하기) — 모두 탔을 때만 탑승 완료 */
@Composable
fun PickupArrivalScreen(
    context: String,
    pointName: String,
    family: List<String>,
    arrivedAt: String,
    waitedMin: Int,
    initiallyBoarded: Set<String> = emptySet(),
    onBoarded: () -> Unit = {},
    onWaiting: () -> Unit = {},
) {
    val boarded = remember { mutableStateListOf<String>().apply { addAll(initiallyBoarded) } }
    val left = family.size - boarded.size
    ArrivalLayout(
        context = context,
        kicker = "${pointName}에 도착했어요",
        question = "가족이 모두 탔나요?",
        hint = "탄 사람을 눌러 주세요 · 가족 화면에도 '차가 도착했어요'가 떴어요",
        info = Info(
            "가족",
            if (left == 0) "모두 탔어요" else "${boarded.size}명 탔어요 · ${left}명 오는 중",
            "도착 $arrivedAt · 기다린 시간 ${waitedMin}분",
        ),
        primary = if (left == 0) "탑승 완료" else "탑승 완료 (${boarded.size}/${family.size})",
        primaryEnabled = left == 0,
        onPrimary = onBoarded,
        secondary = "아직 기다리는 중",
        onSecondary = onWaiting,
    ) {
        family.forEach { name ->
            val on = name in boarded
            Choice(
                name, on, 120, { if (on) boarded.remove(name) else boarded.add(name) },
                selectedLine = Ink.ok, selectedBg = Ink.okSurface,
                icon = if (on) R.drawable.ic_check_board else R.drawable.ic_plus,
            )
        }
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun PickupArrivalPreview() = PickupArrivalScreen(
    "스타필드 하남 · 픽업하기", "북문 로비", listOf("엄마", "지민"), "14:48", 2, setOf("엄마"),
)

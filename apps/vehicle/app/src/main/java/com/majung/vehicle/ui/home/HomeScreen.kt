package com.majung.vehicle.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.R
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.card

// ponytail: 최근 간 곳은 기록 API 전까지 Figma 값.
private val RECENT = listOf("스타필드 하남", "롯데월드몰", "현대백화점 판교")

// C01 홈 — 주행 중엔 가림
@Composable
fun HomeScreen(
    driving: Boolean,
    onSearch: () -> Unit = {},
    onRecent: (String) -> Unit = {},
    onHistory: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Ink.bg)) {
        if (driving) {
            Text("정차 후 이용해 주세요", Modifier.align(Alignment.Center), Ink.text, 28.sp)
            return@Box
        }
        Row(Modifier.padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("마중", color = Ink.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.44.sp)
                    Text("홈", Modifier.padding(start = 12.dp, bottom = 2.dp), Ink.sub, 15.sp)
                }
                Text(
                    "어디로 갈까요?",
                    Modifier.padding(top = 22.dp),
                    Ink.text, 34.sp, fontWeight = FontWeight.Bold,
                )
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .height(74.dp)
                        .card(Ink.raised, 14)
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_search), null, tint = Color.Unspecified)
                    Text("목적지 입력", Modifier.padding(start = 14.dp), Ink.sub, 20.sp)
                }
                Text(
                    "최근 간 곳",
                    Modifier.padding(top = 20.dp),
                    Ink.sub, 14.sp, letterSpacing = 0.56.sp,
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RECENT.forEach { name ->
                        Text(
                            name,
                            Modifier
                                .card(Ink.surface, 12)
                                .clickable { onRecent(name) }
                                .padding(horizontal = 20.dp, vertical = 15.dp),
                            Ink.text, 18.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.width(32.dp))
            Column(Modifier.width(340.dp).padding(top = 56.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.fillMaxWidth().height(128.dp).card(Ink.surface, 16).padding(horizontal = 22.dp, vertical = 20.dp)) {
                    Text("진행 중인 방문", color = Ink.sub, fontSize = 14.sp)
                    Text("없음", Modifier.padding(top = 10.dp), Ink.text, 18.sp)
                    Text("시동을 다시 켜면 여기서 이어서 할 수 있어요", Modifier.padding(top = 8.dp), Ink.sub, 14.sp)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .card(Ink.surface, 16)
                        .clickable(onClick = onHistory)
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("목적지 기록", Modifier.weight(1f), Ink.text, 19.sp)
                    Icon(painterResource(R.drawable.ic_chevron_right), null, tint = Color.Unspecified)
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun HomePreview() = HomeScreen(driving = false)

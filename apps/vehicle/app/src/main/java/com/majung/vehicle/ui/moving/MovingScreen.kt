package com.majung.vehicle.ui.moving

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.majung.vehicle.ui.theme.OutlineButton
import com.majung.vehicle.ui.theme.card

/** 가족 상태 카드 내용. null이면 카드 숨김 (내려주기·그냥가기) */
data class FamilyStatus(val label: String, val seenAt: String)

// C06 목적지 이동 — 주행 중에도 보이는 화면이라 글자 크게, 버튼 셋 이하
@Composable
fun MovingScreen(
    context: String,
    destination: String,
    etaMin: Int,
    family: FamilyStatus?,
    heading: String = "픽업 장소로 가는 중",
    onArrived: () -> Unit = {},
    onChangePlace: (() -> Unit)? = null,
    changed: Boolean = false,
    banner: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().background(Ink.bg).padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        Column(Modifier.weight(1f)) {
            BrandBar(context)
            Spacer(Modifier.height(134.dp))
            Text(heading, color = Ink.sub, fontSize = 18.sp)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(destination, color = Ink.text, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp)
                if (changed) {
                    Text(
                        "바뀐 장소",
                        Modifier.padding(start = 14.dp).clip(RoundedCornerShape(8.dp)).background(Ink.okSurface)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        Ink.ok, 16.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // TODO: NaviResult 연결 (실패 시 문구 교체)
                Icon(painterResource(R.drawable.ic_check_green), null, tint = Color.Unspecified)
                Text("내비 안내 중", Modifier.padding(start = 8.dp), Ink.ok, 18.sp)
                Text("· 약 ${etaMin}분", Modifier.padding(start = 8.dp), Ink.sub, 18.sp)
            }
        }
        Column(Modifier.width(380.dp).padding(top = 54.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            family?.let {
                Column(Modifier.fillMaxWidth().height(134.dp).card(Ink.surface, 16).padding(horizontal = 21.dp, vertical = 20.dp)) {
                    Text("가족", color = Ink.sub, fontSize = 14.sp)
                    Text(it.label, Modifier.padding(top = 8.dp), Ink.text, 22.sp, fontWeight = FontWeight.Medium)
                    Text("링크 확인 ${it.seenAt}", Modifier.padding(top = 6.dp), Ink.sub, 14.sp)
                }
            }
            Box(
                Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp)).background(Ink.accent).clickable(onClick = onArrived),
                contentAlignment = Alignment.Center,
            ) {
                Text("도착했어요", color = Ink.onAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            onChangePlace?.let { OutlineButton("위치 바꾸기", it, Modifier.fillMaxWidth()) }
        }
    }
    banner?.invoke()
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun MovingPreview() = MovingScreen(
    "스타필드 하남 · 픽업하기", "북문 로비", 12, FamilyStatus("준비됐어요", "14:31"), onChangePlace = {},
)

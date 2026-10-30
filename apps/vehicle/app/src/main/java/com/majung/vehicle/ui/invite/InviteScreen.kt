package com.majung.vehicle.ui.invite

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.core.qr.qrBitmap
import com.majung.vehicle.ui.theme.BackHeader
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.PrimaryButton
import com.majung.vehicle.ui.theme.SecondaryButton
import com.majung.vehicle.ui.theme.card

// C05 가족 연결 QR (픽업하기에서만)
@Composable
fun InviteScreen(
    pickupLabel: String,
    link: String,
    onBack: () -> Unit = {},
    onDepart: () -> Unit = {},
    onLater: () -> Unit = {},
    onCancelVisit: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().background(Ink.bg).padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        BackHeader("링크 전달", onBack)
        Row(Modifier.padding(top = 48.dp)) {
            Box(Modifier.size(300.dp).clip(RoundedCornerShape(18.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                val qr = remember(link) { qrBitmap(link, 528) }
                Image(qr, "연결용 QR 코드: $link", Modifier.size(264.dp))
            }
            Column(Modifier.padding(start = 40.dp)) {
                Text(
                    "운전자 폰으로 찍고,\n가족에게 링크를 보내 주세요",
                    Modifier.padding(top = 4.dp),
                    Ink.text, 30.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp,
                )
                Text(
                    "가족은 앱 설치 없이 링크만 누르면 픽업 장소와 차 위치를 볼 수 있어요",
                    Modifier.padding(top = 10.dp),
                    Ink.sub, 18.sp, lineHeight = 28.sp,
                )
                Row(
                    Modifier.padding(top = 18.dp).fillMaxWidth().height(60.dp).card(Ink.surface, 14).padding(horizontal = 19.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("픽업 장소", color = Ink.sub, fontSize = 16.sp)
                    Text(pickupLabel, Modifier.padding(start = 10.dp), Ink.text, 20.sp, fontWeight = FontWeight.Medium)
                }
                Text(link, Modifier.padding(top = 8.dp), Ink.sub, 14.sp)
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PrimaryButton("픽업 장소로 출발", onDepart, Modifier.width(296.dp))
                    SecondaryButton("나중에 알리기", onLater, Modifier.width(166.dp))
                    onCancelVisit?.let { SecondaryButton("방문 취소", it, Modifier.width(144.dp)) }
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun InvitePreview() = InviteScreen("스타필드 하남 · 북문 로비", "http://10.0.2.2:5173/v/ab12cd34")

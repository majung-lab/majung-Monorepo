package com.majung.vehicle.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.R

// Figma: 현대공모전 - 마중 / 차량 앱 (1280 × 480 dp 기준)
object Ink {
    val bg = Color(0xFF0E1318)
    val text = Color(0xFFEEF2F5)
    val sub = Color(0xFF9BA8B4)
    val surface = Color(0xFF161D24)
    val raised = Color(0xFF1F2830)
    val line = Color(0xFF2A3540)
    val tag = Color(0xFF24313B)
    val subOnAccent = Color(0xFFC9D2DA)
    val accentSurface = Color(0xFF2A2312)
    val accentTag = Color(0xFF3A3018)
    val accentBody = Color(0xFFE6D3AE)
    val info = Color(0xFF1A2633)
    val ok = Color(0xFF5CC28D)
    val accent = Color(0xFFF5A524)
    val onAccent = Color(0xFF1A1300)
}

/** Figma 카드: 배경 + 1dp 테두리 + 라운드 */
fun Modifier.card(color: Color, radius: Int, line: Color = Ink.line): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return clip(shape).background(color).border(1.dp, line, shape)
}

/** Figma 점선 테두리 (확인 안 된 시설 등) */
fun Modifier.dashedCard(radius: Int, line: Color = Ink.line): Modifier = drawBehind {
    val r = radius.dp.toPx()
    drawRoundRect(
        color = line,
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
    )
}

/** 뒤로 버튼 + 제목(+ 위 작은 부제). C02~ 공통 */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Ink.surface).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_back), "뒤로", tint = Color.Unspecified)
        }
        Column(Modifier.padding(start = 14.dp)) {
            subtitle?.let { Text(it, color = Ink.sub, fontSize = 14.sp) }
            Text(title, color = Ink.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 작은 회색 태그 (승하차 구역 확인됨, 실내 주차 …) */
@Composable
fun Tag(text: String, color: Color = Ink.tag) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 10.dp, vertical = 6.dp),
        Ink.text, 14.sp,
    )
}

/** 주 버튼 (주황) */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(64.dp).clip(RoundedCornerShape(14.dp)).background(Ink.accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink.onAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** 보조 버튼 (어두운 카드) */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(64.dp).card(Ink.surface, 14).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink.text, fontSize = 20.sp)
    }
}

/** 상단 "마중  스타필드 하남 · 픽업하기" */
@Composable
fun BrandBar(context: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("마중", color = Ink.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(context, Modifier.padding(start = 10.dp, bottom = 2.dp), Ink.sub, 16.sp)
    }
}

/** 테두리만 주황인 버튼 (위치 바꾸기 등) */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(60.dp).clip(shape).background(Ink.surface).border(2.dp, Ink.accent, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

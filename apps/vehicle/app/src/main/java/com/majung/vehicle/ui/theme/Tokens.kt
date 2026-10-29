package com.majung.vehicle.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Figma: 현대공모전 - 마중 / 차량 앱 (1280 × 480 dp 기준)
object Ink {
    val bg = Color(0xFF0E1318)
    val text = Color(0xFFEEF2F5)
    val sub = Color(0xFF9BA8B4)
    val surface = Color(0xFF161D24)
    val raised = Color(0xFF1F2830)
    val line = Color(0xFF2A3540)
    val accent = Color(0xFFF5A524)
    val onAccent = Color(0xFF1A1300)
}

/** Figma 카드: 배경 + 1dp 테두리 + 라운드 */
fun Modifier.card(color: Color, radius: Int, line: Color = Ink.line): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return clip(shape).background(color).border(1.dp, line, shape)
}

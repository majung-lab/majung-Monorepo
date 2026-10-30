package com.majung.vehicle.ui.proposal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.ui.theme.Ink

/** C12 결과 종류: 색 + 문구 (화면동작정의 C12-1~4) */
enum class ProposalResult(val bg: Color, val fg: Color) {
    ACCEPTED(Color(0xFF1F7A4A), Color.White),
    REJECTED(Color(0xFF3A4450), Color.White),
    TIMEOUT(Ink.accent, Ink.onAccent),
    CANCELLED_BY_ME(Color(0xFF3A4450), Color.White),
    CANCELLED_BY_FAMILY(Color(0xFF3A4450), Color.White),
}

private fun ProposalResult.title(to: String) = when (this) {
    ProposalResult.ACCEPTED -> "가족이 수락했어요 · ${to}로 바뀌었어요"
    ProposalResult.REJECTED -> "가족이 기존 장소 그대로 가재요"
    ProposalResult.TIMEOUT -> "제안이 종료되었어요"
    ProposalResult.CANCELLED_BY_ME -> "바꾸자는 제안을 취소했어요"
    ProposalResult.CANCELLED_BY_FAMILY -> "가족이 제안을 취소했어요"
}

private fun ProposalResult.sub(from: String, to: String) = when (this) {
    ProposalResult.ACCEPTED -> "확정 장소를 가족 화면에도 반영했어요"
    ProposalResult.TIMEOUT -> "제안이 취소되거나 만료됐어요 · $from 그대로예요"
    ProposalResult.CANCELLED_BY_ME -> "가족에게도 알렸어요 · $from 그대로예요"
    else -> "$from 그대로 안내해요"
}

// C12 변경 결과 알림 — C06 위 큰 띠, 버튼 하나 (주행 중에도 표시). 화면동작정의 기준, Figma 미대조
@Composable
fun ResultBanner(result: ProposalResult, from: String, to: String, onConfirm: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .fillMaxWidth()
            .height(96.dp)
            .shadow(9.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(result.bg)
            .padding(start = 28.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(result.title(to), color = result.fg, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(result.sub(from, to), Modifier.padding(top = 4.dp), result.fg.copy(alpha = 0.9f), 18.sp)
        }
        Box(
            Modifier
                .width(110.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(result.fg.copy(alpha = 0.18f))
                .clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text("확인", color = result.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

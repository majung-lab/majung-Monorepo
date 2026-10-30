package com.majung.vehicle.ui.proposal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.R
import com.majung.vehicle.domain.model.LatLng
import com.majung.vehicle.domain.model.mapPoint
import com.majung.vehicle.ui.theme.LocalStaticMap
import com.majung.vehicle.ui.theme.MapPin
import com.majung.vehicle.ui.theme.OsmMap
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.MockMap
import com.majung.vehicle.ui.theme.PrimaryButton
import com.majung.vehicle.ui.theme.SecondaryButton

data class FamilyProposal(
    val from: String,
    val to: String,
    val reason: String,
    val distanceM: Int,
)

/** "북문 로비" → "북문" (버튼 문구용) */
private val String.short get() = substringBefore(' ')

// C11 가족 제안 받기 — 화면동작정의 기준 (Figma 43:1748 미대조)
@Composable
fun ProposalReceivedScreen(
    proposal: FamilyProposal,
    driving: Boolean = false,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {},
    onContinue: () -> Unit = {},
    response: Boolean? = null,
    fromPosition: LatLng? = null,
    toPosition: LatLng? = null,
) {
    val accepted = response

    Row(Modifier.fillMaxSize().background(Ink.bg)) {
        Box(Modifier.weight(0.64f).fillMaxHeight()) {
            val from = fromPosition ?: mapPoint(proposal.from).pos
            val to = toPosition ?: mapPoint(proposal.to).pos
            if (LocalStaticMap.current) {
                MockMap(STARFIELD_MAP, Modifier.fillMaxSize())
                if (accepted != false) {
                    // 서문(410,240) → 북문(610,60): STARFIELD_MAP 출입구 좌표 + 캔버스 오프셋
                    Canvas(Modifier.fillMaxSize()) {
                        drawLine(
                            PinTag, Offset(410.dp.toPx(), 240.dp.toPx()), Offset(610.dp.toPx(), 60.dp.toPx()),
                            strokeWidth = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                        )
                    }
                    Image(painterResource(R.drawable.ic_pin_target), null, Modifier.offset(590.dp, 8.dp))
                }
            } else {
                // 기존 핀(흰색) → 제안 핀(주황) 점선. 거절하면 제안 핀 삭제
                OsmMap(
                    LatLng((from.lat + to.lat) / 2, (from.lng + to.lng) / 2), Modifier.fillMaxSize(), zoom = 18.0,
                    pins = listOfNotNull(
                        MapPin(if (accepted == true) to else from, R.drawable.map_c10_pin_now),
                        if (accepted == null) MapPin(to, R.drawable.ic_pin_target) else null,
                    ),
                    dashed = if (accepted == null) listOf(from, to) else null,
                )
            }
        }
        Column(Modifier.weight(0.36f).padding(28.dp)) {
            Text(
                when (accepted) {
                    null -> "가족이 만날 곳을 바꾸자고 해요"
                    true -> "${proposal.to}로 바뀌었어요"
                    false -> "${proposal.from} 그대로 가요"
                },
                color = Ink.text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            )
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(proposal.from, color = Ink.sub, fontSize = 20.sp)
                Text("→", Modifier.padding(horizontal = 12.dp), Ink.sub, 20.sp)
                Text(proposal.to, color = PinTag, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                proposal.reason,
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.accentSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                Ink.accentBody, 17.sp, lineHeight = 24.sp,
            )
            Text(
                "두 지점 사이 ${proposal.distanceM}m · 응답 전까지 기존 장소로 안내해요",
                Modifier.padding(top = 10.dp),
                Ink.sub, 14.sp,
            )
            Spacer(Modifier.weight(1f))
            when {
                accepted != null -> PrimaryButton("안내 계속", onContinue, Modifier.fillMaxWidth())
                driving -> Text("정차하면 수락·거절할 수 있어요 · 그동안 기존 장소로 안내", color = Ink.sub, fontSize = 18.sp)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("${proposal.from.short} 그대로", onReject, Modifier.weight(1f))
                    PrimaryButton("${proposal.to.short}으로 바꾸기", onAccept, Modifier.weight(1.4f))
                }
            }
        }
    }
}

// ponytail: 제안 API 전까지 예시값.
val SAMPLE_FAMILY_PROPOSAL = FamilyProposal("서문 로비", "북문 로비", "비가 와서 지붕 있는 북문 로비에서 기다릴게요", 80)

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun ProposalReceivedPreview() = ProposalReceivedScreen(SAMPLE_FAMILY_PROPOSAL)

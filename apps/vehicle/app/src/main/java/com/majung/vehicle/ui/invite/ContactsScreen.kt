package com.majung.vehicle.ui.invite

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majung.vehicle.core.contacts.PhoneContact
import com.majung.vehicle.core.contacts.mobileDigits
import com.majung.vehicle.ui.theme.BackHeader
import com.majung.vehicle.ui.theme.Ink
import com.majung.vehicle.ui.theme.PrimaryButton
import com.majung.vehicle.ui.theme.SecondaryButton
import com.majung.vehicle.ui.theme.card

/** 연락처 목록 상태. 권한이 없으면 목록 대신 허용 버튼 */
sealed interface ContactsState {
    data object Loading : ContactsState
    data object NoPermission : ContactsState
    data class Loaded(val contacts: List<PhoneContact>) : ContactsState
}

// C05-1 연락처로 링크 보내기 (출발 전에만. 주행 중에는 열리지 않는다)
@Composable
fun ContactsScreen(
    state: ContactsState,
    /** 지금 보내는 번호(숫자만) */
    sending: String?,
    /** 이번 방문에서 이미 보낸 번호(숫자만) */
    sent: Set<String>,
    notice: String?,
    onSend: (name: String?, digits: String) -> Unit,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
) {
    // 차량 시스템 키보드는 전화번호 모드를 무시하고 전체 자판을 띄우므로, 숫자 패드를 앱이 직접 그린다
    var typed by remember { mutableStateOf("") }
    var padOpen by remember { mutableStateOf(false) }
    val typedDigits = mobileDigits(typed)
    val sendTyped = { typedDigits?.let { padOpen = false; onSend(null, it) } }
    BackHandler(padOpen) { padOpen = false }
    Column(Modifier.fillMaxSize().background(Ink.bg).padding(start = 40.dp, end = 40.dp, top = 28.dp)) {
        BackHeader("연락처로 링크 보내기", onBack)
        Row(Modifier.padding(top = 28.dp).fillMaxSize()) {
            Column(Modifier.width(380.dp)) {
                Text("번호 직접 입력", color = Ink.sub, fontSize = 16.sp)
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().height(64.dp)
                        .card(Ink.surface, 14, if (padOpen) Ink.accent else Ink.line)
                        .clickable { padOpen = !padOpen }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (typed.isEmpty() && !padOpen) Text("눌러서 번호 입력", color = Ink.sub, fontSize = 22.sp)
                    else Text(groupPhone(typed), color = Ink.text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    if (padOpen) Box(Modifier.padding(start = 2.dp).width(2.dp).height(28.dp).background(Ink.accent))
                }
                if (padOpen) NumberPad(
                    onDigit = { if (typed.length < 11) typed += it },
                    onDelete = { typed = typed.dropLast(1) },
                    onClear = { typed = "" },
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (typedDigits != null && sending == null) PrimaryButton("이 번호로 보내기", { sendTyped() }, Modifier.padding(top = 10.dp).fillMaxWidth())
                else SecondaryButton(if (sending != null) "보내는 중…" else "이 번호로 보내기", {}, Modifier.padding(top = 10.dp).fillMaxWidth())
                if (!padOpen) Text(
                    notice ?: "고르면 바로 문자로 링크가 가요. 받는 분은 앱 설치 없이 링크만 누르면 돼요.",
                    Modifier.padding(top = 18.dp),
                    if (notice != null) Ink.text else Ink.sub, 18.sp, lineHeight = 26.sp,
                    fontWeight = if (notice != null) FontWeight.Medium else FontWeight.Normal,
                )
            }
            Column(Modifier.padding(start = 40.dp).weight(1f)) {
                Text("연락처", color = Ink.sub, fontSize = 16.sp)
                when (state) {
                    ContactsState.Loading -> Text("연락처를 불러오는 중…", Modifier.padding(top = 16.dp), Ink.sub, 18.sp)
                    ContactsState.NoPermission -> Column(Modifier.padding(top = 16.dp)) {
                        Text("연락처를 보려면 접근을 허용해 주세요", color = Ink.text, fontSize = 20.sp)
                        SecondaryButton("연락처 접근 허용", onRequestPermission, Modifier.padding(top = 14.dp).width(260.dp))
                    }
                    is ContactsState.Loaded -> if (state.contacts.isEmpty()) {
                        Text("연락처가 없어요. 휴대폰을 블루투스로 연결하거나 번호를 직접 입력해 주세요.", Modifier.padding(top = 16.dp), Ink.sub, 18.sp, lineHeight = 26.sp)
                    } else {
                        LazyColumn(Modifier.padding(top = 10.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.contacts, key = { it.number }) { contact ->
                                val digits = mobileDigits(contact.number) ?: return@items
                                ContactRow(contact, sending = sending == digits, sent = digits in sent, enabled = sending == null) { padOpen = false; onSend(contact.name, digits) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 1 2 3 / 4 5 6 / 7 8 9 / 전체 지우기 0 ⌫ — 장갑 낀 손가락도 누르기 쉬운 크기 */
@Composable
private fun NumberPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf("123", "456", "789")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d -> PadKey(d.toString(), Modifier.weight(1f)) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadKey("전체 지우기", Modifier.weight(1f), fontSize = 16, color = Ink.sub, onClick = onClear)
            PadKey("0", Modifier.weight(1f)) { onDigit('0') }
            PadKey("⌫", Modifier.weight(1f), onClick = onDelete)
        }
    }
}

@Composable
private fun PadKey(label: String, modifier: Modifier, fontSize: Int = 26, color: androidx.compose.ui.graphics.Color = Ink.text, onClick: () -> Unit) {
    Box(modifier.height(50.dp).clip(RoundedCornerShape(12.dp)).background(Ink.raised).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = color, fontSize = fontSize.sp, fontWeight = FontWeight.Medium)
    }
}

/** 입력 중인 숫자를 010-1234-5678 모양으로 */
private fun groupPhone(d: String): String = when {
    d.length <= 3 -> d
    d.length <= 7 -> "${d.take(3)}-${d.drop(3)}"
    d.length <= 10 -> "${d.take(3)}-${d.substring(3, 6)}-${d.drop(6)}"
    else -> "${d.take(3)}-${d.substring(3, 7)}-${d.drop(7)}"
}

@Composable
private fun ContactRow(contact: PhoneContact, sending: Boolean, sent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).card(Ink.surface, 14).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = Ink.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(contact.number, color = Ink.sub, fontSize = 15.sp)
        }
        val (label, color) = when {
            sending -> "보내는 중…" to Ink.sub
            sent -> "보냄 · 다시 보내기" to Ink.ok
            else -> "보내기" to Ink.accent
        }
        Text(label, Modifier.clip(RoundedCornerShape(10.dp)).background(Ink.raised).padding(horizontal = 14.dp, vertical = 8.dp), color, 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(widthDp = 1280, heightDp = 480)
@Composable
private fun ContactsPreview() = ContactsScreen(
    ContactsState.Loaded(listOf(PhoneContact("엄마", "010-1234-5678"), PhoneContact("동생", "010-2222-3333"))),
    sending = null, sent = setOf("01012345678"), notice = null, onSend = { _, _ -> }, onRequestPermission = {}, onBack = {},
)

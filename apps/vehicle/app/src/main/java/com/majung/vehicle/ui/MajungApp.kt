package com.majung.vehicle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.majung.vehicle.BuildConfig
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.domain.model.LatLng
import com.majung.vehicle.domain.model.MAP_POINTS
import com.majung.vehicle.domain.model.Place
import com.majung.vehicle.domain.model.SAMPLE_FAMILY
import com.majung.vehicle.domain.model.distanceM
import com.majung.vehicle.domain.model.mapPoint
import com.majung.vehicle.domain.model.SAMPLE_PLACES
import com.majung.vehicle.domain.model.VisitMode
import com.majung.vehicle.domain.model.label
import com.majung.vehicle.ui.arrival.DropoffArrivalScreen
import com.majung.vehicle.ui.arrival.ParkArrivalScreen
import com.majung.vehicle.ui.arrival.PickupArrivalScreen
import com.majung.vehicle.ui.destination.DestinationScreen
import com.majung.vehicle.ui.history.HistoryScreen
import com.majung.vehicle.ui.home.HomeScreen
import com.majung.vehicle.ui.invite.InviteScreen
import com.majung.vehicle.ui.mode.ModeScreen
import com.majung.vehicle.ui.moving.FamilyStatus
import com.majung.vehicle.ui.moving.MovingScreen
import com.majung.vehicle.ui.place.PlaceScreen
import com.majung.vehicle.ui.proposal.FamilyProposal
import com.majung.vehicle.ui.proposal.MovePinScreen
import com.majung.vehicle.ui.proposal.ProposalReceivedScreen
import com.majung.vehicle.ui.proposal.ProposalResult
import com.majung.vehicle.ui.proposal.ResultBanner
import com.majung.vehicle.ui.review.ReviewScreen
import com.majung.vehicle.ui.theme.Ink

// 화면 번호는 docs/planning/마중_화면동작정의.md 기준 (C01~C12)
private enum class Screen { HOME, DESTINATION, MODE, PLACE, INVITE, MOVING, ARRIVAL, REVIEW, HISTORY, MOVE_PIN }

/** 주행 중에도 보이는 화면 (화면동작정의 C01 주행 중). C01·C10은 스스로 주행 중 상태를 그린다 */
private val SHOWN_WHILE_DRIVING = setOf(Screen.HOME, Screen.MOVING, Screen.ARRIVAL, Screen.MOVE_PIN)

// ponytail: 화면 전환은 상태 하나로. 방문 상태를 서버에서 받기 시작하면 ViewModel로 옮긴다.
@Composable
fun MajungApp(uxRestriction: UxRestrictionWatcher) {
    val driving by uxRestriction.isRestricted.collectAsState()
    // 재생성(주야간 전환 등)에도 진행 중인 방문을 잃지 않게 rememberSaveable
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var placeName by rememberSaveable { mutableStateOf(SAMPLE_PLACES.first().name) }
    var mode by rememberSaveable { mutableStateOf(VisitMode.PICKUP) }
    var point by rememberSaveable { mutableStateOf("북문 로비") }
    var result by rememberSaveable { mutableStateOf<ProposalResult?>(null) }
    // ponytail: 방문 토큰은 서버 붙기 전까지 앱에서 만든다
    val token = rememberSaveable { UUID.randomUUID().toString().take(8) }
    var resultFrom by rememberSaveable { mutableStateOf("") }
    // ── 시뮬레이션 상태 (서버·웹소켓 붙기 전, 앱 안에서만) ──
    var movingSince by rememberSaveable { mutableLongStateOf(0L) }
    var arrivedAt by rememberSaveable { mutableLongStateOf(0L) }
    var firstPoint by rememberSaveable { mutableStateOf("") }
    var sentTo by rememberSaveable { mutableStateOf<String?>(null) } // C10 내가 보낸 제안
    var familyAsked by rememberSaveable { mutableStateOf(false) } // C11 한 번만
    var familyProposal by rememberSaveable { mutableStateOf<String?>(null) } // C11 제안 장소
    val now = rememberNow(screen in TICKING)
    val elapsed = now - movingSince
    // ponytail: 시설 데이터 붙기 전엔 SAMPLE_PLACES에 없는 곳 = 미등록 시설
    val place = SAMPLE_PLACES.firstOrNull { it.name == placeName } ?: Place(placeName, registered = false)
    val home = { screen = Screen.HOME }
    val context = "${place.name} · ${mode.label}"

    BackHandler(screen != Screen.HOME) {
        screen = when (screen) {
            Screen.MODE -> Screen.DESTINATION
            Screen.PLACE -> Screen.MODE
            Screen.INVITE -> Screen.PLACE
            Screen.MOVE_PIN, Screen.ARRIVAL -> Screen.MOVING
            else -> Screen.HOME
        }
    }

    // C10 제안 → 가족이 수락 (5초)
    LaunchedEffect(sentTo) {
        val to = sentTo ?: return@LaunchedEffect
        delay(SIM_FAMILY_ANSWER_MS)
        resultFrom = point; point = to; result = ProposalResult.ACCEPTED; sentTo = null; screen = Screen.MOVING
    }
    // C11 가족 제안 도착 (픽업하기 C06에서 25초 뒤 한 번)
    // ponytail: 더미 시연은 방문당 제안 한 번. 내가 이미 제안했거나 결과 띠가 떠 있으면 안 띄운다
    if (mode == VisitMode.PICKUP && screen == Screen.MOVING && !familyAsked && sentTo == null && result == null &&
        point == firstPoint && elapsed >= SIM_FAMILY_PROPOSAL_MS
    ) {
        familyAsked = true
        familyProposal = MAP_POINTS.first { it.name != point && it.id != "front" }.name
    }

    fun endVisit() {
        movingSince = 0L; arrivedAt = 0L; familyAsked = false; familyProposal = null; sentTo = null; result = null
        home()
    }

    fun startMoving() {
        if (movingSince == 0L) { movingSince = System.currentTimeMillis(); firstPoint = point }
        screen = Screen.MOVING
    }

    // 미등록 시설은 승하차 데이터가 없어 일반 길안내만 (C02 점선 카드와 같은 규칙)
    fun pick(name: String) {
        placeName = name
        if (SAMPLE_PLACES.any { it.name == name && it.registered }) {
            screen = Screen.MODE
        } else {
            mode = VisitMode.PARK; point = name; startMoving()
        }
    }

    Box(Modifier.fillMaxSize()) {
    when (screen) {
        Screen.HOME -> HomeScreen(
            driving,
            onSearch = { screen = Screen.DESTINATION },
            onRecent = ::pick,
            onHistory = { screen = Screen.HISTORY },
        )
        Screen.DESTINATION -> DestinationScreen(onBack = home, onSelect = { pick(it.name) })
        Screen.MODE -> ModeScreen(place.name, onBack = { screen = Screen.DESTINATION }) { mode = it; screen = Screen.PLACE }
        Screen.PLACE -> PlaceScreen(place.name, mode, onBack = { screen = Screen.MODE }) {
            point = it.meetName
            // TODO: 그냥가기·내려주기는 NaviClient.requestRoute 후 C06
            if (mode == VisitMode.PICKUP) screen = Screen.INVITE else startMoving()
        }
        Screen.INVITE -> InviteScreen(
            "${place.name} · $point",
            "${BuildConfig.WEB_URL}/v/$token",
            onBack = { screen = Screen.PLACE },
            onDepart = ::startMoving,
            onLater = ::startMoving,
        )
        Screen.MOVING -> MovingScreen(
            context, point, etaMin = (SIM_ETA_MIN - elapsed / 60_000).toInt().coerceAtLeast(1),
            heading = when (mode) {
                VisitMode.PICKUP -> "픽업 장소로 가는 중"
                VisitMode.DROPOFF -> "내릴 곳으로 가는 중"
                VisitMode.PARK -> "목적지로 가는 중"
            },
            // 주행 중엔 장소 · 상태 · 도착했어요만
            family = if (mode == VisitMode.PICKUP && !driving) familyStatus(movingSince, elapsed) else null,
            onArrived = { arrivedAt = System.currentTimeMillis(); screen = Screen.ARRIVAL },
            changed = point != firstPoint,
            // 위치 바꾸기는 정차 중만: 픽업하기는 C10, 나머지는 C04 다시 고르기 (미등록 시설은 없음)
            onChangePlace = if (driving || !place.registered) null else ({
                screen = if (mode == VisitMode.PICKUP) Screen.MOVE_PIN else Screen.PLACE
            }),
            banner = result?.let { r -> { ResultBanner(r, resultFrom.ifEmpty { point }, point) { result = null } } },
        )
        // TODO: onPropose → 제안 API, 결과(웹소켓)로 result 설정
        Screen.MOVE_PIN -> MovePinScreen(
            point, driving,
            onBack = { screen = Screen.MOVING },
            onPropose = { sentTo = it; familyAsked = true },
            onCancel = { sentTo = null; resultFrom = point; result = ProposalResult.CANCELLED_BY_ME; screen = Screen.MOVING },
        )
        Screen.ARRIVAL -> when (mode) {
            // TODO: 주차 층은 방문 기록 API에 저장
            VisitMode.PARK -> ParkArrivalScreen(context, "북측 주차장", onParked = { screen = Screen.REVIEW }) { screen = Screen.MOVING }
            // Figma 문구가 "하차 완료하면 홈으로 가요"라 C08 대신 홈 (화면동작정의는 C08)
            VisitMode.DROPOFF -> DropoffArrivalScreen(context, point, onDone = home) { screen = Screen.MOVING }
            VisitMode.PICKUP -> PickupArrivalScreen(
                context, point, SAMPLE_FAMILY, hhmm(arrivedAt), ((now - arrivedAt) / 60_000).toInt(),
                onBoarded = { screen = Screen.REVIEW },
                onWaiting = { screen = Screen.MOVING },
            )
        }
        Screen.REVIEW -> ReviewScreen(
            context, point, if (mode == VisitMode.PICKUP) "비 · ${hhmm(arrivedAt)} 도착 · 가족 ${SAMPLE_FAMILY.size}명 탑승" else "비 · ${hhmm(arrivedAt)} 도착",
            kicker = if (mode == VisitMode.PICKUP) "픽업 장소 평가" else "장소 평가",
            onDone = { _, _ -> endVisit() }, onSkip = ::endVisit,
        )
        Screen.HISTORY -> HistoryScreen(onBack = home) { pick(it.place) }
    }
    // C11은 어느 화면 위에든 뜬다 (주행 중에도 표시, 버튼만 숨김)
    familyProposal?.let { to ->
        ProposalReceivedScreen(
            FamilyProposal(point, to, "비가 와서 지붕 있는 ${to}에서 기다릴게요", distanceM(mapPoint(to).pos, MY_POS_NEAR).roundToInt()),
            driving,
            onAccept = { point = to },
            onContinue = { familyProposal = null },
            onReject = {},
        )
    }
    if (driving && screen !in SHOWN_WHILE_DRIVING && familyProposal == null) {
        // 화면은 그대로 두고 덮기만 해서 입력 값은 보관
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Text("정차 후 이용해 주세요", color = Ink.text, fontSize = 28.sp)
        }
    }
    }
}

private val TICKING = setOf(Screen.MOVING, Screen.ARRIVAL)

// 시뮬레이션 타이밍
private const val SIM_ETA_MIN = 12
private const val SIM_FAMILY_ANSWER_MS = 5_000L
private const val SIM_FAMILY_PROPOSAL_MS = 25_000L
/** 가족(동승자) 더미 위치: 웹 dummy.ts MY_POS 와 같은 값 */
private val MY_POS_NEAR = LatLng(37.5461, 127.2228)

private fun hhmm(t: Long): String = if (t == 0L) "-" else SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(t))

/** 링크 보낸 뒤 가족 상태가 바뀌는 흐름 (더미) */
private fun familyStatus(since: Long, elapsed: Long): FamilyStatus = when {
    elapsed < 6_000 -> FamilyStatus("아직 링크를 안 열었어요", "링크 보냄 ${hhmm(since)}")
    elapsed < 14_000 -> FamilyStatus("링크를 열었어요", "링크 확인 ${hhmm(since + 6_000)}")
    else -> FamilyStatus("준비됐어요", "링크 확인 ${hhmm(since + 6_000)}")
}

/** active일 때만 1초씩 흐르는 시각. tick을 쌓아서 테스트 시계(mainClock)로도 움직인다 */
@Composable
private fun rememberNow(active: Boolean): Long {
    val now by produceState(System.currentTimeMillis(), active) {
        var t = System.currentTimeMillis()
        value = t
        while (active) { delay(1_000); t += 1_000; value = t }
    }
    return now
}

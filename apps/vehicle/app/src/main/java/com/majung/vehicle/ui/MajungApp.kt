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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import java.util.UUID
import com.majung.vehicle.BuildConfig
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.domain.model.Place
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
import com.majung.vehicle.ui.proposal.MovePinScreen
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

    // 미등록 시설은 승하차 데이터가 없어 일반 길안내만 (C02 점선 카드와 같은 규칙)
    fun pick(name: String) {
        placeName = name
        if (SAMPLE_PLACES.any { it.name == name && it.registered }) {
            screen = Screen.MODE
        } else {
            mode = VisitMode.PARK; point = name; screen = Screen.MOVING
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
            point = it.name
            // TODO: 그냥가기·내려주기는 NaviClient.requestRoute 후 C06
            screen = if (mode == VisitMode.PICKUP) Screen.INVITE else Screen.MOVING
        }
        Screen.INVITE -> InviteScreen(
            "${place.name} · $point",
            "${BuildConfig.WEB_URL}/v/$token",
            onBack = { screen = Screen.PLACE },
            onDepart = { screen = Screen.MOVING },
            onLater = { screen = Screen.MOVING },
        )
        Screen.MOVING -> MovingScreen(
            context, point, etaMin = 12,
            heading = when (mode) {
                VisitMode.PICKUP -> "픽업 장소로 가는 중"
                VisitMode.DROPOFF -> "내릴 곳으로 가는 중"
                VisitMode.PARK -> "목적지로 가는 중"
            },
            // 주행 중엔 장소 · 상태 · 도착했어요만
            family = if (mode == VisitMode.PICKUP && !driving) FamilyStatus("준비됐어요", "14:31") else null,
            onArrived = { screen = Screen.ARRIVAL },
            // 위치 바꾸기는 정차 중만: 픽업하기는 C10, 나머지는 C04 다시 고르기 (미등록 시설은 없음)
            onChangePlace = if (driving || !place.registered) null else ({
                screen = if (mode == VisitMode.PICKUP) Screen.MOVE_PIN else Screen.PLACE
            }),
            banner = result?.let { r -> { ResultBanner(r, point, point) { result = null } } },
        )
        // TODO: onPropose → 제안 API, 결과(웹소켓)로 result 설정
        Screen.MOVE_PIN -> MovePinScreen(
            point, driving,
            onBack = { screen = Screen.MOVING },
            onCancel = { result = ProposalResult.CANCELLED_BY_ME; screen = Screen.MOVING },
        )
        Screen.ARRIVAL -> when (mode) {
            // TODO: 주차 층은 방문 기록 API에 저장
            VisitMode.PARK -> ParkArrivalScreen(context, "북측 주차장", onParked = { screen = Screen.REVIEW }) { screen = Screen.MOVING }
            // Figma 문구가 "하차 완료하면 홈으로 가요"라 C08 대신 홈 (화면동작정의는 C08)
            VisitMode.DROPOFF -> DropoffArrivalScreen(context, point, onDone = home) { screen = Screen.MOVING }
            VisitMode.PICKUP -> PickupArrivalScreen(
                context, point, listOf("엄마", "지민"), "14:48", 2,
                onBoarded = { screen = Screen.REVIEW },
                onWaiting = { screen = Screen.MOVING },
            )
        }
        Screen.REVIEW -> ReviewScreen(
            context, point, "비 · 14:48 도착",
            kicker = if (mode == VisitMode.PICKUP) "픽업 장소 평가" else "장소 평가",
            onDone = { _, _ -> home() }, onSkip = home,
        )
        Screen.HISTORY -> HistoryScreen(onBack = home) { pick(it.place) }
    }
    if (driving && screen !in SHOWN_WHILE_DRIVING) {
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

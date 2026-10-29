package com.majung.vehicle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.majung.vehicle.ui.review.ReviewScreen

// 화면 번호는 docs/planning/마중_화면동작정의.md 기준 (C01~C12)
private enum class Screen { HOME, DESTINATION, MODE, PLACE, INVITE, MOVING, ARRIVAL, REVIEW, HISTORY, MOVE_PIN }

// ponytail: 화면 전환은 상태 하나로. 방문 상태를 서버에서 받기 시작하면 ViewModel로 옮긴다.
@Composable
fun MajungApp(uxRestriction: UxRestrictionWatcher) {
    val driving by uxRestriction.isRestricted.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var place by remember { mutableStateOf(SAMPLE_PLACES.first()) }
    var mode by remember { mutableStateOf(VisitMode.PICKUP) }
    var point by remember { mutableStateOf("북문 로비") }
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

    fun pick(p: Place) { place = p; screen = Screen.MODE }

    when (screen) {
        Screen.HOME -> HomeScreen(
            driving,
            onSearch = { screen = Screen.DESTINATION },
            onRecent = { name -> pick(SAMPLE_PLACES.firstOrNull { it.name == name } ?: Place(name)) },
            onHistory = { screen = Screen.HISTORY },
        )
        Screen.DESTINATION -> DestinationScreen(onBack = home, onSelect = ::pick)
        Screen.MODE -> ModeScreen(place.name, onBack = { screen = Screen.DESTINATION }) { mode = it; screen = Screen.PLACE }
        Screen.PLACE -> PlaceScreen(place.name, mode, onBack = { screen = Screen.MODE }) {
            point = it.name
            // TODO: 그냥가기·내려주기는 NaviClient.requestRoute 후 C06
            screen = if (mode == VisitMode.PICKUP) Screen.INVITE else Screen.MOVING
        }
        Screen.INVITE -> InviteScreen(
            "${place.name} · $point",
            onBack = { screen = Screen.PLACE },
            onDepart = { screen = Screen.MOVING },
            onLater = { screen = Screen.MOVING },
        )
        Screen.MOVING -> MovingScreen(
            context, point, etaMin = 12,
            family = if (mode == VisitMode.PICKUP) FamilyStatus("준비됐어요", "14:31") else null,
            onArrived = { screen = Screen.ARRIVAL },
            // 위치 바꾸기는 정차 중만: 픽업하기는 C10, 나머지는 C04 다시 고르기
            onChangePlace = if (driving) null else ({
                screen = if (mode == VisitMode.PICKUP) Screen.MOVE_PIN else Screen.PLACE
            }),
        )
        Screen.MOVE_PIN -> MovePinScreen(point, driving, onBack = { screen = Screen.MOVING }, onCancel = { screen = Screen.MOVING })
        Screen.ARRIVAL -> when (mode) {
            VisitMode.PARK -> ParkArrivalScreen(context, "북측 주차장", onParked = { screen = Screen.REVIEW }) { screen = Screen.MOVING }
            // Figma 문구가 "하차 완료하면 홈으로 가요"라 C08 대신 홈 (화면동작정의는 C08)
            VisitMode.DROPOFF -> DropoffArrivalScreen(context, point, onDone = home) { screen = Screen.MOVING }
            VisitMode.PICKUP -> PickupArrivalScreen(
                context, point, listOf("엄마", "지민"), "14:48", 2,
                onBoarded = { screen = Screen.REVIEW },
                onWaiting = { screen = Screen.MOVING },
            )
        }
        Screen.REVIEW -> ReviewScreen(context, point, "비 · 14:48 도착", onDone = { _, _ -> home() }, onSkip = home)
        Screen.HISTORY -> HistoryScreen(onBack = home) { r -> pick(SAMPLE_PLACES.firstOrNull { it.name == r.place } ?: Place(r.place)) }
    }
}

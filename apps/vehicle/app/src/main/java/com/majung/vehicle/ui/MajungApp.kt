package com.majung.vehicle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.majung.vehicle.BuildConfig
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.domain.model.*
import com.majung.vehicle.ui.arrival.*
import com.majung.vehicle.ui.destination.DestinationScreen
import com.majung.vehicle.ui.history.HistoryScreen
import com.majung.vehicle.ui.home.HomeScreen
import com.majung.vehicle.ui.invite.InviteScreen
import com.majung.vehicle.ui.mode.ModeScreen
import com.majung.vehicle.ui.moving.*
import com.majung.vehicle.ui.place.PlaceScreen
import com.majung.vehicle.ui.proposal.*
import com.majung.vehicle.ui.review.*
import com.majung.vehicle.ui.theme.Ink
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class Screen { HOME, DESTINATION, MODE, PLACE, INVITE, MOVING, ARRIVAL, REVIEW, HISTORY, MOVE_PIN }
private val SHOWN_WHILE_DRIVING = setOf(Screen.HOME, Screen.MOVING, Screen.ARRIVAL, Screen.MOVE_PIN)

@Composable
fun MajungApp(uxRestriction: UxRestrictionWatcher, model: VehicleViewModel = viewModel()) {
    val driving by uxRestriction.isRestricted.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var facilityId by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf(VisitMode.PICKUP) }
    var proposalId by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissedProposal by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissedResult by rememberSaveable { mutableStateOf<String?>(null) }
    val snapshot = model.snapshot
    val visit = snapshot?.visit
    val facility = snapshot?.facility ?: model.facilities.firstOrNull { it.id == facilityId }
    val selectedMode = visit?.mode?.let(VisitMode::valueOf) ?: mode
    val point = facility?.accessPoints?.firstOrNull { it.id == visit?.confirmedAccessPointId }
    val context = "${facility?.name.orEmpty()} · ${selectedMode.label}"
    val home = { screen = Screen.HOME }
    val ownPending = snapshot?.proposals?.lastOrNull { it.proposedBy == "DRIVER" && it.status == "PENDING" }
    val ownResult = snapshot?.proposals?.firstOrNull { it.proposedBy == "DRIVER" && it.status != "PENDING" }?.takeUnless { it.id == dismissedResult }
    val incoming = snapshot?.proposals?.lastOrNull { it.proposedBy == "PASSENGER" && it.status == "PENDING" && it.id != dismissedProposal }
    val shownProposal = snapshot?.proposals?.firstOrNull { it.id == proposalId }
    val mapPoints = facility?.accessPoints.orEmpty().filter { it.allowed && if (selectedMode == VisitMode.PARK) it.kind == "PARKING_ENTRANCE" else it.kind == "PICKUP" }.map { MapPoint(it.id, it.name, LatLng(it.lat, it.lng)) }

    fun resumeVisit() { screen = when (visit?.phase) {
        "PREPARING" -> Screen.INVITE
        "ARRIVED" -> Screen.ARRIVAL
        "COMPLETED" -> Screen.REVIEW
        else -> Screen.MOVING
    } }
    fun pick(name: String) {
        if (visit != null) { resumeVisit(); return }
        facilityId = model.facilities.firstOrNull { it.name == name }?.id
        if (facilityId != null) screen = Screen.MODE
    }
    fun finish() { model.finish(); home() }

    LaunchedEffect(visit?.id, visit?.phase) {
        when (visit?.phase) {
            "CANCELLED" -> finish()
            "COMPLETED" -> if (snapshot.rating == null) screen = Screen.REVIEW else finish()
            "ARRIVED" -> screen = Screen.ARRIVAL
            "MOVING" -> screen = Screen.MOVING
            "PREPARING" -> screen = Screen.INVITE
            null -> if (screen in setOf(Screen.MOVING, Screen.INVITE, Screen.ARRIVAL, Screen.REVIEW, Screen.MOVE_PIN)) home()
        }
    }
    LaunchedEffect(incoming?.id) { incoming?.let { proposalId = it.id } }
    LaunchedEffect(ownPending?.id, ownResult?.id) {
        if (ownPending == null && ownResult != null && screen == Screen.MOVE_PIN) screen = Screen.MOVING
    }
    BackHandler(screen != Screen.HOME && shownProposal == null) {
        screen = when (screen) {
            Screen.MODE -> Screen.DESTINATION
            Screen.PLACE -> if (visit == null) Screen.MODE else Screen.MOVING
            Screen.MOVE_PIN -> Screen.MOVING
            else -> Screen.HOME
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.HOME -> HomeScreen(driving, onSearch = { if (visit == null) screen = Screen.DESTINATION else resumeVisit() }, onRecent = ::pick,
                onHistory = { model.refresh(); screen = Screen.HISTORY }, recent = model.history.map { it.facility.name }.distinct().take(3),
                activeVisit = visit?.let { "${facility?.name.orEmpty()} · 이어서 하기" }, onResume = ::resumeVisit)
            Screen.DESTINATION -> DestinationScreen(onBack = home, places = model.facilities.map { Place(it.name, it.address, id = it.id) }, onSelect = { facilityId = it.id; screen = Screen.MODE })
            Screen.MODE -> ModeScreen(facility?.name.orEmpty(), onBack = { screen = Screen.DESTINATION }) { mode = it; screen = Screen.PLACE }
            Screen.PLACE -> PlaceScreen(facility?.name.orEmpty(), selectedMode, onBack = { screen = if (visit == null) Screen.MODE else Screen.MOVING },
                points = facility?.accessPoints.orEmpty().filter { it.allowed && if (selectedMode == VisitMode.PARK) it.kind == "PARKING_ENTRANCE" else it.kind == "PICKUP" }
                    .map { AccessPoint(it.name, it.description, listOf("예시 데이터"), id = it.id) }) { selected ->
                if (visit != null) model.changePoint(selected.id) { screen = Screen.MOVING }
                else facility?.let { model.create(it.id, mode, selected.id) {
                    if (mode == VisitMode.PICKUP) screen = Screen.INVITE
                    else screen = Screen.MOVING
                } }
            }
            Screen.INVITE -> InviteScreen("${facility?.name.orEmpty()} · ${point?.name.orEmpty()}",
                "${BuildConfig.WEB_URL.trimEnd('/')}/v/${model.passengerToken.orEmpty()}", onBack = home,
                onDepart = { model.phase("MOVING") { screen = Screen.MOVING } },
                onLater = { model.phase("MOVING") { screen = Screen.MOVING } },
                onCancelVisit = { model.phase("CANCELLED") { finish() } })
            Screen.MOVING -> MovingScreen(context, point?.name.orEmpty(),
                etaMin = snapshot?.vehicle?.etaSec?.let { ceil(it / 60.0).toInt() },
                family = if (selectedMode != VisitMode.PICKUP || driving) null else FamilyStatus(when (visit?.passengerReadiness) {
                    "READY" -> "준비됐어요"; "HEADING_OUT" -> "나오는 중이에요"; "SEEN" -> "링크를 열었어요"; else -> "아직 링크를 안 열었어요"
                }, "동승자 기기에서 보낸 상태"),
                heading = if (visit?.phase == "ARRIVED") "목적지에 도착했어요" else when (selectedMode) { VisitMode.PICKUP -> "픽업 장소로 가는 중"; VisitMode.DROPOFF -> "내릴 곳으로 가는 중"; VisitMode.PARK -> "목적지로 가는 중" },
                onArrived = { if (visit?.phase == "ARRIVED") screen = Screen.ARRIVAL else model.phase("ARRIVED") { screen = Screen.ARRIVAL } },
                onChangePlace = if (driving || mapPoints.size < 2) null else ({ screen = if (selectedMode == VisitMode.PICKUP) Screen.MOVE_PIN else Screen.PLACE }),
                onCancelVisit = if (driving) null else ({ model.phase("CANCELLED") { finish() } }),
                navigationStatus = when (snapshot?.vehicle?.navResult) { "set" -> "내비 안내 중"; "failed" -> "내비 안내 실패"; else -> "내비 SDK 미연결 · 예시 시설" },
                banner = ownResult?.let { proposal -> {
                    val result = when (proposal.status) { "ACCEPTED" -> ProposalResult.ACCEPTED; "REJECTED" -> ProposalResult.REJECTED; else -> ProposalResult.TIMEOUT }
                    ResultBanner(result, facility?.accessPoints?.firstOrNull { it.id == proposal.fromAccessPointId }?.name.orEmpty(),
                        facility?.accessPoints?.firstOrNull { it.id == proposal.accessPointId }?.name.orEmpty()) { dismissedResult = proposal.id }
                } })
            Screen.MOVE_PIN -> if (point != null && mapPoints.isNotEmpty()) MovePinScreen(point.name, driving,
                onBack = { screen = Screen.MOVING }, points = mapPoints,
                pendingTarget = ownPending?.let { p -> facility.accessPoints.firstOrNull { it.id == p.accessPointId }?.name },
                onPropose = { name -> mapPoints.firstOrNull { it.name == name }?.let { model.propose(it.id) } },
                onCancel = { ownPending?.let { model.cancelProposal(it.id) { screen = Screen.MOVING } } })
            Screen.ARRIVAL -> when (selectedMode) {
                VisitMode.PARK -> ParkArrivalScreen(context, point?.name.orEmpty(), onParked = { floor -> model.phase("COMPLETED", floor) { screen = Screen.REVIEW } }, onStillLooking = { screen = Screen.MOVING })
                VisitMode.DROPOFF -> DropoffArrivalScreen(context, point?.name.orEmpty(), onDone = { model.phase("COMPLETED") { screen = Screen.REVIEW } }, onStillGettingOff = { screen = Screen.MOVING })
                VisitMode.PICKUP -> PickupArrivalScreen(context, point?.name.orEmpty(), listOf("동승자"), formatTime(visit?.updatedAt), null,
                    onBoarded = { model.phase("COMPLETED") { screen = Screen.REVIEW } }, onWaiting = { screen = Screen.MOVING })
            }
            Screen.REVIEW -> ReviewScreen(context, point?.name.orEmpty(), "방문 완료 · ${formatTime(visit?.completedAt ?: visit?.updatedAt)}", onDone = { rating, tags ->
                model.rate(when (rating) { Rating.GOOD -> 5; Rating.OK -> 3; Rating.BAD -> 1 }, tags, home)
            }, onSkip = ::finish)
            Screen.HISTORY -> HistoryScreen(records = model.history.map { entry ->
                val p = entry.facility.accessPoints.firstOrNull { it.id == entry.visit.confirmedAccessPointId }
                VisitRecord(entry.facility.name, formatTime(entry.visit.completedAt ?: entry.visit.updatedAt), VisitMode.valueOf(entry.visit.mode),
                    "${p?.name.orEmpty()} · ${entry.visit.parkingFloor.orEmpty()} · ${phaseLabel(entry.visit.phase)}",
                    entry.rating?.let { "평가 ${it.rating}/5 · ${it.tags.joinToString()}" } ?: "평가 없음")
            }, onBack = home) { pick(it.place) }
        }
        shownProposal?.let { proposal ->
            val from = facility?.accessPoints?.firstOrNull { it.id == proposal.fromAccessPointId }
            val to = facility?.accessPoints?.firstOrNull { it.id == proposal.accessPointId }
            if (from != null && to != null) ProposalReceivedScreen(
                FamilyProposal(from.name, to.name, proposal.reason ?: to.description, distanceM(LatLng(from.lat, from.lng), LatLng(to.lat, to.lng)).roundToInt()),
                driving, response = when (proposal.status) { "ACCEPTED" -> true; "REJECTED", "EXPIRED" -> false; else -> null },
                fromPosition = LatLng(from.lat, from.lng), toPosition = LatLng(to.lat, to.lng),
                onAccept = { model.respond(proposal.id, true) }, onReject = { model.respond(proposal.id, false) },
                onContinue = { dismissedProposal = proposal.id; proposalId = null })
        }
        if (driving && screen !in SHOWN_WHILE_DRIVING && shownProposal == null) {
            Box(Modifier.fillMaxSize().background(Ink.bg).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
                Text("정차 후 이용해 주세요", color = Ink.text, fontSize = 28.sp)
            }
        }
        if (model.busy) Box(Modifier.fillMaxSize().background(Ink.bg.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
            Text("서버에 저장 중…", color = Ink.text, fontSize = 24.sp)
        }
        model.error?.let { error ->
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Ink.surface).padding(16.dp)) {
                Text(error, color = Ink.text, fontSize = 18.sp)
                if (!driving) Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Text("다시 연결", Modifier.clickable { model.refresh() }.padding(12.dp), Ink.accent, 18.sp)
                    Text("닫기", Modifier.clickable { model.dismissError() }.padding(12.dp), Ink.sub, 18.sp)
                }
            }
        }
    }
}

private fun formatTime(value: String?): String = try {
    DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
} catch (_: Exception) { "-" }
private fun phaseLabel(phase: String): String = when (phase) {
    "COMPLETED" -> "완료"; "CANCELLED" -> "취소"; "ARRIVED" -> "도착"; "MOVING" -> "이동 중"; else -> "준비 중"
}

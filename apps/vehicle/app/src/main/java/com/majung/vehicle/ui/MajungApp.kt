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
import com.majung.vehicle.data.remote.dto.AccessPointDto
import com.majung.vehicle.data.remote.dto.RankedAccessPointDto
import com.majung.vehicle.domain.model.*
import com.majung.vehicle.ui.arrival.*
import com.majung.vehicle.ui.destination.DestinationScreen
import com.majung.vehicle.ui.history.HistoryScreen
import com.majung.vehicle.ui.home.HomeScreen
import com.majung.vehicle.ui.invite.ContactsRoute
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

private enum class Screen { HOME, DESTINATION, MODE, PLACE, INVITE, CONTACTS, MOVING, ARRIVAL, REVIEW, HISTORY, MOVE_PIN }
private val SHOWN_WHILE_DRIVING = setOf(Screen.HOME, Screen.MOVING, Screen.ARRIVAL, Screen.MOVE_PIN)

@Composable
fun MajungApp(uxRestriction: UxRestrictionWatcher, model: VehicleViewModel = viewModel()) {
    val driving by uxRestriction.isRestricted.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var facilityId by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf(VisitMode.PICKUP) }
    var proposalId by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissedProposal by rememberSaveable { mutableStateOf<String?>(null) }
    val snapshot = model.snapshot
    val visit = snapshot?.visit
    val facility = snapshot?.facility ?: model.facilities.firstOrNull { it.id == facilityId }
    val selectedMode = visit?.mode?.let(VisitMode::valueOf) ?: mode
    val point = facility?.accessPoints?.firstOrNull { it.id == visit?.confirmedAccessPointId }
    val context = "${facility?.name.orEmpty()} · ${selectedMode.label}"
    val home = { screen = Screen.HOME }
    val ownPending = snapshot?.proposals?.lastOrNull { it.proposedBy == "DRIVER" && it.status == "PENDING" }
    // 내가 직접 취소한 제안은 결과를 알릴 필요가 없다
    val ownResult = snapshot?.proposals?.firstOrNull()?.takeIf { it.proposedBy == "DRIVER" && it.status !in setOf("PENDING", "CANCELLED") && it.id != model.dismissedResult }
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
        val choose = {
            facilityId = model.facilities.firstOrNull { it.name == name }?.id
            if (facilityId != null) screen = Screen.MODE
        }
        // 출발 전(QR 대기) 방문은 버리고 새로 고른다. 이미 출발한 방문은 이어서 한다
        when {
            visit?.phase == "PREPARING" -> model.startOver(choose)
            visit != null -> resumeVisit()
            else -> choose()
        }
    }
    fun search() = when {
        visit?.phase == "PREPARING" -> model.startOver { screen = Screen.DESTINATION }
        visit == null -> screen = Screen.DESTINATION
        else -> resumeVisit()
    }
    fun finish() { model.finish(); home() }
    /** 다시 가기: 같은 장소·방식·지점으로 바로 새 방문을 만들고 QR 공유(픽업) 또는 이동 화면으로 간다 */
    fun revisit(record: VisitRecord) {
        // 이미 출발한 방문은 그대로 이어서 하고, 출발 전(QR 대기) 방문은 새 방문으로 바꾼다
        if (visit != null && visit.phase != "PREPARING") { resumeVisit(); return }
        facilityId = record.facilityId; mode = record.mode
        val f = model.facilities.firstOrNull { it.id == record.facilityId }
        val p = f?.accessPoints?.firstOrNull { it.id == record.pointId && it.allowed }
        if (f == null) { screen = Screen.DESTINATION; return }
        if (p == null) { screen = Screen.PLACE; return }
        model.revisit(f.id, record.mode, p.id) { screen = if (record.mode == VisitMode.PICKUP) Screen.INVITE else Screen.MOVING }
    }

    LaunchedEffect(visit?.id, visit?.phase) {
        when (visit?.phase) {
            "CANCELLED" -> finish()
            "COMPLETED" -> if (snapshot.rating == null) screen = Screen.REVIEW else finish()
            "ARRIVED" -> screen = Screen.ARRIVAL
            "MOVING" -> screen = Screen.MOVING
            "PREPARING" -> screen = Screen.INVITE
            null -> if (screen in setOf(Screen.MOVING, Screen.INVITE, Screen.CONTACTS, Screen.ARRIVAL, Screen.REVIEW, Screen.MOVE_PIN)) home()
        }
    }
    LaunchedEffect(incoming?.id) { incoming?.let { proposalId = it.id } }
    // C04·C06에서 그 시설·모드의 날씨와 추천 순서를 받고, 머무는 동안 10분마다 갱신한다
    val wantsWeather = screen == Screen.PLACE || screen == Screen.MOVING
    LaunchedEffect(wantsWeather, facility?.id, selectedMode) {
        val id = facility?.id ?: return@LaunchedEffect
        while (wantsWeather) { model.loadPlaceWeather(id, selectedMode); kotlinx.coroutines.delay(10 * 60_000L) }
    }
    LaunchedEffect(ownPending?.id, ownResult?.id) {
        if (ownPending == null && ownResult != null && screen == Screen.MOVE_PIN) screen = Screen.MOVING
    }
    BackHandler(screen != Screen.HOME && shownProposal == null) {
        screen = when (screen) {
            Screen.MODE -> Screen.DESTINATION
            Screen.PLACE -> if (visit == null) Screen.MODE else Screen.MOVING
            Screen.MOVE_PIN -> Screen.MOVING
            Screen.CONTACTS -> Screen.INVITE
            else -> Screen.HOME
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.HOME -> HomeScreen(driving, onSearch = ::search, onRecent = ::pick,
                onHistory = { model.refresh(); screen = Screen.HISTORY }, recent = model.history.map { it.facility.name }.distinct().take(3),
                activeVisit = visit?.let { "${facility?.name.orEmpty()} · 이어서 하기" }, onResume = ::resumeVisit)
            Screen.DESTINATION -> DestinationScreen(onBack = home, places = model.facilities.map { Place(it.name, it.address, id = it.id) }, onSelect = { facilityId = it.id; screen = Screen.MODE })
            Screen.MODE -> ModeScreen(facility?.name.orEmpty(), onBack = { screen = Screen.DESTINATION }) { mode = it; screen = Screen.PLACE }
            Screen.PLACE -> PlaceScreen(facility?.name.orEmpty(), selectedMode, onBack = { screen = if (visit == null) Screen.MODE else Screen.MOVING },
                weather = model.placeWeather?.weather?.summary, rainy = model.placeWeather?.weather?.rainSoon == true,
                points = rankedPoints(facility?.accessPoints.orEmpty().filter { it.allowed && if (selectedMode == VisitMode.PARK) it.kind == "PARKING_ENTRANCE" else it.kind == "PICKUP" }, model.placeWeather?.ranking)) { selected ->
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
                onCancelVisit = { model.phase("CANCELLED") { finish() } },
                onContacts = { screen = Screen.CONTACTS })
            Screen.CONTACTS -> ContactsRoute(model, onBack = { screen = Screen.INVITE })
            Screen.MOVING -> MovingScreen(context, point?.name.orEmpty(),
                etaMin = snapshot?.vehicle?.etaSec?.let { ceil(it / 60.0).toInt() },
                family = if (selectedMode != VisitMode.PICKUP || driving) null else FamilyStatus(when (visit?.passengerReadiness) {
                    "READY" -> "준비됐어요"; "HEADING_OUT" -> "나오는 중이에요"; "SEEN" -> "링크를 열었어요"; else -> "아직 링크를 안 열었어요"
                }, snapshot?.passengerWaitSpot?.let { "${it.name}에서 대기 · 걸어서 ${it.walkMinutes}분" } ?: "동승자 기기에서 보낸 상태"),
                weather = model.placeWeather?.weather?.let { "도착지 날씨 · ${it.summary}" }, rainy = model.placeWeather?.weather?.rainSoon == true,
                heading = if (visit?.phase == "ARRIVED") "목적지에 도착했어요" else when (selectedMode) { VisitMode.PICKUP -> "픽업 장소로 가는 중"; VisitMode.DROPOFF -> "내릴 곳으로 가는 중"; VisitMode.PARK -> "목적지로 가는 중" },
                onArrived = { if (visit?.phase == "ARRIVED") screen = Screen.ARRIVAL else model.phase("ARRIVED") { screen = Screen.ARRIVAL } },
                onChangePlace = if (driving || mapPoints.size < 2) null else ({ screen = if (selectedMode == VisitMode.PICKUP) Screen.MOVE_PIN else Screen.PLACE }),
                onCancelVisit = if (driving) null else ({ model.phase("CANCELLED") { finish() } }),
                navigationStatus = model.naviStatus?.label ?: when (snapshot?.vehicle?.navResult) { "set" -> NaviStatus.GUIDING.label; "failed" -> NaviStatus.FAILED.label; else -> "내비 연결 전" },
                onShowNavi = model::showNavi,
                onRetryNavi = if (model.naviStatus == NaviStatus.FAILED || (model.naviStatus == null && snapshot?.vehicle?.navResult == "failed")) model::retryNavi else null,
                banner = ownResult?.let { proposal -> {
                    val result = when (proposal.status) { "ACCEPTED" -> ProposalResult.ACCEPTED; "REJECTED" -> ProposalResult.REJECTED; "CANCELLED" -> ProposalResult.CANCELLED_BY_ME; else -> ProposalResult.TIMEOUT }
                    ResultBanner(result, facility?.accessPoints?.firstOrNull { it.id == proposal.fromAccessPointId }?.name.orEmpty(),
                        facility?.accessPoints?.firstOrNull { it.id == proposal.accessPointId }?.name.orEmpty()) { model.dismissResult(proposal.id) }
                } })
            Screen.MOVE_PIN -> if (point != null && mapPoints.isNotEmpty()) MovePinScreen(point.name, driving,
                currentPosition = LatLng(point.lat, point.lng),
                onBack = { screen = Screen.MOVING }, points = mapPoints,
                pendingTarget = ownPending?.let { p -> facility.accessPoints.firstOrNull { it.id == p.accessPointId }?.name },
                pendingPosition = ownPending?.let { p -> facility.accessPoints.firstOrNull { it.id == p.accessPointId }?.let { LatLng(it.lat, it.lng) } },
                onPropose = { registered, position -> if (registered != null) model.propose(registered.id) else model.proposeLocation(position.lat, position.lng) },
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
                    listOfNotNull(entry.weather?.let(::weatherWord), entry.rating?.let { "평가 ${it.rating}/5 · ${it.tags.joinToString()}" } ?: "평가 없음").joinToString(" · "),
                    facilityId = entry.facility.id, pointId = entry.visit.confirmedAccessPointId)
            }, onBack = home, onRevisit = ::revisit)
        }
        shownProposal?.let { proposal ->
            val from = facility?.accessPoints?.firstOrNull { it.id == proposal.fromAccessPointId }
            val to = facility?.accessPoints?.firstOrNull { it.id == proposal.accessPointId }
            if (from != null && to != null) ProposalReceivedScreen(
                FamilyProposal(from.name, to.name, proposal.reason ?: to.description, distanceM(LatLng(from.lat, from.lng), LatLng(to.lat, to.lng)).roundToInt()),
                driving, response = when (proposal.status) { "ACCEPTED" -> true; "REJECTED", "EXPIRED", "CANCELLED" -> false; else -> null },
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

/** 서버 추천 순서대로 카드를 만든다. 날씨·추천을 못 받으면 서버가 준 순서 그대로, 추천 표시 없이 */
private fun rankedPoints(points: List<AccessPointDto>, ranking: List<RankedAccessPointDto>?): List<AccessPoint> {
    val byId = ranking?.associateBy { it.accessPointId }.orEmpty()
    val ordered = if (ranking == null) points else ranking.mapNotNull { r -> points.find { it.id == r.accessPointId } } + points.filter { it.id !in byId }
    return ordered.map { p ->
        val r = byId[p.id]
        val tags = listOfNotNull(if (p.covered) "지붕 있음" else "지붕 없음", p.walkMinutes?.let { "걸어서 ${it}분" })
        AccessPoint(p.name, r?.reason ?: p.description, tags, recommended = r?.recommended == true, id = p.id)
    }
}

/** 기록 한 줄에 쓰는 짧은 날씨: 비 · 14° */
private fun weatherWord(w: com.majung.vehicle.data.remote.dto.WeatherDto): String {
    val word = when (w.condition) { "RAIN" -> "비"; "SNOW" -> "눈"; else -> if (w.rainSoon) "비 예보" else if (w.condition == "CLOUDY") "흐림" else "맑음" }
    return w.tempC?.let { "$word · $it°" } ?: word
}

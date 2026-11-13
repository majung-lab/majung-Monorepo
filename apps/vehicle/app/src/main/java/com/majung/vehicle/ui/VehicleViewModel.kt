package com.majung.vehicle.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.majung.vehicle.core.notice.AndroidVisitNotifier
import com.majung.vehicle.core.notice.VisitNotifier
import com.majung.vehicle.data.remote.ApiException
import com.majung.vehicle.data.remote.VisitApi
import com.majung.vehicle.data.remote.dto.*
import com.majung.vehicle.domain.model.VisitMode
import com.majung.vehicle.pleos.navi.NaviClient
import com.majung.vehicle.pleos.navi.NaviResult
import com.majung.vehicle.pleos.navi.PleosNaviClient
import com.majung.vehicle.pleos.voice.VoiceGuide
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class VehicleViewModel @JvmOverloads constructor(
    application: Application,
    private val api: VisitApi = VisitApi(),
    private val navi: NaviClient = PleosNaviClient(application),
    private val notice: VisitNotifier = AndroidVisitNotifier(application),
) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("visit", 0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    val deviceId: String = prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }
    var facilities by mutableStateOf<List<FacilityDto>>(emptyList()); private set
    var history by mutableStateOf<List<HistoryEntry>>(emptyList()); private set
    var snapshot by mutableStateOf<VisitSnapshot?>(null); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var passengerToken by mutableStateOf(prefs.getString("passenger", null)); private set
    private var driverToken = prefs.getString("driver", null)
    private var lastLocation: Location? = null
    var dismissedResult by mutableStateOf(prefs.getString("dismissedResult", null)); private set
    /** 이번 실행에서 내비에 넣은 결과. null이면 서버에 남은 마지막 결과를 쓴다 */
    var naviStatus by mutableStateOf<NaviStatus?>(null); private set
    /** C04에서 보는 시설 날씨와 추천 순서. 못 받으면 null이고 지점은 서버 순서 그대로 */
    var placeWeather by mutableStateOf<FacilityWeatherDto?>(null); private set
    /** 내비가 알려 준 지금 목적지까지 남은 시간(초). 위치와 함께 서버로 보낸다. 목적지가 바뀌면 지운다 */
    private var etaSec: Int? = null
    /** 내비가 안내를 시작한 시각. 새 목적지를 넣으면 이전 경로가 취소 이벤트로 오므로 직후의 종료는 무시한다 */
    private var guidingSince = 0L
    private var placeWeatherKey: String? = null
    /** 가족 제안을 말로 묻고 듣는 상태 (화면 표시용). 음성 엔진이 없으면 계속 Idle */
    var voiceState by mutableStateOf<VoiceGuide.State>(VoiceGuide.State.Idle); private set
    private val voice: VoiceGuide? = runCatching { VoiceGuide(application).also { it.onState = { s -> voiceState = s; if (s == VoiceGuide.State.Idle) snapshot?.let(::sayReadiness) } } }
        .onFailure { android.util.Log.w("VoiceGuide", "음성 엔진 없음", it) }.getOrNull()
    private var askedProposal: String? = null

    init {
        navi.watchArrival(::onNaviArrived)
        navi.watchEta(::onNaviEta)
        navi.watchGuidanceEnded(::onNaviEnded)
        refresh()
        scope.launch {
            while (isActive) {
                delay(5_000)
                // 앱을 다시 켜서 이번 실행의 결과가 없으면 서버에 남은 "안내 중"을 믿는다
                val guiding = naviStatus == NaviStatus.GUIDING || (naviStatus == null && snapshot?.vehicle?.navResult == "set")
                if (guiding && snapshot?.visit?.phase == "MOVING") navi.refreshEta()
                lock.withLock {
                    try { refreshVisit() } catch (e: CancellationException) { throw e } catch (e: Exception) { handle(e) }
                }
            }
        }
    }

    /** 문자 초대: 보내는 중인 번호, 이번 방문에서 보낸 번호, 마지막 결과 문구 */
    var inviteSending by mutableStateOf<String?>(null); private set
    var invitedNumbers by mutableStateOf<Set<String>>(emptySet()); private set
    var inviteNotice by mutableStateOf<String?>(null); private set

    /** 동승자 링크를 문자로 보낸다. 번호는 서버로만 보내고 앱에 저장하지 않는다 */
    fun sendInvite(name: String?, digits: String) {
        val token = driverToken
        if (token == null) { inviteNotice = "진행 중인 방문이 없어요"; return }
        if (inviteSending != null) return
        inviteSending = digits
        scope.launch {
            inviteNotice = try {
                val body = buildJsonObject { put("requestId", UUID.randomUUID().toString()); put("phone", digits) }
                val result: InviteResult = api.write("POST", "/api/visits/$token/invites", body)
                invitedNumbers = invitedNumbers + digits
                "${name ?: result.sentTo}님께 링크를 보냈어요"
            } catch (e: CancellationException) { throw e
            } catch (e: ApiException) {
                when (e.status) {
                    400 -> "휴대폰 번호를 확인해 주세요"
                    429 -> "이 방문에서는 문자를 더 보낼 수 없어요"
                    409 -> "이미 끝난 방문이에요"
                    else -> "문자를 보내지 못했어요. 잠시 후 다시 시도해 주세요"
                }
            } catch (e: Exception) { "서버에 연결하지 못했어요. 연결을 확인하고 다시 시도해 주세요." }
            finally { inviteSending = null }
        }
    }

    fun dismissError() { error = null }
    fun dismissResult(id: String) {
        dismissedResult = id
        prefs.edit().putString("dismissedResult", id).apply()
    }
    fun refresh() = run {
        facilities = api.get("/api/facilities")
        history = api.get<List<HistoryEntry>>("/api/history?driverDeviceId=$deviceId").filter { it.visit.phase == "COMPLETED" }
        if (driverToken == null) prefs.getString("pendingBody", null)?.let { recoverSession(Json.parseToJsonElement(it)) }
        refreshVisit()
    }

    /** 날씨는 추천에만 쓰므로 실패해도 오류를 띄우지 않는다 */
    fun loadPlaceWeather(facilityId: String, mode: VisitMode) {
        val key = "$facilityId/${mode.name}"
        // 같은 시설을 다시 받을 때(10분마다 갱신)는 이전 값을 그대로 보여 준다
        if (placeWeatherKey != key) placeWeather = null
        placeWeatherKey = key
        scope.launch {
            val result = try { api.get<FacilityWeatherDto>("/api/facilities/$facilityId/weather?mode=${mode.name}&driverDeviceId=$deviceId") } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (placeWeatherKey == key) placeWeather = result
        }
    }

    private suspend fun refreshVisit() {
        val token = driverToken ?: return
        applySnapshot(api.get("/api/visits/$token"))
    }

    private fun applySnapshot(value: VisitSnapshot) {
        val old = snapshot
        if (old == null || old.visit.id != value.visit.id || value.visit.version >= old.visit.version) {
            snapshot = value
            requestNaviIfNeeded(value)
            updateNotice(value)
        }
    }

    /** 내비가 앞에 있어도 알림 한 번으로 마중에 돌아오게 한다 */
    private fun updateNotice(value: VisitSnapshot) {
        val visit = value.visit
        val point = value.facility.accessPoints.firstOrNull { it.id == visit.confirmedAccessPointId }?.name.orEmpty()
        val title = "마중 · $point"
        when (visit.phase) {
            "PREPARING" -> notice.show(title, "출발 준비 중 · 눌러서 마중 열기")
            "MOVING" -> notice.show(title, "가는 중 · 눌러서 마중 열기")
            "ARRIVED" -> notice.show(title, "도착했어요 · 눌러서 마중 열기")
            else -> { notice.stop(); return }
        }
        val pending = value.proposals.firstOrNull { it.proposedBy == "PASSENGER" && it.status == "PENDING" }
        pending?.let { p ->
            val to = value.facility.accessPoints.firstOrNull { it.id == p.accessPointId }?.name ?: "지도에서 고른 위치"
            notice.alert("proposal:${p.id}", "가족이 장소를 바꾸자고 해요", to)
            if (p.id != askedProposal) { askedProposal = p.id; askByVoice(p.id, to) }
        }
        if (pending == null) voice?.cancel()   // 화면에서 먼저 답했거나 가족이 취소했다
        if (visit.passengerReadiness == "HEADING_OUT") notice.alert("out:${visit.id}", "가족이 나오는 중이에요", point)
        if (visit.passengerReadiness == "AT_LOBBY") notice.alert("lobby:${visit.id}", "가족이 로비에 도착했어요", point)
        sayReadiness(value)
    }

    /**
     * 가족이 준비·나오는 중·도착을 누르면 한 번 소리로 알린다. 운전 중에 화면을 보지 않아도 되게 한다.
     * 제안을 묻고 듣는 중이면 끼어들지 않고 다음 상태 갱신 때 말한다. 말한 상태는 저장해 앱을 다시 켜도 되풀이하지 않는다
     */
    private fun sayReadiness(value: VisitSnapshot) {
        val v = voice ?: return
        val visit = value.visit
        if (visit.mode != "PICKUP" || visit.phase !in listOf("PREPARING", "MOVING", "ARRIVED")) return
        // passengerWaitSpot은 가족이 지금 있는 곳(웹의 "지금 어디 계세요?")이다. 나오는 출발점으로 말한다
        val spot = value.passengerWaitSpot
        val text = when (visit.passengerReadiness) {
            "READY" -> "가족이 준비됐어요."
            "HEADING_OUT" -> if (spot == null) "가족이 지금 나오고 있어요."
                else "가족이 ${spot.name}에서 나오고 있어요." + (spot.walkMinutes?.let { " 만날 곳까지 걸어서 ${it}분쯤 걸려요." } ?: "")
            "AT_LOBBY" -> "가족이 로비에 도착해서 기다리고 있어요."
            else -> return
        }
        val key = "${visit.id}:${visit.passengerReadiness}"
        if (prefs.getString("spokenReadiness", null) == key) return
        if (voiceState != VoiceGuide.State.Idle && voiceState !is VoiceGuide.State.Heard) return
        prefs.edit().putString("spokenReadiness", key).apply()
        v.say(text)
    }

    /**
     * 이동 중인 방문의 확정 장소가 정해지거나 바뀌면 내장 내비 목적지로 넣는다.
     * 같은 방문·장소는 한 번만 보내서 앱을 다시 켜도 지난 명령을 자동으로 다시 실행하지 않는다.
     */
    private fun requestNaviIfNeeded(value: VisitSnapshot) {
        val visit = value.visit
        if (visit.phase != "MOVING") return
        val point = value.facility.accessPoints.firstOrNull { it.id == visit.confirmedAccessPointId } ?: return
        val key = "${visit.id}:${point.id}"
        if (prefs.getString("naviTarget", null) == key) return
        prefs.edit().putString("naviTarget", key).apply()
        naviStatus = NaviStatus.REQUESTING
        etaSec = null
        navi.requestRoute(point.name, point.lat, point.lng) { result ->
            if (prefs.getString("naviTarget", null) != key) return@requestRoute
            naviStatus = if (result is NaviResult.Guiding) NaviStatus.GUIDING else NaviStatus.FAILED
            if (result is NaviResult.Guiding) guidingSince = System.currentTimeMillis()
            reportNavi(if (result is NaviResult.Guiding) "set" else "failed")
        }
    }

    fun showNavi() = navi.showNavi()

    /** 주행 중에도 눈을 떼지 않고 답하도록 제안을 읽어 주고 한 번 듣는다. 못 알아들으면 화면 응답을 기다린다 */
    private fun askByVoice(id: String, to: String) {
        val v = voice ?: return
        v.ask("가족이 만날 곳을 $to 쪽으로 바꾸자고 해요. 바꾸려면 바꿔, 그대로 가려면 그대로라고 말해 주세요.") { answer ->
            when (answer) {
                true -> { respond(id, true); v.say("목적지를 $to 쪽으로 바꿨어요. 새 장소로 다시 안내할게요.") }
                false -> { respond(id, false); v.say("기존 장소로 계속 안내할게요.") }
                null -> v.say("잘 알아듣지 못했어요. 차를 세운 뒤 화면에서 답해 주세요.")
            }
        }
    }

    /** 디버그 빌드에서 에뮬레이터 대신 들은 말을 넣는다 */
    fun injectVoice(text: String) = voice?.inject(text)

    /** 디버그 빌드에서 에뮬레이터 내비 대신 도착(arrived)·경로 종료(ended) 이벤트를 넣는다 */
    fun injectNavi(event: String, name: String) = when (event) { "arrived" -> onNaviArrived(name); "ended" -> onNaviEnded(); else -> Unit }

    /**
     * 내비의 남은 시간. 내비가 마중 장소가 아닌 곳(300m 밖)을 안내 중이면 쓰지 않는다.
     * 1분 넘게 바뀌었을 때만 바로 보내고, 나머지는 다음 위치 전송에 실린다
     */
    private fun onNaviEta(remainSec: Int, destLat: Double?, destLng: Double?) {
        val value = snapshot ?: return
        if (value.visit.phase != "MOVING" || remainSec < 0 || naviStatus == NaviStatus.ENDED) return
        val point = value.facility.accessPoints.firstOrNull { it.id == value.visit.confirmedAccessPointId } ?: return
        if (destLat != null && destLng != null) {
            val d = FloatArray(1)
            Location.distanceBetween(destLat, destLng, point.lat, point.lng, d)
            if (d[0] > ARRIVAL_RADIUS_M) return
        }
        val before = etaSec
        etaSec = remainSec.coerceAtMost(86_400)
        if (before == null || kotlin.math.abs(before - remainSec) >= 60) lastLocation?.let(::publishLocation)
    }

    /**
     * 내비가 도착을 알리면 "도착했어요"를 대신 누르고 마중 화면을 앞으로 띄운다.
     * 운전자가 내비에서 다른 곳으로 간 경우는 무시한다 (도착지 이름이 우리 장소이거나 차가 300m 안일 때만).
     */
    private fun onNaviArrived(name: String) {
        val value = snapshot ?: return
        if (value.visit.phase != "MOVING") return
        val point = value.facility.accessPoints.firstOrNull { it.id == value.visit.confirmedAccessPointId } ?: return
        val near = lastLocation?.let { here ->
            val d = FloatArray(1)
            Location.distanceBetween(here.latitude, here.longitude, point.lat, point.lng, d)
            d[0] < ARRIVAL_RADIUS_M
        } ?: false
        if (name != point.name && !near) return
        phase("ARRIVED") { notice.bringToFront("마중 · ${point.name}", "도착했어요 · 가족에게 알렸어요") }
    }

    /**
     * 운전자가 내비에서 안내를 끄면 가족에게 알린다. 가족 화면은 남은 시간 대신 차 위치만 보여 준다.
     * 도착으로 경로가 끝난 경우는 알리지 않는다: 잠시 기다려 도착 이벤트가 먼저 처리되게 하고, 차가 만날 곳 근처면 무시한다.
     */
    private fun onNaviEnded() {
        if (snapshot?.visit?.phase != "MOVING" || naviStatus == NaviStatus.REQUESTING || naviStatus == NaviStatus.ENDED) return
        if (System.currentTimeMillis() - guidingSince < IGNORE_END_AFTER_START_MS) return
        scope.launch {
            delay(3_000)
            val value = snapshot ?: return@launch
            if (value.visit.phase != "MOVING" || naviStatus == NaviStatus.REQUESTING || naviStatus == NaviStatus.ENDED) return@launch
            val point = value.facility.accessPoints.firstOrNull { it.id == value.visit.confirmedAccessPointId }
            val near = point != null && lastLocation?.let { here ->
                val d = FloatArray(1)
                Location.distanceBetween(here.latitude, here.longitude, point.lat, point.lng, d)
                d[0] < ARRIVAL_RADIUS_M
            } == true
            if (near) return@launch
            naviStatus = NaviStatus.ENDED
            etaSec = null
            reportNavi("ended")
        }
    }

    fun retryNavi() {
        prefs.edit().remove("naviTarget").apply()
        snapshot?.let(::requestNaviIfNeeded)
    }

    private fun reportNavi(result: String) {
        scope.launch {
            lock.withLock {
                // 위치 전송과 버전이 엇갈리면 최신 상태를 받아 한 번 더 보낸다
                for (attempt in 0..1) {
                    val token = driverToken ?: return@withLock
                    val version = snapshot?.visit?.version ?: return@withLock
                    try {
                        applySnapshot(api.write("PATCH", "/api/visits/$token", buildJsonObject {
                            put("requestId", UUID.randomUUID().toString()); put("version", version)
                            putJsonObject("vehicle") {
                                put("lat", lastLocation?.latitude); put("lng", lastLocation?.longitude)
                                put("etaSec", etaSec); put("navResult", result)
                            }
                        }))
                        return@withLock
                    } catch (e: CancellationException) { throw e
                    } catch (e: ApiException) {
                        if (e.status != 409 || attempt == 1) { handle(e); return@withLock }
                        refreshVisit()
                    } catch (e: Exception) { handle(e); return@withLock }
                }
            }
        }
    }

    fun create(facilityId: String, mode: VisitMode, pointId: String, onDone: () -> Unit) = run { createVisit(facilityId, mode, pointId, onDone) }

    /**
     * 다시 가기: 아직 출발 전(QR 대기)인 방문이 있으면 취소하고 새로 만든다.
     * 이미 출발한 방문은 건드리지 않는다 (화면에서 그 방문으로 돌아간다).
     */
    fun revisit(facilityId: String, mode: VisitMode, pointId: String, onDone: () -> Unit) = run {
        discardPreparingVisit()
        createVisit(facilityId, mode, pointId, onDone)
    }

    /** 처음부터 다시 고르기: 출발 전(QR 대기) 방문을 정리하고 끝나면 onDone */
    fun startOver(onDone: () -> Unit) = run {
        discardPreparingVisit()
        onDone()
    }

    private suspend fun discardPreparingVisit() {
        val current = snapshot ?: return
        val token = driverToken ?: return
        if (current.visit.phase != "PREPARING") return
        api.write<VisitSnapshot>("PATCH", "/api/visits/$token", buildJsonObject {
            put("requestId", UUID.randomUUID().toString()); put("version", current.visit.version); put("phase", "CANCELLED")
        })
        driverToken = null; passengerToken = null; snapshot = null
        prefs.edit().remove("driver").remove("passenger").commit()
        notice.stop()
    }

    private suspend fun createVisit(facilityId: String, mode: VisitMode, pointId: String, onDone: () -> Unit) {
        check(driverToken == null) { "먼저 진행 중인 방문을 종료해 주세요" }
        val body = prefs.getString("pendingBody", null)?.let(Json::parseToJsonElement) ?: buildJsonObject {
            put("requestId", UUID.randomUUID().toString()); put("facilityId", facilityId); put("mode", mode.name); put("accessPointId", pointId); put("driverDeviceId", deviceId)
        }.also { prefs.edit().putString("pendingBody", it.toString()).commit() }
        val session = recoverSession(body)
        refreshVisit()
        if (session.visit.mode != VisitMode.PICKUP.name) {
            val next: VisitSnapshot = api.write("PATCH", "/api/visits/${session.driverToken}", buildJsonObject {
                put("requestId", UUID.randomUUID().toString()); put("version", snapshot!!.visit.version); put("phase", "MOVING")
            })
            applySnapshot(next)
        }
        // 정차 중이면 위치가 안 바뀌므로 동승자 링크가 열리기 전에 현재 위치를 먼저 보낸다
        lastLocation?.let { location -> try { sendLocation(location) } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
        onDone()
    }

    private suspend fun recoverSession(body: JsonElement): VisitSession {
        val session: VisitSession = api.write("POST", "/api/visits", body)
        driverToken = session.driverToken; passengerToken = session.passengerToken
        invitedNumbers = emptySet(); inviteNotice = null
        prefs.edit().putString("driver", session.driverToken).putString("passenger", session.passengerToken).remove("pendingBody").commit()
        return session
    }

    fun changePoint(pointId: String, onDone: () -> Unit) = mutate("PATCH", "", { put("accessPointId", pointId) }, onDone)
    fun phase(phase: String, floor: String? = null, onDone: () -> Unit = {}) = mutate("PATCH", "", {
        put("phase", phase)
        floor?.let { put("parkingFloor", it) }
    }, onDone)
    fun propose(pointId: String, onDone: () -> Unit = {}) = mutate("POST", "/proposals", { put("accessPointId", pointId) }, onDone)
    fun proposeLocation(lat: Double, lng: Double, onDone: () -> Unit = {}) = mutate("POST", "/proposals", {
        putJsonObject("location") { put("lat", lat); put("lng", lng) }
    }, onDone)
    fun respond(id: String, accept: Boolean) = mutate("POST", "/proposals/$id/respond", { put("accept", accept) })
    fun cancelProposal(id: String, onDone: () -> Unit) = mutate("DELETE", "/proposals/$id", {}, onDone)
    fun rate(rating: Int, tags: List<String>, onDone: () -> Unit) = mutate("POST", "/rating", {
        put("rating", rating); putJsonArray("tags") { tags.forEach { add(it) } }
    }) { finish(); onDone() }

    fun publishLocation(location: Location) {
        lastLocation = location
        if (snapshot?.visit?.phase !in listOf("PREPARING", "MOVING", "ARRIVED") || busy) return
        // 위치는 뒤에서 계속 보낸다. 가족이 대기 지점·준비 상태를 바꿔 버전이 엇갈려도 운전자에게 경고하지 않고
        // 최신 상태로 한 번 더 보낸다. 그래도 실패하면 다음 위치 갱신 때 다시 보낸다
        scope.launch {
            lock.withLock {
                for (attempt in 0..1) {
                    try { sendLocation(location); return@withLock }
                    catch (e: CancellationException) { throw e }
                    catch (e: ApiException) { if (e.status != 409 || attempt == 1) return@withLock; refreshVisit() }
                    catch (_: Exception) { return@withLock }
                }
            }
        }
    }

    private suspend fun sendLocation(location: Location) {
        val token = driverToken ?: return
        val version = snapshot?.visit?.version ?: return
        applySnapshot(api.write("PATCH", "/api/visits/$token", buildJsonObject {
            put("requestId", UUID.randomUUID().toString()); put("version", version)
            putJsonObject("vehicle") { put("lat", location.latitude); put("lng", location.longitude); put("etaSec", etaSec) }
        }))
    }

    fun finish() {
        // 목적지 기록에는 완료한 방문만 남긴다
        snapshot?.let { done ->
            val others = history.filter { it.visit.id != done.visit.id }
            history = if (done.visit.phase != "COMPLETED") others else listOf(HistoryEntry(done.visit, done.facility, done.rating)) + others
        }
        driverToken = null; passengerToken = null; snapshot = null
        prefs.edit().remove("driver").remove("passenger").commit()
        notice.stop()
    }

    private fun mutate(method: String, suffix: String, fields: JsonObjectBuilder.() -> Unit, onDone: () -> Unit = {}) = run {
        val token = driverToken ?: error("진행 중인 방문이 없어요")
        val version = snapshot?.visit?.version ?: error("방문을 먼저 새로고침해 주세요")
        val body = buildJsonObject { put("requestId", UUID.randomUUID().toString()); put("version", version); fields() }
        applySnapshot(api.write(method, "/api/visits/$token$suffix", body))
        onDone()
    }

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { lock.withLock { block() }; error = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { handle(e) }
            finally { busy = false }
        }
    }

    private suspend fun handle(e: Exception) {
        if (e is ApiException && e.status == 409) {
            try { refreshVisit() } catch (_: Exception) { }
            error = "다른 기기에서 상태가 바뀌었어요. 최신 내용을 확인하고 다시 선택해 주세요."
        } else if (e is ApiException && e.status in listOf(404, 410) && driverToken != null) {
            driverToken = null; passengerToken = null; snapshot = null
            prefs.edit().remove("driver").remove("passenger").commit()
            notice.stop()
            error = "방문 링크가 만료되었거나 없어졌어요. 새 방문을 시작해 주세요."
        } else if (e is ApiException && e.status in listOf(400, 403)) {
            error = "이 작업을 할 수 없어요. 장소와 방문 상태를 확인해 주세요."
        } else {
            error = if (e is IllegalStateException) e.message else "서버에 연결하지 못했어요. 연결을 확인하고 다시 시도해 주세요."
        }
    }

    override fun onCleared() { navi.release(); voice?.release(); scope.cancel() }
}

private const val ARRIVAL_RADIUS_M = 300f
private const val IGNORE_END_AFTER_START_MS = 5_000L

enum class NaviStatus(val label: String) {
    REQUESTING("내비에 목적지 넣는 중"),
    GUIDING("내비 안내 중"),
    FAILED("내비에 넣지 못했어요 · 다시 시도"),
    ENDED("내비 안내를 종료했어요 · 다시 안내"),
}

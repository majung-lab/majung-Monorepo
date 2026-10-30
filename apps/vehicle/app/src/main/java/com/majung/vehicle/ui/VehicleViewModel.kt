package com.majung.vehicle.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.majung.vehicle.data.remote.ApiException
import com.majung.vehicle.data.remote.VisitApi
import com.majung.vehicle.data.remote.dto.*
import com.majung.vehicle.domain.model.VisitMode
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class VehicleViewModel @JvmOverloads constructor(application: Application, private val api: VisitApi = VisitApi()) : AndroidViewModel(application) {
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

    init {
        refresh()
        scope.launch {
            while (isActive) {
                delay(5_000)
                lock.withLock {
                    try { refreshVisit() } catch (e: CancellationException) { throw e } catch (e: Exception) { handle(e) }
                }
            }
        }
    }

    fun dismissError() { error = null }
    fun refresh() = run {
        facilities = api.get("/api/facilities")
        history = api.get("/api/history?driverDeviceId=$deviceId")
        if (driverToken == null) prefs.getString("pendingBody", null)?.let { recoverSession(Json.parseToJsonElement(it)) }
        refreshVisit()
    }

    private suspend fun refreshVisit() {
        val token = driverToken ?: return
        applySnapshot(api.get("/api/visits/$token"))
    }

    private fun applySnapshot(value: VisitSnapshot) {
        val old = snapshot
        if (old == null || old.visit.id != value.visit.id || value.visit.version >= old.visit.version) snapshot = value
    }

    fun create(facilityId: String, mode: VisitMode, pointId: String, onDone: () -> Unit) = run {
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
        onDone()
    }

    private suspend fun recoverSession(body: JsonElement): VisitSession {
        val session: VisitSession = api.write("POST", "/api/visits", body)
        driverToken = session.driverToken; passengerToken = session.passengerToken
        prefs.edit().putString("driver", session.driverToken).putString("passenger", session.passengerToken).remove("pendingBody").commit()
        return session
    }

    fun changePoint(pointId: String, onDone: () -> Unit) = mutate("PATCH", "", { put("accessPointId", pointId) }, onDone)
    fun phase(phase: String, floor: String? = null, onDone: () -> Unit = {}) = mutate("PATCH", "", {
        put("phase", phase)
        floor?.let { put("parkingFloor", it) }
    }, onDone)
    fun propose(pointId: String, onDone: () -> Unit = {}) = mutate("POST", "/proposals", { put("accessPointId", pointId) }, onDone)
    fun respond(id: String, accept: Boolean) = mutate("POST", "/proposals/$id/respond", { put("accept", accept) })
    fun cancelProposal(id: String, onDone: () -> Unit) = mutate("DELETE", "/proposals/$id", {}, onDone)
    fun rate(rating: Int, tags: List<String>, onDone: () -> Unit) = mutate("POST", "/rating", {
        put("rating", rating); putJsonArray("tags") { tags.forEach { add(it) } }
    }) { finish(); onDone() }

    fun publishLocation(location: Location) {
        if (snapshot?.visit?.phase !in listOf("MOVING", "ARRIVED") || busy) return
        mutate("PATCH", "", {
            putJsonObject("vehicle") { put("lat", location.latitude); put("lng", location.longitude); put("etaSec", JsonNull) }
        })
    }

    fun finish() {
        snapshot?.let { completed -> history = listOf(HistoryEntry(completed.visit, completed.facility, completed.rating)) + history.filter { it.visit.id != completed.visit.id } }
        driverToken = null; passengerToken = null; snapshot = null
        prefs.edit().remove("driver").remove("passenger").commit()
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
            error = "방문 링크가 만료되었거나 없어졌어요. 새 방문을 시작해 주세요."
        } else if (e is ApiException && e.status in listOf(400, 403)) {
            error = "이 작업을 할 수 없어요. 장소와 방문 상태를 확인해 주세요."
        } else {
            error = if (e is IllegalStateException) e.message else "서버에 연결하지 못했어요. 연결을 확인하고 다시 시도해 주세요."
        }
    }

    override fun onCleared() { scope.cancel() }
}

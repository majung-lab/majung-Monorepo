package com.majung.vehicle

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import org.robolectric.Shadows.shadowOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.data.remote.VisitApi
import com.majung.vehicle.ui.MajungApp
import com.majung.vehicle.ui.VehicleViewModel
import com.majung.vehicle.ui.theme.LocalStaticMap
import com.majung.vehicle.data.remote.TestHttpServer
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h480dp-mdpi")
class NavigationTest {
    @get:Rule val rule = createComposeRule()
    private val modelStores = mutableListOf<ViewModelStore>()
    private lateinit var server: TestHttpServer
    private lateinit var model: VehicleViewModel
    private lateinit var application: Application
    @Volatile private var phase = "PREPARING"
    @Volatile private var visitMode = "PICKUP"
    @Volatile private var parkedFloor: String? = null
    @Volatile private var proposalsOverride: String? = null
    @Volatile private var version = 1
    @Volatile private var confirmed = "1"
    @Volatile private var proposalStatus: String? = null
    @Volatile private var rejectNext = false
    @Volatile private var rating: String? = null
    @Volatile private var creates = 0
    private val facility = """{"id":"1","name":"예시 시설","address":"테스트 주소","lat":37.5,"lng":127.2,"accessPoints":[{"id":"1","name":"서문","lat":37.5,"lng":127.2,"description":"서문 대기","kind":"PICKUP","allowed":true},{"id":"2","name":"북문","lat":37.501,"lng":127.2,"description":"북문 대기","kind":"PICKUP","allowed":true},{"id":"3","name":"제1 주차장","lat":37.502,"lng":127.2,"description":"주차장 진입","kind":"PARKING_ENTRANCE","allowed":true},{"id":"4","name":"제2 주차장","lat":37.503,"lng":127.2,"description":"주차장 진입","kind":"PARKING_ENTRANCE","allowed":true}],"dataStatus":"EXAMPLE"}"""
    private fun visit() = """{"id":"visit","facilityId":"1","mode":"$visitMode","phase":"$phase","confirmedAccessPointId":"$confirmed","version":$version,"passengerReadiness":"UNSEEN","updatedAt":"2026-10-30T00:00:00Z","expiresAt":"2027-01-01T00:00:00Z","parkingFloor":${parkedFloor?.let { "\"$it\"" } ?: "null"}}"""
    private fun snapshot() = """{"visit":${visit()},"role":"DRIVER","facility":$facility,"proposals":[${proposalsOverride ?: proposalStatus?.let { """{"id":"proposal","visitId":"visit","proposedBy":"PASSENGER","accessPointId":"2","fromAccessPointId":"1","baseVersion":1,"status":"$it","createdAt":"2026-10-30T00:00:00Z","expiresAt":"2027-01-01T00:00:00Z"}""" }.orEmpty()}],"vehicle":null,"rating":${rating ?: "null"}}"""

    @Before fun start() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("visit", 0).edit().clear().commit()
        server = TestHttpServer { exchange ->
            val path = exchange.path
            val body = exchange.body.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it).jsonObject }
            var status = 200
            val response = synchronized(this) {
                when {
                    path == "/api/facilities" -> "[$facility]"
                    path == "/api/history" -> if (phase == "COMPLETED") """[{"visit":${visit()},"facility":$facility,"rating":${rating ?: "null"}}]""" else "[]"
                    path == "/api/visits" -> { creates++; visitMode = body!!["mode"]!!.jsonPrimitive.content; confirmed = body["accessPointId"]!!.jsonPrimitive.content; """{"visit":${visit()},"driverToken":"driver-secret","passengerToken":"passenger-link"}""" }
                    exchange.method == "GET" -> snapshot()
                    rejectNext -> { rejectNext = false; status = 409; """{"code":"STALE","message":"stale"}""" }
                    path.endsWith("/respond") -> {
                        proposalStatus = if (body!!["accept"]!!.jsonPrimitive.boolean) "ACCEPTED" else "REJECTED"
                        if (proposalStatus == "ACCEPTED") confirmed = "2"
                        version++; snapshot()
                    }
                    path.endsWith("/rating") -> { rating = buildJsonObject { put("rating", body!!["rating"]!!); put("tags", body["tags"]!!); put("comment", JsonNull) }.toString(); version++; snapshot() }
                    else -> { body?.get("phase")?.let { phase = it.jsonPrimitive.content }; body?.get("accessPointId")?.let { confirmed = it.jsonPrimitive.content }; body?.get("parkingFloor")?.let { parkedFloor = it.jsonPrimitive.content }; version++; snapshot() }
                }
            }
            status to response
        }
        model = newModel()
        rule.setContent { CompositionLocalProvider(LocalStaticMap provides true) { MajungApp(UxRestrictionWatcher(application), model) } }
        awaitReady()
    }
    private fun newModel() = VehicleViewModel(application, VisitApi("http://127.0.0.1:${server.port}")).also { model ->
        modelStores.add(ViewModelStore().also { it.put("model", model) })
    }
    @After fun stop() { modelStores.forEach { it.clear() }; server.close() }
    private fun awaitReady() = rule.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); !model.busy }
    private fun toMoving() {
        rule.onNodeWithText("목적지 입력").performClick()
        rule.onNodeWithText("예시 시설").performClick()
        rule.onNodeWithText("픽업하기").performClick()
        rule.onNodeWithText("다음").performClick()
        awaitReady()
        rule.onNodeWithText("픽업 장소로 출발").performClick()
        awaitReady()
    }
    @Test fun pickupPersistsRatingAndHistoryThroughHttp() {
        toMoving()
        assertEquals("MOVING", phase)
        rule.onNodeWithText("도착했어요").performClick(); awaitReady()
        rule.onNodeWithText("동승자").performClick()
        rule.onNodeWithText("탑승 완료").performClick(); awaitReady()
        rule.onNodeWithText("좋았어요").performClick()
        rule.onNodeWithText("찾기 쉬웠어요").performClick()
        rule.onNodeWithText("평가 완료").performClick(); awaitReady()
        assertEquals("COMPLETED", phase)
        assertEquals(5, Json.parseToJsonElement(rating!!).jsonObject["rating"]!!.jsonPrimitive.int)
        assertTrue(rating!!.contains("찾기 쉬웠어요"))
        rule.onNodeWithText("목적지 기록").performClick(); awaitReady()
        rule.onNodeWithText("평가 5/5 · 찾기 쉬웠어요").assertExists()
    }
    @Test fun conflictDoesNotPretendProposalAcceptedAndKeepsOrigin() {
        toMoving()
        proposalStatus = "PENDING"
        rule.runOnIdle { model.refresh() }; awaitReady()
        rejectNext = true
        rule.onNodeWithText("북문으로 바꾸기").performClick(); awaitReady()
        assertEquals("1", model.snapshot!!.visit.confirmedAccessPointId)
        rule.onNodeWithText("북문로 바뀌었어요").assertDoesNotExist()
        rule.onNodeWithText("닫기").performClick()
        rule.onNodeWithText("북문으로 바꾸기").performClick(); awaitReady()
        assertEquals("2", model.snapshot!!.visit.confirmedAccessPointId)
        rule.onNodeWithText("서문").assertExists()
        rule.onNodeWithText("북문로 바뀌었어요").assertExists()
    }
    @Test fun returningToArrivalDoesNotWriteArrivedTwice() {
        toMoving()
        rule.onNodeWithText("도착했어요").performClick(); awaitReady()
        val arrivedVersion = version
        rule.onNodeWithText("아직 기다리는 중").performClick()
        rule.onNodeWithText("도착했어요").performClick(); awaitReady()
        assertEquals(arrivedVersion, version)
        rule.onNodeWithText("가족이 모두 탔나요?").assertExists()
    }
    @Test fun dismissingLatestResultDoesNotResurfaceOlderResults() {
        toMoving()
        fun proposal(id: String) = """{"id":"$id","visitId":"visit","proposedBy":"DRIVER","accessPointId":"2","fromAccessPointId":"1","baseVersion":1,"status":"REJECTED","createdAt":"2026-10-30T00:00:00Z","expiresAt":"2027-01-01T00:00:00Z"}"""
        proposalsOverride = proposal("latest") + "," + proposal("older")
        rule.runOnIdle { model.refresh() }; awaitReady()
        rule.onNodeWithText("가족이 기존 장소 그대로 가재요").assertExists()
        rule.onNodeWithText("확인").performClick()
        rule.onNodeWithText("가족이 기존 장소 그대로 가재요").assertDoesNotExist()
    }
    @Test fun dropoffChangesPointDirectlyAndOpensReview() {
        rule.onNodeWithText("목적지 입력").performClick()
        rule.onNodeWithText("예시 시설").performClick()
        rule.onNodeWithText("내려주기").performClick()
        rule.onNodeWithText("다음").performClick(); awaitReady()
        assertEquals("MOVING", phase)
        rule.onNodeWithText("위치 바꾸기").performClick()
        rule.onNodeWithText("북문").performClick()
        rule.onNodeWithText("다음").performClick(); awaitReady()
        assertEquals("2", confirmed)
        assertNull(proposalStatus)
        rule.onNodeWithText("도착했어요").performClick(); awaitReady()
        rule.onNodeWithText("하차 완료").performClick(); awaitReady()
        rule.onNodeWithText("북문 어땠어요?").assertExists()
        assertEquals("COMPLETED", phase)
    }
    @Test fun parkingSavesFloorAndCanChangeLotWithoutPassenger() {
        rule.onNodeWithText("목적지 입력").performClick()
        rule.onNodeWithText("예시 시설").performClick()
        rule.onNodeWithText("그냥가기").performClick()
        rule.onNodeWithText("다음").performClick(); awaitReady()
        assertEquals("MOVING", phase)
        rule.onNodeWithText("위치 바꾸기").performClick()
        rule.onNodeWithText("제2 주차장").performClick()
        rule.onNodeWithText("다음").performClick(); awaitReady()
        assertEquals("4", confirmed)
        rule.onNodeWithText("도착했어요").performClick(); awaitReady()
        rule.onNodeWithText("B3").performClick()
        rule.onNodeWithText("주차 완료").performClick(); awaitReady()
        assertEquals("B3", parkedFloor)
        assertEquals("B3", model.snapshot!!.visit.parkingFloor)
        rule.onNodeWithText("제2 주차장 어땠어요?").assertExists()
    }
    @Test fun restartRecoversCreationWhoseResponseWasLost() {
        application.getSharedPreferences("visit", 0).edit().putString("pendingBody", """{"requestId":"retry-stable-id","driverDeviceId":"${model.deviceId}","facilityId":"1","mode":"PICKUP","accessPointId":"1"}""").commit()
        val restored = newModel()
        rule.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); !restored.busy }
        assertEquals("visit", restored.snapshot?.visit?.id)
        assertEquals(1, creates)
        assertNull(application.getSharedPreferences("visit", 0).getString("pendingBody", null))
    }
    @Test fun deviceRestartRestoresServerVisitWithoutCreatingDuplicate() {
        toMoving()
        val restored = newModel()
        rule.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); !restored.busy }
        assertEquals("visit", restored.snapshot!!.visit.id)
        assertEquals("passenger-link", restored.passengerToken)
        assertEquals(1, creates)
        assertNotEquals(restored.passengerToken, application.getSharedPreferences("visit", 0).getString("driver", null))
    }
}

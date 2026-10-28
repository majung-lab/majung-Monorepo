package com.majung.vehicle.pleos.navi

/**
 * Pleos NaviHelper 연동 경계. requestRoute 호출 성공 ≠ 경로 탐색 완료이므로
 * 결과는 콜백(NaviResult)으로만 확정한다. SDK 받기 전까지는 FakeNaviClient 사용.
 */
interface NaviClient {
    fun requestRoute(name: String, lat: Double, lng: Double, onResult: (NaviResult) -> Unit)
}

sealed interface NaviResult {
    data object Guiding : NaviResult
    data class Failed(val reason: String) : NaviResult
}

class FakeNaviClient : NaviClient {
    override fun requestRoute(name: String, lat: Double, lng: Double, onResult: (NaviResult) -> Unit) {
        onResult(NaviResult.Guiding)
    }
}

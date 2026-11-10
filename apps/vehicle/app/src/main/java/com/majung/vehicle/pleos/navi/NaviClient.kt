package com.majung.vehicle.pleos.navi

/**
 * Pleos NaviHelper 연동 경계. requestRoute 호출 성공 ≠ 경로 탐색 완료이므로
 * 결과는 콜백(NaviResult)으로만 확정한다. 실제 구현은 PleosNaviClient.
 */
interface NaviClient {
    fun requestRoute(name: String, lat: Double, lng: Double, onResult: (NaviResult) -> Unit)
    /** 내장 내비 화면을 앞으로 띄운다 (경로가 있으면 안내 화면) */
    fun showNavi() {}
    /** 내비가 목적지 도착을 알리면 도착지 이름과 함께 부른다 */
    fun watchArrival(onArrived: (name: String) -> Unit) {}
    /**
     * 내비가 알려 주는 목적지까지 남은 시간(초). 안내 시작 직후에는 전체 소요 시간이 오고 목적지 좌표는 null,
     * 이후 refreshEta 답에는 내비가 안내 중인 목적지 좌표가 함께 온다
     */
    fun watchEta(onEta: (remainSec: Int, destLat: Double?, destLng: Double?) -> Unit) {}
    /** 남은 시간을 한 번 물어본다. 답은 watchEta로 온다 */
    fun refreshEta() {}
    fun release() {}
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

package com.majung.vehicle.pleos.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Pleos NaviHelper로 차량 내장 내비에 목적지를 넣는다.
 * requestRoute 호출만으로는 성공이 아니므로 경로 시작 이벤트나 오류 이벤트, 10초 무응답으로 결과를 정한다.
 * Pleos 내비가 없는 기기에서는 연결이 실패해 Failed로 끝난다.
 */
class PleosNaviClient(context: Context) : NaviClient {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    private var connected = false
    private val waitingForConnection = mutableListOf<() -> Unit>()
    private var pending: ((NaviResult) -> Unit)? = null
    private var arrived: ((String) -> Unit)? = null
    private val timeout = Runnable { finish(NaviResult.Failed("내비 응답 없음")) }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { main.post { finish(NaviResult.Guiding) } }
        override fun onDestinationArrived(info: DestinationArrivedInfo) { main.post { arrived?.invoke(info.name) } }
        override fun onError(errorCode: NaviErrorCode) { main.post { finish(NaviResult.Failed(errorCode.name)) } }
    }

    override fun requestRoute(name: String, lat: Double, lng: Double, onResult: (NaviResult) -> Unit) {
        main.post {
            // 새 목적지가 오면 이전 요청의 결과는 버린다
            pending = onResult
            main.removeCallbacks(timeout)
            main.postDelayed(timeout, RESULT_TIMEOUT_MS)
            whenConnected {
                try {
                    // 우리 장소는 Pleos 장소 DB에 없으므로 좌표로만 안내한다
                    helper?.requestRoute(RouteInfo(latitude = lat, longitude = lng, poiName = name, poiId = "", poiSubId = "0", address = name))
                } catch (e: Exception) {
                    finish(NaviResult.Failed(e.message ?: "목적지 요청 실패"))
                }
            }
        }
    }

    override fun watchArrival(onArrived: (name: String) -> Unit) {
        main.post {
            arrived = onArrived
            // 앱을 다시 켜서 목적지를 다시 넣지 않은 경우에도 도착 이벤트를 받도록 연결만 해 둔다
            whenConnected {}
        }
    }

    override fun showNavi() {
        main.post {
            whenConnected(onFailed = ::launchNaviApp) {
                try {
                    helper?.requestNavigationScreen(NavigationScreenRequest.Foreground)
                } catch (e: Exception) {
                    launchNaviApp()
                }
            }
        }
    }

    /** SDK로 못 띄우면 내비 앱을 직접 연다 */
    private fun launchNaviApp() {
        context.packageManager.getLaunchIntentForPackage(NAVI_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { runCatching { context.startActivity(it) } }
    }

    override fun release() {
        main.post {
            helper?.let { it.removeListener(listener); it.release() }
            helper = null
            connected = false
            waitingForConnection.clear()
            failedWhileWaiting.clear()
            pending = null
            arrived = null
            main.removeCallbacks(timeout)
        }
    }

    private val failedWhileWaiting = mutableListOf<() -> Unit>()

    private fun whenConnected(onFailed: () -> Unit = {}, block: () -> Unit) {
        if (connected) return block()
        waitingForConnection += block
        failedWhileWaiting += onFailed
        if (helper != null) return
        try {
            helper = NaviHelper(context).also {
                it.initialize(
                    onConnected = { main.post { onConnected() } },
                    onFailed = { main.post { onConnectFailed() } },
                )
            }
        } catch (e: Throwable) {
            onConnectFailed()
        }
    }

    private fun onConnected() {
        connected = true
        helper?.addListener(listener)
        failedWhileWaiting.clear()
        waitingForConnection.toList().also { waitingForConnection.clear() }.forEach { it() }
    }

    private fun onConnectFailed() {
        // 다음 요청 때 다시 연결을 시도한다 (release 후에는 새 객체가 필요)
        helper = null
        connected = false
        waitingForConnection.clear()
        failedWhileWaiting.toList().also { failedWhileWaiting.clear() }.forEach { it() }
        finish(NaviResult.Failed("내비 연결 실패"))
    }

    private fun finish(result: NaviResult) {
        main.removeCallbacks(timeout)
        val callback = pending ?: return
        pending = null
        callback(result)
    }

    private companion object {
        const val RESULT_TIMEOUT_MS = 10_000L
        const val NAVI_PACKAGE = "ai.umos.maps.android.navigation.app"
    }
}

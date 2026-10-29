package com.majung.vehicle.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.majung.vehicle.domain.model.LatLng
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// ponytail: OpenStreetMap(osmdroid). 카카오맵 키가 생기면 이 파일만 교체한다.

/** true면 타일 없이 Figma 목업 지도를 그린다 (스냅샷 테스트·프리뷰) */
val LocalStaticMap = staticCompositionLocalOf { false }

/** 흑백으로 바꾼 뒤 반전 + 살짝 푸른 톤 → Ink.bg 계열의 어두운 지도 */
private val DARK_TILES = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -0.30f, -0.59f, -0.11f, 0f, 235f,
            -0.30f, -0.59f, -0.11f, 0f, 240f,
            -0.30f, -0.59f, -0.11f, 0f, 250f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** anchorY 1 = 핀 끝, 0.5 = 가운데 점 */
class MapPin(val pos: LatLng, val icon: Int, val anchorY: Float = 1f)

private fun LatLng.geo() = GeoPoint(lat, lng)

@Composable
fun OsmMap(
    center: LatLng,
    modifier: Modifier = Modifier,
    zoom: Double = 18.0,
    pins: List<MapPin> = emptyList(),
    dashed: List<LatLng>? = null,
    locked: Boolean = false,
    /** 값이 바뀌면 그 좌표로 지도를 옮긴다 (가장 가까운 지점으로 맞추기) */
    focus: LatLng? = null,
    onMoveEnd: ((LatLng) -> Unit)? = null,
) {
    val moveEnd by rememberUpdatedState(onMoveEnd)
    val isLocked by rememberUpdatedState(locked)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                overlayManager.tilesOverlay.setColorFilter(DARK_TILES) // 차량 화면은 어두운 지도
                controller.setZoom(zoom)
                controller.setCenter(center.geo())
                // 주행 중엔 지도 잠김
                setOnTouchListener { v, e -> if (isLocked) true else { if (e.action == MotionEvent.ACTION_UP) v.performClick(); false } }
                addMapListener(DelayedMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        val c = mapCenter
                        moveEnd?.invoke(LatLng(c.latitude, c.longitude))
                        return true
                    }
                    override fun onZoom(event: ZoomEvent?) = false
                }, 250))
            }
        },
        update = { map ->
            if (focus != null && map.tag != focus) { map.tag = focus; map.controller.animateTo(focus.geo()) }
            map.overlays.removeAll { it is Marker || it is Polyline }
            dashed?.let { pts ->
                map.overlays.add(Polyline(map).apply {
                    setPoints(pts.map { it.geo() })
                    outlinePaint.color = 0xFFFF7A45.toInt()
                    outlinePaint.strokeWidth = 6f
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
                })
            }
            pins.forEach { p ->
                map.overlays.add(Marker(map).apply {
                    position = p.pos.geo()
                    icon = ContextCompat.getDrawable(map.context, p.icon)
                    setAnchor(Marker.ANCHOR_CENTER, p.anchorY)
                    setInfoWindow(null)
                })
            }
            map.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

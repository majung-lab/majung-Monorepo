package com.majung.vehicle.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Density

/** 큰 패널에서 앱이 차지할 논리 폭. Figma(1280dp)보다 좁게 잡아 글자·버튼을 키운다 (Pleos 1682dp → 약 2.2배) */
private const val PANEL_WIDTH_DP = 760f
/** 이 높이 이상인 큰 패널(Pleos 1682×1210dp 등)에서만 키운다. 기존 차량 화면은 1배 그대로 */
private const val LARGE_PANEL_HEIGHT_DP = 960

internal fun panelScale(widthDp: Int, heightDp: Int): Float =
    if (heightDp < LARGE_PANEL_HEIGHT_DP) 1f else (widthDp / PANEL_WIDTH_DP).coerceIn(1f, 3f)

/** 큰 차량 패널에서 앱 전체를 같은 비율로 키운다 */
@Composable
fun ScaleToPanel(content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val scale = panelScale(config.screenWidthDp, config.screenHeightDp)
    if (scale == 1f) return content()
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, base.fontScale),
        // 키운 만큼 줄바꿈이 잦아지므로 "구/역"처럼 글자 중간이 아니라 어절 단위로 끊는다
        LocalTextStyle provides LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
        content = content,
    )
}

package com.majung.vehicle

import com.majung.vehicle.ui.theme.panelScale
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelScaleTest {
    @Test fun `기존 차량 화면은 그대로`() {
        assertEquals(1f, panelScale(1280, 480))
        assertEquals(1f, panelScale(1408, 792))
        assertEquals(1f, panelScale(1920, 720))
    }

    @Test fun `Pleos 큰 패널은 폭에 맞춰 키운다`() {
        assertEquals(1682 / 760f, panelScale(1682, 1210), 0.001f)
    }
}

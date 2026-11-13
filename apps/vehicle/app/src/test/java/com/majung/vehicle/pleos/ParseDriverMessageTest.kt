package com.majung.vehicle.pleos

import com.majung.vehicle.pleos.voice.DriverMessage
import com.majung.vehicle.pleos.voice.parseDriverMessage
import com.majung.vehicle.pleos.voice.parseMinutes
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseDriverMessageTest {
    @Test fun minutes() = mapOf("10분" to 10, "십 분" to 10, "십오분" to 15, "이십분쯤" to 20, "삼십 오 분" to 35, "오 분" to 5, "다섯 분" to 5, "늦을 것 같아 오분" to 5, "200분" to null, "조금" to null)
        .forEach { (heard, n) -> assertEquals(heard, n, parseMinutes(heard)) }

    @Test fun late() {
        assertEquals(DriverMessage("LATE", 10), parseDriverMessage("10분 늦어"))
        assertEquals(DriverMessage("LATE", 15), parseDriverMessage("십오 분 정도 늦을 것 같아"))
        assertEquals(DriverMessage("LATE", null), parseDriverMessage("조금 늦어"))
    }

    @Test fun others() {
        assertEquals(DriverMessage("ALMOST"), parseDriverMessage("거의 다 왔어"))
        assertEquals(DriverMessage("ALMOST"), parseDriverMessage("곧 도착해"))
        assertEquals(DriverMessage("WAIT_INSIDE"), parseDriverMessage("안에서 기다려"))
        assertEquals(DriverMessage("WAIT_INSIDE"), parseDriverMessage("비 오니까 나오지 마"))
    }

    @Test fun unknown() = listOf("", "음", "날씨 어때").forEach { assertEquals(it, null, parseDriverMessage(it)) }
}

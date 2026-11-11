package com.majung.vehicle.pleos

import com.majung.vehicle.pleos.voice.parseAnswer
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseAnswerTest {
    @Test fun accept() = listOf("바꿔", "응 바꿔 줘", "수락", "좋아요", "네").forEach { assertEquals(it, true, parseAnswer(it)) }
    @Test fun reject() = listOf("그대로", "안 바꿔", "아니", "거절할게", "싫어").forEach { assertEquals(it, false, parseAnswer(it)) }
    @Test fun unknown() = listOf("", "음", "몇 분 남았어").forEach { assertEquals(it, null, parseAnswer(it)) }
}

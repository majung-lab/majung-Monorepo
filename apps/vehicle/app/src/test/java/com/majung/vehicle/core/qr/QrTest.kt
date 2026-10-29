package com.majung.vehicle.core.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class QrTest {
    @Test
    fun encodesLinkThatDecodesBack() {
        val link = "http://192.168.0.12:5173/v/ab12cd34"
        val m = qrMatrix(link, 264)
        val px = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) 0xFF000000.toInt() else -1 }
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(m.width, m.height, px))))
        assertEquals(link, decoded.text)
    }
}

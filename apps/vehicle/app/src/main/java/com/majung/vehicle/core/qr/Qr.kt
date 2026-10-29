package com.majung.vehicle.core.qr

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** 방문 링크 → QR 행렬. 차 화면에서 휴대폰으로 찍으니 오류 정정 M, 여백 1칸 */
fun qrMatrix(text: String, size: Int): BitMatrix = QRCodeWriter().encode(
    text, BarcodeFormat.QR_CODE, size, size,
    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"),
)

fun qrBitmap(text: String, size: Int, dark: Int = 0xFF0E1318.toInt()): ImageBitmap {
    val m = qrMatrix(text, size)
    val px = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) dark else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

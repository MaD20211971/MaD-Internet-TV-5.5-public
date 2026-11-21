package com.example.cast.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Erzeugt ein gestochen scharfes QR-Code-ImageBitmap für die Web-Cast-URL im lokalen WLAN.
 */
object QrCodeGenerator {

    fun generateMatrix(content: String, sizePx: Int = 360): BooleanArray? {
        if (content.isBlank() || sizePx <= 0) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bits = BooleanArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                for (x in 0 until sizePx) {
                    bits[y * sizePx + x] = matrix[x, y]
                }
            }
            bits
        } catch (_: Exception) {
            null
        }
    }

    fun generateBitmap(content: String, sizePx: Int = 360): ImageBitmap? {
        val bits = generateMatrix(content, sizePx) ?: return null
        return try {
            val pixels = IntArray(sizePx * sizePx) { i ->
                if (bits[i]) AndroidColor.BLACK else AndroidColor.WHITE
            }
            Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

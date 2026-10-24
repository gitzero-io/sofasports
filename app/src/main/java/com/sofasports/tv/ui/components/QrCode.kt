package com.sofasports.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

object Qr {
    /** Module matrix for [text]; pure (no Android), so it's unit tested. */
    fun matrix(text: String, size: Int = 256): BitMatrix =
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))

    fun bitmap(text: String, size: Int = 256): Bitmap {
        val m = matrix(text, size)
        val pixels = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
    }
}

/** Black-on-white QR (phones scan it reliably off a TV). */
@Composable
fun QrCode(text: String, size: Dp = 160.dp, modifier: Modifier = Modifier) {
    val bmp = remember(text) { Qr.bitmap(text).asImageBitmap() }
    Image(bmp, contentDescription = "QR code for $text", filterQuality = FilterQuality.None, modifier = modifier.background(Color.White).padding(6.dp).size(size))
}

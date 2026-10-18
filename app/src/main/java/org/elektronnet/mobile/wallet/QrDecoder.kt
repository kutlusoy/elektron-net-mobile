package org.elektronnet.mobile.wallet

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

class QrDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    fun decode(luminance: ByteArray, width: Int, height: Int, rowStride: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        return attempt(BinaryBitmap(HybridBinarizer(source))) ?: attempt(BinaryBitmap(HybridBinarizer(source.invert())))
    }

    private fun attempt(bitmap: BinaryBitmap): String? = try {
        reader.decodeWithState(bitmap).text?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}

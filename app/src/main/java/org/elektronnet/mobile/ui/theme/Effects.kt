package org.elektronnet.mobile.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class EffectLevel(val glowRadius: Dp, val glowAlpha: Float) {
    Off(0.dp, 0f),
    Subtle(8.dp, 0.35f),
    Full(16.dp, 0.55f),
}

fun Modifier.accentGlow(level: EffectLevel, color: Color, cornerRadius: Dp = 12.dp): Modifier {
    if (level == EffectLevel.Off) return this
    return drawBehind {
        val radius = level.glowRadius.toPx()
        val corner = cornerRadius.toPx()
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    this.color = color.copy(alpha = level.glowAlpha).toArgb()
                    maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.drawRoundRect(0f, 0f, size.width, size.height, corner, corner, paint)
        }
    }
}

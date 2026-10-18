package org.elektronnet.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.elektronnet.mobile.R

val ElektronDisplay = FontFamily(
    Font(R.font.orbitron_medium, FontWeight.Medium),
    Font(R.font.orbitron_bold, FontWeight.Bold),
)

val ElektronMono = FontFamily(Font(R.font.share_tech_mono, FontWeight.Normal))

fun elektronTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = ElektronDisplay, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = ElektronDisplay, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = ElektronDisplay, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = ElektronDisplay, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = ElektronDisplay, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = ElektronDisplay, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = base.titleLarge.copy(fontFamily = ElektronDisplay, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    )
}

package org.elektronnet.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.elektronnet.mobile.R
import org.elektronnet.mobile.ui.theme.EffectLevel
import org.elektronnet.mobile.ui.theme.ElektronDisplay
import org.elektronnet.mobile.ui.theme.LocalEffectLevel
import org.elektronnet.mobile.ui.theme.LocalTokens
import org.elektronnet.mobile.ui.theme.accentGlow

@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalTokens.current
    val level = LocalEffectLevel.current
    val glow = when (level) {
        EffectLevel.Off -> 0f
        EffectLevel.Subtle -> 0.22f
        EffectLevel.Full -> 0.34f
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tokens.bg, lerp(tokens.bg, tokens.accent, 0.08f), tokens.bg)))
            .drawBehind {
                if (glow > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(tokens.accent.copy(alpha = glow), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.04f),
                            radius = size.width * 0.95f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(tokens.accent2.copy(alpha = glow * 0.55f), Color.Transparent),
                            center = Offset(size.width * 0.95f, size.height * 0.98f),
                            radius = size.width * 0.8f,
                        ),
                    )
                }
            },
    ) { content() }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.surface.copy(alpha = GLASS_ALPHA))
            .border(1.dp, Brush.linearGradient(listOf(tokens.accent.copy(alpha = 0.8f), tokens.accent.copy(alpha = 0.35f), tokens.accent2.copy(alpha = 0.5f))), shape)
            .padding(20.dp),
    ) { content() }
}

@Composable
fun ElektronLogo(size: Dp, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    val level = LocalEffectLevel.current
    Image(
        painter = painterResource(R.drawable.logo_mark),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .accentGlow(level, tokens.accent, cornerRadius = size / 2)
            .clip(CircleShape),
    )
}

@Composable
fun BrandHeader(compact: Boolean, showTagline: Boolean = false) {
    val name = stringResource(R.string.app_name).uppercase()
    if (compact) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ElektronLogo(if (showTagline) 52.dp else 36.dp)
            Column {
                Text(name, fontFamily = ElektronDisplay, fontWeight = FontWeight.Bold, fontSize = if (showTagline) 22.sp else 18.sp, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onBackground)
                if (showTagline) Text(stringResource(R.string.brand_tagline), color = LocalTokens.current.accent, style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp)) {
            ElektronLogo(128.dp)
            Text(
                name,
                fontFamily = ElektronDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.brand_tagline),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val GLASS_ALPHA = 0.82f

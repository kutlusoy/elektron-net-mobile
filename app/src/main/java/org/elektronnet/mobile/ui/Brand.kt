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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
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
import org.elektronnet.mobile.ui.theme.LocalEffectLevel
import org.elektronnet.mobile.ui.theme.LocalTokens
import org.elektronnet.mobile.ui.theme.accentGlow

@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalTokens.current
    val level = LocalEffectLevel.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glow = when (level) {
        EffectLevel.Off -> 0f
        EffectLevel.Subtle -> 0.22f
        EffectLevel.Full -> 0.34f
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        lerp(tokens.bg, tokens.accent, if (darkTheme) 0.11f else 0.07f),
                        tokens.bg,
                        lerp(tokens.bg, tokens.accent2, if (darkTheme) 0.08f else 0.05f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            )
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
    ) {
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.92f).alpha(0.07f),
        )
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) { content() }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tokens.surface.copy(alpha = 0.86f), tokens.surface.copy(alpha = 0.78f))))
            .border(1.2.dp, Brush.linearGradient(listOf(tokens.accent.copy(alpha = 0.8f), tokens.accent.copy(alpha = 0.35f), tokens.accent2.copy(alpha = 0.5f))), shape)
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
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            ElektronLogo(if (showTagline) 52.dp else 36.dp)
            Column {
                Text(name, fontWeight = FontWeight.Bold, fontSize = if (showTagline) 22.sp else 18.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onBackground)
                if (showTagline) Text(stringResource(R.string.brand_tagline), color = LocalTokens.current.accent, style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp)) {
            ElektronLogo(128.dp)
            Text(
                name,
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


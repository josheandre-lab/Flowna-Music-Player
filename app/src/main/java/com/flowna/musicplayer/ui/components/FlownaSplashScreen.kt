package com.flowna.musicplayer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowna.musicplayer.R
import kotlinx.coroutines.delay

@Composable
fun FlownaSplashScreen(
    onFinished: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.82f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "splashLogoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "splashLogoAlpha"
    )

    LaunchedEffect(Unit) {
        entered = true
        delay(3000L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF24105A),
                        Color(0xFF0D0F1A)
                    ),
                    center = Offset(260f, 420f),
                    radius = 720f
                )
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0D0F1A),
                        Color(0xFF120B2C),
                        Color(0xFF061D35)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AudioBars(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 122.dp),
            alpha = 0.32f
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.flowna_launcher_logo),
                contentDescription = "Flowna Music Player",
                modifier = Modifier
                    .size(150.dp)
                    .scale(logoScale)
                    .graphicsLayer { alpha = logoAlpha }
                    .clip(RoundedCornerShape(34.dp))
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "M U S I C   P L A Y E R",
                color = Color(0xFFC4B5FD).copy(alpha = logoAlpha),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 6.sp
            )
        }
    }
}

@Composable
private fun AudioBars(
    modifier: Modifier = Modifier,
    alpha: Float
) {
    val transition = rememberInfiniteTransition(label = "splashBars")
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashBarsPulse"
    )
    val bars = listOf(24, 38, 54, 68, 48, 34, 22, 18, 26, 34, 44, 58, 72, 54, 40, 26)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEachIndexed { index, height ->
            val sideFade = if (index in 6..9) 0.38f else 1f
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height((height * if (index % 2 == 0) pulse else (1.35f - pulse)).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7C3AED).copy(alpha = alpha * sideFade))
            )
        }
    }
}

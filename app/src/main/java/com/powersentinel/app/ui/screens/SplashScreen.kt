package com.powersentinel.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.powersentinel.app.ui.SetupState
import com.powersentinel.app.utils.RootDetector
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SplashBlack = Color(0xFF050711)
private val SplashPanel = Color(0xFF11182A)
private val SplashBlue = Color(0xFF24A8FF)
private val SplashPurple = Color(0xFFB172FF)
private val SplashText = Color(0xFFF7FAFF)
private val SplashMuted = Color(0xFFAAB6C8)

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current
    val setupAccepted = remember { SetupState.isAccepted(context) }

    LaunchedEffect(setupAccepted) {
        if (setupAccepted) {
            navController.navigate("dashboard") {
                popUpTo("splash") { inclusive = true }
            }
        }
    }

    if (!setupAccepted) {
        FirstRunStatusScreen(navController)
    }
}

@Composable
private fun FirstRunStatusScreen(navController: NavController) {
    val scale = remember { Animatable(0.88f) }
    val rootAvailable = remember { RootDetector.isRootAvailable() }
    val now = remember {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    }
    val status = if (rootAvailable) "Root mode detected" else "Non-root Play mode"
    val detail = if (rootAvailable) {
        "Advanced controls stay behind consent."
    } else {
        "Guided optimization, local learning, Play-safe actions."
    }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(640, easing = FastOutSlowInEasing))
        delay(1050)
        navController.navigate("onboarding") {
            popUpTo("splash") { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = SplashBlue.copy(alpha = 0.38f),
                radius = size.width * 0.76f,
                center = Offset(size.width * 0.50f, size.height * 1.02f)
            )
            drawCircle(
                color = SplashPurple.copy(alpha = 0.22f),
                radius = size.width * 0.48f,
                center = Offset(size.width * 0.18f, size.height * 0.20f)
            )
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(now, color = SplashText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .scale(scale.value)
                .clip(RoundedCornerShape(34.dp))
                .background(
                    Brush.linearGradient(
                        listOf(SplashPanel.copy(alpha = 0.90f), Color(0xFF0A1020).copy(alpha = 0.74f))
                    )
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.16f), SplashPurple.copy(alpha = 0.38f))
                    ),
                    RoundedCornerShape(34.dp)
                )
                .padding(horizontal = 30.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CheckMarkCanvas(rootAvailable)
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = status,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = SplashText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = detail,
                fontSize = 13.sp,
                color = SplashMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CheckMarkCanvas(rootAvailable: Boolean) {
    Canvas(modifier = Modifier.size(82.dp)) {
        val fill = if (rootAvailable) SplashPurple else SplashBlue
        drawCircle(color = fill.copy(alpha = 0.25f), radius = size.minDimension * 0.50f)
        drawCircle(color = fill, radius = size.minDimension * 0.38f)
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.30f, size.height * 0.52f),
            end = Offset(size.width * 0.45f, size.height * 0.67f),
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.45f, size.height * 0.67f),
            end = Offset(size.width * 0.72f, size.height * 0.34f),
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = size.minDimension * 0.49f,
            style = Stroke(width = 2.5f)
        )
    }
}

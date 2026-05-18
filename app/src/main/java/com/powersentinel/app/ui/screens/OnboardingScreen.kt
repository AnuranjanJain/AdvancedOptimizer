package com.powersentinel.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.powersentinel.app.system.UsageAccess
import com.powersentinel.app.ui.SetupState

private val SetupBlack = Color(0xFF050711)
private val SetupPanel = Color(0xFF11182A)
private val SetupPanelSoft = Color(0xFF171D32)
private val SetupBlue = Color(0xFF24A8FF)
private val SetupPurple = Color(0xFFB172FF)
private val SetupText = Color(0xFFF7FAFF)
private val SetupMuted = Color(0xFFAAB6C8)

@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SetupBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = SetupBlue.copy(alpha = 0.35f),
                radius = size.width * 0.78f,
                center = Offset(size.width * 0.50f, size.height * 1.05f)
            )
            drawCircle(
                color = SetupPurple.copy(alpha = 0.20f),
                radius = size.width * 0.50f,
                center = Offset(size.width * 0.16f, size.height * 0.18f)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF08112A).copy(alpha = 0.54f))
                    )
                )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Text(
            text = "One-time setup",
            color = SetupBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(SetupPanelSoft.copy(alpha = 0.96f), SetupPanel.copy(alpha = 0.84f))
                    ),
                    RoundedCornerShape(30.dp)
                )
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), SetupPurple.copy(alpha = 0.34f))),
                    RoundedCornerShape(30.dp)
                )
                .padding(24.dp)
        ) {
            Text(
                text = "Power Sentinel",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SetupText
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Elegant battery intelligence that learns quietly, explains clearly, and only acts with your permission.",
                style = MaterialTheme.typography.bodyLarge,
                color = SetupMuted
            )
            Spacer(modifier = Modifier.height(18.dp))
            SetupPoint(
                title = "Local learning",
                body = "Usage patterns, app visibility, cache pressure, display state, battery state, and sensor capability stay on this device."
            )
            Spacer(modifier = Modifier.height(12.dp))
            SetupPoint(
                title = "Root aware",
                body = "The app auto-detects root. Root actions stay locked behind consent; Play Store mode stays guided and policy-safe."
            )
            Spacer(modifier = Modifier.height(12.dp))
            SetupPoint(
                title = "Terms and permission disclosure",
                body = "This acceptance is saved after you continue, so this screen will not appear on every launch."
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Some Android-controlled permissions, like Usage Access, may still need to be granted in system settings if you skip them now.",
                style = MaterialTheme.typography.bodyMedium,
                color = SetupMuted
            )
            Spacer(modifier = Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, SetupBlue.copy(alpha = 0.72f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SetupText
                    ),
                    onClick = {
                        context.startActivity(Intent(UsageAccess(context).settingsAction()))
                    }
                ) {
                    Text("Usage")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SetupPurple.copy(alpha = 0.58f),
                        contentColor = SetupText
                    ),
                    onClick = {
                        SetupState.markAccepted(context)
                        navController.navigate("dashboard") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                ) {
                    Text("Accept")
                }
            }
        }
        }
    }
}

@Composable
private fun SetupPoint(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            color = SetupText
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = SetupMuted
        )
    }
}

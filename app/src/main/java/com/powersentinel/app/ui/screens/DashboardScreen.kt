package com.powersentinel.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.powersentinel.app.system.DisplayProbe
import com.powersentinel.app.system.LearningMode
import com.powersentinel.app.analyze.AppUsageAnalyzer
import com.powersentinel.app.control.OptimizationController
import com.powersentinel.app.utils.RootDetector
import com.powersentinel.app.ui.glassmorphism
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val displayInfo = remember { DisplayProbe(context).getDisplayInfo() }
    val isLearning = remember { LearningMode(context).isLearningActive() }
    val daysRemaining = remember { LearningMode(context).getDaysRemaining() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Column(
            modifier = Modifier
                .glassmorphism(RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Device Health",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Display: $displayInfo",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isLearning) {
                Text(
                    text = "AI is learning your usage patterns. ($daysRemaining days remaining)",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            } else {
                 Text(
                    text = "AI learning complete. Ready to optimize.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                coroutineScope.launch {
                     val optimizationController = OptimizationController(context)
                     val isRooted = RootDetector.isRootAvailable()

                     val analyzer = AppUsageAnalyzer(context)
                     val infrequentApps = analyzer.getInfrequentApps(5 * 60 * 1000) // Less than 5 mins

                     if (isRooted) {
                         withContext(Dispatchers.IO) {
                             optimizationController.trimGlobalCachesWithRoot()
                             for (app in infrequentApps) {
                                 // Close apps with root
                             }
                         }
                         Toast.makeText(context, "Optimized caches via Root", Toast.LENGTH_SHORT).show()
                     } else {
                         Toast.makeText(context, "Optimization handled via Accessibility settings automation", Toast.LENGTH_SHORT).show()
                         // Iterate through apps and launch settings screen so accessibility service can click
                         for (app in infrequentApps) {
                             val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                             intent.data = Uri.parse("package:$app")
                             intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                             context.startActivity(intent)
                             delay(2000) // wait for service to click
                         }
                     }
                }
            }) {
                Text("Optimize Now")
            }
        }
    }
}

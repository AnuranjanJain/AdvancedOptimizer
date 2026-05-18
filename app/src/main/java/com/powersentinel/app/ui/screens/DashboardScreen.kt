package com.powersentinel.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powersentinel.app.analyze.AIPlanGenerator
import com.powersentinel.app.analyze.PowerAnalyzer
import com.powersentinel.app.control.OptimizationController
import com.powersentinel.app.model.AppPowerReport
import com.powersentinel.app.model.BatteryHealthReport
import com.powersentinel.app.model.DisplayReport
import com.powersentinel.app.model.OptimizationAction
import com.powersentinel.app.model.OptimizationPlan
import com.powersentinel.app.model.SensorContextReport
import com.powersentinel.app.model.SensorDrainReport
import com.powersentinel.app.system.BatteryProbe
import com.powersentinel.app.system.BatteryHealthProbe
import com.powersentinel.app.system.DisplayProbe
import com.powersentinel.app.system.PackageInventory
import com.powersentinel.app.system.SensorContextProbe
import com.powersentinel.app.system.SensorDrainProbe
import com.powersentinel.app.system.StorageProbe
import com.powersentinel.app.system.UsageAccess
import com.powersentinel.app.utils.RootDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val ScreenBlack = Color(0xFF050711)
private val Panel = Color(0xFF101525)
private val PanelLight = Color(0xFF171D32)
private val ElectricBlue = Color(0xFF25A7FF)
private val DeepBlue = Color(0xFF164BFF)
private val GlowPurple = Color(0xFFB172FF)
private val SoftPurple = Color(0xFFE0C8FF)
private val TextPrimary = Color(0xFFF7FAFF)
private val TextMuted = Color(0xFFAAB6C8)

private enum class DashboardTab {
    OVERVIEW,
    APPS,
    SENSORS
}

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootAvailable = remember { RootDetector.isRootAvailable() }
    val deviceOwnerAvailable = remember { OptimizationController(context).canUseDeviceOwnerControls() }
    val displayReport = remember { DisplayProbe(context).read() }
    val sensorReport = remember { SensorContextProbe(context).read() }
    var battery by remember { mutableStateOf(BatteryHealthProbe(context).read()) }
    var plan by remember { mutableStateOf<OptimizationPlan?>(null) }
    var appReports by remember { mutableStateOf<List<AppPowerReport>>(emptyList()) }
    var sensorDrains by remember { mutableStateOf<List<SensorDrainReport>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(DashboardTab.OVERVIEW) }
    var loading by remember { mutableStateOf(true) }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        plan = withContext(Dispatchers.IO) {
            AIPlanGenerator(context).generatePlan(rootAvailable, deviceOwnerAvailable)
        }
        appReports = withContext(Dispatchers.IO) { loadAppReports(context) }
        sensorDrains = withContext(Dispatchers.IO) { SensorDrainProbe(context).read() }
        loading = false
        while (true) {
            battery = BatteryHealthProbe(context).read()
            delay(4000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBlack)
    ) {
        GlowBackground()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                TopBar(rootAvailable, deviceOwnerAvailable)
            }
            item {
                BatteryHero(battery)
            }
            item {
                OptimizeButton(enabled = !loading && plan != null) {
                    showConfirm = true
                }
            }
            item {
                DashboardTabs(selectedTab) { selectedTab = it }
            }
            when (selectedTab) {
                DashboardTab.OVERVIEW -> {
                    item { PowerCalculator(battery) }
                    item { DeviceHealthGrid(battery, displayReport, sensorReport) }
                    item {
                        SectionCard(
                            title = "AI Optimization",
                            body = plan?.headline ?: "Building local optimization model...",
                            footer = "Confidence ${plan?.confidencePercent ?: 8}%"
                        )
                    }
                    val currentPlan = plan
                    if (currentPlan != null) {
                        items(currentPlan.actions.take(5)) { action ->
                            ActionCard(action)
                        }
                    }
                }
                DashboardTab.APPS -> {
                    item { AppDrainSummary(appReports, battery) }
                    items(appReports.take(30)) { report ->
                        AppDrainCard(report, battery)
                    }
                }
                DashboardTab.SENSORS -> {
                    item { SensorDrainSummary(sensorDrains) }
                    items(sensorDrains) { report ->
                        SensorDrainCard(report)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(18.dp)) }
        }
    }

    val currentPlan = plan
    if (showConfirm && currentPlan != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Optimize now?") },
            text = { Text(currentPlan.consentText) },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    scope.launch { executePlan(context, currentPlan) }
                }) {
                    Text("Run")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GlowBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = DeepBlue.copy(alpha = 0.42f),
            radius = size.width * 0.78f,
            center = Offset(size.width * 0.50f, size.height * 1.06f)
        )
        drawCircle(
            color = ElectricBlue.copy(alpha = 0.26f),
            radius = size.width * 0.52f,
            center = Offset(size.width * 0.82f, size.height * 0.86f)
        )
        drawCircle(
            color = GlowPurple.copy(alpha = 0.14f),
            radius = size.width * 0.50f,
            center = Offset(size.width * 0.18f, size.height * 0.18f)
        )
    }
}

@Composable
private fun TopBar(rootAvailable: Boolean, deviceOwnerAvailable: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Power Sentinel", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Battery intelligence console", color = TextMuted, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Pill(if (rootAvailable) "Root" else "Non-root")
            Spacer(modifier = Modifier.height(6.dp))
            Pill(if (deviceOwnerAvailable) "Owner" else "Play safe")
        }
    }
}

@Composable
private fun BatteryHero(battery: BatteryHealthReport) {
    GlassPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            BatteryRing(battery.levelPercent.coerceIn(0, 100))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    battery.statusLabel,
                    color = if (battery.charging) Color(0xFF7CFFCB) else Color(0xFFFFD48A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${battery.levelPercent.coerceAtLeast(0)}%",
                    color = TextPrimary,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${formatMah(battery.currentMilliAmps)} mA live flow",
                    color = TextMuted,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { battery.levelPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = ElectricBlue,
                    trackColor = Color.White.copy(alpha = 0.10f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun BatteryRing(level: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(116.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 12f, cap = StrokeCap.Round)
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(ElectricBlue, GlowPurple, ElectricBlue)),
                startAngle = -90f,
                sweepAngle = level * 3.6f,
                useCenter = false,
                style = stroke
            )
        }
        Text("$level%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
}

@Composable
private fun OptimizeButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.22f), GlowPurple.copy(alpha = 0.65f))),
                RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GlowPurple.copy(alpha = 0.42f),
            contentColor = TextPrimary,
            disabledContainerColor = GlowPurple.copy(alpha = 0.20f),
            disabledContentColor = TextPrimary.copy(alpha = 0.55f)
        )
    ) {
        Text("Optimize Now", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DashboardTabs(selected: DashboardTab, onSelected: (DashboardTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TabButton("Overview", DashboardTab.OVERVIEW, selected, onSelected, Modifier.weight(1f))
        TabButton("Apps", DashboardTab.APPS, selected, onSelected, Modifier.weight(1f))
        TabButton("Sensors", DashboardTab.SENSORS, selected, onSelected, Modifier.weight(1f))
    }
}

@Composable
private fun TabButton(
    label: String,
    tab: DashboardTab,
    selected: DashboardTab,
    onSelected: (DashboardTab) -> Unit,
    modifier: Modifier
) {
    val active = selected == tab
    Button(
        onClick = { onSelected(tab) },
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) GlowPurple.copy(alpha = 0.44f) else Color.Transparent,
            contentColor = if (active) TextPrimary else TextMuted
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PowerCalculator(battery: BatteryHealthReport) {
    GlassPanel {
        Text("mAh Calculator", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Remaining", mahOrLearning(battery.remainingMilliAmpHours), Modifier.weight(1f))
            MetricTile("Used", mahOrLearning(battery.usedMilliAmpHours), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Full est.", mahOrLearning(battery.estimatedFullMilliAmpHours), Modifier.weight(1f))
            MetricTile(if (battery.charging) "Full in" else "Time left", battery.timeEstimate, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppDrainSummary(reports: List<AppPowerReport>, battery: BatteryHealthReport) {
    val top = reports.take(5).sumOf { estimateAppMahPerHour(it, battery) }
    GlassPanel {
        Text("Battery Usage by App", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Estimated per-app drain from usage time, active services, cache pressure, recency, and live battery current.",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Tracked apps", reports.size.toString(), Modifier.weight(1f))
            MetricTile("Top 5 drain", "${formatMah(top)} mAh/hr", Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppDrainCard(report: AppPowerReport, battery: BatteryHealthReport) {
    val mah = estimateAppMahPerHour(report, battery)
    val progress = (report.score / 100f).coerceIn(0.04f, 1f)
    GlassPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(report.app.label, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(report.app.packageName, color = TextMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatMah(mah)}", color = ElectricBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("mAh/hr", color = TextMuted, fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = drainColor(report.score),
            trackColor = Color.White.copy(alpha = 0.08f),
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MiniStat("Score", report.score.toString(), Modifier.weight(1f))
            MiniStat("Services", report.app.declaredServices.size.toString(), Modifier.weight(1f))
            MiniStat("Cache", cacheLabel(report.cacheBytes), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Foreground ${minutesLabel(report.foregroundMillis)} today. ${report.recommendation}",
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SensorDrainSummary(reports: List<SensorDrainReport>) {
    val total = reports.sumOf { it.estimatedMilliAmpHoursPerHour }
    val high = reports.count { it.impactLabel == "High" }
    GlassPanel {
        Text("Battery Usage by Sensor", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Hardware sensor power is reported by Android in mA. For one hour of active use, mA is approximately mAh.",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Sensors", reports.size.toString(), Modifier.weight(1f))
            MetricTile("Possible load", "${formatMah(total)} mAh/hr", Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        MetricTile("High impact sensors", high.toString(), Modifier.fillMaxWidth())
    }
}

@Composable
private fun SensorDrainCard(report: SensorDrainReport) {
    val progress = (report.estimatedMilliAmpHoursPerHour / 20.0).toFloat().coerceIn(0.04f, 1f)
    GlassPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(report.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${report.category} | ${report.vendor}", color = TextMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatMah(report.estimatedMilliAmpHoursPerHour)}", color = SoftPurple, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("mAh/hr", color = TextMuted, fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = sensorColor(report.impactLabel),
            trackColor = Color.White.copy(alpha = 0.08f),
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MiniStat("Impact", report.impactLabel, Modifier.weight(1f))
            MiniStat("Power", String.format(Locale.US, "%.2f mA", report.powerMilliAmps), Modifier.weight(1f))
            MiniStat("Resolution", String.format(Locale.US, "%.3g", report.resolution), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeviceHealthGrid(
    battery: BatteryHealthReport,
    display: DisplayReport,
    sensors: SensorContextReport
) {
    GlassPanel {
        Text("Battery Health", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Health", battery.healthLabel, Modifier.weight(1f))
            MetricTile("Temp", tempLabel(battery.temperatureDeciCelsius), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Voltage", voltageLabel(battery.voltageMillivolts), Modifier.weight(1f))
            MetricTile("Display", "${Math.round(display.refreshRateHz)}Hz", Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(display.summary(), color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(sensors.optimizationSummary, color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionCard(title: String, body: String, footer: String) {
    GlassPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(footer, color = ElectricBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(body, color = TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun ActionCard(action: OptimizationAction) {
    GlassPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(action.title, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${action.estimatedSavingsScore}", color = SoftPurple, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(action.detail, color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (action.automatic) "Auto after consent" else "Android confirmation required",
            color = if (action.automatic) Color(0xFF7CFFCB) else Color(0xFFFFD48A),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(PanelLight.copy(alpha = 0.84f), Panel.copy(alpha = 0.62f))
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), ElectricBlue.copy(alpha = 0.10f))),
                RoundedCornerShape(26.dp)
            )
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        color = TextPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

private suspend fun executePlan(context: Context, plan: OptimizationPlan) {
    withContext(Dispatchers.IO) {
        val controller = OptimizationController(context)
        var rootTrimDone = false
        for (action in plan.actions) {
            when (action.kind) {
                OptimizationAction.Kind.ROOT_TRIM_CACHE -> {
                    if (!rootTrimDone) {
                        controller.trimGlobalCachesWithRoot()
                        rootTrimDone = true
                    }
                }
                OptimizationAction.Kind.ROOT_FORCE_STOP -> {
                    if (action.packageName != null) {
                        controller.forceStopPackageWithRoot(action.packageName)
                    }
                }
                else -> Unit
            }
        }
    }

    val firstManual = plan.actions.firstOrNull { !it.automatic }
    if (firstManual != null) {
        openManualAction(context, firstManual)
    }
    Toast.makeText(context, "Optimization pass finished.", Toast.LENGTH_LONG).show()
}

private fun openManualAction(context: Context, action: OptimizationAction) {
    val intent = when (action.kind) {
        OptimizationAction.Kind.OPEN_USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        OptimizationAction.Kind.OPEN_DISPLAY_SETTINGS -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        OptimizationAction.Kind.OPEN_LOCATION_SETTINGS -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        OptimizationAction.Kind.OPEN_APP_SETTINGS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${action.packageName}")
        }
        else -> null
    }
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun loadAppReports(context: Context): List<AppPowerReport> {
    val apps = PackageInventory(context).scan()
    val usage = UsageAccess(context).usageForLastDay()
    val storage = StorageProbe(context)
    val cacheByUid = mutableMapOf<Int, Long>()
    apps.forEach { app ->
        if (!cacheByUid.containsKey(app.uid)) {
            cacheByUid[app.uid] = storage.cacheBytesForUid(app.uid)
        }
    }
    return PowerAnalyzer().analyze(apps, usage, cacheByUid, BatteryProbe(context).read())
}

private fun estimateAppMahPerHour(report: AppPowerReport, battery: BatteryHealthReport): Double {
    val liveBase = if (battery.currentMilliAmps > 1.0) battery.currentMilliAmps else 450.0
    val scoreShare = report.score.coerceAtLeast(1) / 100.0
    val foregroundMinutes = report.foregroundMillis / 60000.0
    val serviceLoad = report.app.declaredServices.size * 0.35
    val cacheLoad = (report.cacheBytes / 1024.0 / 1024.0) * 0.015
    val foregroundLoad = foregroundMinutes * 0.55
    val systemDiscount = if (report.app.systemApp) 0.62 else 1.0
    return ((liveBase * 0.16 * scoreShare) + serviceLoad + cacheLoad + foregroundLoad)
        .coerceAtLeast(1.0) * systemDiscount
}

private fun drainColor(score: Int): Color {
    return when {
        score >= 75 -> Color(0xFFFF5D7A)
        score >= 52 -> Color(0xFFFFB86B)
        score >= 28 -> GlowPurple
        else -> ElectricBlue
    }
}

private fun sensorColor(label: String): Color {
    return when (label) {
        "High" -> Color(0xFFFF5D7A)
        "Medium" -> Color(0xFFFFB86B)
        "Low" -> GlowPurple
        else -> ElectricBlue
    }
}

private fun formatMah(value: Double): String {
    return String.format(Locale.US, "%.0f", value)
}

private fun mahOrLearning(value: Double): String {
    return if (value > 0.0) "${formatMah(value)} mAh" else "Learning"
}

private fun tempLabel(value: Int): String {
    return if (value >= 0) String.format(Locale.US, "%.1f C", value / 10.0) else "Unknown"
}

private fun voltageLabel(value: Int): String {
    return if (value > 0) String.format(Locale.US, "%.2f V", value / 1000.0) else "Unknown"
}

private fun cacheLabel(bytes: Long): String {
    return if (bytes <= 0L) "0 MB" else String.format(Locale.US, "%.0f MB", bytes / 1024.0 / 1024.0)
}

private fun minutesLabel(millis: Long): String {
    return String.format(Locale.US, "%.0f min", millis / 60000.0)
}

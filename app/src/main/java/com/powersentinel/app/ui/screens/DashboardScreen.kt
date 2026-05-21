package com.powersentinel.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.window.Dialog
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
    SENSORS,
    SETTINGS
}

private data class CacheThresholdSettings(
    val generalCacheMb: Int,
    val socialCacheMb: Int,
    val aiAutoBatterySaver: Boolean,
    val autoWifi: Boolean,
    val autoMobileData: Boolean,
    val autoSync: Boolean,
    val autoBluetooth: Boolean
)

private data class ChargingLimitInfo(
    val label: String,
    val source: String,
    val limitPercent: Int?
)

private data class ChargingInsight(
    val cyclesLabel: String,
    val targetLabel: String,
    val chargePlan: String,
    val cycleCostLabel: String,
    val limitInfo: ChargingLimitInfo,
    val aiTip: String
)

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
    var optimizing by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var runReport by remember { mutableStateOf<OptimizationRunReport?>(null) }
    var cacheSettings by remember { mutableStateOf(readCacheThresholdSettings(context)) }
    var settingsRevision by remember { mutableStateOf(0) }

    LaunchedEffect(settingsRevision) {
        plan = withContext(Dispatchers.IO) {
            AIPlanGenerator(context).generatePlan(rootAvailable, deviceOwnerAvailable)
        }
        appReports = withContext(Dispatchers.IO) { loadAppReports(context) }
        sensorDrains = withContext(Dispatchers.IO) { SensorDrainProbe(context).read() }
        loading = false
    }

    LaunchedEffect(Unit) {
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
                TopBar(onSettingsClick = { selectedTab = DashboardTab.SETTINGS })
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
                    item { ChargingIntelligenceCard(battery) }
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
                DashboardTab.SETTINGS -> {
                    item {
                        OptimizationSettingsPanel(
                            settings = cacheSettings,
                            rootAvailable = rootAvailable,
                            deviceOwnerAvailable = deviceOwnerAvailable,
                            onSettingsChanged = { updated ->
                                cacheSettings = updated
                                saveCacheThresholdSettings(context, updated)
                                settingsRevision++
                            }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(18.dp)) }
        }
    }

    val currentPlan = plan
    if (showConfirm && currentPlan != null) {
        OptimizationPreviewDialog(
            plan = currentPlan,
            optimizing = optimizing,
            onRun = {
                scope.launch {
                    optimizing = true
                    runReport = executePlan(context, currentPlan)
                    optimizing = false
                    showConfirm = false
                }
            },
            onDismiss = { if (!optimizing) showConfirm = false }
        )
    }

    val currentReport = runReport
    if (currentReport != null) {
        OptimizationReportDialog(
            report = currentReport,
            onOpenSuggestion = { action -> openManualAction(context, action) },
            onDismiss = { runReport = null }
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
private fun TopBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Power Sentinel", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Battery intelligence console", color = TextMuted, fontSize = 13.sp)
        }
        Button(
            onClick = onSettingsClick,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.09f),
                contentColor = TextPrimary
            ),
            modifier = Modifier
                .height(46.dp)
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(ElectricBlue.copy(alpha = 0.55f), GlowPurple.copy(alpha = 0.70f))),
                    RoundedCornerShape(18.dp)
                ),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Text("Settings", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    if (battery.currentMilliAmps >= 10.0) {
                        "${formatMah(battery.currentMilliAmps)} mA live flow"
                    } else {
                        "Learning live flow"
                    },
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
private fun ChargingIntelligenceCard(battery: BatteryHealthReport) {
    val context = LocalContext.current
    val insight = remember(context, battery.levelPercent, battery.charging, battery.currentMicroAmps, battery.temperatureDeciCelsius) {
        buildChargingInsight(context, battery)
    }
    GlassPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Charging Intelligence", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "AI charge-cycle guidance updates with your live battery level.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
            Pill(if (battery.charging) "Live charge" else "AI")
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Cycles", insight.cyclesLabel, Modifier.weight(1f))
            MetricTile("Target", insight.targetLabel, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Charge now", insight.chargePlan, Modifier.weight(1f))
            MetricTile("Cycle cost", insight.cycleCostLabel, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Charging limit", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(insight.limitInfo.label, color = ElectricBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(insight.limitInfo.source, color = TextMuted, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(insight.aiTip, color = TextMuted, fontSize = 13.sp)
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
private fun OptimizationSettingsPanel(
    settings: CacheThresholdSettings,
    rootAvailable: Boolean,
    deviceOwnerAvailable: Boolean,
    onSettingsChanged: (CacheThresholdSettings) -> Unit
) {
    GlassPanel {
        Text("Optimization Settings", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Choose what AI may apply after consent. Wi-Fi/mobile data automation needs root; sync can be paused by the app.",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        DeviceModeSettings(rootAvailable, deviceOwnerAvailable)
        Spacer(modifier = Modifier.height(14.dp))
        AutoSaverSettings(settings, onSettingsChanged)
        Spacer(modifier = Modifier.height(16.dp))
        ThresholdControl(
            title = "Normal apps",
            detail = "Ignore cache below this size.",
            valueMb = settings.generalCacheMb,
            minMb = 10,
            maxMb = 500,
            stepMb = 10,
            onValueChange = {
                onSettingsChanged(settings.copy(generalCacheMb = it.coerceAtMost(settings.socialCacheMb)))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        ThresholdControl(
            title = "Social apps",
            detail = "Facebook, Instagram, WhatsApp, Telegram, Discord, Reddit, and similar apps.",
            valueMb = settings.socialCacheMb,
            minMb = 20,
            maxMb = 1000,
            stepMb = 10,
            onValueChange = {
                onSettingsChanged(settings.copy(socialCacheMb = it.coerceAtLeast(settings.generalCacheMb)))
            }
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Normal threshold", "${settings.generalCacheMb} MB", Modifier.weight(1f))
            MetricTile("Social threshold", "${settings.socialCacheMb} MB", Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeviceModeSettings(rootAvailable: Boolean, deviceOwnerAvailable: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text("Device mode", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                "Root access",
                if (rootAvailable) "Root" else "Non-root",
                Modifier.weight(1f)
            )
            MetricTile(
                "Control mode",
                if (deviceOwnerAvailable) "Owner" else "Guided",
                Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            if (rootAvailable) {
                "Root actions can run after your consent. Android-protected changes still respect system limits."
            } else {
                "Non-root mode keeps restricted actions guided through Android settings and Play Store-safe controls."
            },
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AutoSaverSettings(
    settings: CacheThresholdSettings,
    onSettingsChanged: (CacheThresholdSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        SettingsSwitchRow(
            title = "AI auto battery saving",
            detail = "Allows selected optimizations to run after you press Run Core.",
            checked = settings.aiAutoBatterySaver,
            onCheckedChange = { onSettingsChanged(settings.copy(aiAutoBatterySaver = it)) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsSwitchRow(
            title = "Wi-Fi sleep saver",
            detail = "Root only. Non-root opens Wi-Fi settings.",
            checked = settings.autoWifi,
            enabled = settings.aiAutoBatterySaver,
            onCheckedChange = { onSettingsChanged(settings.copy(autoWifi = it)) }
        )
        SettingsSwitchRow(
            title = "Mobile data sleep saver",
            detail = "Root only. Useful in weak signal nights.",
            checked = settings.autoMobileData,
            enabled = settings.aiAutoBatterySaver,
            onCheckedChange = { onSettingsChanged(settings.copy(autoMobileData = it)) }
        )
        SettingsSwitchRow(
            title = "Auto-sync rest",
            detail = "Pauses Android sync during learned idle windows.",
            checked = settings.autoSync,
            enabled = settings.aiAutoBatterySaver,
            onCheckedChange = { onSettingsChanged(settings.copy(autoSync = it)) }
        )
        SettingsSwitchRow(
            title = "Bluetooth rest",
            detail = "Root only. Keep off if you use wearables overnight.",
            checked = settings.autoBluetooth,
            enabled = settings.aiAutoBatterySaver,
            onCheckedChange = { onSettingsChanged(settings.copy(autoBluetooth = it)) }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.55f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(detail, color = TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = GlowPurple.copy(alpha = 0.75f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

@Composable
private fun ThresholdControl(
    title: String,
    detail: String,
    valueMb: Int,
    minMb: Int,
    maxMb: Int,
    stepMb: Int,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(detail, color = TextMuted, fontSize = 12.sp)
            }
            Text("${valueMb} MB", color = ElectricBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StepButton(
                text = "-",
                enabled = valueMb > minMb,
                modifier = Modifier.weight(1f)
            ) {
                onValueChange((valueMb - stepMb).coerceAtLeast(minMb))
            }
            StepButton(
                text = "+",
                enabled = valueMb < maxMb,
                modifier = Modifier.weight(1f)
            ) {
                onValueChange((valueMb + stepMb).coerceAtMost(maxMb))
            }
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.075f),
            contentColor = TextPrimary,
            disabledContainerColor = Color.White.copy(alpha = 0.035f),
            disabledContentColor = TextMuted.copy(alpha = 0.45f)
        )
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            Pill(action.category)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(action.detail, color = TextMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(10.dp))
        GainStrip(action)
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
private fun OptimizationPreviewDialog(
    plan: OptimizationPlan,
    optimizing: Boolean,
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    val coreActions = plan.actions.filter { it.automatic || it.kind.name.startsWith("ROOT_") }
    val suggestions = plan.actions.filter { !it.automatic && it.kind != OptimizationAction.Kind.REVIEW_ONLY }
    val cacheActions = plan.cacheActions()
    val cacheTargetMb = cacheActions.cacheTargetMb()
    val totalBattery = plan.actions.sumOf { it.estimatedBatteryPercent }.coerceAtMost(18.0)
    val totalIdleMinutes = plan.actions.sumOf { it.estimatedIdleMinutes }.coerceAtMost(720)
    val totalSot = plan.actions.sumOf { it.estimatedSotMinutes }.coerceAtMost(60)

    GlassDialogShell(
        title = "Optimize Now",
        onDismiss = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Core actions run only after consent. In non-root mode, Android handles restricted changes through Settings.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DialogMetricTile("Battery", "+${formatOneDecimal(totalBattery)}%", Modifier.weight(1f))
                    DialogMetricTile("Idle", "+${minutesShort(totalIdleMinutes)}", Modifier.weight(1f))
                    DialogMetricTile("SOT", "+${totalSot}m", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DialogMetricTile(
                        "Cache",
                        if (cacheTargetMb > 0L) "${cacheTargetMb} MB" else "Clean",
                        Modifier.weight(1f)
                    )
                    DialogMetricTile(
                        "Cache apps",
                        if (cacheActions.isEmpty()) "0" else cacheActions.size.toString(),
                        Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Cache cleanup", color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (cacheActions.isEmpty()) {
                    Text("No app cache is above your current thresholds. Normal apps below the limit are ignored.", color = TextMuted, fontSize = 13.sp)
                } else {
                    cacheActions.take(2).forEach { action ->
                        OptimizationActionRow(action)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Core optimizations", color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (coreActions.isEmpty()) {
                    Text("No direct root/core actions are available in this mode.", color = TextMuted, fontSize = 13.sp)
                } else {
                    coreActions.take(4).forEach { action ->
                        OptimizationActionRow(action)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Smart suggestions", color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                suggestions.take(3).forEach { action ->
                    OptimizationActionRow(action)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        actions = {
            DialogTextButton(text = "Cancel", enabled = !optimizing, onClick = onDismiss)
            DialogPrimaryButton(
                text = if (optimizing) "Optimizing..." else "Run Core",
                enabled = !optimizing,
                onClick = onRun
            )
        }
    )
}

@Composable
private fun OptimizationReportDialog(
    report: OptimizationRunReport,
    onOpenSuggestion: (OptimizationAction) -> Unit,
    onDismiss: () -> Unit
) {
    var manualIndex by remember(report) { mutableStateOf(0) }
    val manualActions = report.manualActions
    val nextManualAction = manualActions.getOrNull(manualIndex)
    GlassDialogShell(
        title = "Optimization Report",
        onDismiss = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DialogMetricTile("Battery", "+${formatOneDecimal(report.estimatedBatteryPercent)}%", Modifier.weight(1f))
                    DialogMetricTile("Idle", "+${minutesShort(report.estimatedIdleMinutes)}", Modifier.weight(1f))
                    DialogMetricTile("SOT", "+${report.estimatedSotMinutes}m", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DialogMetricTile("Cache", report.cacheClearedLabel, Modifier.weight(1f))
                    DialogMetricTile("Cache apps", report.cacheActionCount.toString(), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text("Optimized", color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(report.cacheStatus, color = if (report.cacheActionCount > 0) Color(0xFF7CFFCB) else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                if (report.appliedTitles.isEmpty()) {
                    Text("No automatic action was available. Power Sentinel prepared the best Android-safe suggestions.", color = TextMuted, fontSize = 13.sp)
                } else {
                    report.appliedTitles.forEach {
                        Text("Done: $it", color = Color(0xFF7CFFCB), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Suggestions", color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (nextManualAction != null) {
                    Text(
                        "Next setting ${manualIndex + 1}/${manualActions.size}: ${nextManualAction.title}",
                        color = ElectricBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                report.suggestionTitles.take(6).forEach {
                    Text(it, color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        actions = {
            DialogTextButton(text = "Done", onClick = onDismiss)
            if (nextManualAction != null) {
                DialogPrimaryButton(
                    text = "Open Setting ${manualIndex + 1}/${manualActions.size}",
                    onClick = {
                        onOpenSuggestion(nextManualAction)
                        manualIndex = (manualIndex + 1).coerceAtMost(manualActions.size)
                    }
                )
            }
        }
    )
}

@Composable
private fun GlassDialogShell(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF17213A).copy(alpha = 0.98f),
                            Panel.copy(alpha = 0.96f),
                            ScreenBlack.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.20f),
                            ElectricBlue.copy(alpha = 0.42f),
                            GlowPurple.copy(alpha = 0.50f)
                        )
                    ),
                    RoundedCornerShape(30.dp)
                )
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = ElectricBlue.copy(alpha = 0.10f),
                    radius = size.width * 0.34f,
                    center = Offset(size.width * 0.90f, size.height * 0.08f)
                )
                drawCircle(
                    color = GlowPurple.copy(alpha = 0.08f),
                    radius = size.width * 0.36f,
                    center = Offset(size.width * 0.10f, size.height * 0.92f)
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("On-device battery intelligence", color = TextMuted, fontSize = 12.sp)
                    }
                    Pill("AI")
                }
                Spacer(modifier = Modifier.height(14.dp))
                content()
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
private fun DialogPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GlowPurple.copy(alpha = 0.50f),
            contentColor = TextPrimary,
            disabledContainerColor = GlowPurple.copy(alpha = 0.20f),
            disabledContentColor = TextPrimary.copy(alpha = 0.55f)
        ),
        modifier = Modifier.border(
            1.dp,
            Brush.horizontalGradient(listOf(ElectricBlue.copy(alpha = 0.32f), GlowPurple.copy(alpha = 0.70f))),
            RoundedCornerShape(18.dp)
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DialogTextButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DialogMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OptimizationActionRow(action: OptimizationAction) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(action.title, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("+${formatOneDecimal(action.estimatedBatteryPercent)}%", color = ElectricBlue, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(action.detail, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun GainStrip(action: OptimizationAction) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MiniStat("Battery", "+${formatOneDecimal(action.estimatedBatteryPercent)}%", Modifier.weight(1f))
        MiniStat("Idle", "+${minutesShort(action.estimatedIdleMinutes)}", Modifier.weight(1f))
        MiniStat("SOT", "+${action.estimatedSotMinutes}m", Modifier.weight(1f))
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

private data class OptimizationRunReport(
    val appliedTitles: List<String>,
    val suggestionTitles: List<String>,
    val manualActions: List<OptimizationAction>,
    val cacheClearedLabel: String,
    val cacheStatus: String,
    val cacheActionCount: Int,
    val estimatedBatteryPercent: Double,
    val estimatedIdleMinutes: Int,
    val estimatedSotMinutes: Int
)

private suspend fun executePlan(context: Context, plan: OptimizationPlan): OptimizationRunReport {
    val applied = mutableListOf<String>()
    val cacheActions = plan.cacheActions()
    val cacheTargetMb = cacheActions.cacheTargetMb()
    var cacheTrimApplied = false
    var cacheTrimAttempted = false
    withContext(Dispatchers.IO) {
        val controller = OptimizationController(context)
        var rootTrimDone = false
        for (action in plan.actions) {
            when (action.kind) {
                OptimizationAction.Kind.ROOT_TRIM_CACHE -> {
                    if (!rootTrimDone) {
                        cacheTrimAttempted = true
                        if (controller.trimGlobalCachesWithRoot().isSuccess()) {
                            cacheTrimApplied = true
                            applied += action.title
                        }
                        rootTrimDone = true
                    }
                }
                OptimizationAction.Kind.ROOT_FORCE_STOP -> {
                    if (action.packageName != null) {
                        if (controller.forceStopPackageWithRoot(action.packageName).isSuccess()) {
                            applied += action.title
                        }
                    }
                }
                OptimizationAction.Kind.ROOT_DISABLE_WIFI -> {
                    if (controller.setWifiEnabled(false).isSuccess()) applied += action.title
                }
                OptimizationAction.Kind.ROOT_DISABLE_BLUETOOTH -> {
                    if (controller.setBluetoothEnabled(false).isSuccess()) applied += action.title
                }
                OptimizationAction.Kind.ROOT_DISABLE_LOCATION -> {
                    if (controller.setLocationEnabled(false).isSuccess()) applied += action.title
                }
                OptimizationAction.Kind.ROOT_DISABLE_MOBILE_DATA -> {
                    if (controller.setMobileDataEnabled(false).isSuccess()) applied += action.title
                }
                OptimizationAction.Kind.DISABLE_SYNC -> {
                    if (controller.setMasterSyncEnabled(false)) applied += action.title
                }
                else -> Unit
            }
        }
    }

    val manualActions = plan.actions
        .filter { !it.automatic && it.kind != OptimizationAction.Kind.REVIEW_ONLY }
        .sortedWith(compareByDescending<OptimizationAction> { it.category.equals("Cache", ignoreCase = true) })
    val countedActions = plan.actions.filter { it.kind != OptimizationAction.Kind.REVIEW_ONLY }
    val cacheStatus = when {
        cacheTrimApplied -> "Cache cleared: Android trim-cache command completed for apps above threshold."
        cacheTrimAttempted -> "Cache cleanup attempted, but Android/root did not confirm completion."
        cacheActions.isNotEmpty() -> "Cache cleanup ready: ${cacheActions.size} app${if (cacheActions.size == 1) "" else "s"} above threshold. Non-root mode opens app storage settings."
        else -> "Cache scan complete: no app cache is above your current thresholds."
    }
    Toast.makeText(context, "Optimization pass finished.", Toast.LENGTH_LONG).show()
    return OptimizationRunReport(
        appliedTitles = applied,
        suggestionTitles = plan.actions
            .filter { !it.automatic && it.kind != OptimizationAction.Kind.REVIEW_ONLY }
            .map { "${it.title}: ${it.expectedOutcome}" },
        manualActions = manualActions,
        cacheClearedLabel = when {
            cacheTrimApplied && cacheTargetMb > 0L -> "${cacheTargetMb} MB"
            cacheTrimApplied -> "Done"
            cacheTargetMb > 0L -> "${cacheTargetMb} MB"
            else -> "Clean"
        },
        cacheStatus = cacheStatus,
        cacheActionCount = cacheActions.size,
        estimatedBatteryPercent = countedActions.sumOf { it.estimatedBatteryPercent }.coerceAtMost(18.0),
        estimatedIdleMinutes = countedActions.sumOf { it.estimatedIdleMinutes }.coerceAtMost(720),
        estimatedSotMinutes = countedActions.sumOf { it.estimatedSotMinutes }.coerceAtMost(60)
    )
}

private fun OptimizationPlan.cacheActions(): List<OptimizationAction> {
    return actions.filter { it.category.equals("Cache", ignoreCase = true) }
}

private fun List<OptimizationAction>.cacheTargetMb(): Long {
    val pattern = Regex("Cache is around (\\d+) MB")
    return sumOf { action ->
        pattern.find(action.detail)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    }
}

private fun readCacheThresholdSettings(context: Context): CacheThresholdSettings {
    val prefs = context.getSharedPreferences("optimizer_settings", Context.MODE_PRIVATE)
    val general = prefs.getInt("general_cache_threshold_mb", 50).coerceIn(10, 500)
    val social = prefs.getInt("social_cache_threshold_mb", 100).coerceIn(general, 1000)
    return CacheThresholdSettings(
        generalCacheMb = general,
        socialCacheMb = social,
        aiAutoBatterySaver = prefs.getBoolean("ai_auto_battery_saver_enabled", false),
        autoWifi = prefs.getBoolean("ai_auto_wifi_enabled", true),
        autoMobileData = prefs.getBoolean("ai_auto_mobile_data_enabled", true),
        autoSync = prefs.getBoolean("ai_auto_sync_enabled", true),
        autoBluetooth = prefs.getBoolean("ai_auto_bluetooth_enabled", true)
    )
}

private fun saveCacheThresholdSettings(context: Context, settings: CacheThresholdSettings) {
    context.getSharedPreferences("optimizer_settings", Context.MODE_PRIVATE)
        .edit()
        .putInt("general_cache_threshold_mb", settings.generalCacheMb.coerceIn(10, 500))
        .putInt("social_cache_threshold_mb", settings.socialCacheMb.coerceIn(settings.generalCacheMb, 1000))
        .putBoolean("ai_auto_battery_saver_enabled", settings.aiAutoBatterySaver)
        .putBoolean("ai_auto_wifi_enabled", settings.autoWifi)
        .putBoolean("ai_auto_mobile_data_enabled", settings.autoMobileData)
        .putBoolean("ai_auto_sync_enabled", settings.autoSync)
        .putBoolean("ai_auto_bluetooth_enabled", settings.autoBluetooth)
        .apply()
}

private fun buildChargingInsight(context: Context, battery: BatteryHealthReport): ChargingInsight {
    val level = battery.levelPercent.coerceIn(0, 100)
    val limitInfo = readChargingLimitInfo(context)
    val target = limitInfo.limitPercent?.coerceIn(60, 100) ?: if (battery.temperatureDeciCelsius >= 380) 75 else 80
    val deltaToTarget = (target - level).coerceAtLeast(0)
    val cycleCost = deltaToTarget / 100.0
    val cycles = readBatteryCycleCount(context)
    val cyclesLabel = cycles?.toString() ?: "Not exposed"
    val targetLabel = "$target%"
    val chargePlan = when {
        level >= target + 3 -> "Unplug"
        level >= target -> "Hold"
        deltaToTarget == 0 -> "No top-up"
        else -> "+$deltaToTarget%"
    }
    val aiTip = chargingAiTip(level, target, battery)
    return ChargingInsight(
        cyclesLabel = cyclesLabel,
        targetLabel = targetLabel,
        chargePlan = chargePlan,
        cycleCostLabel = String.format(Locale.US, "%.2f cycle", cycleCost),
        limitInfo = limitInfo,
        aiTip = aiTip
    )
}

private fun readBatteryCycleCount(context: Context): Int? {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
    return try {
        val propertyId = BatteryManager::class.java
            .getField("BATTERY_PROPERTY_CYCLE_COUNT")
            .getInt(null)
        manager.getIntProperty(propertyId).takeIf { it >= 0 && it != Int.MIN_VALUE }
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}

private fun readChargingLimitInfo(context: Context): ChargingLimitInfo {
    val percentKeys = listOf(
        "charge_control_limit",
        "battery_charge_limit",
        "charging_limit",
        "charge_limit",
        "battery_protection_threshold",
        "maximum_charge_limit",
        "smart_charge_limit"
    )
    val toggleKeys = listOf(
        "protect_battery",
        "adaptive_charging_enabled",
        "charging_protection_enabled",
        "battery_protection",
        "smart_charging",
        "optimized_charging_enabled"
    )
    for (key in percentKeys) {
        val value = readSettingValue(context, key) ?: continue
        val percent = value.toIntOrNull()?.takeIf { it in 50..100 } ?: continue
        return ChargingLimitInfo("$percent%", "Detected from system setting `$key`.", percent)
    }
    for (key in toggleKeys) {
        val value = readSettingValue(context, key) ?: continue
        if (value == "1" || value.equals("true", ignoreCase = true) || value.equals("enabled", ignoreCase = true)) {
            return ChargingLimitInfo("Protected", "System reports `$key` is enabled. OEM target is usually 80-85%.", 80)
        }
    }
    val adaptive = safeSecureSetting(context, "adaptive_charging_enabled")
    if (adaptive == "1") {
        return ChargingLimitInfo("Adaptive", "Android adaptive charging appears enabled.", 80)
    }
    return ChargingLimitInfo("Not exposed", "No readable OEM charging-limit setting was found on this device.", null)
}

private fun readSettingValue(context: Context, key: String): String? {
    val resolver = context.contentResolver
    return safeSetting { Settings.System.getString(resolver, key) }
        ?: safeSetting { Settings.Global.getString(resolver, key) }
        ?: safeSetting { Settings.Secure.getString(resolver, key) }
}

private fun safeSecureSetting(context: Context, key: String): String? {
    return safeSetting { Settings.Secure.getString(context.contentResolver, key) }
}

private fun safeSetting(read: () -> String?): String? {
    return try {
        read()
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}

private fun chargingAiTip(level: Int, target: Int, battery: BatteryHealthReport): String {
    val tempC = battery.temperatureDeciCelsius / 10.0
    return when {
        tempC >= 38.0 -> "AI tip: battery is warm at ${String.format(Locale.US, "%.1f", tempC)} C. Prefer slow charging and stop near ${target}% today."
        level < 20 -> "AI tip: charge now, but stop around ${target}% to reduce full-cycle wear. This top-up uses about ${String.format(Locale.US, "%.2f", (target - level).coerceAtLeast(0) / 100.0)} cycle."
        level in 20 until target -> "AI tip: a top-up to ${target}% is enough for daily use while avoiding unnecessary full 100% cycles."
        level in target..90 -> "AI tip: you are inside the healthy daily range. Holding near ${target}% reduces cycle stress compared with charging to 100%."
        else -> "AI tip: battery is already high. Unplug soon unless you need maximum runtime; staying near 100% adds heat and cycle stress."
    }
}

private fun openManualAction(context: Context, action: OptimizationAction) {
    val intent = when (action.kind) {
        OptimizationAction.Kind.OPEN_USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        OptimizationAction.Kind.OPEN_WIFI_SETTINGS -> Intent(Settings.ACTION_WIFI_SETTINGS)
        OptimizationAction.Kind.OPEN_BLUETOOTH_SETTINGS -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        OptimizationAction.Kind.OPEN_NETWORK_SETTINGS -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
        OptimizationAction.Kind.OPEN_SYNC_SETTINGS -> Intent(Settings.ACTION_SYNC_SETTINGS)
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

private fun formatOneDecimal(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
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

private fun minutesShort(minutes: Int): String {
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
    } else {
        "${minutes}m"
    }
}

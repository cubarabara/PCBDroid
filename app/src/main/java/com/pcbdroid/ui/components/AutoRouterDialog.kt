@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pcbdroid.data.model.*
import com.pcbdroid.domain.autorouter.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

// ─── RouterConfig ─────────────────────────────────────────────────────────────
data class RouterConfig(
    val trackWidth: Float = 0.25f,
    val clearance: Float = 0.25f,
    val viaSize: Float = 0.8f,
    val viaDrill: Float = 0.4f,
    val allowVias: Boolean = true,
    val routingAngle: RoutingAngle = RoutingAngle.FORTY_FIVE,
    val maxPasses: Int = 10
)
enum class RoutingAngle { ORTHOGONAL, FORTY_FIVE }

// ─── RouterProgress ───────────────────────────────────────────────────────────
data class RouterProgress(
    val totalNets: Int = 0,
    val routedNets: Int = 0,
    val failedNets: Int = 0,
    val currentNet: String = "",
    val isComplete: Boolean = false,
    val elapsedMs: Long = 0L
) {
    val percentage: Float get() = if (totalNets == 0) 0f else routedNets.toFloat() / totalNets
}

// ─── ViewModel ────────────────────────────────────────────────────────────────
class AutoRouterViewModel : androidx.lifecycle.ViewModel() {
    var config   by mutableStateOf(RouterConfig())
    var progress by mutableStateOf<RouterProgress?>(null)
    var isRunning by mutableStateOf(false)
    var result   by mutableStateOf<Pair<List<Track>, List<Via>>?>(null)

    private var job: Job? = null

    fun updateConfig(new: RouterConfig) { config = new }

    fun startRouting(board: PcbBoard, nets: List<Net>) {
        if (isRunning) return
        isRunning = true; progress = RouterProgress(); result = null
        job = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            // Simulate routing progress (replace with real router)
            val totalNets = 10
            for (i in 1..totalNets) {
                delay(200)
                progress = RouterProgress(
                    totalNets = totalNets, routedNets = i,
                    failedNets = 0, currentNet = "Net$i",
                    elapsedMs = System.currentTimeMillis() - startMs
                )
            }
            progress = progress?.copy(isComplete = true)
            result = Pair(emptyList(), emptyList())
            isRunning = false
        }
    }

    fun stopRouting() { job?.cancel(); isRunning = false }
}

// ─── Dialog UI ────────────────────────────────────────────────────────────────
@Composable
fun AutoRouterDialog(
    board: PcbBoard,
    nets: List<Net>,
    onApply: (List<Track>, List<Via>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: AutoRouterViewModel = viewModel()
) {
    val p = viewModel.progress
    val cfg = viewModel.config

    AlertDialog(
        onDismissRequest = { if (!viewModel.isRunning) onDismiss() },
        containerColor = Color(0xFF16213E),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFF00D4FF))
                Text("Auto Router", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Config (shown before running)
                if (!viewModel.isRunning && p?.isComplete != true) {
                    ConfigSection(cfg, viewModel::updateConfig)
                }

                // Progress
                if (p != null) {
                    Divider(color = Color(0xFF2A3F5F))
                    ProgressSection(p)
                }

                // Result
                if (p?.isComplete == true) {
                    val tracks = viewModel.result?.first?.size ?: 0
                    val vias   = viewModel.result?.second?.size ?: 0
                    Divider(color = Color(0xFF2A3F5F))
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatChip("Tracks", "$tracks", Color(0xFF00D4FF))
                        StatChip("Vias",   "$vias",   Color(0xFFFFD740))
                        StatChip("Time",   "${p.elapsedMs}ms", Color(0xFF90A4AE))
                    }
                }
            }
        },
        confirmButton = {
            when {
                viewModel.isRunning -> OutlinedButton(
                    onClick = viewModel::stopRouting,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252))
                ) { Text("Stop") }

                viewModel.result != null -> Button(
                    onClick = { viewModel.result?.let { onApply(it.first, it.second) }; onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
                ) { Text("Apply Routes", color = Color.Black) }

                else -> Button(
                    onClick = { viewModel.startRouting(board, nets) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
                ) { Text("Start Routing", color = Color.Black) }
            }
        },
        dismissButton = {
            if (!viewModel.isRunning) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF90A4AE)) }
            }
        }
    )
}

@Composable
private fun ConfigSection(cfg: RouterConfig, onUpdate: (RouterConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Router Settings", color = Color(0xFF00D4FF),
            style = MaterialTheme.typography.labelMedium)
        RouterSlider("Track Width", cfg.trackWidth, 0.1f..2f, "mm") {
            onUpdate(cfg.copy(trackWidth = it))
        }
        RouterSlider("Clearance", cfg.clearance, 0.1f..1f, "mm") {
            onUpdate(cfg.copy(clearance = it))
        }
        RouterSlider("Via Size", cfg.viaSize, 0.4f..2f, "mm") {
            onUpdate(cfg.copy(viaSize = it))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Angle", color = Color(0xFF90A4AE),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(80.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoutingAngle.values().forEach { angle ->
                    FilterChip(
                        selected = cfg.routingAngle == angle,
                        onClick  = { onUpdate(cfg.copy(routingAngle = angle)) },
                        label    = { Text(if (angle == RoutingAngle.ORTHOGONAL) "90°" else "45°",
                            style = MaterialTheme.typography.labelSmall) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00D4FF).copy(alpha = 0.2f),
                            selectedLabelColor     = Color(0xFF00D4FF)
                        )
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Allow Vias", color = Color(0xFF90A4AE),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Switch(checked = cfg.allowVias, onCheckedChange = { onUpdate(cfg.copy(allowVias = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00D4FF),
                    checkedTrackColor = Color(0xFF00D4FF).copy(alpha = 0.3f)))
        }
    }
}

@Composable
private fun ProgressSection(p: RouterProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Progress", color = Color(0xFF00D4FF), style = MaterialTheme.typography.labelMedium)
        val animProg by animateFloatAsState(p.percentage, tween(300), label = "prog")
        LinearProgressIndicator(
            progress = animProg,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Color(0xFF00D4FF),
            trackColor = Color(0xFF2A3F5F)
        )
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (p.isComplete) "✓ Complete" else "Routing: ${p.currentNet}",
                color = if (p.isComplete) Color(0xFF00FF88) else Color(0xFF90A4AE),
                style = MaterialTheme.typography.labelSmall
            )
            Text("${(p.percentage * 100).toInt()}%", color = Color(0xFF00D4FF),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace)
        }
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            StatChip("Routed",  "${p.routedNets}", Color(0xFF00FF88))
            StatChip("Failed",  "${p.failedNets}", Color(0xFFFF5252))
            StatChip("Time",    "${p.elapsedMs}ms", Color(0xFFFFD740))
        }
    }
}

@Composable
fun RouterSlider(label: String, value: Float,
                 range: ClosedFloatingPointRange<Float>, unit: String,
                 onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = Color(0xFF90A4AE),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("${"%.2f".format(value)} $unit", color = Color(0xFF00D4FF),
                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00D4FF), activeTrackColor = Color(0xFF00D4FF),
                inactiveTrackColor = Color(0xFF2A3F5F)))
    }
}

@Composable
fun StatChip(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace)
            Text(label, color = color.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

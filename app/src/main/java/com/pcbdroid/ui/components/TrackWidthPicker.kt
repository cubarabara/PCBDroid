@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.pcbdroid.data.model.PcbLayer

data class TrackPreset(val widthMm: Float, val label: String, val desc: String)

val TRACK_PRESETS = listOf(
    TrackPreset(0.1f,  "0.1mm",  "Min / High density"),
    TrackPreset(0.127f,"0.127mm","5 mil / JLCPCB min"),
    TrackPreset(0.2f,  "0.2mm",  "Signal trace"),
    TrackPreset(0.25f, "0.25mm", "Standard signal"),
    TrackPreset(0.5f,  "0.5mm",  "Power trace"),
    TrackPreset(1.0f,  "1.0mm",  "High current"),
    TrackPreset(2.0f,  "2.0mm",  "High power"),
    TrackPreset(3.0f,  "3.0mm",  "Very high current")
)

@Composable
fun TrackWidthBar(
    currentWidth: Float,
    currentLayer: PcbLayer,
    onWidthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val layerColor = Color(currentLayer.color.toInt())

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(6.dp),
            color = layerColor.copy(alpha = .12f),
            border = BorderStroke(1.dp, layerColor.copy(alpha = .4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track width preview line
                Canvas(modifier = Modifier.size(width = 24.dp, height = 14.dp)) {
                    val h = (currentWidth * 20f).coerceIn(1f, size.height)
                    drawRect(layerColor,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - h) / 2),
                        size = androidx.compose.ui.geometry.Size(size.width, h))
                }
                Text(String.format("%.3f mm", currentWidth),
                    color = layerColor,
                    style = MaterialTheme.typography.labelSmall)
                Icon(Icons.Default.ArrowDropDown, null,
                    tint = layerColor.copy(alpha = .7f),
                    modifier = Modifier.size(14.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF16213E))
        ) {
            Text("Track Width", color = Color(0xFF546E7A),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            Divider(color = Color(0xFF2A3F5F))

            TRACK_PRESETS.forEach { preset ->
                val sel = Math.abs(preset.widthMm - currentWidth) < 0.001f
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (sel) Icon(Icons.Default.Check, null,
                                tint = Color(0xFF00D4FF), modifier = Modifier.size(14.dp))
                            else Spacer(Modifier.size(14.dp))

                            // Width preview
                            Canvas(modifier = Modifier.size(32.dp, 16.dp)) {
                                val h = (preset.widthMm * 15f).coerceIn(1f, size.height)
                                drawRect(Color(0xFF00D4FF).copy(alpha = if(sel) 1f else 0.6f),
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - h) / 2),
                                    size = androidx.compose.ui.geometry.Size(size.width, h))
                            }

                            Column {
                                Text(preset.label,
                                    color = if(sel) Color(0xFF00D4FF) else Color.White,
                                    style = MaterialTheme.typography.bodySmall)
                                Text(preset.desc,
                                    color = Color(0xFF546E7A),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    onClick = { onWidthChange(preset.widthMm); expanded = false }
                )
            }

            Divider(color = Color(0xFF2A3F5F))

            // Custom input
            var custom by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Custom mm", color = Color(0xFF546E7A),
                        style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFF00D4FF),
                        unfocusedBorderColor = Color(0xFF2A3F5F),
                        textColor = Color.White,
                        cursorColor = Color(0xFF00D4FF)
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                TextButton(onClick = {
                    custom.toFloatOrNull()?.let { w ->
                        if (w in 0.05f..10f) { onWidthChange(w); expanded = false }
                    }
                }) { Text("OK", color = Color(0xFF00D4FF)) }
            }
        }
    }
}

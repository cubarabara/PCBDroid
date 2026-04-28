@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.pcbdroid.data.model.*

data class BoardTemplate(
    val name: String,
    val widthMm: Float,
    val heightMm: Float,
    val layers: Int,
    val desc: String
)

val BOARD_TEMPLATES = listOf(
    BoardTemplate("Custom",         0f,    0f,    2, "Define your own size"),
    BoardTemplate("Arduino Uno",    68.6f, 53.3f, 2, "Arduino Uno form factor"),
    BoardTemplate("Arduino Nano",   43.2f, 18.0f, 2, "Arduino Nano form factor"),
    BoardTemplate("Raspberry Pi",   85.0f, 56.0f, 4, "Raspberry Pi HAT"),
    BoardTemplate("ESP32 DevKit",   55.9f, 28.6f, 2, "ESP32 Dev board"),
    BoardTemplate("Business Card",  85.6f, 54.0f, 2, "PCB business card"),
    BoardTemplate("50x50mm",        50.0f, 50.0f, 2, "50×50 mm square"),
    BoardTemplate("100x100mm",     100.0f,100.0f, 2, "100×100 mm square"),
    BoardTemplate("Eurorack 3U",   128.5f, 133.4f,4, "Eurorack module")
)

@Composable
fun BoardSetupDialog(
    currentRules: DesignRules,
    onConfirm: (Float, Float, Int, DesignRules) -> Unit,
    onDismiss: () -> Unit
) {
    var widthMm  by remember { mutableStateOf("100.0") }
    var heightMm by remember { mutableStateOf("80.0") }
    var layerCount by remember { mutableStateOf(2) }
    var selectedTemplate by remember { mutableStateOf<BoardTemplate?>(null) }

    // DRS
    var minTrack by remember { mutableStateOf(currentRules.minTrackWidth.toString()) }
    var minClear by remember { mutableStateOf(currentRules.minClearance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213E),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AspectRatio, null, tint = Color(0xFF00D4FF))
                Text("Board Setup", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Templates
                Text("Templates", color = Color(0xFF546E7A),
                    style = MaterialTheme.typography.labelSmall)

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(BOARD_TEMPLATES) { t ->
                        val sel = selectedTemplate == t
                        Surface(
                            onClick = {
                                selectedTemplate = t
                                if (t.widthMm > 0) {
                                    widthMm  = t.widthMm.toString()
                                    heightMm = t.heightMm.toString()
                                    layerCount = t.layers
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if(sel) Color(0xFF00D4FF).copy(alpha=.15f) else Color(0xFF1E2D4A),
                            border = BorderStroke(1.dp, if(sel) Color(0xFF00D4FF) else Color(0xFF2A3F5F))
                        ) {
                            Column(modifier = Modifier.padding(10.dp).width(100.dp)) {
                                Text(t.name, color = if(sel) Color(0xFF00D4FF) else Color.White,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                if (t.widthMm > 0)
                                    Text("${t.widthMm}×${t.heightMm}mm",
                                        color = Color(0xFF546E7A),
                                        style = MaterialTheme.typography.labelSmall)
                                Text("${t.layers}L", color = Color(0xFF546E7A),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Divider(color = Color(0xFF2A3F5F))

                // Size
                Text("Board Size", color = Color(0xFF00D4FF),
                    style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeField("Width (mm)", widthMm, Modifier.weight(1f)) { widthMm = it }
                    SizeField("Height (mm)", heightMm, Modifier.weight(1f)) { heightMm = it }
                }

                // Layer count
                Text("Copper Layers", color = Color(0xFF00D4FF),
                    style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4, 6, 8).forEach { n ->
                        val sel = n == layerCount
                        Surface(
                            onClick = { layerCount = n },
                            shape = RoundedCornerShape(6.dp),
                            color = if(sel) Color(0xFF00D4FF).copy(alpha=.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if(sel) Color(0xFF00D4FF) else Color(0xFF2A3F5F))
                        ) {
                            Text("${n}L",
                                color = if(sel) Color(0xFF00D4FF) else Color(0xFF90A4AE),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal=14.dp, vertical=6.dp))
                        }
                    }
                }

                Divider(color = Color(0xFF2A3F5F))

                // Quick DRS
                Text("Design Rules", color = Color(0xFF00D4FF),
                    style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeField("Min Track (mm)", minTrack, Modifier.weight(1f)) { minTrack = it }
                    SizeField("Min Clear (mm)", minClear, Modifier.weight(1f)) { minClear = it }
                }

                // Quick presets
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "JLCPCB" to Triple("0.127","0.127",2),
                        "Standard" to Triple("0.25","0.25",2),
                        "Fine" to Triple("0.1","0.1",4)
                    ).forEach { (label, vals) ->
                        Surface(
                            onClick = {
                                minTrack = vals.first
                                minClear = vals.second
                                layerCount = vals.third
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00D4FF).copy(alpha=.08f),
                            border = BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha=.3f))
                        ) {
                            Text(label, color = Color(0xFF00D4FF),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal=8.dp, vertical=4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = widthMm.toFloatOrNull() ?: 100f
                    val h = heightMm.toFloatOrNull() ?: 80f
                    val rules = currentRules.copy(
                        minTrackWidth = minTrack.toFloatOrNull() ?: currentRules.minTrackWidth,
                        minClearance  = minClear.toFloatOrNull() ?: currentRules.minClearance
                    )
                    onConfirm(w, h, layerCount, rules)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
            ) { Text("Create Board", color = Color.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF90A4AE))
            }
        }
    )
}

@Composable
fun SizeField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        singleLine = true,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = Color(0xFF00D4FF),
            unfocusedBorderColor = Color(0xFF2A3F5F),
            textColor = Color.White,
            focusedLabelColor = Color(0xFF00D4FF),
            unfocusedLabelColor = Color(0xFF546E7A),
            cursorColor = Color(0xFF00D4FF)
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
        )
    )
}

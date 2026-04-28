@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.pcbdroid.data.model.*

// ─── Design Rules Dialog ──────────────────────────────────────────────────────
@Composable
fun DesignRulesDialog(
    rules: DesignRules,
    onSave: (DesignRules) -> Unit,
    onDismiss: () -> Unit
) {
    var minTrack  by remember { mutableStateOf(rules.minTrackWidth.toString()) }
    var minClear  by remember { mutableStateOf(rules.minClearance.toString()) }
    var viaDrill  by remember { mutableStateOf(rules.minViaDrill.toString()) }
    var viaSize   by remember { mutableStateOf(rules.minViaSize.toString()) }
    var copper    by remember { mutableStateOf(rules.copperWeight.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213E),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Design Rules", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DrcField("Min Track Width (mm)", minTrack, KeyboardType.Decimal) { minTrack = it }
                DrcField("Min Clearance (mm)",   minClear, KeyboardType.Decimal) { minClear = it }
                DrcField("Min Via Drill (mm)",    viaDrill, KeyboardType.Decimal) { viaDrill = it }
                DrcField("Min Via Size (mm)",     viaSize,  KeyboardType.Decimal) { viaSize  = it }
                DrcField("Copper Weight (µm)",    copper,   KeyboardType.Decimal) { copper   = it }

                Surface(shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF00D4FF).copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Presets:", color = Color(0xFF90A4AE),
                            style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PresetButton("JLCPCB 2L") {
                                minTrack = "0.127"; minClear = "0.127"
                                viaDrill = "0.3";   viaSize  = "0.6"
                            }
                            PresetButton("Standard") {
                                minTrack = "0.25";  minClear = "0.25"
                                viaDrill = "0.4";   viaSize  = "0.8"
                            }
                            PresetButton("Fine") {
                                minTrack = "0.1";   minClear = "0.1"
                                viaDrill = "0.2";   viaSize  = "0.4"
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(DesignRules(
                        minTrackWidth = minTrack.toFloatOrNull() ?: rules.minTrackWidth,
                        minClearance  = minClear.toFloatOrNull() ?: rules.minClearance,
                        minViaDrill   = viaDrill.toFloatOrNull() ?: rules.minViaDrill,
                        minViaSize    = viaSize.toFloatOrNull()  ?: rules.minViaSize,
                        copperWeight  = copper.toFloatOrNull()   ?: rules.copperWeight
                    ))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
            ) { Text("Save", color = Color.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF90A4AE)) }
        }
    )
}

@Composable
fun DrcField(label: String, value: String,
             keyboardType: KeyboardType = KeyboardType.Text,
             onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor   = Color(0xFF00D4FF),
            unfocusedBorderColor = Color(0xFF2A3F5F),
            textColor            = Color.White,
            cursorColor          = Color(0xFF00D4FF),
            focusedLabelColor    = Color(0xFF00D4FF),
            unfocusedLabelColor  = Color(0xFF546E7A)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
fun PresetButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF00D4FF).copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.3f))
    ) {
        Text(label, color = Color(0xFF00D4FF),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

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

data class PowerSymbolDef(
    val netName: String,
    val description: String,
    val type: com.pcbdroid.data.model.PowerType,
    val color: Color
)

val COMMON_POWER_SYMBOLS = listOf(
    PowerSymbolDef("GND",   "Ground",        com.pcbdroid.data.model.PowerType.GND, Color(0xFF78909C)),
    PowerSymbolDef("AGND",  "Analog Ground", com.pcbdroid.data.model.PowerType.GND, Color(0xFF90A4AE)),
    PowerSymbolDef("DGND",  "Digital Ground",com.pcbdroid.data.model.PowerType.GND, Color(0xFF78909C)),
    PowerSymbolDef("PGND",  "Power Ground",  com.pcbdroid.data.model.PowerType.GND, Color(0xFF607D8B)),
    PowerSymbolDef("VCC",   "+VCC",          com.pcbdroid.data.model.PowerType.VCC, Color(0xFFEF5350)),
    PowerSymbolDef("VDD",   "+VDD",          com.pcbdroid.data.model.PowerType.VCC, Color(0xFFE53935)),
    PowerSymbolDef("+3V3",  "+3.3V",         com.pcbdroid.data.model.PowerType.VCC, Color(0xFFFF7043)),
    PowerSymbolDef("+5V",   "+5V",           com.pcbdroid.data.model.PowerType.VCC, Color(0xFFFF5722)),
    PowerSymbolDef("+12V",  "+12V",          com.pcbdroid.data.model.PowerType.VCC, Color(0xFFFF6E40)),
    PowerSymbolDef("-12V",  "-12V",          com.pcbdroid.data.model.PowerType.VCC, Color(0xFF7E57C2)),
    PowerSymbolDef("+1V8",  "+1.8V",         com.pcbdroid.data.model.PowerType.VCC, Color(0xFFFF8A65)),
    PowerSymbolDef("VBAT",  "Battery +",     com.pcbdroid.data.model.PowerType.VCC, Color(0xFF26C6DA)),
    PowerSymbolDef("VREF",  "Voltage Ref",   com.pcbdroid.data.model.PowerType.VCC, Color(0xFF66BB6A)),
    PowerSymbolDef("IOVDD", "IO Supply",     com.pcbdroid.data.model.PowerType.VCC, Color(0xFFAB47BC)),
    PowerSymbolDef("PWR",   "Generic Power", com.pcbdroid.data.model.PowerType.CUSTOM, Color(0xFFFFA726)),
    PowerSymbolDef("NC",    "No Connect",    com.pcbdroid.data.model.PowerType.CUSTOM, Color(0xFF546E7A))
)

@Composable
fun PowerSymbolPicker(
    onSelected: (PowerSymbolDef) -> Unit,
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213E),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FlashOn, null, tint = Color(0xFFFF5722))
                Text("Power Symbol", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Common Symbols", color = Color(0xFF546E7A),
                    style = MaterialTheme.typography.labelSmall)

                Column(
                    modifier = Modifier.heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    COMMON_POWER_SYMBOLS.chunked(4).forEach { rowSyms ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowSyms.forEach { sym ->
                                Box(modifier = Modifier.weight(1f)) {
                                    PowerSymCell(sym) { onSelected(sym) }
                                }
                            }
                            // Fill empty cells
                            repeat(4 - rowSyms.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Divider(color = Color(0xFF2A3F5F))

                // Custom net name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.uppercase() },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Custom net...", color = Color(0xFF546E7A)) },
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFFF5722),
                            unfocusedBorderColor = Color(0xFF2A3F5F),
                            textColor = Color.White,
                            cursorColor = Color(0xFFFF5722)
                        )
                    )
                    Button(
                        onClick = {
                            if (custom.isNotBlank()) {
                                onSelected(PowerSymbolDef(custom, "Custom", 
                                    com.pcbdroid.data.model.PowerType.CUSTOM, Color(0xFFFFA726)))
                            }
                        },
                        enabled = custom.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                    ) { Text("Add", color = Color.White) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF90A4AE))
            }
        }
    )
}

@Composable
fun PowerSymCell(sym: PowerSymbolDef, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = sym.color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, sym.color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(sym.netName, color = sym.color,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1)
            Text(sym.description, color = Color(0xFF546E7A),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1)
        }
    }
}

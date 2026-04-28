@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import com.pcbdroid.data.model.LabelType

val COMMON_NET_LABELS = listOf(
    "UART_TX", "UART_RX", "SPI_CLK", "SPI_MOSI", "SPI_MISO", "SPI_CS",
    "I2C_SDA", "I2C_SCL", "USB_DP", "USB_DM", "CAN_H", "CAN_L",
    "CLK", "RST", "INT", "EN", "CS", "WR", "RD", "OE",
    "LED1", "LED2", "BTN1", "BTN2", "ADC_IN", "PWM_OUT",
    "SWDIO", "SWDCLK", "TDI", "TDO", "TCK", "TMS"
)

@Composable
fun NetLabelDialog(
    onConfirm: (String, LabelType) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var labelType by remember { mutableStateOf(LabelType.LOCAL) }
    var showSuggestions by remember { mutableStateOf(true) }

    val filtered = if (text.isBlank()) COMMON_NET_LABELS
                   else COMMON_NET_LABELS.filter { it.contains(text, true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213E),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Label, null, tint = Color(0xFF4FC3F7))
                Text("Net Label", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Name input
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.uppercase(); showSuggestions = true },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Net name...", color = Color(0xFF546E7A)) },
                    leadingIcon = { Icon(Icons.Default.Label, null, tint = Color(0xFF4FC3F7)) },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color(0xFF2A3F5F),
                        textColor = Color.White,
                        cursorColor = Color(0xFF4FC3F7)
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { if (text.isNotBlank()) onConfirm(text, labelType) }
                    )
                )

                // Label type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LabelType.values().forEach { t ->
                        val sel = t == labelType
                        val color = when (t) {
                            LabelType.LOCAL        -> Color(0xFF4FC3F7)
                            LabelType.GLOBAL       -> Color(0xFFFF9800)
                            LabelType.HIERARCHICAL -> Color(0xFF9C27B0)
                        }
                        Surface(
                            onClick = { labelType = t },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = if (sel) color.copy(alpha = .2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if(sel) color else Color(0xFF2A3F5F))
                        ) {
                            Text(t.name, color = if(sel) color else Color(0xFF546E7A),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal=8.dp, vertical=6.dp),
                                maxLines = 1)
                        }
                    }
                }

                // Suggestions
                if (showSuggestions && filtered.isNotEmpty()) {
                    Text("Suggestions", color = Color(0xFF546E7A),
                        style = MaterialTheme.typography.labelSmall)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered.take(12)) { suggestion ->
                            Surface(
                                onClick = { text = suggestion; showSuggestions = false },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF4FC3F7).copy(alpha=.1f),
                                border = BorderStroke(1.dp, Color(0xFF4FC3F7).copy(alpha=.3f))
                            ) {
                                Text(suggestion, color = Color(0xFF4FC3F7),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal=8.dp, vertical=4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text, labelType) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))
            ) { Text("Place", color = Color.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF90A4AE))
            }
        }
    )
}

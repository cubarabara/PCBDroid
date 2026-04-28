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

data class GridOption(val mils: Float, val label: String, val desc: String)

val GRID_OPTIONS = listOf(
    GridOption(200f, "200 mil", "5.08mm – Header 0.2\""),
    GridOption(100f, "100 mil", "2.54mm – DIP/Header"),
    GridOption(50f,  "50 mil",  "1.27mm – SMD (default)"),
    GridOption(25f,  "25 mil",  "0.635mm – SMD fine pitch"),
    GridOption(10f,  "10 mil",  "0.254mm – Ultra fine"),
    GridOption(5f,   "5 mil",   "0.127mm – Max density")
)

@Composable
fun GridSettingsBar(
    gridSize: Float,
    onGridChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val current = GRID_OPTIONS.firstOrNull { it.mils == gridSize }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF16213E),
            border = BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = .3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Apps, null, tint = Color(0xFF00D4FF), modifier = Modifier.size(14.dp))
                Text(
                    current?.label ?: "${gridSize.toInt()}mil",
                    color = Color(0xFF00D4FF),
                    style = MaterialTheme.typography.labelSmall
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF546E7A), modifier = Modifier.size(14.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF16213E))
        ) {
            Text(
                "Grid Size", color = Color(0xFF546E7A),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Divider(color = Color(0xFF2A3F5F))
            GRID_OPTIONS.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (opt.mils == gridSize)
                                Icon(Icons.Default.Check, null, tint = Color(0xFF00D4FF), modifier = Modifier.size(14.dp))
                            else
                                Spacer(Modifier.size(14.dp))
                            Column {
                                Text(opt.label, color = Color.White, style = MaterialTheme.typography.bodySmall)
                                Text(opt.desc,  color = Color(0xFF546E7A), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    onClick = { onGridChange(opt.mils); expanded = false }
                )
            }
        }
    }
}

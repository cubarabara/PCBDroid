@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.pcbdroid.data.model.*

@Composable
fun PropertiesPanel(
    selectedIds: Set<String>,
    project: PcbProject,
    onClose:  () -> Unit,
    onDelete: () -> Unit,
    onRotate: () -> Unit = {},
    onFlip:   () -> Unit = {}
) {
    if (selectedIds.isEmpty()) return
    val allElements: List<Any> = project.schematic.elements + project.board.elements
    val selected = allElements.filter { el ->
        when (el) {
            is SchematicElement -> el.id in selectedIds
            is PcbElement       -> el.id in selectedIds
            else -> false
        }
    }
    if (selected.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
        color = Color(0xFF16213E), shadowElevation = 16.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F3460))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, null, tint = Color(0xFF00D4FF))
                Spacer(Modifier.width(8.dp))
                Text("Properties (${selectedIds.size})", color = Color.White,
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                // Action icons
                IconButton(onClick = onRotate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Rotate", tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onFlip, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Autorenew, "Flip", tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Color(0xFF90A4AE),
                        modifier = Modifier.size(18.dp))
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                selected.forEach { el ->
                    when (el) {
                        is SchematicComponent -> SchCompProps(el)
                        is SchematicWire      -> WireProps(el)
                        is SchematicLabel     -> LblProps(el)
                        is PowerSymbol        -> PowerProps(el)
                        is Footprint          -> FpProps(el)
                        is Track              -> TrackProps(el)
                        is Via                -> ViaProps(el)
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable fun SchCompProps(c: SchematicComponent) {
    PropSection("Component: ${c.reference}") {
        PropField("Reference", c.reference)
        PropField("Value",     c.value)
        PropInfo("Symbol",    c.symbolId)
        PropInfo("Footprint", c.footprintId)
        PropInfo("Position",  "X:${String.format("%.2f",c.position.x*0.0254f)} Y:${String.format("%.2f",c.position.y*0.0254f)} mm")
        PropInfo("Rotation",  "${c.rotation}°")
    }
}
@Composable fun WireProps(w: SchematicWire) {
    PropSection("Wire") {
        PropInfo("Start",  "(${w.start.x.toInt()}, ${w.start.y.toInt()})")
        PropInfo("End",    "(${w.end.x.toInt()}, ${w.end.y.toInt()})")
        val dx=w.end.x-w.start.x; val dy=w.end.y-w.start.y
        PropInfo("Length", "${String.format("%.3f", kotlin.math.sqrt(dx*dx+dy*dy)*0.0254f)} mm")
        if (w.netId >= 0) PropInfo("Net", "${w.netId}")
    }
}
@Composable fun LblProps(l: SchematicLabel) {
    PropSection("Net Label") {
        PropField("Name", l.text)
        PropInfo("Type", l.labelType.name)
        PropInfo("Net",  if(l.netId>=0) "${l.netId}" else "—")
    }
}
@Composable fun PowerProps(p: PowerSymbol) {
    PropSection("Power Symbol") {
        PropField("Net Name", p.netName)
        PropInfo("Type", p.symbolType.name)
    }
}
@Composable fun FpProps(fp: Footprint) {
    PropSection("Footprint") {
        PropField("Reference", fp.reference)
        PropField("Value",     fp.value)
        PropInfo("Footprint",  fp.footprintId)
        PropInfo("Layer",      fp.layer.displayName)
        PropInfo("Pads",       "${fp.pads.size}")
        PropInfo("Rotation",   "${fp.rotation}°")
        if (fp.model3dPath != null)
            PropInfo("3D Model", fp.model3dPath.substringAfterLast("/"))
    }
}
@Composable fun TrackProps(t: Track) {
    PropSection("Track") {
        PropInfo("Layer", t.layer.displayName)
        PropInfo("Width", "${String.format("%.3f", t.width)} mm")
        val dx=t.end.x-t.start.x; val dy=t.end.y-t.start.y
        PropInfo("Length", "${String.format("%.3f", kotlin.math.sqrt(dx*dx+dy*dy)/1000f)} mm")
        PropInfo("Net",   if(t.netId>=0) "${t.netId}" else "—")
    }
}
@Composable fun ViaProps(v: Via) {
    PropSection("Via") {
        PropInfo("Size",   "${v.size} mm")
        PropInfo("Drill",  "${v.drill} mm")
        PropInfo("Layers", "${v.fromLayer.displayName} ↔ ${v.toLayer.displayName}")
        PropInfo("Net",    if(v.netId>=0) "${v.netId}" else "—")
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
fun PropSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, color = Color(0xFF00D4FF), style = MaterialTheme.typography.labelMedium)
        Divider(color = Color(0xFF00D4FF).copy(alpha=.2f), modifier = Modifier.padding(vertical=4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun PropField(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF78909C), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(90.dp))
        var v by remember(value) { mutableStateOf(value) }
        OutlinedTextField(value = v, onValueChange = { v = it },
            modifier = Modifier.weight(1f), singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, color = Color.White),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF00D4FF),
                unfocusedBorderColor = Color(0xFF2A3F5F),
                cursorColor = Color(0xFF00D4FF)))
    }
}

@Composable
fun PropInfo(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color(0xFF78909C), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(90.dp))
        Text(value, color = Color(0xFF90A4AE), style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace)
    }
}

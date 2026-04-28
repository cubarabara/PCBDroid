@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.pcbdroid.data.model.*
import com.pcbdroid.domain.erc.*

@Composable
fun RulesCheckPanel(
    ercViolations: List<ErcViolation>,
    drcViolations: List<DrcViolation>,
    onNavigateTo: (PcbPoint) -> Unit,
    onClose: () -> Unit
) {
    // Use Int state explicitly
    var tabIdx by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF16213E)) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF0F3460)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00D4FF))
                Spacer(Modifier.width(8.dp))
                Text("Design Rules Check", color = Color.White,
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = Color(0xFF90A4AE))
                }
            }

            // Summary
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1B2A)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryBadge("ERC Err",  ercViolations.count { it.severity == ErcSeverity.ERROR },   Color(0xFFFF5252))
                SummaryBadge("ERC Warn", ercViolations.count { it.severity == ErcSeverity.WARNING }, Color(0xFFFFD740))
                SummaryBadge("DRC Err",  drcViolations.count { it.severity == DrcSeverity.ERROR },   Color(0xFFFF5252))
                SummaryBadge("DRC Warn", drcViolations.count { it.severity == DrcSeverity.WARNING }, Color(0xFFFFD740))
            }

            // Tabs
            TabRow(selectedTabIndex = tabIdx,
                containerColor = Color(0xFF16213E),
                contentColor = Color(0xFF00D4FF)) {
                Tab(selected = tabIdx == 0, onClick = { tabIdx = 0 },
                    text = { Text("ERC (${ercViolations.size})", style = MaterialTheme.typography.labelSmall) })
                Tab(selected = tabIdx == 1, onClick = { tabIdx = 1 },
                    text = { Text("DRC (${drcViolations.size})", style = MaterialTheme.typography.labelSmall) })
            }

            when (tabIdx) {
                0 -> ErcList(ercViolations, onNavigateTo)
                1 -> DrcList(drcViolations, onNavigateTo)
            }
        }
    }
}

@Composable
fun SummaryBadge(label: String, count: Int, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp),
        color = if (count > 0) color.copy(alpha=.15f) else Color(0xFF2A3F5F).copy(alpha=.3f),
        border = if (count > 0) BorderStroke(1.dp, color.copy(alpha=.4f)) else null) {
        Column(modifier = Modifier.padding(horizontal=8.dp, vertical=4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", color = if(count>0) color else Color(0xFF546E7A),
                style = MaterialTheme.typography.titleSmall)
            Text(label, color = if(count>0) color.copy(alpha=.7f) else Color(0xFF546E7A),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ErcList(violations: List<ErcViolation>, onNav: (PcbPoint) -> Unit) {
    if (violations.isEmpty()) { EmptyCheck("No ERC violations", "✓ Schematic is valid"); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val errs  = violations.filter { it.severity == ErcSeverity.ERROR }
        val warns = violations.filter { it.severity == ErcSeverity.WARNING }
        val infos = violations.filter { it.severity == ErcSeverity.INFO }
        if (errs.isNotEmpty())  { item { GroupHeader("Errors",   errs.size,  Color(0xFFFF5252)) }; items(errs)  { ViolItem(it.type.name, it.description, ErcSeverity.ERROR,   it.position, onNav) } }
        if (warns.isNotEmpty()) { item { GroupHeader("Warnings", warns.size, Color(0xFFFFD740)) }; items(warns) { ViolItem(it.type.name, it.description, ErcSeverity.WARNING, it.position, onNav) } }
        if (infos.isNotEmpty()) { item { GroupHeader("Info",     infos.size, Color(0xFF40C4FF)) }; items(infos) { ViolItem(it.type.name, it.description, ErcSeverity.INFO,    it.position, onNav) } }
    }
}

@Composable
fun DrcList(violations: List<DrcViolation>, onNav: (PcbPoint) -> Unit) {
    if (violations.isEmpty()) { EmptyCheck("No DRC violations", "✓ PCB layout is valid"); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(violations) { v ->
            val sev = when (v.severity) {
                DrcSeverity.ERROR   -> ErcSeverity.ERROR
                DrcSeverity.WARNING -> ErcSeverity.WARNING
                DrcSeverity.INFO    -> ErcSeverity.INFO
            }
            ViolItem(v.type.name, v.description, sev, v.position, onNav)
        }
    }
}

@Composable
fun GroupHeader(title: String, count: Int, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical=4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = color, style = MaterialTheme.typography.labelMedium)
        Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha=.15f)) {
            Text("$count", color = color, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal=6.dp, vertical=2.dp))
        }
        Divider(modifier = Modifier.weight(1f), color = color.copy(alpha=.2f))
    }
}

@Composable
fun ViolItem(title: String, desc: String, severity: ErcSeverity, pos: PcbPoint, onNav: (PcbPoint) -> Unit) {
    val color = when (severity) {
        ErcSeverity.ERROR   -> Color(0xFFFF5252)
        ErcSeverity.WARNING -> Color(0xFFFFD740)
        ErcSeverity.INFO    -> Color(0xFF40C4FF)
    }
    val icon = when (severity) {
        ErcSeverity.ERROR   -> Icons.Default.RemoveCircle
        ErcSeverity.WARNING -> Icons.Default.Warning
        ErcSeverity.INFO    -> Icons.Default.Info
    }
    Surface(onClick = { onNav(pos) }, shape = RoundedCornerShape(6.dp),
        color = Color(0xFF1E2D4A), border = BorderStroke(1.dp, color.copy(alpha=.2f)),
        modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp).padding(top=2.dp))
            Column(Modifier.weight(1f)) {
                Text(title.replace("_", " "), color = color, style = MaterialTheme.typography.labelMedium)
                Text(desc, color = Color(0xFF90A4AE), style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.LocationOn, null, tint = Color(0xFF546E7A), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun EmptyCheck(title: String, sub: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00FF88), modifier = Modifier.size(48.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(sub, color = Color(0xFF546E7A), style = MaterialTheme.typography.bodySmall)
        }
    }
}

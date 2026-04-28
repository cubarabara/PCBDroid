@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import com.pcbdroid.data.model.*
import com.pcbdroid.domain.export.GerberExporter
import java.io.*
import java.util.zip.*

@Composable
fun ExportDialog(project: PcbProject, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var busy   by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213E),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Download, null, tint = Color(0xFF00D4FF))
                Text("Export", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PCB Fabrication", color = Color(0xFF546E7A),
                    style = MaterialTheme.typography.labelSmall)

                ExportRow(Icons.Default.LayersClear, "Gerber + Drill (ZIP)",
                    "RS-274X for JLCPCB / PCBWay", Color(0xFF00D4FF), busy) {
                    busy = true
                    val bundle = GerberExporter.exportBoard(project.board, project.name)
                    val zip = createZip(ctx, bundle.files, "${project.name}_gerbers.zip")
                    shareFile(ctx, zip, "application/zip")
                    status = "✓ ${bundle.files.size} files exported"
                    busy = false
                }

                ExportRow(Icons.Default.Grain, "Drill File (Excellon)",
                    "PTH + NPTH drill holes", Color(0xFFFFD740)) {
                    val files = mapOf(
                        "${project.name}-PTH.drl"  to GerberExporter.drillFile(project.board, project.name, true),
                        "${project.name}-NPTH.drl" to GerberExporter.drillFile(project.board, project.name, false)
                    )
                    shareFile(ctx, createZip(ctx, files, "${project.name}_drill.zip"), "application/zip")
                }

                Divider(color = Color(0xFF2A3F5F))
                Text("Documentation", color = Color(0xFF546E7A),
                    style = MaterialTheme.typography.labelSmall)

                ExportRow(Icons.Default.GridOn, "Bill of Materials (CSV)",
                    "Component list for purchasing", Color(0xFF00FF88)) {
                    val bom = GerberExporter.exportBOM(project.schematic, project.name)
                    shareText(ctx, "${project.name}-BOM.csv", bom, "text/csv")
                }

                ExportRow(Icons.Default.Build, "Pick & Place (CSV)",
                    "SMT assembly data", Color(0xFF26C6DA)) {
                    val pnp = GerberExporter.exportPickAndPlace(project.board)
                    shareText(ctx, "${project.name}-PnP.csv", pnp, "text/csv")
                }

                status?.let { msg ->
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF00FF88).copy(alpha=.1f),
                        border = BorderStroke(1.dp, Color(0xFF00FF88).copy(alpha=.3f)),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(msg, color = Color(0xFF00FF88),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(10.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF90A4AE))
            }
        }
    )
}

@Composable
fun ExportRow(
    icon: ImageVector, title: String, desc: String, color: Color,
    busy: Boolean = false, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E2D4A),
        border = BorderStroke(1.dp, color.copy(alpha=.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.size(36.dp), RoundedCornerShape(8.dp), color.copy(alpha=.1f)) {
                Box(contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color, strokeWidth=2.dp)
                    else Icon(icon, null, tint=color, modifier=Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color=Color.White, style=MaterialTheme.typography.bodySmall)
                Text(desc,  color=Color(0xFF546E7A), style=MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint=Color(0xFF546E7A))
        }
    }
}

// ─── File helpers ─────────────────────────────────────────────────────────────

private fun createZip(ctx: Context, files: Map<String,String>, name: String): File {
    val f = File(ctx.cacheDir, name)
    ZipOutputStream(BufferedOutputStream(FileOutputStream(f))).use { z ->
        files.forEach { (fn, content) ->
            z.putNextEntry(ZipEntry(fn))
            z.write(content.toByteArray())
            z.closeEntry()
        }
    }
    return f
}

private fun shareText(ctx: Context, name: String, content: String, mime: String) {
    val f = File(ctx.cacheDir, name).also { it.writeText(content) }
    shareFile(ctx, f, mime)
}

private fun shareFile(ctx: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
    ctx.startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share ${file.name}"
    ))
}

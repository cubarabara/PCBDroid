@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.*
import com.pcbdroid.data.model.*
import kotlin.math.*

// ─── 3D View Screen ───────────────────────────────────────────────────────────
// Menggunakan Canvas isometric renderer (tidak butuh SceneView/Filament)
// SceneView memerlukan Kotlin 1.9+ yang tidak kompatibel dengan AndroidIDE saat ini

enum class Camera3DMode { ORBIT, TOP, FRONT, SIDE, ISOMETRIC }
enum class ThreeDBackground { DARK, LIGHT, GRADIENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun View3DScreen(viewModel: PcbEditorViewModel) {
    var showCopper     by remember { mutableStateOf(true) }
    var showComponents by remember { mutableStateOf(true) }
    var showSolderMask by remember { mutableStateOf(true) }
    var cameraMode     by remember { mutableStateOf(Camera3DMode.ISOMETRIC) }
    var bgColor        by remember { mutableStateOf(ThreeDBackground.DARK) }

    // Camera rotation state
    var rotX by remember { mutableStateOf(30f) }
    var rotY by remember { mutableStateOf(-45f) }
    var zoom by remember { mutableStateOf(1f) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Isometric Canvas 3D Renderer ──────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when (bgColor) {
                        ThreeDBackground.DARK     -> Color(0xFF0D1B2A)
                        ThreeDBackground.LIGHT    -> Color(0xFFF0F4F8)
                        ThreeDBackground.GRADIENT -> Color(0xFF16213E)
                    }
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomFactor, _ ->
                        zoom = (zoom * zoomFactor).coerceIn(0.3f, 5f)
                        rotY += pan.x * 0.3f
                        rotX = (rotX - pan.y * 0.3f).coerceIn(-89f, 89f)
                    }
                }
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val board = viewModel.project.board

            // Apply camera mode presets
            val (drawRotX, drawRotY) = when (cameraMode) {
                Camera3DMode.TOP        -> Pair(90f, 0f)
                Camera3DMode.FRONT      -> Pair(0f, 0f)
                Camera3DMode.SIDE       -> Pair(0f, 90f)
                Camera3DMode.ISOMETRIC  -> Pair(35.26f, -45f)
                Camera3DMode.ORBIT      -> Pair(rotX, rotY)
            }

            drawPcbBoard3D(
                board          = board,
                centerX        = cx,
                centerY        = cy,
                zoom           = zoom,
                rotX           = drawRotX,
                rotY           = drawRotY,
                showCopper     = showCopper,
                showComponents = showComponents,
                showSolderMask = showSolderMask
            )
        }

        // ── Controls overlay ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color(0x881A1A2E), MaterialTheme.shapes.medium)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("3D View", color = Color(0xFF00D4FF),
                style = MaterialTheme.typography.labelMedium)
            Divider(color = Color(0xFF00D4FF).copy(alpha = 0.3f))

            ToggleRow("Copper",     showCopper)     { showCopper     = it }
            ToggleRow("Components", showComponents) { showComponents = it }
            ToggleRow("Solder Mask",showSolderMask) { showSolderMask = it }

            Divider(color = Color(0xFF00D4FF).copy(alpha = 0.3f))
            Text("Camera", color = Color(0xFF90A4AE),
                style = MaterialTheme.typography.labelSmall)

            listOf(
                Camera3DMode.TOP       to "Top",
                Camera3DMode.FRONT     to "Front",
                Camera3DMode.ISOMETRIC to "ISO",
                Camera3DMode.ORBIT     to "Orbit"
            ).forEach { (mode, label) ->
                CameraButton(label, mode == cameraMode) { cameraMode = mode }
            }

            Divider(color = Color(0xFF00D4FF).copy(alpha = 0.3f))
            Text("BG", color = Color(0xFF90A4AE), style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ThreeDBackground.values().forEach { bg ->
                    Surface(
                        onClick = { bgColor = bg },
                        modifier = Modifier.size(20.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = when (bg) {
                            ThreeDBackground.DARK     -> Color(0xFF1A1A1A)
                            ThreeDBackground.LIGHT    -> Color(0xFFF5F5F5)
                            ThreeDBackground.GRADIENT -> Color(0xFF16213E)
                        },
                        border = if (bgColor == bg)
                            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00D4FF))
                        else null
                    ) {}
                }
            }
        }

        // ── Info bar ──────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Color(0x881A1A2E)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val board = viewModel.project.board
                Text("FP: ${board.elements.filterIsInstance<Footprint>().size}",
                    color = Color(0xFF90A4AE), style = MaterialTheme.typography.labelSmall)
                Text("Tracks: ${board.elements.filterIsInstance<Track>().size}",
                    color = Color(0xFF90A4AE), style = MaterialTheme.typography.labelSmall)
                Text("Vias: ${board.elements.filterIsInstance<Via>().size}",
                    color = Color(0xFF90A4AE), style = MaterialTheme.typography.labelSmall)
                Text("Zoom: ${"%.1f".format(zoom)}x",
                    color = Color(0xFF00D4FF), style = MaterialTheme.typography.labelSmall)
            }
        }

        Text(
            "Pinch to zoom · Drag to orbit",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            color = Color(0xFF546E7A),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            modifier = Modifier.height(20.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor  = Color(0xFF00D4FF),
                checkedTrackColor  = Color(0xFF00D4FF).copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun CameraButton(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = if (active) Color(0xFF00D4FF).copy(alpha = 0.2f) else Color.Transparent,
        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00D4FF)) else null
    ) {
        Text(
            label,
            color = if (active) Color(0xFF00D4FF) else Color(0xFF90A4AE),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ─── Isometric PCB Renderer (Canvas-based) ────────────────────────────────────

fun DrawScope.drawPcbBoard3D(
    board: PcbBoard,
    centerX: Float, centerY: Float,
    zoom: Float, rotX: Float, rotY: Float,
    showCopper: Boolean, showComponents: Boolean, showSolderMask: Boolean
) {
    val scale = 0.15f * zoom

    // Project 3D point to 2D screen with rotation
    fun project(x: Float, y: Float, z: Float): Offset {
        val rx = Math.toRadians(rotX.toDouble())
        val ry = Math.toRadians(rotY.toDouble())

        // Rotate around Y axis
        val x1 = (x * cos(ry) + z * sin(ry)).toFloat()
        val z1 = (-x * sin(ry) + z * cos(ry)).toFloat()

        // Rotate around X axis
        val y2 = (y * cos(rx) - z1 * sin(rx)).toFloat()
        val z2 = (y * sin(rx) + z1 * cos(rx)).toFloat()

        return Offset(centerX + x1 * scale, centerY - y2 * scale)
    }

    // Board dimensions (derive from outline or use default)
    val outlines = board.elements.filterIsInstance<BoardOutline>()
    val boardW = if (outlines.isNotEmpty()) {
        outlines[0].points.maxOf { it.x } - outlines[0].points.minOf { it.x }
    } else 100000f
    val boardH = if (outlines.isNotEmpty()) {
        outlines[0].points.maxOf { it.y } - outlines[0].points.minOf { it.y }
    } else 80000f

    val bx = -boardW / 2; val by = -boardH / 2
    val bz = 0f;          val thickness = 1600f  // ~1.6mm in mils

    // ── FR4 Substrate ──────────────────────────────────────────────────────────
    val fr4Color = if (showSolderMask) Color(0xFF1A5C1A) else Color(0xFF4A3728)
    val corners = listOf(
        Triple(bx, by, bz), Triple(bx + boardW, by, bz),
        Triple(bx + boardW, by + boardH, bz), Triple(bx, by + boardH, bz)
    )
    val p = corners.map { (x, y, z) -> project(x, y, z) }

    // Top face
    val topPath = Path().apply {
        moveTo(p[0].x, p[0].y)
        lineTo(p[1].x, p[1].y)
        lineTo(p[2].x, p[2].y)
        lineTo(p[3].x, p[3].y)
        close()
    }
    drawPath(topPath, fr4Color)
    drawPath(topPath, Color(0xFF2A7A2A), style = Stroke(1f))

    // Side faces
    val sideColor = Color(0xFF0D3A0D)
    val bt = thickness
    val frontPath = Path().apply {
        moveTo(p[0].x, p[0].y)
        lineTo(p[1].x, p[1].y)
        val pb1 = project(bx + boardW, by, bz - bt)
        val pb0 = project(bx, by, bz - bt)
        lineTo(pb1.x, pb1.y)
        lineTo(pb0.x, pb0.y)
        close()
    }
    drawPath(frontPath, sideColor)

    val rightPath = Path().apply {
        moveTo(p[1].x, p[1].y)
        lineTo(p[2].x, p[2].y)
        val pb2 = project(bx + boardW, by + boardH, bz - bt)
        val pb1 = project(bx + boardW, by, bz - bt)
        lineTo(pb2.x, pb2.y)
        lineTo(pb1.x, pb1.y)
        close()
    }
    drawPath(rightPath, Color(0xFF0A2A0A))

    // ── Copper tracks (F.Cu) ──────────────────────────────────────────────────
    if (showCopper) {
        board.elements.filterIsInstance<Track>().filter { it.layer == PcbLayer.F_CU }.forEach { track ->
            val tZ = bz + 50f
            val ts = project(track.start.x + bx + boardW/2, track.start.y + by + boardH/2, tZ)
            val te = project(track.end.x   + bx + boardW/2, track.end.y   + by + boardH/2, tZ)
            drawLine(Color(0xFFB5121B), ts, te, (track.width * scale * 100f).coerceAtLeast(1.5f))
        }

        // Vias
        board.elements.filterIsInstance<Via>().forEach { via ->
            val vp = project(via.position.x + bx + boardW/2, via.position.y + by + boardH/2, bz + 100f)
            drawCircle(Color(0xFFCFD8DC), (via.size * scale * 50f).coerceAtLeast(3f), vp)
            drawCircle(Color(0xFF1A1A1A), (via.drill * scale * 50f).coerceAtLeast(1.5f), vp)
        }
    }

    // ── Footprints ────────────────────────────────────────────────────────────
    if (showComponents) {
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            val fpZ = bz + 100f
            val fpCenter = project(
                fp.position.x + bx + boardW/2,
                fp.position.y + by + boardH/2,
                fpZ
            )
            // Simple component box
            val cSize = 800f * scale
            drawRect(
                color    = Color(0xFF37474F),
                topLeft  = Offset(fpCenter.x - cSize/2, fpCenter.y - cSize/2),
                size     = Size(cSize, cSize * 0.6f)
            )
            drawRect(
                color    = Color(0xFF546E7A),
                topLeft  = Offset(fpCenter.x - cSize/2, fpCenter.y - cSize/2),
                size     = Size(cSize, cSize * 0.6f),
                style    = Stroke(1f)
            )
        }
    }

    // ── Silkscreen labels ─────────────────────────────────────────────────────
    if (showSolderMask) {
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            val lp = project(
                fp.position.x + bx + boardW/2,
                fp.position.y + by + boardH/2 - 600f,
                bz + 60f
            )
            drawContext.canvas.nativeCanvas.drawText(
                fp.reference,
                lp.x, lp.y,
                android.graphics.Paint().apply {
                    color     = android.graphics.Color.WHITE
                    textSize  = (10f * zoom).coerceIn(6f, 18f)
                    isAntiAlias = true
                }
            )
        }
    }

    // ── Edge cuts outline ─────────────────────────────────────────────────────
    if (outlines.isNotEmpty()) {
        val outline = outlines[0]
        for (i in 0 until outline.points.size - 1) {
            val op1 = project(outline.points[i].x,     outline.points[i].y,     bz + 10f)
            val op2 = project(outline.points[i+1].x,   outline.points[i+1].y,   bz + 10f)
            drawLine(Color(0xFFFFFF00), op1, op2, 2f)
        }
    }
}

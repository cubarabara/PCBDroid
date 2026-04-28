package com.pcbdroid.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.*
import com.pcbdroid.data.model.*
import com.pcbdroid.ui.screens.GridSystem.drawPcbGrid
import kotlin.math.*

// ─── Layout Canvas ────────────────────────────────────────────────────────────
@Composable
fun LayoutCanvas(viewModel: PcbEditorViewModel) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF121212))
        .pointerInput(viewModel.activeTool) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                if (zoom != 1f) viewModel.onZoom(zoom, centroid)
                viewModel.onPan(pan.x, pan.y)
            }
        }
        .pointerInput(viewModel.activeTool) {
            detectTapGestures(
                onTap       = { viewModel.onCanvasTap(it) },
                onDoubleTap = { viewModel.onCanvasDoubleTap(it) }
            )
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    ev.changes.firstOrNull()?.let { viewModel.onPointerMove(it.position) }
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = viewModel.canvasTransform
            if (viewModel.showGrid) drawPcbGrid(t, viewModel.gridSize)

            withTransform({
                translate(t.offsetX, t.offsetY)
                scale(t.scale, t.scale, Offset.Zero)
            }) {
                val elems   = viewModel.project.board.elements
                val sel     = viewModel.selection.selectedIds
                val visible = viewModel.visibleLayers

                // Board outline
                elems.filterIsInstance<BoardOutline>().forEach {
                    if (it.layer in visible) drawBoardOutline(it)
                }

                // Copper zones
                PcbLayer.allLayers.filter { it.name.endsWith("_CU") && it in visible }
                    .sortedBy { it.zIndex }.forEach { layer ->
                        elems.filterIsInstance<Zone>().filter { it.layer == layer }
                            .forEach { drawCopperZone(it, it.id in sel) }
                        elems.filterIsInstance<Track>().filter { it.layer == layer }
                            .forEach { drawTrack(it, it.id in sel) }
                    }

                // Vias
                elems.filterIsInstance<Via>().forEach { drawVia(it, it.id in sel) }

                // Footprints
                elems.filterIsInstance<Footprint>()
                    .forEach { drawFootprint(it, it.id in sel, visible) }

                // DRC markers
                viewModel.drcViolations.forEach { drawDrcMarker(it) }
            }

            // Live route preview
            viewModel.routeState.let { rs ->
                if (rs.isRouting && rs.points.isNotEmpty() && rs.currentPos != null) {
                    val t2 = viewModel.canvasTransform
                    val last = rs.points.last()
                    val s = t2.worldToScreen(last)
                    val e = t2.worldToScreen(rs.currentPos)
                    drawRoutePreview(s, e, rs.currentLayer, rs.trackWidth * t2.scale)
                }
            }

            // Measurement overlays
            val t2 = viewModel.canvasTransform
            viewModel.measureLines.forEach { drawMeasureLine(it, t2, Color(0xFF00E5FF)) }
            viewModel.activeMeasure?.let { drawMeasureLine(it, t2, Color(0xFFFFD740), active=true) }
        }

        // Coordinate display
        viewModel.cursorWorld?.let { pos ->
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 70.dp, bottom = 32.dp),
                color = Color(0x99000000), shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "X: ${"%.3f".format(pos.x * 0.0254f)} Y: ${"%.3f".format(pos.y * 0.0254f)} mm",
                    color = Color(0xFF00D4FF),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ─── Layer Panel ──────────────────────────────────────────────────────────────
@Composable
fun LayerPanel(
    layers: List<PcbLayer>,
    activeLayer: PcbLayer,
    visibleLayers: Set<PcbLayer>,
    onLayerClick: (PcbLayer) -> Unit,
    onToggleVisibility: (PcbLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .width(140.dp)
            .background(Color(0xFF16213E).copy(alpha = 0.95f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        item {
            Text("Layers", color = Color(0xFF00D4FF),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        items(layers) { layer ->
            val active  = layer == activeLayer
            val visible = layer in visibleLayers
            val lColor  = Color(layer.color.toInt())
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(if (active) lColor.copy(0.15f) else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable { onLayerClick(layer) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(Modifier.size(10.dp)) { drawCircle(color = lColor, radius = size.minDimension / 2) }
                Text(layer.displayName,
                    color = if (active) lColor else Color(0xFF90A4AE),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { onToggleVisibility(layer) }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null,
                        tint = if (visible) lColor.copy(0.7f) else Color(0xFF546E7A),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ─── PCB Draw Functions ───────────────────────────────────────────────────────
fun DrawScope.drawTrack(track: Track, sel: Boolean) {
    drawLine(if(sel) Color(0xFFFFD600) else Color(track.layer.color),
        Offset(track.start.x, track.start.y), Offset(track.end.x, track.end.y),
        (track.width * 100f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
}

fun DrawScope.drawVia(via: Via, sel: Boolean) {
    val c = Offset(via.position.x, via.position.y)
    drawCircle(if(sel) Color(0xFFFFD600) else Color(0xFFCFD8DC), via.size*50f, c)
    drawCircle(Color(0xFF1A1A1A), via.drill*50f, c)
}

fun DrawScope.drawFootprint(fp: Footprint, sel: Boolean, visible: Set<PcbLayer>) {
    fp.pads.filter { it.layers.any { l -> l in visible } }.forEach { drawPad(it, fp.position, sel) }
    if (PcbLayer.F_SILKSCREEN in visible || PcbLayer.B_SILKSCREEN in visible) {
        fp.silkscreen.forEach { line ->
            drawLine(Color.White,
                Offset(fp.position.x + line.start.x, fp.position.y + line.start.y),
                Offset(fp.position.x + line.end.x,   fp.position.y + line.end.y),
                line.width * 100f)
        }
    }
    if (sel || PcbLayer.F_COURTYARD in visible) {
        val col = if(sel) Color(0xFFFFD600) else Color(fp.layer.color).copy(0.5f)
        val pts = fp.courtyard
        for (i in 0 until pts.size-1) {
            drawLine(col, Offset(fp.position.x+pts[i].x, fp.position.y+pts[i].y),
                         Offset(fp.position.x+pts[i+1].x, fp.position.y+pts[i+1].y),
                1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f,3f)))
        }
    }
    drawContext.canvas.nativeCanvas.drawText(fp.reference,
        fp.position.x + 5f, fp.position.y - 5f,
        android.graphics.Paint().apply {
            color = if(sel) android.graphics.Color.parseColor("#FFD600")
                    else android.graphics.Color.WHITE
            textSize = 18f; isAntiAlias = true
        })
}

fun DrawScope.drawPad(pad: Pad, fpPos: PcbPoint, sel: Boolean) {
    val col = if(sel) Color(0xFFFFD600) else Color(pad.layers.firstOrNull()?.color ?: 0xFFCFD8DC)
    val pos = Offset(fpPos.x + pad.position.x, fpPos.y + pad.position.y)
    val w = pad.size.x * 50f; val h = pad.size.y * 50f
    when (pad.shape) {
        PadShape.ROUND -> drawCircle(col, minOf(w,h)/2, pos)
        PadShape.OVAL  -> drawOval(col, topLeft=Offset(pos.x-w/2, pos.y-h/2), size=Size(w,h))
        else           -> drawRect(col, pos + Offset(-w/2,-h/2), Size(w,h))
    }
    if (pad.type == PadType.THROUGH_HOLE) drawCircle(Color(0xFF1A1A1A), pad.drill*50f, pos)
}

fun DrawScope.drawBoardOutline(o: BoardOutline) {
    if (o.points.size < 2) return
    val path = Path().apply {
        moveTo(o.points[0].x, o.points[0].y)
        for (i in 1 until o.points.size) lineTo(o.points[i].x, o.points[i].y)
        close()
    }
    drawPath(path, Color.Transparent)
    drawPath(path, Color(0xFFFFFF00), style = Stroke(2f))
}

fun DrawScope.drawCopperZone(z: Zone, sel: Boolean) {
    if (z.points.size < 3) return
    val col = Color(z.layer.color)
    val path = Path().apply {
        moveTo(z.points[0].x, z.points[0].y)
        for (i in 1 until z.points.size) lineTo(z.points[i].x, z.points[i].y)
        close()
    }
    drawPath(path, col.copy(0.25f))
    drawPath(path, if(sel) Color(0xFFFFD600) else col, style=Stroke(if(sel) 2f else 1f))
}

fun DrawScope.drawDrcMarker(v: DrcViolation) {
    val c = Offset(v.position.x, v.position.y)
    val col = when(v.severity) {
        DrcSeverity.ERROR   -> Color(0xFFFF5252)
        DrcSeverity.WARNING -> Color(0xFFFFD740)
        DrcSeverity.INFO    -> Color(0xFF40C4FF)
    }
    val path = Path().apply { moveTo(c.x,c.y-20f); lineTo(c.x+17f,c.y+10f); lineTo(c.x-17f,c.y+10f); close() }
    drawPath(path, col.copy(0.2f)); drawPath(path, col, style=Stroke(2f))
}

fun DrawScope.drawRoutePreview(start: Offset, end: Offset, layer: PcbLayer, widthPx: Float) {
    val col = Color(layer.color.toInt()).copy(0.7f)
    val dx = end.x - start.x; val dy = end.y - start.y
    val corner = if (abs(dx) > abs(dy)) Offset(start.x + dx - dy, end.y)
                 else Offset(end.x, start.y + dy - dx)
    val pe = PathEffect.dashPathEffect(floatArrayOf(8f,4f))
    val w  = widthPx.coerceAtLeast(2f)
    drawLine(col, start, corner, w, cap=StrokeCap.Round, pathEffect=pe)
    drawLine(col, corner, end,   w, cap=StrokeCap.Round, pathEffect=pe)
    drawCircle(col, 6f, end)
}

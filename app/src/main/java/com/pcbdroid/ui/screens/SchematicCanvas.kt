@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.pcbdroid.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import com.pcbdroid.data.model.*
import com.pcbdroid.ui.screens.GridSystem.drawSchematicGrid
import com.pcbdroid.ui.screens.SymbolRenderer.drawSymbol
import kotlin.math.*

@Composable
fun SchematicCanvas(viewModel: PcbEditorViewModel) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF191919))
            .pointerInput(viewModel.activeTool) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (zoom != 1f) viewModel.onZoom(zoom, centroid)
                    viewModel.onPan(pan.x, pan.y)
                }
            }
            .pointerInput(viewModel.activeTool) {
                detectTapGestures(
                    onTap         = { viewModel.onCanvasTap(it) },
                    onDoubleTap   = { viewModel.onCanvasDoubleTap(it) }
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { viewModel.onPointerMove(it.position) }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = viewModel.canvasTransform

            // ── Grid ─────────────────────────────────────────────────────────
            if (viewModel.showGrid) drawSchematicGrid(t, viewModel.gridSize)

            // ── World transform ───────────────────────────────────────────────
            withTransform({
                translate(t.offsetX, t.offsetY)
                scale(t.scale, t.scale, Offset.Zero)
            }) {
                val elems = viewModel.project.schematic.elements
                val sel   = viewModel.selection.selectedIds

                // Draw order: wires → buses → junctions → no-connects → power → components → labels

                // Wires
                elems.filterIsInstance<SchematicWire>()
                    .forEach { drawSchWire(it, it.id in sel) }

                // Bus lines
                elems.filterIsInstance<BusLine>()
                    .forEach { drawBusLine(it, it.id in sel) }

                // Junctions
                elems.filterIsInstance<SchematicJunction>()
                    .forEach { drawJunction(it) }

                // No-connects
                elems.filterIsInstance<NoConnect>()
                    .forEach { drawNoConnect(it) }

                // Power symbols
                elems.filterIsInstance<PowerSymbol>()
                    .forEach { drawPowerSym(it, it.id in sel) }

                // Components — use real symbol renderer
                elems.filterIsInstance<SchematicComponent>()
                    .forEach { drawSymbol(it, it.id in sel) }

                // Net labels
                elems.filterIsInstance<SchematicLabel>()
                    .forEach { drawNetLabel(it, it.id in sel) }

                // Selection highlight box
                sel.forEach { id ->
                    elems.firstOrNull { it.id == id }?.let { drawSelectionBox(it) }
                }

                // Pending component ghost
                viewModel.pendingComponent?.let { comp ->
                    viewModel.cursorWorld?.let { pos ->
                        drawPendingGhost(pos)
                    }
                }
            }

            // ── Live wire preview (screen space) ─────────────────────────────
            viewModel.wireState.let { ws ->
                if (ws.isDrawing && ws.startPoint != null && ws.currentPoint != null) {
                    val t2 = viewModel.canvasTransform
                    val s  = t2.worldToScreen(ws.startPoint)
                    val e  = t2.worldToScreen(ws.currentPoint)
                    val corner = wireCorner(s, e, ws.wireMode)
                    drawLine(Color(0xFF00FF88), s, corner, 2f * t2.scale,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                    drawLine(Color(0xFF00FF88), corner, e, 2f * t2.scale,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
                    drawCircle(Color(0xFF00FF88), 5f, s)
                    drawCircle(Color(0xFF00FF88), 5f, e)
                }
            }

            // ── Measurement lines ─────────────────────────────────────────────
            val t2 = viewModel.canvasTransform
            viewModel.measureLines.forEach { m ->
                drawMeasureLine(m, t2, Color(0xFF00E5FF))
            }
            viewModel.activeMeasure?.let { m ->
                drawMeasureLine(m, t2, Color(0xFFFFD740), active = true)
            }
        }
    }
}

// ─── Wire corner calculation ──────────────────────────────────────────────────

private fun wireCorner(start: Offset, end: Offset, mode: WireMode): Offset {
    return when (mode) {
        WireMode.ORTHOGONAL -> {
            val dx = end.x - start.x; val dy = end.y - start.y
            if (abs(dx) > abs(dy)) Offset(end.x, start.y) else Offset(start.x, end.y)
        }
        WireMode.FORTY_FIVE -> {
            val dx = end.x - start.x; val dy = end.y - start.y
            if (abs(dx) > abs(dy)) Offset(start.x + dx - dy, end.y)
            else Offset(end.x, start.y + dy - dx)
        }
        WireMode.FREE -> end
    }
}

// ─── Draw helpers ─────────────────────────────────────────────────────────────

fun DrawScope.drawSchWire(wire: SchematicWire, sel: Boolean) {
    val color = if (sel) Color(0xFFFFD600) else Color(0xFF4CAF50)
    drawLine(color, Offset(wire.start.x, wire.start.y), Offset(wire.end.x, wire.end.y),
        if (sel) 2.5f else 1.5f, cap = StrokeCap.Round)
    // Endpoints
    drawCircle(color, if(sel) 4f else 2f, Offset(wire.start.x, wire.start.y))
    drawCircle(color, if(sel) 4f else 2f, Offset(wire.end.x,   wire.end.y))
}

fun DrawScope.drawBusLine(bus: BusLine, sel: Boolean) {
    if (bus.points.size < 2) return
    val color = if (sel) Color(0xFFFFD600) else Color(0xFF2196F3)
    val path = Path().apply {
        moveTo(bus.points[0].x, bus.points[0].y)
        for (i in 1 until bus.points.size) lineTo(bus.points[i].x, bus.points[i].y)
    }
    drawPath(path, color, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

fun DrawScope.drawJunction(j: SchematicJunction) {
    drawCircle(Color(0xFF4CAF50), 8f, Offset(j.position.x, j.position.y))
    drawCircle(Color(0xFF81C784), 4f, Offset(j.position.x, j.position.y))
}

fun DrawScope.drawNoConnect(nc: NoConnect) {
    val c = Offset(nc.position.x, nc.position.y); val s = 9f
    drawLine(Color(0xFF2196F3), c + Offset(-s,-s), c + Offset(s,s), 2f)
    drawLine(Color(0xFF2196F3), c + Offset(s,-s),  c + Offset(-s,s), 2f)
}

fun DrawScope.drawPowerSym(ps: PowerSymbol, sel: Boolean) {
    val c = Offset(ps.position.x, ps.position.y)
    val color = if (sel) Color(0xFFFFD600) else Color(0xFFFF5722)
    when (ps.symbolType) {
        PowerType.GND -> {
            drawLine(color, c, c + Offset(0f,30f), 2f)
            drawLine(color, c + Offset(-22f,30f), c + Offset(22f,30f), 2f)
            drawLine(color, c + Offset(-14f,39f), c + Offset(14f,39f), 2f)
            drawLine(color, c + Offset(-6f,48f),  c + Offset(6f,48f),  2f)
        }
        PowerType.VCC -> {
            drawLine(color, c, c + Offset(0f,-30f), 2f)
            drawLine(color, c + Offset(-22f,-30f), c + Offset(22f,-30f), 4f)
        }
        else -> {
            drawCircle(color, 16f, c, style = Stroke(2f))
            drawLine(color, c + Offset(0f,-16f), c + Offset(0f,-32f), 2f)
        }
    }
    // Net name
    nativeText(ps.netName, c.x - 20f, c.y + 62f,
        if(sel) "#FFD600" else "#FF5722", 12f)
}

fun DrawScope.drawNetLabel(lbl: SchematicLabel, sel: Boolean) {
    val c     = Offset(lbl.position.x, lbl.position.y)
    val color = if (sel) Color(0xFFFFD600) else when (lbl.labelType) {
        LabelType.GLOBAL       -> Color(0xFFFF9800)
        LabelType.HIERARCHICAL -> Color(0xFF9C27B0)
        else                   -> Color(0xFF4FC3F7)
    }
    val tw = lbl.text.length * 6.5f + 10f
    val path = Path().apply {
        moveTo(c.x, c.y)
        lineTo(c.x + tw, c.y)
        lineTo(c.x + tw + 10f, c.y - 11f)
        lineTo(c.x + tw, c.y - 22f)
        lineTo(c.x, c.y - 22f)
        close()
    }
    drawPath(path, color.copy(alpha=.15f))
    drawPath(path, color, style = Stroke(1.5f))
    nativeText(lbl.text, c.x + 4f, c.y - 5f, if(sel) "#FFD600" else "#4FC3F7", 13f)
}

fun DrawScope.drawSelectionBox(el: SchematicElement) {
    val pad = 18f
    val c = Offset(el.position.x, el.position.y)
    drawRect(Color(0xFFFFD600).copy(alpha=.08f),
        c + Offset(-pad,-pad), androidx.compose.ui.geometry.Size(pad*2, pad*2))
    drawRect(Color(0xFFFFD600).copy(alpha=.6f),
        c + Offset(-pad,-pad), androidx.compose.ui.geometry.Size(pad*2, pad*2),
        style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f,3f))))
}

fun DrawScope.drawPendingGhost(pos: PcbPoint) {
    val c = Offset(pos.x, pos.y)
    drawCircle(Color(0xFF00D4FF).copy(alpha=.3f), 25f, c)
    drawCircle(Color(0xFF00D4FF).copy(alpha=.7f), 25f, c, style = Stroke(1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f,3f))))
    drawLine(Color(0xFF00D4FF), c + Offset(-20f,0f), c + Offset(20f,0f), 1f)
    drawLine(Color(0xFF00D4FF), c + Offset(0f,-20f), c + Offset(0f,20f), 1f)
}

fun DrawScope.drawMeasureLine(m: MeasureLine, t: CanvasTransform, color: Color, active: Boolean = false) {
    val s = t.worldToScreen(m.start)
    val e = t.worldToScreen(m.end)
    drawLine(color, s, e, if(active) 2f else 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f,4f)))
    val tick = 10f
    val angle = atan2(e.y-s.y, e.x-s.x)
    val perp = angle + PI.toFloat()/2
    for (pt in listOf(s,e)) {
        drawLine(color, pt + Offset(cos(perp)*tick, sin(perp)*tick),
                         pt - Offset(cos(perp)*tick, sin(perp)*tick), 1.5f)
    }
    if (m.inMm > 0f) {
        val mid = Offset((s.x+e.x)/2, (s.y+e.y)/2)
        nativeTextScreen(String.format("%.3f mm", m.inMm), mid, color)
    }
}

private fun DrawScope.nativeTextScreen(text: String, pos: Offset, color: Color) {
    drawContext.canvas.nativeCanvas.apply {
        val bg = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(180,13,27,42)
        }
        val tp = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(
                ((color.alpha*255).toInt()), (color.red*255).toInt(),
                (color.green*255).toInt(),(color.blue*255).toInt())
            textSize = 14f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE
        }
        val tw = tp.measureText(text)
        drawRoundRect(pos.x-tw/2-4, pos.y-16f, pos.x+tw/2+4, pos.y+4f, 4f,4f, bg)
        drawText(text, pos.x-tw/2, pos.y, tp)
    }
}

fun DrawScope.nativeText(text: String, x: Float, y: Float, hexColor: String, size: Float) {
    drawContext.canvas.nativeCanvas.drawText(text, x, y,
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor(hexColor)
            textSize = size; isAntiAlias = true
        })
}

package com.pcbdroid.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.pcbdroid.data.model.*
import kotlin.math.*

/**
 * Menggambar simbol IEEE standar untuk setiap tipe komponen.
 * Dipanggil dari SchematicCanvas dalam withTransform (world space).
 */
object SymbolRenderer {

    fun DrawScope.drawSymbol(comp: SchematicComponent, selected: Boolean) {
        val c = Offset(comp.position.x, comp.position.y)
        val bd = if (selected) Color(0xFFFFD600) else Color(0xFFE0E0E0)
        val pn = if (selected) Color(0xFFFFD600) else Color(0xFF78909C)

        withTransform({ translate(c.x, c.y); rotate(comp.rotation, Offset.Zero) }) {
            val sid = comp.symbolId.lowercase()
            when {
                sid.contains(":r") && !sid.contains("led") -> drawResistor(bd, pn)
                sid.contains(":c") -> drawCapacitor(bd, pn)
                sid.contains(":l") -> drawInductor(bd, pn)
                sid.contains("led") -> drawLED(bd, pn)
                sid.contains(":d")  -> drawDiode(bd, pn)
                sid.contains("nmos") || sid.contains("pmos") -> drawMosfet(bd, pn)
                sid.contains(":q")  -> drawBJT(bd, pn)
                sid.contains("sw_push") || sid.contains(":sw") -> drawSwitch(bd, pn)
                sid.contains("crystal") -> drawCrystal(bd, pn)
                else -> drawICBox(comp, bd, pn)
            }
            // Labels
            nativeText(comp.reference, -28f, -28f, if (selected) "#FFD600" else "#80CBC4", 13f)
            nativeText(comp.value,     -28f,  40f, if (selected) "#FFD600" else "#BDBDBD", 12f)
        }
    }

    // ── Resistor ──────────────────────────────────────────────────────────────
    private fun DrawScope.drawResistor(b: Color, p: Color) {
        drawLine(p, Offset(-50f,0f), Offset(-20f,0f), 2f)
        drawLine(p, Offset(20f,0f),  Offset(50f,0f),  2f)
        drawRect(b.copy(alpha=.18f), Offset(-20f,-10f), Size(40f,20f))
        drawRect(b, Offset(-20f,-10f), Size(40f,20f), style=Stroke(1.8f))
    }

    // ── Capacitor ─────────────────────────────────────────────────────────────
    private fun DrawScope.drawCapacitor(b: Color, p: Color) {
        drawLine(p, Offset(-50f,0f), Offset(-5f,0f), 2f)
        drawLine(p, Offset(5f,0f),   Offset(50f,0f), 2f)
        drawLine(b, Offset(-5f,-20f), Offset(-5f,20f), 4f)
        drawLine(b, Offset(5f,-20f),  Offset(5f,20f),  4f)
    }

    // ── Inductor ──────────────────────────────────────────────────────────────
    private fun DrawScope.drawInductor(b: Color, p: Color) {
        drawLine(p, Offset(-50f,0f), Offset(-30f,0f), 2f)
        drawLine(p, Offset(30f,0f),  Offset(50f,0f),  2f)
        for (i in 0..3) {
            val cx = -22f + i*15f
            drawArc(b, 0f, 180f, false,
                topLeft=Offset(cx-7.5f,-10f), size=Size(15f,15f), style=Stroke(2f))
        }
    }

    // ── LED ───────────────────────────────────────────────────────────────────
    private fun DrawScope.drawLED(b: Color, p: Color) {
        drawLine(p, Offset(-50f,0f), Offset(-15f,0f), 2f)
        drawLine(p, Offset(15f,0f),  Offset(50f,0f),  2f)
        val tri = Path().apply { moveTo(-15f,-15f); lineTo(-15f,15f); lineTo(15f,0f); close() }
        drawPath(tri, b.copy(alpha=.2f)); drawPath(tri, b, style=Stroke(2f))
        drawLine(b, Offset(15f,-15f), Offset(15f,15f), 2.5f)
        // light rays
        drawLine(Color(0xFFFFFF44), Offset(8f,-16f), Offset(18f,-26f), 1.5f)
        drawLine(Color(0xFFFFFF44), Offset(14f,-12f), Offset(24f,-22f), 1.5f)
    }

    // ── Diode ─────────────────────────────────────────────────────────────────
    private fun DrawScope.drawDiode(b: Color, p: Color) {
        drawLine(p, Offset(-50f,0f), Offset(-15f,0f), 2f)
        drawLine(p, Offset(15f,0f),  Offset(50f,0f),  2f)
        val tri = Path().apply { moveTo(-15f,-15f); lineTo(-15f,15f); lineTo(15f,0f); close() }
        drawPath(tri, b.copy(alpha=.2f)); drawPath(tri, b, style=Stroke(2f))
        drawLine(b, Offset(15f,-15f), Offset(15f,15f), 2.5f)
    }

    // ── MOSFET ────────────────────────────────────────────────────────────────
    private fun DrawScope.drawMosfet(b: Color, p: Color) {
        drawCircle(b.copy(alpha=.1f), 25f, Offset.Zero)
        drawCircle(b, 25f, Offset.Zero, style=Stroke(2f))
        drawLine(p, Offset(-50f,0f), Offset(-20f,0f), 2f)  // gate
        drawLine(b, Offset(-20f,-20f), Offset(-20f,20f), 3f)
        drawLine(b, Offset(-20f,-12f), Offset(0f,-12f), 2f)  // D
        drawLine(b, Offset(-20f, 12f), Offset(0f, 12f), 2f)  // S
        drawLine(b, Offset(0f,-25f), Offset(0f,25f), 2f)
        drawLine(p, Offset(0f,-12f), Offset(50f,-12f), 2f)
        drawLine(p, Offset(0f, 12f), Offset(50f, 12f), 2f)
        // arrow
        val arr = Path().apply { moveTo(0f,0f); lineTo(-8f,-6f); lineTo(-8f,6f); close() }
        drawPath(arr, b)
    }

    // ── BJT NPN ───────────────────────────────────────────────────────────────
    private fun DrawScope.drawBJT(b: Color, p: Color) {
        drawCircle(b.copy(alpha=.1f), 25f, Offset.Zero)
        drawCircle(b, 25f, Offset.Zero, style=Stroke(2f))
        drawLine(p, Offset(-50f,0f), Offset(-15f,0f), 2f)  // base
        drawLine(b, Offset(-15f,-22f), Offset(-15f,22f), 3f)
        drawLine(b, Offset(-15f,-15f), Offset(20f,-30f), 2f)
        drawLine(p, Offset(20f,-30f),  Offset(50f,-30f), 2f)
        drawLine(b, Offset(-15f,15f),  Offset(20f,30f),  2f)
        drawLine(p, Offset(20f,30f),   Offset(50f,30f),  2f)
        // emitter arrow
        val arr = Path().apply { moveTo(14f,26f); lineTo(22f,30f); lineTo(16f,38f) }
        drawPath(arr, b, style=Stroke(2f))
    }

    // ── Switch ────────────────────────────────────────────────────────────────
    private fun DrawScope.drawSwitch(b: Color, p: Color) {
        drawLine(p, Offset(-50f,0f), Offset(-20f,0f), 2f)
        drawLine(p, Offset(20f,0f),  Offset(50f,0f),  2f)
        drawCircle(b, 4f, Offset(-20f,0f))
        drawCircle(b, 4f, Offset(20f,0f))
        drawLine(b, Offset(-20f,0f), Offset(18f,-18f), 2f)
    }

    // ── Crystal ───────────────────────────────────────────────────────────────
    private fun DrawScope.drawCrystal(b: Color, p: Color) {
        drawLine(p, Offset(-50f,0f), Offset(-15f,0f), 2f)
        drawLine(p, Offset(15f,0f),  Offset(50f,0f),  2f)
        drawRect(b.copy(alpha=.2f), Offset(-15f,-18f), Size(30f,36f))
        drawRect(b, Offset(-15f,-18f), Size(30f,36f), style=Stroke(2f))
        drawLine(b, Offset(-7f,-22f), Offset(-7f,22f), 3f)
        drawLine(b, Offset(7f,-22f),  Offset(7f,22f),  3f)
    }

    // ── Generic IC box ────────────────────────────────────────────────────────
    private fun DrawScope.drawICBox(comp: SchematicComponent, b: Color, p: Color) {
        val leftPins  = comp.pins.filter { it.direction == PinDirection.LEFT  || it.direction == PinDirection.RIGHT && it.position.x < 0 }
        val rightPins = comp.pins.filter { it.direction == PinDirection.RIGHT || it.direction == PinDirection.LEFT  && it.position.x > 0 }
        val maxSide   = maxOf(leftPins.size, rightPins.size, 1)
        val h = maxOf(50f, (maxSide + 1) * 24f)
        val w = 70f

        drawRect(b.copy(alpha=.12f), Offset(-w/2,-h/2), Size(w,h))
        drawRect(b, Offset(-w/2,-h/2), Size(w,h), style=Stroke(1.8f))

        leftPins.forEachIndexed { i, pin ->
            val py = -h/2 + 20f + i * 24f
            drawLine(p, Offset(-w/2-20f, py), Offset(-w/2, py), 1.5f)
            nativeText(pin.name, -w/2+4f, py+4f, "#78909C", 9f)
        }
        rightPins.forEachIndexed { i, pin ->
            val py = -h/2 + 20f + i * 24f
            drawLine(p, Offset(w/2, py), Offset(w/2+20f, py), 1.5f)
            nativeText(pin.name, w/2-4f - pin.name.length*5.5f, py+4f, "#78909C", 9f)
        }
        // IC name centre
        nativeText(comp.symbolId.substringAfterLast(":").take(10),
            -w/2+4f, 5f, "#546E7A", 10f)
    }

    private fun DrawScope.nativeText(text: String, x: Float, y: Float, hexColor: String, size: Float) {
        drawContext.canvas.nativeCanvas.drawText(
            text, x, y,
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor(hexColor)
                textSize = size; isAntiAlias = true
            }
        )
    }
}

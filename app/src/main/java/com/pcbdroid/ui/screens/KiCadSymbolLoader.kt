package com.pcbdroid.ui.screens

import android.util.Log
import com.pcbdroid.data.model.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

// ─── S-Expression Parser ──────────────────────────────────────────────────────

private sealed class SX {
    data class Atom(val v: String) : SX()
    data class Node(val tag: String, val ch: List<SX>) : SX()
}

private class SXParser(private val src: String) {
    private var pos = 0

    fun parse(): SX {
        skip()
        return if (pos < src.length && src[pos] == '(') node() else SX.Atom(token())
    }

    private fun node(): SX {
        expect('('); skip()
        val tag = token(); skip()
        val ch = mutableListOf<SX>()
        while (pos < src.length && src[pos] != ')') {
            ch.add(if (src[pos] == '(') node() else SX.Atom(token()))
            skip()
        }
        if (pos < src.length) pos++
        return SX.Node(tag, ch)
    }

    private fun token(): String {
        if (pos < src.length && src[pos] == '"') {
            pos++; val sb = StringBuilder()
            while (pos < src.length && src[pos] != '"') {
                if (src[pos] == '\\') pos++
                sb.append(src[pos++])
            }
            if (pos < src.length) pos++
            return sb.toString()
        }
        val s = pos
        while (pos < src.length && !src[pos].isWhitespace() && src[pos] != '(' && src[pos] != ')') pos++
        return src.substring(s, pos)
    }

    private fun skip() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
        if (pos + 1 < src.length && src[pos] == ';') { while (pos < src.length && src[pos] != '\n') pos++ }
    }
    private fun expect(c: Char) { if (pos < src.length && src[pos] == c) pos++ }
}

private fun SX.Node.child(name: String): SX.Node? = ch.filterIsInstance<SX.Node>().firstOrNull { it.tag == name }
private fun SX.Node.children(name: String): List<SX.Node> = ch.filterIsInstance<SX.Node>().filter { it.tag == name }
private fun SX.Node.atom(i: Int): String = ch.filterIsInstance<SX.Atom>().getOrNull(i)?.v ?: ""
private fun SX.Node.float(i: Int): Float = atom(i).toFloatOrNull() ?: 0f

// ─── KiCad Symbol Loader ──────────────────────────────────────────────────────

data class ParsedSymbol(
    val id: String,
    val pins: List<ComponentPin>,
    val graphics: List<SymbolGraphic>
)

sealed class SymbolGraphic {
    data class Line(val pts: List<PcbPoint>, val filled: Boolean = false) : SymbolGraphic()
    data class Rect(val start: PcbPoint, val end: PcbPoint, val filled: Boolean = false) : SymbolGraphic()
    data class Circle(val center: PcbPoint, val radius: Float, val filled: Boolean = false) : SymbolGraphic()
    data class Arc(val center: PcbPoint, val radius: Float, val startAngle: Float, val endAngle: Float) : SymbolGraphic()
    data class Poly(val pts: List<PcbPoint>, val filled: Boolean = false) : SymbolGraphic()
}

object KiCadSymbolLoader {

    private val cache = mutableMapOf<String, ParsedSymbol>()

    suspend fun loadFromFile(file: File): Map<String, ParsedSymbol> = withContext(Dispatchers.IO) {
        try {
            val src = file.readText()
            val root = SXParser(src).parse() as? SX.Node ?: return@withContext emptyMap()
            parseLibrary(root)
        } catch (e: Exception) {
            Log.w("KiCadLoader", "Failed: ${e.message}")
            emptyMap()
        }
    }

    private fun parseLibrary(root: SX.Node): Map<String, ParsedSymbol> {
        val result = mutableMapOf<String, ParsedSymbol>()
        root.children("symbol").forEach { sym ->
            val parsed = parseSymbol(sym) ?: return@forEach
            result[parsed.id] = parsed
            cache[parsed.id] = parsed
        }
        return result
    }

    private fun parseSymbol(node: SX.Node): ParsedSymbol? {
        val id = node.atom(0).ifBlank { return null }
        val pins = mutableListOf<ComponentPin>()
        val graphics = mutableListOf<SymbolGraphic>()

        // Recurse into sub-symbols (KiCad 6 format: symbol_1_1 etc.)
        node.children("symbol").forEach { sub ->
            sub.children("pin").forEach { pins.add(parsePin(it) ?: return@forEach) }
            extractGraphics(sub, graphics)
        }
        node.children("pin").forEach { pins.add(parsePin(it) ?: return@forEach) }
        extractGraphics(node, graphics)

        return ParsedSymbol(id, pins, graphics)
    }

    private fun extractGraphics(node: SX.Node, out: MutableList<SymbolGraphic>) {
        node.children("polyline").forEach { pl ->
            val pts = pl.child("pts")?.children("xy")?.map { xy ->
                PcbPoint(xy.float(0) * 1000f, xy.float(1) * 1000f)
            } ?: return@forEach
            val filled = pl.child("fill")?.child("type")?.atom(0) == "background"
            out.add(SymbolGraphic.Poly(pts, filled))
        }
        node.children("rectangle").forEach { r ->
            val s = r.child("start") ?: return@forEach
            val e = r.child("end")   ?: return@forEach
            val filled = r.child("fill")?.child("type")?.atom(0) == "background"
            out.add(SymbolGraphic.Rect(
                PcbPoint(s.float(0)*1000f, s.float(1)*1000f),
                PcbPoint(e.float(0)*1000f, e.float(1)*1000f), filled))
        }
        node.children("circle").forEach { c ->
            val ctr = c.child("center") ?: return@forEach
            val r = c.child("radius")?.float(0)?.times(1000f) ?: return@forEach
            val filled = c.child("fill")?.child("type")?.atom(0) == "background"
            out.add(SymbolGraphic.Circle(PcbPoint(ctr.float(0)*1000f, ctr.float(1)*1000f), r, filled))
        }
        node.children("arc").forEach { a ->
            val ctr = a.child("center") ?: return@forEach
            val r = a.child("radius")?.float(0)?.times(1000f) ?: return@forEach
            val sa = a.child("start_angle")?.float(0) ?: 0f
            val ea = a.child("end_angle")?.float(0)   ?: 360f
            out.add(SymbolGraphic.Arc(PcbPoint(ctr.float(0)*1000f, ctr.float(1)*1000f), r, sa, ea))
        }
    }

    private fun parsePin(node: SX.Node): ComponentPin? {
        val typeStr = node.atom(0)
        val styleStr = node.atom(1)
        val at = node.child("at") ?: return null
        val nameNode = node.child("name")
        val numNode  = node.child("number")
        val name   = nameNode?.atom(0) ?: "~"
        val number = numNode?.atom(0)  ?: "0"
        val x = at.float(0) * 1000f
        val y = at.float(1) * 1000f
        val angle = at.float(2)
        val dir = when (Math.round(angle) % 360) {
            0    -> PinDirection.RIGHT
            90   -> PinDirection.UP
            180  -> PinDirection.LEFT
            270  -> PinDirection.DOWN
            else -> PinDirection.RIGHT
        }
        val type = when (typeStr) {
            "input"         -> PinType.INPUT
            "output"        -> PinType.OUTPUT
            "bidirectional" -> PinType.BIDIRECTIONAL
            "power_in"      -> PinType.POWER_IN
            "power_out"     -> PinType.POWER_OUT
            "passive"       -> PinType.PASSIVE
            "no_connect"    -> PinType.NOT_CONNECTED
            else            -> PinType.PASSIVE
        }
        return ComponentPin(number, name, PcbPoint(x, y), dir, type)
    }

    fun get(id: String): ParsedSymbol? = cache[id]
}

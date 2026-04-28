package com.pcbdroid.domain.import

import com.pcbdroid.data.model.*
import java.io.*
import java.util.UUID

// ─── KiCad File Importer ──────────────────────────────────────────────────────
// Supports KiCad 6/7/8 S-expression format (.kicad_sch, .kicad_pcb, .kicad_sym)

object KiCadImporter {

    data class ImportResult(
        val project: PcbProject?,
        val errors: List<String>,
        val warnings: List<String>
    )

    // ── Main entry point ──────────────────────────────────────────────────────

    fun importProject(directory: java.io.File): ImportResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val proFile = directory.listFiles()?.firstOrNull { it.extension == "kicad_pro" }
        val schFile = directory.listFiles()?.firstOrNull { it.extension == "kicad_sch" }
        val pcbFile = directory.listFiles()?.firstOrNull { it.extension == "kicad_pcb" }

        if (proFile == null && schFile == null && pcbFile == null) {
            return ImportResult(null, listOf("No KiCad files found in directory"), warnings)
        }

        try {
            val projectName = proFile?.nameWithoutExtension
                ?: schFile?.nameWithoutExtension
                ?: pcbFile?.nameWithoutExtension
                ?: "Imported"

            val schematic = schFile?.let { parseSchematic(it, warnings) } ?: SchematicSheet()
            val board = pcbFile?.let { parsePcbLayout(it, warnings) } ?: PcbBoard()

            val project = PcbProject(
                id = UUID.randomUUID().toString(),
                name = projectName,
                schematic = schematic,
                board = board
            )

            return ImportResult(project, errors, warnings)
        } catch (e: Exception) {
            errors.add("Import failed: ${e.message}")
            return ImportResult(null, errors, warnings)
        }
    }

    // ── Schematic Parser ──────────────────────────────────────────────────────

    private fun parseSchematic(file: java.io.File, warnings: MutableList<String>): SchematicSheet {
        val sheet = SchematicSheet()
        val content = file.readText()
        val tokens = SExprTokenizer(content)
        val root = tokens.parse()

        root.children.forEach { node ->
            when (node.name) {
                "wire" -> parseWire(node)?.let { sheet.elements.add(it) }
                "symbol" -> parseSchematicComponent(node)?.let { sheet.elements.add(it) }
                "label", "global_label", "hierarchical_label" ->
                    parseLabel(node)?.let { sheet.elements.add(it) }
                "junction" -> parseJunction(node)?.let { sheet.elements.add(it) }
                "no_connect" -> parseNoConnect(node)?.let { sheet.elements.add(it) }
                "bus" -> parseBus(node)?.let { sheet.elements.add(it) }
                "power_symbol" -> parsePowerSymbol(node)?.let { sheet.elements.add(it) }
            }
        }
        return sheet
    }

    private fun parseWire(node: SExprNode): SchematicWire? {
        val pts = node.findChild("pts") ?: return null
        val xyNodes = pts.children.filter { it.name == "xy" }
        if (xyNodes.size < 2) return null
        val start = PcbPoint(xyNodes[0].floatAt(0), xyNodes[0].floatAt(1))
        val end = PcbPoint(xyNodes[1].floatAt(0), xyNodes[1].floatAt(1))
        return SchematicWire(
            id = UUID.randomUUID().toString(),
            position = start,
            start = start * 1000f,  // mm → mils (1 mm = 39.37 mils, KiCad uses mm)
            end = end * 1000f
        )
    }

    private fun parseSchematicComponent(node: SExprNode): SchematicComponent? {
        val ref = node.findChild("property")?.let { prop ->
            if (prop.stringAt(0) == "Reference") prop.stringAt(1) else null
        } ?: node.stringAt(0)

        val atNode = node.findChild("at") ?: return null
        val x = atNode.floatAt(0) * 1000f
        val y = atNode.floatAt(1) * 1000f
        val rotation = atNode.floatAt(2)

        val value = node.children.firstOrNull { it.name == "property" && it.stringAt(0) == "Value" }
            ?.stringAt(1) ?: "?"
        val footprint = node.children.firstOrNull { it.name == "property" && it.stringAt(0) == "Footprint" }
            ?.stringAt(1) ?: ""

        return SchematicComponent(
            id = UUID.randomUUID().toString(),
            position = PcbPoint(x, y),
            reference = ref ?: "?",
            value = value,
            symbolId = node.stringAt(0) ?: "",
            footprintId = footprint,
            rotation = rotation
        )
    }

    private fun parseLabel(node: SExprNode): SchematicLabel? {
        val text = node.stringAt(0) ?: return null
        val atNode = node.findChild("at") ?: return null
        val pos = PcbPoint(atNode.floatAt(0) * 1000f, atNode.floatAt(1) * 1000f)
        val type = when (node.name) {
            "global_label" -> LabelType.GLOBAL
            "hierarchical_label" -> LabelType.HIERARCHICAL
            else -> LabelType.LOCAL
        }
        return SchematicLabel(UUID.randomUUID().toString(), pos, text, labelType = type)
    }

    private fun parseJunction(node: SExprNode): SchematicJunction? {
        val atNode = node.findChild("at") ?: return null
        val pos = PcbPoint(atNode.floatAt(0) * 1000f, atNode.floatAt(1) * 1000f)
        return SchematicJunction(UUID.randomUUID().toString(), pos)
    }

    private fun parseNoConnect(node: SExprNode): NoConnect? {
        val atNode = node.findChild("at") ?: return null
        val pos = PcbPoint(atNode.floatAt(0) * 1000f, atNode.floatAt(1) * 1000f)
        return NoConnect(UUID.randomUUID().toString(), pos)
    }

    private fun parseBus(node: SExprNode): BusLine? {
        val pts = node.findChild("pts") ?: return null
        val points = pts.children.filter { it.name == "xy" }.map { xy ->
            PcbPoint(xy.floatAt(0) * 1000f, xy.floatAt(1) * 1000f)
        }
        if (points.size < 2) return null
        return BusLine(UUID.randomUUID().toString(), points[0], points)
    }

    private fun parsePowerSymbol(node: SExprNode): PowerSymbol? {
        val atNode = node.findChild("at") ?: return null
        val pos = PcbPoint(atNode.floatAt(0) * 1000f, atNode.floatAt(1) * 1000f)
        val netName = node.findChild("lib_id")?.stringAt(0)?.substringAfterLast(":") ?: "GND"
        val type = when {
            netName.contains("GND", true) -> PowerType.GND
            netName.contains("VCC", true) || netName.contains("VDD", true) -> PowerType.VCC
            else -> PowerType.CUSTOM
        }
        return PowerSymbol(UUID.randomUUID().toString(), pos, netName, type)
    }

    // ── PCB Layout Parser ─────────────────────────────────────────────────────

    private fun parsePcbLayout(file: java.io.File, warnings: MutableList<String>): PcbBoard {
        val board = PcbBoard()
        val content = file.readText()
        val tokens = SExprTokenizer(content)
        val root = tokens.parse()

        root.children.forEach { node ->
            when (node.name) {
                "segment" -> parseTrack(node)?.let { board.elements.add(it) }
                "via" -> parseVia(node)?.let { board.elements.add(it) }
                "footprint" -> parseFootprint(node)?.let { board.elements.add(it) }
                "zone" -> parseZone(node)?.let { board.elements.add(it) }
                "gr_line" -> parseBoardLine(node)?.let { board.elements.add(it) }
                "gr_rect" -> parseBoardRect(node, board)
            }
        }
        return board
    }

    private fun parseTrack(node: SExprNode): Track? {
        val start = node.findChild("start") ?: return null
        val end = node.findChild("end") ?: return null
        val width = node.findChild("width")?.floatAt(0) ?: 0.25f
        val layerName = node.findChild("layer")?.stringAt(0) ?: "F.Cu"
        val netId = node.findChild("net")?.intAt(0) ?: -1
        val layer = layerFromName(layerName)

        val s = PcbPoint(start.floatAt(0) * 1000f, start.floatAt(1) * 1000f)
        val e = PcbPoint(end.floatAt(0) * 1000f, end.floatAt(1) * 1000f)
        return Track(UUID.randomUUID().toString(), s, layer, s, e, width, netId)
    }

    private fun parseVia(node: SExprNode): Via? {
        val at = node.findChild("at") ?: return null
        val size = node.findChild("size")?.floatAt(0) ?: 0.8f
        val drill = node.findChild("drill")?.floatAt(0) ?: 0.4f
        val netId = node.findChild("net")?.intAt(0) ?: -1
        val pos = PcbPoint(at.floatAt(0) * 1000f, at.floatAt(1) * 1000f)
        return Via(UUID.randomUUID().toString(), pos, size = size, drill = drill, netId = netId)
    }

    private fun parseFootprint(node: SExprNode): Footprint? {
        val fpId = node.stringAt(0) ?: ""
        val at = node.findChild("at") ?: return null
        val pos = PcbPoint(at.floatAt(0) * 1000f, at.floatAt(1) * 1000f)
        val rotation = at.floatAt(2)
        val layerName = node.findChild("layer")?.stringAt(0) ?: "F.Cu"

        val pads = node.children.filter { it.name == "pad" }.mapNotNull { padNode ->
            parsePad(padNode)
        }
        val ref = node.children.firstOrNull {
            it.name == "fp_text" && it.stringAt(0) == "reference"
        }?.stringAt(1) ?: fpId.substringAfterLast(":")

        return Footprint(
            id = UUID.randomUUID().toString(),
            position = pos,
            layer = layerFromName(layerName),
            reference = ref,
            value = fpId,
            footprintId = fpId,
            rotation = rotation,
            pads = pads
        )
    }

    private fun parsePad(node: SExprNode): Pad? {
        val number = node.stringAt(0) ?: return null
        val typeStr = node.stringAt(1) ?: "smd"
        val shapeStr = node.stringAt(2) ?: "circle"
        val at = node.findChild("at") ?: return null
        val sizeNode = node.findChild("size") ?: return null
        val drill = node.findChild("drill")?.floatAt(0) ?: 0f
        val netId = node.findChild("net")?.intAt(0) ?: -1

        val pos = PcbPoint(at.floatAt(0) * 1000f, at.floatAt(1) * 1000f)
        val size = PcbPoint(sizeNode.floatAt(0), sizeNode.floatAt(1))
        val padType = when (typeStr) {
            "thru_hole" -> PadType.THROUGH_HOLE
            "np_thru_hole" -> PadType.NPTH
            else -> PadType.SMD
        }
        val padShape = when (shapeStr) {
            "rect" -> PadShape.RECT
            "oval" -> PadShape.OVAL
            "roundrect" -> PadShape.ROUNDRECT
            else -> PadShape.ROUND
        }
        return Pad(number, pos, size, padShape, padType, drill, netId)
    }

    private fun parseZone(node: SExprNode): Zone? {
        val layerName = node.findChild("layer")?.stringAt(0) ?: return null
        val netId = node.findChild("net")?.intAt(0) ?: -1
        val polygon = node.findChild("filled_polygon")?.findChild("pts") ?: return null
        val points = polygon.children.filter { it.name == "xy" }.map { xy ->
            PcbPoint(xy.floatAt(0) * 1000f, xy.floatAt(1) * 1000f)
        }
        if (points.size < 3) return null
        return Zone(UUID.randomUUID().toString(), points[0], layerFromName(layerName), points, netId)
    }

    private fun parseBoardLine(node: SExprNode): PcbElement? {
        val start = node.findChild("start") ?: return null
        val end = node.findChild("end") ?: return null
        val layerName = node.findChild("layer")?.stringAt(0) ?: return null
        val layer = layerFromName(layerName)
        val s = PcbPoint(start.floatAt(0) * 1000f, start.floatAt(1) * 1000f)
        val e = PcbPoint(end.floatAt(0) * 1000f, end.floatAt(1) * 1000f)
        if (layer == PcbLayer.EDGE_CUTS) {
            return BoardOutline(UUID.randomUUID().toString(), s, PcbLayer.EDGE_CUTS, listOf(s, e))
        }
        return Track(UUID.randomUUID().toString(), s, layer, s, e, 0.1f, -1)
    }

    private fun parseBoardRect(node: SExprNode, board: PcbBoard) {
        val start = node.findChild("start") ?: return
        val end = node.findChild("end") ?: return
        val layerName = node.findChild("layer")?.stringAt(0) ?: return
        if (layerFromName(layerName) != PcbLayer.EDGE_CUTS) return
        val s = PcbPoint(start.floatAt(0) * 1000f, start.floatAt(1) * 1000f)
        val e = PcbPoint(end.floatAt(0) * 1000f, end.floatAt(1) * 1000f)
        board.elements.add(BoardOutline(UUID.randomUUID().toString(), s, PcbLayer.EDGE_CUTS,
            listOf(s, PcbPoint(e.x, s.y), e, PcbPoint(s.x, e.y), s)
        ))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun layerFromName(name: String): PcbLayer = when (name) {
        "F.Cu" -> PcbLayer.F_CU
        "B.Cu" -> PcbLayer.B_CU
        "F.SilkS", "F.Silkscreen" -> PcbLayer.F_SILKSCREEN
        "B.SilkS", "B.Silkscreen" -> PcbLayer.B_SILKSCREEN
        "F.CrtYd", "F.Courtyard" -> PcbLayer.F_COURTYARD
        "B.CrtYd", "B.Courtyard" -> PcbLayer.B_COURTYARD
        "Edge.Cuts" -> PcbLayer.EDGE_CUTS
        "In1.Cu" -> PcbLayer.IN1_CU
        "In2.Cu" -> PcbLayer.IN2_CU
        else -> PcbLayer.F_CU
    }

    private operator fun PcbPoint.times(f: Float) = PcbPoint(x * f, y * f)
}

// ─── S-Expression Tokenizer ───────────────────────────────────────────────────
// Parses KiCad S-expression format: (key value (child ...) ...)

data class SExprNode(
    val name: String,
    val atoms: List<String> = emptyList(),
    val children: List<SExprNode> = emptyList()
) {
    fun findChild(name: String): SExprNode? = children.firstOrNull { it.name == name }
    fun stringAt(i: Int): String? = atoms.getOrNull(i)
    fun floatAt(i: Int): Float = atoms.getOrNull(i)?.toFloatOrNull() ?: 0f
    fun intAt(i: Int): Int = atoms.getOrNull(i)?.toIntOrNull() ?: 0
}

class SExprTokenizer(private val content: String) {
    private var pos = 0

    fun parse(): SExprNode {
        skipWhitespace()
        return if (pos < content.length && content[pos] == '(') parseNode()
        else SExprNode("root", children = parseChildren())
    }

    private fun parseNode(): SExprNode {
        expect('(')
        skipWhitespace()
        val name = readToken()
        val atoms = mutableListOf<String>()
        val children = mutableListOf<SExprNode>()
        while (pos < content.length && content[pos] != ')') {
            skipWhitespace()
            if (pos >= content.length || content[pos] == ')') break
            if (content[pos] == '(') {
                children.add(parseNode())
            } else {
                atoms.add(readToken())
            }
        }
        if (pos < content.length) pos++ // consume ')'
        return SExprNode(name, atoms, children)
    }

    private fun parseChildren(): List<SExprNode> {
        val children = mutableListOf<SExprNode>()
        while (pos < content.length) {
            skipWhitespace()
            if (pos < content.length && content[pos] == '(') children.add(parseNode())
            else break
        }
        return children
    }

    private fun readToken(): String {
        skipWhitespace()
        if (pos >= content.length) return ""
        return if (content[pos] == '"') readQuoted() else readUnquoted()
    }

    private fun readQuoted(): String {
        pos++ // skip opening "
        val sb = StringBuilder()
        while (pos < content.length && content[pos] != '"') {
            if (content[pos] == '\\') pos++
            sb.append(content[pos++])
        }
        if (pos < content.length) pos++ // skip closing "
        return sb.toString()
    }

    private fun readUnquoted(): String {
        val start = pos
        while (pos < content.length && !content[pos].isWhitespace()
            && content[pos] != '(' && content[pos] != ')') pos++
        return content.substring(start, pos)
    }

    private fun skipWhitespace() {
        while (pos < content.length && (content[pos].isWhitespace() || content[pos] == '\n')) pos++
        // Skip comments
        if (pos < content.length - 1 && content[pos] == ';') {
            while (pos < content.length && content[pos] != '\n') pos++
        }
    }

    private fun expect(c: Char) {
        if (pos < content.length && content[pos] == c) pos++ else pos++
    }
}

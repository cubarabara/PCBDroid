package com.pcbdroid.domain.export

import com.pcbdroid.data.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

object GerberExporter {

    data class ExportBundle(val files: Map<String, String>, val errors: List<String>)

    fun exportBoard(board: PcbBoard, name: String): ExportBundle {
        val files  = mutableMapOf<String, String>()
        val errors = mutableListOf<String>()
        try {
            files["$name-F_Cu.gbr"]      = copperLayer(board, PcbLayer.F_CU, name)
            files["$name-B_Cu.gbr"]      = copperLayer(board, PcbLayer.B_CU, name)
            files["$name-F_SilkS.gbr"]   = silkLayer(board, PcbLayer.F_SILKSCREEN, name)
            files["$name-B_SilkS.gbr"]   = silkLayer(board, PcbLayer.B_SILKSCREEN, name)
            files["$name-F_Mask.gbr"]    = maskLayer(board, PcbLayer.F_CU, name)
            files["$name-B_Mask.gbr"]    = maskLayer(board, PcbLayer.B_CU, name)
            files["$name-Edge_Cuts.gbr"] = edgeCuts(board, name)
            files["$name-PTH.drl"]       = drillFile(board, name, plated = true)
            files["$name-NPTH.drl"]      = drillFile(board, name, plated = false)
        } catch (e: Exception) { errors.add("Export error: ${e.message}") }
        return ExportBundle(files, errors)
    }

    // ── Gerber header ─────────────────────────────────────────────────────────

    private fun header(proj: String, layer: String) = buildString {
        val d = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        appendLine("G04 PCBDroid Gerber export*")
        appendLine("G04 Project: $proj  Layer: $layer  Date: $d*")
        appendLine("%FSLAX46Y46*%")   // 4 integer, 6 decimal
        appendLine("%MOMM*%")          // metric
        appendLine("%LPD*%")           // dark polarity
        appendLine("G01*")             // linear
        appendLine("G75*")             // multi-quadrant
    }

    private fun footer() = "M02*\n"

    // Convert mm to Gerber int (×1_000_000)
    private fun g(v: Float) = (v * 1_000_000L).toLong()

    // ── Copper layer ──────────────────────────────────────────────────────────

    private fun copperLayer(board: PcbBoard, layer: PcbLayer, name: String) = buildString {
        append(header(name, layer.displayName))

        // Apertures for tracks
        val trackWidths = board.elements.filterIsInstance<Track>()
            .filter { it.layer == layer }.map { it.width }.distinct().sorted()
        val viaWidths = board.elements.filterIsInstance<Via>().map { it.size }.distinct().sorted()

        val aptMap = mutableMapOf<String, Int>()
        var aptId = 10

        trackWidths.forEach { w ->
            appendLine("%ADD${aptId}C,${fmt(w)}*%"); aptMap["T$w"] = aptId++
        }
        viaWidths.forEach { s ->
            appendLine("%ADD${aptId}C,${fmt(s)}*%"); aptMap["V$s"] = aptId++
        }

        // Pad apertures
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            fp.pads.filter { layer in it.layers }.forEach { pad ->
                val key = "P${pad.shape}${pad.size.x}x${pad.size.y}"
                if (key !in aptMap) {
                    when (pad.shape) {
                        PadShape.ROUND -> appendLine("%ADD${aptId}C,${fmt(pad.size.x)}*%")
                        PadShape.OVAL  -> appendLine("%ADD${aptId}O,${fmt(pad.size.x)}X${fmt(pad.size.y)}*%")
                        else           -> appendLine("%ADD${aptId}R,${fmt(pad.size.x)}X${fmt(pad.size.y)}*%")
                    }
                    aptMap[key] = aptId++
                }
            }
        }

        // Draw tracks
        board.elements.filterIsInstance<Track>().filter { it.layer == layer }.forEach { t ->
            aptMap["T${t.width}"]?.let { apt ->
                appendLine("D${apt}*")
                appendLine("X${g(t.start.x)}Y${g(t.start.y)}D02*")
                appendLine("X${g(t.end.x)}Y${g(t.end.y)}D01*")
            }
        }

        // Flash vias
        board.elements.filterIsInstance<Via>().forEach { v ->
            aptMap["V${v.size}"]?.let { apt ->
                appendLine("D${apt}*")
                appendLine("X${g(v.position.x)}Y${g(v.position.y)}D03*")
            }
        }

        // Flash pads
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            fp.pads.filter { layer in it.layers }.forEach { pad ->
                val key = "P${pad.shape}${pad.size.x}x${pad.size.y}"
                aptMap[key]?.let { apt ->
                    val px = fp.position.x + pad.position.x
                    val py = fp.position.y + pad.position.y
                    appendLine("D${apt}*")
                    appendLine("X${g(px)}Y${g(py)}D03*")
                }
            }
        }

        // Copper zones
        board.elements.filterIsInstance<Zone>().filter { it.layer == layer }.forEach { z ->
            if (z.points.size >= 3) {
                appendLine("G36*")
                appendLine("X${g(z.points[0].x)}Y${g(z.points[0].y)}D02*")
                z.points.drop(1).forEach { p -> appendLine("X${g(p.x)}Y${g(p.y)}D01*") }
                appendLine("X${g(z.points[0].x)}Y${g(z.points[0].y)}D01*")
                appendLine("G37*")
            }
        }

        append(footer())
    }

    // ── Silkscreen ────────────────────────────────────────────────────────────

    private fun silkLayer(board: PcbBoard, layer: PcbLayer, name: String) = buildString {
        append(header(name, layer.displayName))
        appendLine("%ADD10C,0.120000*%")
        appendLine("D10*")
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            fp.silkscreen.forEach { line ->
                val sx = fp.position.x + line.start.x; val sy = fp.position.y + line.start.y
                val ex = fp.position.x + line.end.x;   val ey = fp.position.y + line.end.y
                appendLine("X${g(sx)}Y${g(sy)}D02*")
                appendLine("X${g(ex)}Y${g(ey)}D01*")
            }
        }
        append(footer())
    }

    // ── Solder mask ───────────────────────────────────────────────────────────

    private fun maskLayer(board: PcbBoard, copperLayer: PcbLayer, name: String) = buildString {
        val maskName = if (copperLayer == PcbLayer.F_CU) "F.Mask" else "B.Mask"
        append(header(name, maskName))
        appendLine("%LPC*%")
        val exp = 0.051f
        val aptMap = mutableMapOf<String, Int>(); var aptId = 10
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            fp.pads.filter { copperLayer in it.layers }.forEach { pad ->
                val sw = pad.size.x + exp*2; val sh = pad.size.y + exp*2
                val key = "${pad.shape}${sw}x${sh}"
                if (key !in aptMap) {
                    when (pad.shape) {
                        PadShape.ROUND -> appendLine("%ADD${aptId}C,${fmt(sw)}*%")
                        else -> appendLine("%ADD${aptId}R,${fmt(sw)}X${fmt(sh)}*%")
                    }
                    aptMap[key] = aptId++
                }
                aptMap[key]?.let { apt ->
                    val px = fp.position.x + pad.position.x
                    val py = fp.position.y + pad.position.y
                    appendLine("D${apt}*")
                    appendLine("X${g(px)}Y${g(py)}D03*")
                }
            }
        }
        append(footer())
    }

    // ── Edge cuts ─────────────────────────────────────────────────────────────

    private fun edgeCuts(board: PcbBoard, name: String) = buildString {
        append(header(name, "Edge.Cuts"))
        appendLine("%ADD10C,0.050000*%"); appendLine("D10*")
        board.elements.filterIsInstance<BoardOutline>().forEach { o ->
            if (o.points.size >= 2) {
                appendLine("X${g(o.points[0].x)}Y${g(o.points[0].y)}D02*")
                o.points.drop(1).forEach { p -> appendLine("X${g(p.x)}Y${g(p.y)}D01*") }
                appendLine("X${g(o.points[0].x)}Y${g(o.points[0].y)}D01*")
            }
        }
        append(footer())
    }

    // ── Excellon drill ────────────────────────────────────────────────────────

    fun drillFile(board: PcbBoard, name: String, plated: Boolean) = buildString {
        appendLine("M48\n; PCBDroid Drill — $name\nMETRIC,TZ\nFMAT,2")
        val sizes = mutableSetOf<Float>()
        board.elements.filterIsInstance<Via>().forEach { sizes.add(it.drill) }
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            fp.pads.filter { it.drill > 0 }.forEach { sizes.add(it.drill) }
        }
        val sorted = sizes.sorted()
        val toolMap = mutableMapOf<Float, Int>()
        sorted.forEachIndexed { i, s ->
            appendLine("T${String.format("%02d", i+1)}C${String.format("%.3f", s)}")
            toolMap[s] = i+1
        }
        appendLine("%\nG90\nG05")
        board.elements.filterIsInstance<Via>().forEach { v ->
            toolMap[v.drill]?.let { t ->
                appendLine("T${String.format("%02d", t)}")
                appendLine("X${String.format("%.3f", v.position.x)}Y${String.format("%.3f", v.position.y)}")
            }
        }
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            fp.pads.filter { it.drill > 0 && (plated xor (it.type == PadType.NPTH)) }.forEach { pad ->
                toolMap[pad.drill]?.let { t ->
                    appendLine("T${String.format("%02d", t)}")
                    appendLine("X${String.format("%.3f", fp.position.x + pad.position.x)}Y${String.format("%.3f", fp.position.y + pad.position.y)}")
                }
            }
        }
        appendLine("T00\nM30")
    }

    // ── BOM ───────────────────────────────────────────────────────────────────

    fun exportBOM(schematic: SchematicSheet, name: String) = buildString {
        appendLine("Reference,Value,Footprint,Quantity,MPN,Manufacturer")
        schematic.elements.filterIsInstance<SchematicComponent>()
            .groupBy { "${it.value}_${it.footprintId}" }
            .values.forEach { grp ->
                val refs = grp.map { it.reference }.sortedWith(
                    compareBy({ it.filter(Char::isLetter) }, { it.filter(Char::isDigit).toIntOrNull() ?: 0 })
                )
                val c = grp.first()
                appendLine("\"${refs.joinToString(" ")}\",\"${c.value}\",\"${c.footprintId}\",${grp.size},\"\",\"\"")
            }
    }

    // ── Pick & Place ──────────────────────────────────────────────────────────

    fun exportPickAndPlace(board: PcbBoard) = buildString {
        appendLine("Ref,Value,Package,PosX,PosY,Rotation,Side")
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            val side = if (fp.layer == PcbLayer.F_CU) "top" else "bottom"
            appendLine("${fp.reference},${fp.value},${fp.footprintId}," +
                "${String.format("%.3f", fp.position.x/1000f)}," +
                "${String.format("%.3f", fp.position.y/1000f)}," +
                "${fp.rotation},$side")
        }
    }

    private fun fmt(v: Float) = String.format("%.6f", v)
}

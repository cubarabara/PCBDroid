package com.pcbdroid.domain.erc

import com.pcbdroid.data.model.*
import kotlin.math.*

// ─── ERC Engine ───────────────────────────────────────────────────────────────

data class ErcViolation(
    val type: ErcType,
    val description: String,
    val elementId: String,
    val position: PcbPoint,
    val severity: ErcSeverity = ErcSeverity.ERROR
)

enum class ErcSeverity { ERROR, WARNING, INFO }
enum class ErcType {
    DUPLICATE_REFERENCE,
    PIN_NOT_CONNECTED,
    MULTIPLE_DRIVERS,
    NO_DRIVER,
    FLOATING_LABEL,
    MISSING_POWER_SYMBOL,
    WIRE_NOT_CONNECTED,
    BUS_NO_ENTRIES
}

object ErcEngine {

    private const val SNAP_RADIUS = 5f

    fun run(schematic: SchematicSheet, nets: List<Net>): List<ErcViolation> {
        val v = mutableListOf<ErcViolation>()
        checkDuplicateRefs(schematic, v)
        checkUnconnectedPins(schematic, v)
        checkFloatingLabels(schematic, v)
        checkMissingPowerSymbols(schematic, v)
        return v.sortedBy { it.severity.ordinal }
    }

    private fun checkDuplicateRefs(sch: SchematicSheet, out: MutableList<ErcViolation>) {
        sch.elements.filterIsInstance<SchematicComponent>()
            .groupBy { it.reference }
            .filter { it.value.size > 1 }
            .forEach { (ref, comps) ->
                comps.forEach { c ->
                    out.add(ErcViolation(ErcType.DUPLICATE_REFERENCE,
                        "Duplicate reference: $ref", c.id, c.position))
                }
            }
    }

    private fun checkUnconnectedPins(sch: SchematicSheet, out: MutableList<ErcViolation>) {
        val wireEnds  = sch.elements.filterIsInstance<SchematicWire>()
            .flatMap { listOf(it.start, it.end) }
        val noConnects = sch.elements.filterIsInstance<NoConnect>().map { it.position }

        sch.elements.filterIsInstance<SchematicComponent>().forEach { comp ->
            comp.pins.forEach { pin ->
                val world = PcbPoint(comp.position.x + pin.position.x,
                                    comp.position.y + pin.position.y)
                val connected  = wireEnds.any  { dist(it, world) < SNAP_RADIUS }
                val noConnect  = noConnects.any { dist(it, world) < SNAP_RADIUS }
                if (!connected && !noConnect && pin.type != PinType.NOT_CONNECTED) {
                    val sev = when (pin.type) {
                        PinType.POWER_IN, PinType.POWER_OUT -> ErcSeverity.ERROR
                        PinType.INPUT, PinType.OUTPUT        -> ErcSeverity.WARNING
                        else                                 -> ErcSeverity.INFO
                    }
                    out.add(ErcViolation(ErcType.PIN_NOT_CONNECTED,
                        "Pin ${pin.number}(${pin.name}) of ${comp.reference} unconnected",
                        comp.id, world, sev))
                }
            }
        }
    }

    private fun checkFloatingLabels(sch: SchematicSheet, out: MutableList<ErcViolation>) {
        val wireEnds = sch.elements.filterIsInstance<SchematicWire>()
            .flatMap { listOf(it.start, it.end) }
        sch.elements.filterIsInstance<SchematicLabel>().forEach { lbl ->
            if (wireEnds.none { dist(it, lbl.position) < SNAP_RADIUS }) {
                out.add(ErcViolation(ErcType.FLOATING_LABEL,
                    "Label '${lbl.text}' not connected to a wire",
                    lbl.id, lbl.position, ErcSeverity.WARNING))
            }
        }
        // Global labels that appear only once
        sch.elements.filterIsInstance<SchematicLabel>()
            .filter { it.labelType == LabelType.GLOBAL }
            .groupBy { it.text }
            .filter { it.value.size == 1 }
            .forEach { (name, lbls) ->
                out.add(ErcViolation(ErcType.FLOATING_LABEL,
                    "Global label '$name' has no pair (possible typo?)",
                    lbls[0].id, lbls[0].position, ErcSeverity.INFO))
            }
    }

    private fun checkMissingPowerSymbols(sch: SchematicSheet, out: MutableList<ErcViolation>) {
        val powerNets = sch.elements.filterIsInstance<PowerSymbol>().map { it.netName }.toSet()
        sch.elements.filterIsInstance<SchematicLabel>()
            .filter { it.text.uppercase().let { n -> n == "VCC" || n == "GND" || n.startsWith("+") } }
            .forEach { lbl ->
                if (lbl.text !in powerNets) {
                    out.add(ErcViolation(ErcType.MISSING_POWER_SYMBOL,
                        "Net '${lbl.text}' has no power symbol (add PWR_FLAG?)",
                        lbl.id, lbl.position, ErcSeverity.WARNING))
                }
            }
    }

    private fun dist(a: PcbPoint, b: PcbPoint): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

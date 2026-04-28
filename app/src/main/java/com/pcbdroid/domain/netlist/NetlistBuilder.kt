package com.pcbdroid.domain.netlist

import com.pcbdroid.data.model.*
import kotlin.math.sqrt

/**
 * Builds a netlist from a schematic.
 * Connects wires → labels → component pins into logical nets.
 */
object NetlistBuilder {

    private const val SNAP_R = 8f

    data class NetlistResult(
        val nets: List<Net>,
        val pinNets: Map<String, Int>   // "componentId:pinNumber" → netId
    )

    fun build(schematic: SchematicSheet): NetlistResult {
        // 1. Build wire graph - union-find to group connected wires
        val wires = schematic.elements.filterIsInstance<SchematicWire>()
        val labels = schematic.elements.filterIsInstance<SchematicLabel>()
        val powers = schematic.elements.filterIsInstance<PowerSymbol>()
        val comps  = schematic.elements.filterIsInstance<SchematicComponent>()

        // Collect all endpoints
        val endpoints = mutableListOf<PcbPoint>()
        wires.forEach { endpoints.add(it.start); endpoints.add(it.end) }
        labels.forEach { endpoints.add(it.position) }
        powers.forEach { endpoints.add(it.position) }

        // Union-find
        val parent = IntArray(endpoints.size) { it }
        fun find(i: Int): Int { if (parent[i] != i) parent[i] = find(parent[i]); return parent[i] }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

        // Connect points that are close
        for (i in endpoints.indices) {
            for (j in i + 1 until endpoints.size) {
                if (dist(endpoints[i], endpoints[j]) < SNAP_R) union(i, j)
            }
        }

        // Wire endpoints connect to each other
        var epIdx = 0
        val wireStartIdx = mutableListOf<Int>()
        val wireEndIdx   = mutableListOf<Int>()
        wires.forEach { w ->
            wireStartIdx.add(epIdx); wireEndIdx.add(epIdx + 1)
            union(epIdx, epIdx + 1)
            epIdx += 2
        }
        val labelIdx = mutableListOf<Int>()
        labels.forEach { _ -> labelIdx.add(epIdx++)}
        val powerIdx = mutableListOf<Int>()
        powers.forEach { _ -> powerIdx.add(epIdx++) }

        // Map root → net name
        val rootNames = mutableMapOf<Int, String>()

        // Assign power net names
        powers.forEachIndexed { i, ps ->
            rootNames[find(powerIdx[i])] = ps.netName
        }
        // Assign label names
        labels.forEachIndexed { i, lbl ->
            val root = find(labelIdx[i])
            if (root !in rootNames) rootNames[root] = lbl.text
        }

        // Build net objects
        val rootNetId = mutableMapOf<Int, Int>()
        var nextNetId = 1
        (wireStartIdx + wireEndIdx + labelIdx + powerIdx).forEach { idx ->
            val root = find(idx)
            if (root !in rootNetId) {
                rootNetId[root] = nextNetId++
            }
        }

        val nets = rootNetId.map { (root, id) ->
            Net(id, rootNames[root] ?: "Net$id")
        }

        // Map pin positions to nets
        val pinNets = mutableMapOf<String, Int>()
        comps.forEach { comp ->
            comp.pins.forEach { pin ->
                val world = PcbPoint(comp.position.x + pin.position.x,
                                     comp.position.y + pin.position.y)
                // Find closest wire endpoint
                var bestDist = Float.MAX_VALUE
                var bestNetId = -1
                (wireStartIdx.indices).forEach { wi ->
                    val idx = wireStartIdx[wi]
                    val d = dist(world, endpoints[idx])
                    if (d < SNAP_R && d < bestDist) {
                        bestDist = d; bestNetId = rootNetId[find(idx)] ?: -1
                    }
                }
                if (bestNetId >= 0) {
                    pinNets["${comp.id}:${pin.number}"] = bestNetId
                }
            }
        }

        return NetlistResult(nets, pinNets)
    }

    private fun dist(a: PcbPoint, b: PcbPoint): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

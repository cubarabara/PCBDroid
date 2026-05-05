package com.pcbdroid.domain.history.command

import com.pcbdroid.data.model.*
import com.pcbdroid.domain.history.Command

// ═══════════════════════════════════════════════════════════════════════════════
// BOARD / LAYOUT COMMANDS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Tambah track (jalur tembaga) ke PCB layout.
 */
class AddTrackCommand(
    private val elements: MutableList<PcbElement>,
    private val track: Track
) : Command {
    override val description = "Add Track (${track.layer.displayName})"
    override fun execute() { elements.add(track) }
    override fun undo()    { elements.remove(track) }
}

/**
 * Tambah via ke PCB layout.
 */
class AddViaCommand(
    private val elements: MutableList<PcbElement>,
    private val via: Via
) : Command {
    override val description = "Add Via"
    override fun execute() { elements.add(via) }
    override fun undo()    { elements.remove(via) }
}

/**
 * Tambah footprint ke PCB layout.
 */
class AddFootprintCommand(
    private val elements: MutableList<PcbElement>,
    private val footprint: Footprint
) : Command {
    override val description = "Place ${footprint.reference}"
    override fun execute() { elements.add(footprint) }
    override fun undo()    { elements.remove(footprint) }
}

/**
 * Hapus satu atau lebih elemen dari board.
 */
class DeleteBoardElementsCommand(
    private val elements: MutableList<PcbElement>,
    private val toDelete: List<PcbElement>
) : Command {
    override val description =
        if (toDelete.size == 1) "Delete ${toDelete[0].javaClass.simpleName}"
        else "Delete ${toDelete.size} Elements"

    private val originalIndices = mutableMapOf<PcbElement, Int>()

    override fun execute() {
        toDelete.forEach { el ->
            val idx = elements.indexOf(el)
            if (idx >= 0) {
                originalIndices[el] = idx
                elements.removeAt(idx)
            }
        }
    }

    override fun undo() {
        originalIndices.entries
            .sortedByDescending { it.value }
            .forEach { (el, idx) ->
                elements.add(idx.coerceAtMost(elements.size), el)
            }
    }
}

/**
 * Pindahkan footprint di board.
 * Mendukung merge agar drag halus jadi 1 undo step.
 */
class MoveFootprintCommand(
    private val elements: MutableList<PcbElement>,
    private val id: String,
    private var delta: PcbPoint
) : Command {
    override val description = "Move Footprint"

    override fun execute() = applyDelta(delta)
    override fun undo()    = applyDelta(PcbPoint(-delta.x, -delta.y))

    override fun canMergeWith(other: Command) =
        other is MoveFootprintCommand && other.id == id

    override fun mergeWith(other: Command) {
        if (other is MoveFootprintCommand)
            delta = PcbPoint(delta.x + other.delta.x, delta.y + other.delta.y)
    }

    private fun applyDelta(d: PcbPoint) {
        elements.replaceAll { el ->
            if (el.id != id || el !is Footprint) el
            else el.copy(position = el.position + d)
        }
    }
}

/**
 * Rotasi footprint.
 */
class RotateFootprintCommand(
    private val elements: MutableList<PcbElement>,
    private val id: String,
    private val angleDelta: Float
) : Command {
    override val description = "Rotate Footprint"
    override fun execute() = rotate(angleDelta)
    override fun undo()    = rotate(-angleDelta)

    private fun rotate(angle: Float) {
        elements.replaceAll { el ->
            if (el.id != id || el !is Footprint) el
            else el.copy(rotation = (el.rotation + angle) % 360f)
        }
    }
}

/**
 * Ganti layer track.
 */
class ChangeTrackLayerCommand(
    private val elements: MutableList<PcbElement>,
    private val id: String,
    private val newLayer: PcbLayer
) : Command {
    override val description = "Change Track Layer to ${newLayer.displayName}"

    private var oldLayer = PcbLayer.F_CU

    override fun execute() {
        elements.replaceAll { el ->
            if (el.id != id || el !is Track) el
            else { oldLayer = el.layer; el.copy(layer = newLayer) }
        }
    }

    override fun undo() {
        elements.replaceAll { el ->
            if (el.id != id || el !is Track) el
            else el.copy(layer = oldLayer)
        }
    }
}

/**
 * Jalankan beberapa command sekaligus sebagai satu aksi.
 * Berguna untuk operasi kompleks seperti auto-route seluruh board
 * yang menghasilkan ratusan track sekaligus, tapi cukup 1x Undo.
 *
 * Contoh:
 * ```kotlin
 * history.execute(CompositeCommand("Auto Route", listOf(
 *     AddTrackCommand(elements, track1),
 *     AddTrackCommand(elements, track2),
 *     AddViaCommand(elements, via1),
 * )))
 * ```
 */
class CompositeCommand(
    override val description: String,
    private val commands: List<Command>
) : Command {
    override fun execute() { commands.forEach { it.execute() } }
    override fun undo()    { commands.reversed().forEach { it.undo() } }
}

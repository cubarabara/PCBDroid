package com.pcbdroid.domain.history.command

import com.pcbdroid.data.model.*
import com.pcbdroid.domain.history.Command

// ═══════════════════════════════════════════════════════════════════════════════
// SCHEMATIC COMMANDS
//
// Semua aksi di schematic editor yang bisa di-undo.
// Setiap command HARUS bisa dibalik sempurna oleh undo().
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Tambah satu elemen ke schematic (wire, component, label, dll).
 *
 * execute → tambah ke list
 * undo    → hapus dari list
 */
class AddElementCommand(
    private val elements: MutableList<SchematicElement>,
    private val element: SchematicElement
) : Command {
    override val description = when (element) {
        is SchematicWire      -> "Add Wire"
        is SchematicComponent -> "Add ${element.reference}"
        is SchematicLabel     -> "Add Label '${element.text}'"
        is SchematicJunction  -> "Add Junction"
        is NoConnect          -> "Add No-Connect"
        is PowerSymbol        -> "Add Power '${element.netName}'"
        is BusLine            -> "Add Bus"
        else                  -> "Add Element"
    }

    override fun execute() { elements.add(element) }
    override fun undo()    { elements.remove(element) }
}

/**
 * Hapus satu atau lebih elemen dari schematic.
 *
 * execute → hapus semua dari list (simpan urutan asli untuk undo)
 * undo    → kembalikan ke posisi semula
 */
class DeleteElementsCommand(
    private val elements: MutableList<SchematicElement>,
    private val toDelete: List<SchematicElement>
) : Command {
    override val description =
        if (toDelete.size == 1) "Delete ${toDelete[0].javaClass.simpleName}"
        else "Delete ${toDelete.size} Elements"

    // Simpan index asli agar undo bisa kembalikan ke posisi yang sama
    private val originalIndices = mutableMapOf<SchematicElement, Int>()

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
        // Kembalikan dari index terbesar ke terkecil agar tidak geser
        originalIndices.entries
            .sortedByDescending { it.value }
            .forEach { (el, idx) ->
                val safeIdx = idx.coerceAtMost(elements.size)
                elements.add(safeIdx, el)
            }
    }
}

/**
 * Pindahkan satu atau lebih elemen.
 *
 * execute → pindah ke posisi baru
 * undo    → kembali ke posisi semula
 *
 * Mendukung merge: drag komponen menghasilkan banyak MoveCommand,
 * tapi di-merge jadi satu sehingga Undo hanya sekali.
 */
class MoveElementsCommand(
    private val elements: MutableList<SchematicElement>,
    private val ids: Set<String>,
    private var delta: PcbPoint
) : Command {
    override val description =
        if (ids.size == 1) "Move Element" else "Move ${ids.size} Elements"

    override fun execute() = applyDelta(delta)
    override fun undo()    = applyDelta(PcbPoint(-delta.x, -delta.y))

    override fun canMergeWith(other: Command): Boolean =
        other is MoveElementsCommand && other.ids == ids

    override fun mergeWith(other: Command) {
        if (other is MoveElementsCommand) {
            delta = PcbPoint(delta.x + other.delta.x, delta.y + other.delta.y)
        }
    }

    private fun applyDelta(d: PcbPoint) {
        elements.replaceAll { el ->
            if (el.id !in ids) return@replaceAll el
            when (el) {
                is SchematicComponent -> el.copy(position = el.position + d)
                is SchematicWire      -> el.copy(
                    position = el.position + d,
                    start    = el.start + d,
                    end      = el.end + d)
                is SchematicLabel     -> el.copy(position = el.position + d)
                is SchematicJunction  -> el.copy(position = el.position + d)
                is PowerSymbol        -> el.copy(position = el.position + d)
                is NoConnect          -> el.copy(position = el.position + d)
                is BusLine            -> el.copy(
                    position = el.position + d,
                    points   = el.points.map { it + d })
                else                  -> el
            }
        }
    }
}

/**
 * Rotasi komponen.
 */
class RotateComponentCommand(
    private val elements: MutableList<SchematicElement>,
    private val id: String,
    private val angleDelta: Float  // biasanya 90f
) : Command {
    override val description = "Rotate Component"

    override fun execute() = rotate(angleDelta)
    override fun undo()    = rotate(-angleDelta)

    private fun rotate(angle: Float) {
        elements.replaceAll { el ->
            if (el.id != id || el !is SchematicComponent) el
            else el.copy(rotation = (el.rotation + angle) % 360f)
        }
    }
}

/**
 * Edit properti komponen (reference, value).
 */
class EditComponentPropertiesCommand(
    private val elements: MutableList<SchematicElement>,
    private val id: String,
    private val newReference: String,
    private val newValue: String
) : Command {
    override val description = "Edit Properties"

    private var oldReference = ""
    private var oldValue     = ""

    override fun execute() {
        elements.replaceAll { el ->
            if (el.id != id || el !is SchematicComponent) el
            else {
                oldReference = el.reference
                oldValue     = el.value
                el.copy(reference = newReference, value = newValue)
            }
        }
    }

    override fun undo() {
        elements.replaceAll { el ->
            if (el.id != id || el !is SchematicComponent) el
            else el.copy(reference = oldReference, value = oldValue)
        }
    }
}

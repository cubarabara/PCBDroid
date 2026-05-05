package com.pcbdroid.domain.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * # HistoryManager — Manajer Undo/Redo
 *
 * Kelas utama yang menyimpan dan mengelola riwayat aksi.
 * Gunakan ini di ViewModel manapun yang butuh undo/redo.
 *
 * ## Cara pakai:
 *
 * ```kotlin
 * // 1. Buat instance (biasanya di ViewModel)
 * val history = HistoryManager(maxHistory = 50)
 *
 * // 2. Setiap aksi user, buat Command dan jalankan lewat history
 * history.execute(AddWireCommand(elements, wire))
 *
 * // 3. Undo/Redo
 * history.undo()
 * history.redo()
 *
 * // 4. Cek state di UI
 * val canUndo = history.canUndo   // untuk disable tombol
 * val canRedo = history.canRedo
 * val undoLabel = history.undoDescription  // "Undo Add Wire"
 * ```
 *
 * @param maxHistory Maksimal jumlah aksi yang disimpan. Default 50.
 */
class HistoryManager(private val maxHistory: Int = 50) {

    // ── State yang bisa diobservasi oleh Compose ──────────────────────────────

    /** True jika ada aksi yang bisa di-undo */
    var canUndo by mutableStateOf(false)
        private set

    /** True jika ada aksi yang bisa di-redo */
    var canRedo by mutableStateOf(false)
        private set

    /** Deskripsi aksi yang akan di-undo. Null jika tidak ada. */
    var undoDescription by mutableStateOf<String?>(null)
        private set

    /** Deskripsi aksi yang akan di-redo. Null jika tidak ada. */
    var redoDescription by mutableStateOf<String?>(null)
        private set

    /** Jumlah aksi di undo stack */
    var undoCount by mutableStateOf(0)
        private set

    // ── Event flow untuk notifikasi ke UI ─────────────────────────────────────

    private val _events = MutableSharedFlow<HistoryEvent>(extraBufferCapacity = 10)

    /**
     * Stream event untuk ditampilkan sebagai snackbar/toast.
     * Collect di ViewModel atau Composable.
     */
    val events = _events.asSharedFlow()

    // ── Internal stacks ───────────────────────────────────────────────────────

    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Jalankan sebuah Command dan simpan ke history.
     *
     * Command akan di-execute, lalu dimasukkan ke undo stack.
     * Redo stack dikosongkan (karena ada aksi baru).
     *
     * Jika command bisa di-merge dengan command terakhir,
     * keduanya akan digabung menjadi satu entry.
     *
     * @param command Command yang akan dijalankan
     * @param execute Jika false, command langsung masuk history tanpa execute()
     *                (berguna jika aksi sudah dilakukan sebelum dipanggil)
     */
    fun execute(command: Command, execute: Boolean = true) {
        if (execute) command.execute()

        // Coba merge dengan command terakhir
        val last = undoStack.lastOrNull()
        if (last != null && last.canMergeWith(command)) {
            last.mergeWith(command)
        } else {
            undoStack.addLast(command)
            // Batasi ukuran stack
            if (undoStack.size > maxHistory) {
                undoStack.removeFirst()
            }
        }

        // Aksi baru → hapus redo history
        redoStack.clear()

        updateState()
        _events.tryEmit(HistoryEvent.ActionDone(command.description))
    }

    /**
     * Batalkan aksi terakhir.
     * Tidak melakukan apapun jika tidak ada yang bisa di-undo.
     */
    fun undo() {
        if (undoStack.isEmpty()) return

        val command = undoStack.removeLast()
        command.undo()
        redoStack.addLast(command)

        updateState()
        _events.tryEmit(HistoryEvent.Undone(command.description))
    }

    /**
     * Ulangi aksi yang sudah di-undo.
     * Tidak melakukan apapun jika tidak ada yang bisa di-redo.
     */
    fun redo() {
        if (redoStack.isEmpty()) return

        val command = redoStack.removeLast()
        command.execute()
        undoStack.addLast(command)

        updateState()
        _events.tryEmit(HistoryEvent.Redone(command.description))
    }

    /**
     * Hapus seluruh riwayat.
     * Dipanggil saat project baru dibuka atau file baru dibuat.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    /**
     * Tandai posisi saat ini sebagai "tersimpan".
     * Gunakan untuk fitur "document modified" indicator.
     */
    fun markSaved() {
        _savedIndex = undoStack.size
        updateState()
    }

    /**
     * True jika ada perubahan sejak terakhir disimpan.
     */
    val isModified: Boolean get() = undoStack.size != _savedIndex

    // ── Internal ──────────────────────────────────────────────────────────────

    private var _savedIndex = 0

    private fun updateState() {
        canUndo          = undoStack.isNotEmpty()
        canRedo          = redoStack.isNotEmpty()
        undoDescription  = undoStack.lastOrNull()?.description
        redoDescription  = redoStack.lastOrNull()?.description
        undoCount        = undoStack.size
    }
}

/**
 * Event yang dikirim HistoryManager ke UI.
 * Tampilkan sebagai Snackbar atau Toast.
 */
sealed class HistoryEvent {
    data class ActionDone(val description: String) : HistoryEvent()
    data class Undone(val description: String)     : HistoryEvent()
    data class Redone(val description: String)     : HistoryEvent()
}

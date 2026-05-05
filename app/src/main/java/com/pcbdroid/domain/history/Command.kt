package com.pcbdroid.domain.history

/**
 * # Command Pattern untuk Undo/Redo
 *
 * Setiap aksi yang bisa di-undo harus implement interface ini.
 *
 * ## Cara pakai (untuk junior developer):
 *
 * 1. Buat class yang implement [Command]
 * 2. Isi [execute] → apa yang terjadi saat aksi dilakukan
 * 3. Isi [undo]    → kebalikan dari execute
 * 4. Isi [description] → nama aksi (muncul di tooltip Undo/Redo)
 *
 * ## Contoh:
 * ```kotlin
 * class AddWireCommand(
 *     private val elements: MutableList<SchematicElement>,
 *     private val wire: SchematicWire
 * ) : Command {
 *     override val description = "Add Wire"
 *     override fun execute() { elements.add(wire) }
 *     override fun undo()    { elements.remove(wire) }
 * }
 * ```
 */
interface Command {

    /**
     * Nama aksi yang ditampilkan di UI.
     * Contoh: "Add Wire", "Delete Component", "Move Footprint"
     */
    val description: String

    /**
     * Jalankan aksi ini.
     * Dipanggil saat user melakukan sesuatu, atau saat Redo.
     */
    fun execute()

    /**
     * Batalkan aksi ini.
     * Dipanggil saat user menekan Undo.
     * Harus kebalikan persis dari [execute].
     */
    fun undo()

    /**
     * Override ini jika command bisa digabung dengan command sebelumnya.
     *
     * Berguna untuk: mengetik teks (setiap huruf jadi 1 command → terlalu banyak),
     * drag komponen (setiap pixel jadi 1 command → terlalu banyak).
     *
     * Return true jika [other] bisa digabung ke command ini.
     * Jika true, panggil [mergeWith] untuk update state internal.
     *
     * Contoh: MoveCommand bisa merge selama komponen yang dipindah sama.
     */
    fun canMergeWith(other: Command): Boolean = false

    /**
     * Gabungkan [other] ke command ini.
     * Hanya dipanggil jika [canMergeWith] return true.
     */
    fun mergeWith(other: Command) {}
}

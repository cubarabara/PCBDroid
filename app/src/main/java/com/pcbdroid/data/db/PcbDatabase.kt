package com.pcbdroid.data.db

// Room dihapus karena SQLite JDBC tidak support ARM64 (aarch64) saat compile-time
// di AndroidIDE yang berjalan di HP Android.
//
// Diganti dengan ProjectRepository menggunakan kotlinx.serialization + File I/O
// Lihat: data/repository/ProjectRepository.kt

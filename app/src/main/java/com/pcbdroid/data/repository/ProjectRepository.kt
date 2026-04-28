package com.pcbdroid.data.repository

import android.content.Context
import com.pcbdroid.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// ─── Project Repository ───────────────────────────────────────────────────────
// Menyimpan project sebagai file JSON di internal storage
// Menggantikan Room Database yang tidak kompatibel dengan ARM64 compile-time

class ProjectRepository constructor(
    private val context: Context
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val projectsDir: File
        get() = File(context.filesDir, "projects").also { it.mkdirs() }

    private val libraryDir: File
        get() = File(context.filesDir, "library").also { it.mkdirs() }

    // ── Projects ──────────────────────────────────────────────────────────────

    suspend fun saveProject(project: PcbProject) = withContext(Dispatchers.IO) {
        val file = File(projectsDir, "${project.id}.json")
        file.writeText(json.encodeToString(project))
    }

    suspend fun loadProject(id: String): PcbProject? = withContext(Dispatchers.IO) {
        val file = File(projectsDir, "$id.json")
        if (!file.exists()) return@withContext null
        try {
            Json.decodeFromString(PcbProject.serializer(), file.readText())
        } catch (e: Exception) {
            null
        }
    }

    suspend fun listProjects(): List<RecentProject> = withContext(Dispatchers.IO) {
        projectsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val project = Json.decodeFromString(PcbProject.serializer(), file.readText())
                    RecentProject(
                        id = project.id,
                        name = project.name,
                        layerCount = project.board.layers.count { it.name.endsWith("_CU") },
                        lastModified = project.modifiedAt
                    )
                } catch (e: Exception) { null }
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        File(projectsDir, "$id.json").delete()
    }

    // ── Library Cache (pengganti Room LibraryComponentDao) ────────────────────

    suspend fun cacheComponents(components: List<LibraryComponent>) = withContext(Dispatchers.IO) {
        val file = File(libraryDir, "cached_components.json")
        file.writeText(json.encodeToString(components))
    }

    suspend fun getCachedComponents(): List<LibraryComponent> = withContext(Dispatchers.IO) {
        val file = File(libraryDir, "cached_components.json")
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LibraryComponent.serializer()), file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchCachedComponents(query: String): List<LibraryComponent> =
        getCachedComponents().filter { comp ->
            comp.name.contains(query, ignoreCase = true) ||
            comp.description.contains(query, ignoreCase = true) ||
            comp.mpn.contains(query, ignoreCase = true) ||
            comp.manufacturer.contains(query, ignoreCase = true)
        }

    // ── User imported symbols ─────────────────────────────────────────────────

    suspend fun saveUserSymbol(id: String, kicadData: String) = withContext(Dispatchers.IO) {
        File(libraryDir, "sym_$id.kicad_sym").writeText(kicadData)
    }

    suspend fun getUserSymbols(): List<File> = withContext(Dispatchers.IO) {
        libraryDir.listFiles()
            ?.filter { it.name.startsWith("sym_") }
            ?.toList()
            ?: emptyList()
    }
}

// ─── Simple data class for recent project list ────────────────────────────────
data class RecentProject(
    val id: String,
    val name: String,
    val layerCount: Int,
    val lastModified: Long
)

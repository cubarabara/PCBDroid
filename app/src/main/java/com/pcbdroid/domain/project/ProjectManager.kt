package com.pcbdroid.domain.project

import android.content.Context
import com.pcbdroid.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ─── Project Manager ──────────────────────────────────────────────────────────
// Saves/loads PcbProject as JSON to app's files directory.
// Format: <app_files>/projects/<project_name>.pcbd (JSON)

object ProjectManager {

    private fun projectsDir(ctx: Context): File =
        File(ctx.filesDir, "projects").also { it.mkdirs() }

    data class ProjectInfo(
        val name: String,
        val filePath: String,
        val modifiedAt: Long,
        val schElementCount: Int,
        val boardElementCount: Int
    )

    // ── List ─────────────────────────────────────────────────────────────────

    fun listProjects(ctx: Context): List<ProjectInfo> {
        return projectsDir(ctx).listFiles()
            ?.filter { it.extension == "pcbd" }
            ?.mapNotNull { f ->
                try {
                    val obj = JSONObject(f.readText())
                    ProjectInfo(
                        name = obj.optString("name", f.nameWithoutExtension),
                        filePath = f.absolutePath,
                        modifiedAt = f.lastModified(),
                        schElementCount = obj.optJSONObject("schematic")
                            ?.optJSONArray("elements")?.length() ?: 0,
                        boardElementCount = obj.optJSONObject("board")
                            ?.optJSONArray("elements")?.length() ?: 0
                    )
                } catch (e: Exception) { null }
            }
            ?.sortedByDescending { it.modifiedAt }
            ?: emptyList()
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    fun save(ctx: Context, project: PcbProject): Result<File> {
        return try {
            val json = projectToJson(project)
            val safe = project.name.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            val file = File(projectsDir(ctx), "$safe.pcbd")
            file.writeText(json.toString(2))
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    fun load(filePath: String): Result<PcbProject> {
        return try {
            val obj = JSONObject(File(filePath).readText())
            Result.success(projectFromJson(obj))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun delete(filePath: String): Boolean = File(filePath).delete()

    // ── JSON: Project ─────────────────────────────────────────────────────────

    private fun projectToJson(p: PcbProject): JSONObject = JSONObject().apply {
        put("id",   p.id)
        put("name", p.name)
        put("createdAt", p.createdAt)
        put("schematic", schematicToJson(p.schematic))
        put("board",     boardToJson(p.board))
        put("nets",      JSONArray(p.nets.map { netToJson(it) }))
    }

    private fun projectFromJson(o: JSONObject) = PcbProject(
        id        = o.optString("id", UUID.randomUUID().toString()),
        name      = o.optString("name", "Untitled"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        schematic = o.optJSONObject("schematic")?.let { schematicFromJson(it) } ?: SchematicSheet(),
        board     = o.optJSONObject("board")?.let { boardFromJson(it) } ?: PcbBoard(),
        nets      = o.optJSONArray("nets")?.let { arr ->
            (0 until arr.length()).map { netFromJson(arr.getJSONObject(it)) }.toMutableList()
        } ?: mutableListOf()
    )

    // ── JSON: Net ─────────────────────────────────────────────────────────────

    private fun netToJson(n: Net) = JSONObject().apply {
        put("id", n.id); put("name", n.name)
    }
    private fun netFromJson(o: JSONObject) = Net(o.getInt("id"), o.getString("name"))

    // ── JSON: Schematic ───────────────────────────────────────────────────────

    private fun schematicToJson(s: SchematicSheet): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        s.elements.forEach { arr.put(elementToJson(it)) }
        put("elements", arr)
    }

    private fun schematicFromJson(o: JSONObject): SchematicSheet {
        val list = mutableListOf<SchematicElement>()
        val arr = o.optJSONArray("elements") ?: return SchematicSheet()
        for (i in 0 until arr.length()) {
            elementFromJson(arr.getJSONObject(i))?.let { list.add(it) }
        }
        return SchematicSheet(elements = list)
    }

    private fun elementToJson(e: SchematicElement): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("type", e::class.simpleName)
        put("x", e.position.x); put("y", e.position.y)
        when (e) {
            is SchematicComponent -> {
                put("ref", e.reference); put("value", e.value)
                put("symbolId", e.symbolId); put("footprintId", e.footprintId)
                put("rotation", e.rotation)
            }
            is SchematicWire -> {
                put("sx", e.start.x); put("sy", e.start.y)
                put("ex", e.end.x);   put("ey", e.end.y)
                put("netId", e.netId)
            }
            is SchematicLabel -> {
                put("text", e.text); put("netId", e.netId)
                put("labelType", e.labelType.name)
            }
            is PowerSymbol -> {
                put("netName", e.netName); put("symbolType", e.symbolType.name)
            }
            is SchematicJunction -> { /* position only */ }
            is NoConnect         -> { /* position only */ }
            is BusLine -> {
                val pts = JSONArray()
                e.points.forEach { p -> pts.put(JSONObject().apply { put("x",p.x); put("y",p.y) }) }
                put("points", pts)
            }
            else -> {}
        }
    }

    private fun elementFromJson(o: JSONObject): SchematicElement? {
        val id  = o.optString("id", UUID.randomUUID().toString())
        val pos = PcbPoint(o.optDouble("x").toFloat(), o.optDouble("y").toFloat())
        return when (o.optString("type")) {
            "SchematicComponent" -> SchematicComponent(id, pos,
                reference   = o.optString("ref"),
                value       = o.optString("value"),
                symbolId    = o.optString("symbolId"),
                footprintId = o.optString("footprintId"),
                rotation    = o.optDouble("rotation").toFloat())
            "SchematicWire" -> SchematicWire(id, pos,
                start = PcbPoint(o.optDouble("sx").toFloat(), o.optDouble("sy").toFloat()),
                end   = PcbPoint(o.optDouble("ex").toFloat(), o.optDouble("ey").toFloat()),
                netId = o.optInt("netId", -1))
            "SchematicLabel" -> SchematicLabel(id, pos,
                text      = o.optString("text"),
                netId     = o.optInt("netId", -1),
                labelType = try { LabelType.valueOf(o.optString("labelType","LOCAL")) } catch (e: Exception) { LabelType.LOCAL })
            "PowerSymbol" -> PowerSymbol(id, pos,
                netName    = o.optString("netName"),
                symbolType = try { PowerType.valueOf(o.optString("symbolType","CUSTOM")) } catch (e: Exception) { PowerType.CUSTOM })
            "SchematicJunction" -> SchematicJunction(id, pos)
            "NoConnect"         -> NoConnect(id, pos)
            "BusLine" -> {
                val pts = o.optJSONArray("points")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val p = arr.getJSONObject(i)
                        PcbPoint(p.optDouble("x").toFloat(), p.optDouble("y").toFloat())
                    }
                } ?: listOf(pos)
                BusLine(id, pos, pts)
            }
            else -> null
        }
    }

    // ── JSON: Board ───────────────────────────────────────────────────────────

    private fun boardToJson(b: PcbBoard): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        b.elements.forEach { arr.put(pcbElementToJson(it)) }
        put("elements", arr)
        put("rules", JSONObject().apply {
            put("minTrack", b.designRules.minTrackWidth)
            put("minClear", b.designRules.minClearance)
            put("minViaDrill", b.designRules.minViaDrill)
            put("minViaSize",  b.designRules.minViaSize)
            put("copperWeight", b.designRules.copperWeight)
        })
    }

    private fun boardFromJson(o: JSONObject): PcbBoard {
        val list = mutableListOf<PcbElement>()
        val arr = o.optJSONArray("elements") ?: return PcbBoard()
        for (i in 0 until arr.length()) {
            pcbElementFromJson(arr.getJSONObject(i))?.let { list.add(it) }
        }
        val rules = o.optJSONObject("rules")?.let { r ->
            DesignRules(
                minTrackWidth = r.optDouble("minTrack",   0.25).toFloat(),
                minClearance  = r.optDouble("minClear",   0.25).toFloat(),
                minViaDrill   = r.optDouble("minViaDrill",0.3).toFloat(),
                minViaSize    = r.optDouble("minViaSize", 0.6).toFloat(),
                copperWeight  = r.optDouble("copperWeight",35.0).toFloat()
            )
        } ?: DesignRules()
        return PcbBoard(elements = list, designRules = rules)
    }

    private fun pcbElementToJson(e: PcbElement): JSONObject = JSONObject().apply {
        put("id", e.id); put("type", e::class.simpleName)
        put("x", e.position.x); put("y", e.position.y)
        put("layer", e.layer.name)
        when (e) {
            is Track -> {
                put("sx", e.start.x); put("sy", e.start.y)
                put("ex", e.end.x);   put("ey", e.end.y)
                put("width", e.width); put("netId", e.netId)
            }
            is Via -> {
                put("size", e.size); put("drill", e.drill)
                put("netId", e.netId)
                put("fromLayer", e.fromLayer.name); put("toLayer", e.toLayer.name)
            }
            is Footprint -> {
                put("ref", e.reference); put("value", e.value)
                put("footprintId", e.footprintId); put("rotation", e.rotation)
                put("model3d", e.model3dPath ?: "")
            }
            is Zone -> {
                put("netId", e.netId)
                val pts = JSONArray()
                e.points.forEach { p -> pts.put(JSONObject().apply { put("x",p.x);put("y",p.y) }) }
                put("points", pts)
            }
            is BoardOutline -> {
                val pts = JSONArray()
                e.points.forEach { p -> pts.put(JSONObject().apply { put("x",p.x);put("y",p.y) }) }
                put("points", pts)
            }
            else -> {}
        }
    }

    private fun pcbElementFromJson(o: JSONObject): PcbElement? {
        val id    = o.optString("id", UUID.randomUUID().toString())
        val pos   = PcbPoint(o.optDouble("x").toFloat(), o.optDouble("y").toFloat())
        val layer = try { PcbLayer.valueOf(o.optString("layer","F_CU")) } catch (e: Exception) { PcbLayer.F_CU }
        return when (o.optString("type")) {
            "Track" -> Track(id, pos, layer,
                start  = PcbPoint(o.optDouble("sx").toFloat(), o.optDouble("sy").toFloat()),
                end    = PcbPoint(o.optDouble("ex").toFloat(), o.optDouble("ey").toFloat()),
                width  = o.optDouble("width", 0.25).toFloat(),
                netId  = o.optInt("netId", -1))
            "Via" -> Via(id, pos,
                size   = o.optDouble("size",  0.8).toFloat(),
                drill  = o.optDouble("drill", 0.4).toFloat(),
                netId  = o.optInt("netId", -1),
                fromLayer = try { PcbLayer.valueOf(o.optString("fromLayer","F_CU")) } catch (e: Exception) { PcbLayer.F_CU },
                toLayer   = try { PcbLayer.valueOf(o.optString("toLayer",  "B_CU")) } catch (e: Exception) { PcbLayer.B_CU })
            "Footprint" -> Footprint(id, pos, layer,
                reference   = o.optString("ref"),
                value       = o.optString("value"),
                footprintId = o.optString("footprintId"),
                rotation    = o.optDouble("rotation").toFloat(),
                model3dPath = o.optString("model3d","").ifBlank { null })
            "Zone" -> {
                val pts = o.optJSONArray("points")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val p = arr.getJSONObject(i)
                        PcbPoint(p.optDouble("x").toFloat(), p.optDouble("y").toFloat())
                    }
                } ?: listOf(pos)
                Zone(id, pos, layer, pts, o.optInt("netId", -1))
            }
            "BoardOutline" -> {
                val pts = o.optJSONArray("points")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val p = arr.getJSONObject(i)
                        PcbPoint(p.optDouble("x").toFloat(), p.optDouble("y").toFloat())
                    }
                } ?: listOf(pos)
                BoardOutline(id, pos, points = pts)
            }
            else -> null
        }
    }
}

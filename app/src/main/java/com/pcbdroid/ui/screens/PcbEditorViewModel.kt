package com.pcbdroid.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcbdroid.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.*

// ── Tool & Mode enums ─────────────────────────────────────────────────────────
enum class EditorTool {
    SELECT, WIRE, BUS, LABEL, POWER, COMPONENT,
    NO_CONNECT, JUNCTION, TEXT,
    ROUTE_SINGLE, ROUTE_INTERACTIVE, ROUTE_DIFF_PAIR,
    ADD_VIA, POUR_ZONE, MEASURE, DRC, PAN
}
enum class EditorMode { SCHEMATIC, LAYOUT, THREED }
enum class WireMode   { ORTHOGONAL, FORTY_FIVE, FREE }

// ── State classes ─────────────────────────────────────────────────────────────
data class CanvasTransform(
    val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f
) {
    fun screenToWorld(s: Offset) = PcbPoint((s.x - offsetX) / scale, (s.y - offsetY) / scale)
    fun worldToScreen(w: PcbPoint) = Offset(w.x * scale + offsetX, w.y * scale + offsetY)
    fun zoom(f: Float, p: Offset): CanvasTransform {
        val ns = (scale * f).coerceIn(0.05f, 80f); val sf = ns / scale
        return copy(scale=ns, offsetX=p.x-(p.x-offsetX)*sf, offsetY=p.y-(p.y-offsetY)*sf)
    }
    fun pan(dx: Float, dy: Float) = copy(offsetX=offsetX+dx, offsetY=offsetY+dy)
}

data class SelectionState(val selectedIds: Set<String> = emptySet())

data class WireDrawState(
    val isDrawing: Boolean = false,
    val startPoint: PcbPoint? = null,
    val currentPoint: PcbPoint? = null,
    val wireMode: WireMode = WireMode.ORTHOGONAL
)

data class RouteState(
    val isRouting: Boolean = false,
    val currentLayer: PcbLayer = PcbLayer.F_CU,
    val trackWidth: Float = 0.25f,
    val points: List<PcbPoint> = emptyList(),
    val currentPos: PcbPoint? = null
)

data class MeasureLine(val start: PcbPoint, val end: PcbPoint) {
    val inMm: Float get() {
        val dx = end.x - start.x; val dy = end.y - start.y
        return sqrt(dx*dx + dy*dy) * 0.0254f
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────
class PcbEditorViewModel : ViewModel() {

    var editorMode     by mutableStateOf(EditorMode.SCHEMATIC)
    var activeTool     by mutableStateOf(EditorTool.SELECT)
    var canvasTransform by mutableStateOf(CanvasTransform(scale = 2f))
    var project        by mutableStateOf(PcbProject(id=UUID.randomUUID().toString(), name="Untitled"))
    var selection      by mutableStateOf(SelectionState())
    var wireState      by mutableStateOf(WireDrawState())
    var routeState     by mutableStateOf(RouteState())
    var activeLayer    by mutableStateOf(PcbLayer.F_CU)
    var visibleLayers  by mutableStateOf(PcbLayer.allLayers.toSet())
    var gridSize       by mutableStateOf(50f)
    var showGrid       by mutableStateOf(true)
    var showRatsnest   by mutableStateOf(true)
    var drcViolations  by mutableStateOf<List<DrcViolation>>(emptyList())
    var isLibraryPanelOpen     by mutableStateOf(false)
    var isPropertiesPanelOpen  by mutableStateOf(false)

    // Cursor & measurement
    var cursorWorld     by mutableStateOf<PcbPoint?>(null)
    var measureLines    by mutableStateOf<List<MeasureLine>>(emptyList())
    var activeMeasure   by mutableStateOf<MeasureLine?>(null)
    private var measureStart: PcbPoint? = null

    // Pending component placement
    var pendingComponent by mutableStateOf<LibraryComponent?>(null)

    private val undoStack = ArrayDeque<PcbProject>()
    private val redoStack = ArrayDeque<PcbProject>()

    private val _snackMessage = MutableSharedFlow<String>()
    val snackMessage = _snackMessage.asSharedFlow()

    // ── Tool / Mode ───────────────────────────────────────────────────────────
    fun setTool(tool: EditorTool) {
        activeTool = tool
        wireState  = WireDrawState()
        routeState = RouteState()
        if (tool != EditorTool.MEASURE) { activeMeasure = null; measureStart = null }
    }
    fun changeEditorMode(mode: EditorMode) { editorMode = mode }

    // ── Canvas transform ──────────────────────────────────────────────────────
    fun onZoom(f: Float, p: Offset) { canvasTransform = canvasTransform.zoom(f, p) }
    fun onPan(dx: Float, dy: Float)  { canvasTransform = canvasTransform.pan(dx, dy) }
    fun fitView() { canvasTransform = CanvasTransform(scale=2f, offsetX=200f, offsetY=200f) }

    fun onPointerMove(screen: Offset) {
        cursorWorld = canvasTransform.screenToWorld(screen)
        if (wireState.isDrawing) wireState = wireState.copy(currentPoint = snapToGrid(cursorWorld!!))
        if (routeState.isRouting) routeState = routeState.copy(currentPos = snapToGrid(cursorWorld!!))
        if (activeTool == EditorTool.MEASURE && measureStart != null)
            activeMeasure = MeasureLine(measureStart!!, snapToGrid(cursorWorld!!))
        if (pendingComponent != null)
            cursorWorld = cursorWorld  // trigger recompose for ghost
    }

    fun onCanvasTap(screen: Offset) {
        val pos = snapToGrid(canvasTransform.screenToWorld(screen))
        when (activeTool) {
            EditorTool.WIRE       -> handleWireTap(pos)
            EditorTool.JUNCTION   -> { saveUndo(); project.schematic.elements.add(SchematicJunction(UUID.randomUUID().toString(), pos)) }
            EditorTool.NO_CONNECT -> { saveUndo(); project.schematic.elements.add(NoConnect(UUID.randomUUID().toString(), pos)) }
            EditorTool.SELECT     -> handleSelectTap(pos)
            EditorTool.COMPONENT  -> pendingComponent?.let { placePendingComponent(pos) }
            EditorTool.MEASURE    -> handleMeasureTap(pos)
            EditorTool.ROUTE_SINGLE, EditorTool.ROUTE_INTERACTIVE -> handleRouteTap(pos)
            else -> {}
        }
    }

    fun onCanvasDoubleTap(screen: Offset) {
        when (activeTool) {
            EditorTool.WIRE  -> finishWire()
            EditorTool.ROUTE_SINGLE, EditorTool.ROUTE_INTERACTIVE -> finishRoute()
            else -> onZoom(1.5f, screen)
        }
    }

    // ── Wire ──────────────────────────────────────────────────────────────────
    private fun handleWireTap(pos: PcbPoint) {
        if (!wireState.isDrawing) {
            wireState = WireDrawState(true, pos, pos)
        } else {
            val start = wireState.startPoint ?: return
            saveUndo()
            project.schematic.elements.add(SchematicWire(UUID.randomUUID().toString(), start, start, pos))
            wireState = wireState.copy(startPoint=pos, currentPoint=pos)
        }
    }
    fun finishWire() { wireState = WireDrawState() }

    // ── Route ─────────────────────────────────────────────────────────────────
    private fun handleRouteTap(pos: PcbPoint) {
        if (!routeState.isRouting) {
            routeState = RouteState(true, activeLayer, points=listOf(pos), currentPos=pos)
        } else {
            val last = routeState.points.lastOrNull() ?: return
            saveUndo()
            project.board.elements.add(Track(UUID.randomUUID().toString(), last,
                routeState.currentLayer, last, pos, routeState.trackWidth, -1))
            routeState = routeState.copy(points=routeState.points+pos, currentPos=pos)
        }
    }
    fun finishRoute() { routeState = RouteState() }

    // ── Measure ───────────────────────────────────────────────────────────────
    private fun handleMeasureTap(pos: PcbPoint) {
        if (measureStart == null) {
            measureStart  = pos
            activeMeasure = null
        } else {
            activeMeasure?.let { measureLines = measureLines + it }
            measureStart  = null
            activeMeasure = null
        }
    }

    // ── Select ────────────────────────────────────────────────────────────────
    private fun handleSelectTap(pos: PcbPoint) {
        val r   = 15f / canvasTransform.scale
        val hit = findAt(pos, r)
        selection = if (hit != null) SelectionState(setOf(hit)) else SelectionState()
        isPropertiesPanelOpen = hit != null
    }

    private fun findAt(pos: PcbPoint, r: Float): String? {
        val elems: List<Any> = if (editorMode == EditorMode.SCHEMATIC)
            project.schematic.elements else project.board.elements
        for (el in elems.reversed()) {
            when (el) {
                is SchematicComponent -> if (dist(pos,el.position) < r*2) return el.id
                is Footprint          -> if (dist(pos,el.position) < r*3) return el.id
                is SchematicWire      -> if (segDist(pos,el.start,el.end) < r) return el.id
                is Track              -> if (segDist(pos,el.start,el.end) < el.width/2+r) return el.id
            }
        }
        return null
    }

    fun deleteSelected() {
        saveUndo()
        if (editorMode == EditorMode.SCHEMATIC)
            project.schematic.elements.removeAll { it.id in selection.selectedIds }
        else
            project.board.elements.removeAll { it.id in selection.selectedIds }
        selection = SelectionState()
    }

    // ── Component placement ───────────────────────────────────────────────────
    fun startPlacingComponent(comp: LibraryComponent) {
        pendingComponent = comp
        activeTool = EditorTool.COMPONENT
    }

    private fun placePendingComponent(pos: PcbPoint) {
        val comp = pendingComponent ?: return
        saveUndo()
        project.schematic.elements.add(SchematicComponent(
            UUID.randomUUID().toString(), pos,
            generateRef(comp.category), comp.name, comp.symbolId, comp.footprintId))
        viewModelScope.launch { _snackMessage.emit("Placed ${comp.name}") }
        // stay in placement mode for next placement
    }

    fun cancelPendingComponent() { pendingComponent = null; activeTool = EditorTool.SELECT }

    private fun generateRef(cat: String): String {
        val p = when {
            cat.contains("resistor",   ignoreCase=true) -> "R"
            cat.contains("capacitor",  ignoreCase=true) -> "C"
            cat.contains("ic",         ignoreCase=true) -> "U"
            cat.contains("transistor", ignoreCase=true) -> "Q"
            cat.contains("diode",      ignoreCase=true) -> "D"
            cat.contains("connector",  ignoreCase=true) -> "J"
            else -> "X"
        }
        return "$p${project.schematic.elements.filterIsInstance<SchematicComponent>().count { it.reference.startsWith(p) } + 1}"
    }

    // ── Layer ─────────────────────────────────────────────────────────────────
    fun changeActiveLayer(l: PcbLayer) { activeLayer = l }
    fun toggleLayerVisibility(l: PcbLayer) {
        visibleLayers = if (l in visibleLayers) visibleLayers - l else visibleLayers + l
    }

    // ── Misc ──────────────────────────────────────────────────────────────────
    fun snapToGrid(p: PcbPoint) = PcbPoint(
        kotlin.math.round(p.x/gridSize)*gridSize, kotlin.math.round(p.y/gridSize)*gridSize)
    fun toggleGrid()              { showGrid = !showGrid }
    fun toggleLibraryPanel()      { isLibraryPanelOpen = !isLibraryPanelOpen }
    fun togglePropertiesPanel()   { isPropertiesPanelOpen = !isPropertiesPanelOpen }

    fun runDrc() = viewModelScope.launch {
        val v = mutableListOf<DrcViolation>()
        val dr = project.board.designRules
        project.board.elements.filterIsInstance<Track>().filter { it.width < dr.minTrackWidth }
            .forEach { v.add(DrcViolation(DrcType.CLEARANCE,"Track ${it.width}mm < ${dr.minTrackWidth}mm",it.start)) }
        drcViolations = v
        _snackMessage.emit("DRC: ${v.count { it.severity==DrcSeverity.ERROR }} errors")
    }

    private fun saveUndo() {
        undoStack.addLast(project.copy())
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }
    fun undo() { if (undoStack.isNotEmpty()) { redoStack.addLast(project.copy()); project = undoStack.removeLast() } }
    fun redo() { if (redoStack.isNotEmpty()) { undoStack.addLast(project.copy()); project = redoStack.removeLast() } }

    private fun dist(a: PcbPoint, b: PcbPoint) = sqrt((a.x-b.x).pow(2)+(a.y-b.y).pow(2))
    private fun segDist(p: PcbPoint, a: PcbPoint, b: PcbPoint): Float {
        val dx=b.x-a.x; val dy=b.y-a.y
        if (dx==0f && dy==0f) return dist(p,a)
        val t=((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy)
        return dist(p, PcbPoint(a.x+t.coerceIn(0f,1f)*dx, a.y+t.coerceIn(0f,1f)*dy))
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pcbdroid.data.model.*
import com.pcbdroid.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun PcbEditorScreen(vm: PcbEditorViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val scope    = rememberCoroutineScope()
    val haptic   = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        vm.snackMessage.collect { scope.launch { snackbar.showSnackbar(it) } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            PcbTopBar(
                projectName  = vm.project.name,
                editorMode   = vm.editorMode,
                onModeChange = vm::changeEditorMode,
                // ── Undo/Redo: teruskan state dari HistoryManager ──────────
                canUndo      = vm.canUndo,
                canRedo      = vm.canRedo,
                undoLabel    = vm.undoLabel,   // "Undo Add Wire"
                redoLabel    = vm.redoLabel,   // "Redo Delete R1"
                isModified   = vm.isModified,  // titik merah saat ada perubahan
                onUndo       = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.undo() },
                onRedo       = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.redo() },
                onFitView    = vm::fitView,
                onDrc        = vm::runDrc,
                onLibrary    = vm::toggleLibraryPanel,
                onSave       = { /* TODO: save project */ vm.history.markSaved() }
            )
        },
        containerColor = Color(0xFF1A1A2E)
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            // Canvas utama
            when (vm.editorMode) {
                EditorMode.SCHEMATIC -> SchematicCanvas(vm)
                EditorMode.LAYOUT    -> LayoutCanvas(vm)
                EditorMode.THREED    -> View3DScreen(vm)
            }

            // Tool palette kiri
            if (vm.editorMode != EditorMode.THREED) {
                ToolPalette(
                    mode         = vm.editorMode,
                    activeTool   = vm.activeTool,
                    onToolSelected = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.setTool(it)
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                )
            }

            // Layer panel kanan (hanya di layout mode)
            if (vm.editorMode == EditorMode.LAYOUT) {
                LayerPanel(
                    layers            = PcbLayer.allLayers,
                    activeLayer       = vm.activeLayer,
                    visibleLayers     = vm.visibleLayers,
                    onLayerClick      = vm::changeActiveLayer,
                    onToggleVisibility = vm::toggleLayerVisibility,
                    modifier          = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                )
            }

            // Status bar bawah
            StatusBar(
                tool      = vm.activeTool,
                gridSize  = vm.gridSize,
                scale     = vm.canvasTransform.scale,
                drcErrors = vm.drcViolations.count { it.severity == DrcSeverity.ERROR },
                undoCount = vm.history.undoCount,
                modifier  = Modifier.align(Alignment.BottomCenter)
            )

            // Library panel (slide dari kanan)
            AnimatedVisibility(
                visible  = vm.isLibraryPanelOpen,
                enter    = slideInHorizontally { it },
                exit     = slideOutHorizontally { it },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                LibraryPanel(
                    onSelected = { comp ->
                        vm.startPlacingComponent(comp)
                        vm.toggleLibraryPanel()
                    },
                    onClose = vm::toggleLibraryPanel
                )
            }

            // Properties panel (slide dari bawah)
            AnimatedVisibility(
                visible  = vm.isPropertiesPanelOpen,
                enter    = slideInVertically { it },
                exit     = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PropertiesPanel(
                    selectedIds = vm.selection.selectedIds,
                    project     = vm.project,
                    onClose     = vm::togglePropertiesPanel,
                    onDelete    = vm::deleteSelected
                )
            }
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
fun PcbTopBar(
    projectName  : String,
    editorMode   : EditorMode,
    onModeChange : (EditorMode) -> Unit,
    // Undo/Redo state — semua dari HistoryManager
    canUndo      : Boolean,
    canRedo      : Boolean,
    undoLabel    : String,   // "Undo Add Wire"
    redoLabel    : String,   // "Redo Delete R1"
    isModified   : Boolean,
    onUndo       : () -> Unit,
    onRedo       : () -> Unit,
    onFitView    : () -> Unit,
    onDrc        : () -> Unit,
    onLibrary    : () -> Unit,
    onSave       : () -> Unit
) {
    Column {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(projectName, style = MaterialTheme.typography.titleMedium)
                    // Titik merah = ada perubahan belum disimpan
                    if (isModified) {
                        Surface(shape = MaterialTheme.shapes.extraSmall,
                            color = Color(0xFFFF5252)) {
                            Text("●", color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
            },
            actions = {
                // ── Undo button ─────────────────────────────────────────────
                // canUndo → disable saat stack kosong
                // undoLabel → tooltip "Undo Add Wire"
                TooltipButton(
                    icon      = Icons.Default.Undo,
                    tooltip   = undoLabel,
                    enabled   = canUndo,
                    tint      = if (canUndo) Color(0xFF00D4FF) else Color(0xFF546E7A),
                    onClick   = onUndo
                )

                // ── Redo button ─────────────────────────────────────────────
                TooltipButton(
                    icon      = Icons.Default.Redo,
                    tooltip   = redoLabel,
                    enabled   = canRedo,
                    tint      = if (canRedo) Color(0xFF00D4FF) else Color(0xFF546E7A),
                    onClick   = onRedo
                )

                IconButton(onClick = onFitView)  { Icon(Icons.Default.ZoomOutMap, "Fit View") }
                IconButton(onClick = onLibrary)  { Icon(Icons.Default.Add, "Library") }
                IconButton(onClick = onDrc)      { Icon(Icons.Default.CheckCircle, "DRC") }
                IconButton(onClick = onSave)     { Icon(Icons.Default.Save, "Save") }
                IconButton(onClick = {})         { Icon(Icons.Default.MoreVert, "More") }
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor      = Color(0xFF16213E),
                titleContentColor   = Color.White,
                actionIconContentColor = Color(0xFF90A4AE)
            )
        )

        // Mode tabs: Schematic / Layout / 3D
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF0F3460))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorMode.values().forEach { mode ->
                val selected = mode == editorMode
                Surface(
                    onClick = { onModeChange(mode) },
                    shape   = MaterialTheme.shapes.small,
                    color   = if (selected) Color(0xFF00D4FF) else Color.Transparent,
                    border  = if (!selected) BorderStroke(1.dp, Color(0xFF00D4FF).copy(.4f)) else null,
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            when (mode) {
                                EditorMode.SCHEMATIC -> Icons.Default.Edit
                                EditorMode.LAYOUT    -> Icons.Default.GridView
                                EditorMode.THREED    -> Icons.Default.Refresh
                            },
                            null,
                            tint     = if (selected) Color.Black else Color(0xFF00D4FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(mode.name,
                            color = if (selected) Color.Black else Color(0xFF00D4FF),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ─── Tooltip Button ───────────────────────────────────────────────────────────
// Tombol dengan tooltip yang muncul saat long-press

@Composable
fun TooltipButton(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    tooltip : String,
    enabled : Boolean = true,
    tint    : Color   = Color(0xFF00D4FF),
    onClick : () -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, tooltip, tint = tint)
        }
        // Tooltip sederhana (long-press)
        if (showTooltip) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 36.dp),
                color    = Color(0xFF2A2A3A),
                shape    = MaterialTheme.shapes.extraSmall,
                shadowElevation = 4.dp
            ) {
                Text(tooltip, color = Color.White,
                    style    = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

// ─── Tool Palette ─────────────────────────────────────────────────────────────

@Composable
fun ToolPalette(
    mode: EditorMode, activeTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit, modifier: Modifier = Modifier
) {
    val tools = when (mode) {
        EditorMode.SCHEMATIC -> listOf(
            EditorTool.SELECT, EditorTool.WIRE, EditorTool.BUS, EditorTool.LABEL,
            EditorTool.POWER, EditorTool.COMPONENT, EditorTool.JUNCTION,
            EditorTool.NO_CONNECT, EditorTool.TEXT)
        EditorMode.LAYOUT -> listOf(
            EditorTool.SELECT, EditorTool.ROUTE_SINGLE, EditorTool.ROUTE_INTERACTIVE,
            EditorTool.ROUTE_DIFF_PAIR, EditorTool.ADD_VIA, EditorTool.POUR_ZONE,
            EditorTool.TEXT, EditorTool.MEASURE, EditorTool.DRC)
        else -> emptyList()
    }
    Surface(
        modifier        = modifier,
        color           = Color(0xFF16213E).copy(.95f),
        shape           = MaterialTheme.shapes.medium,
        shadowElevation = 8.dp,
        border          = BorderStroke(1.dp, Color(0xFF00D4FF).copy(.2f))
    ) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            tools.forEach { ToolButton(it, it == activeTool) { onToolSelected(it) } }
        }
    }
}

@Composable
fun ToolButton(tool: EditorTool, selected: Boolean, onClick: () -> Unit) {
    val icon = when (tool) {
        EditorTool.SELECT            -> Icons.Default.NearMe
        EditorTool.WIRE              -> Icons.Default.ShowChart
        EditorTool.BUS               -> Icons.Default.AccountTree
        EditorTool.LABEL             -> Icons.Default.Label
        EditorTool.POWER             -> Icons.Default.Bolt
        EditorTool.COMPONENT         -> Icons.Default.Widgets
        EditorTool.JUNCTION          -> Icons.Default.CircleNotifications
        EditorTool.NO_CONNECT        -> Icons.Default.Close
        EditorTool.TEXT              -> Icons.Default.TextFormat
        EditorTool.ROUTE_SINGLE      -> Icons.Default.Timeline
        EditorTool.ROUTE_INTERACTIVE -> Icons.Default.AutoFixHigh
        EditorTool.ROUTE_DIFF_PAIR   -> Icons.Default.CompareArrows
        EditorTool.ADD_VIA           -> Icons.Default.RadioButtonChecked
        EditorTool.POUR_ZONE         -> Icons.Default.FormatColorFill
        EditorTool.MEASURE           -> Icons.Default.Straighten
        EditorTool.DRC               -> Icons.Default.FactCheck
        else                         -> Icons.Default.Build
    }
    Surface(
        onClick      = onClick,
        modifier     = Modifier.size(40.dp),
        shape        = MaterialTheme.shapes.small,
        color        = if (selected) Color(0xFF00D4FF) else Color.Transparent,
        contentColor = if (selected) Color.Black else Color(0xFFB0BEC5)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, tool.name, Modifier.size(20.dp))
        }
    }
}

// ─── Status Bar ───────────────────────────────────────────────────────────────

@Composable
fun StatusBar(
    tool      : EditorTool,
    gridSize  : Float,
    scale     : Float,
    drcErrors : Int,
    undoCount : Int,          // jumlah aksi di undo stack
    modifier  : Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = Color(0x881A1A2E)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tool: ${tool.name}", color = Color(0xFF00D4FF),
                style = MaterialTheme.typography.labelSmall)
            Text("Grid: ${gridSize}mil", color = Color(0xFF90A4AE),
                style = MaterialTheme.typography.labelSmall)
            Text("Zoom: ${"%.1f".format(scale * 100)}%", color = Color(0xFF90A4AE),
                style = MaterialTheme.typography.labelSmall)
            // Tampilkan berapa aksi yang bisa di-undo
            Text("History: $undoCount", color = Color(0xFF546E7A),
                style = MaterialTheme.typography.labelSmall)
            if (drcErrors > 0)
                Text("⚠ $drcErrors DRC", color = Color(0xFFFF5252),
                    style = MaterialTheme.typography.labelSmall)
            else
                Text("✓ No DRC", color = Color(0xFF69F0AE),
                    style = MaterialTheme.typography.labelSmall)
        }
    }
}

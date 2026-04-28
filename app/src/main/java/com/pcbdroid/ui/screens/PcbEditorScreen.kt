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
import com.pcbdroid.data.model.*
import com.pcbdroid.ui.components.*
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcbEditorScreen(viewModel: PcbEditorViewModel = viewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.snackMessage.collect { scope.launch { snackbarHostState.showSnackbar(it) } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PcbTopBar(
                projectName = viewModel.project.name,
                editorMode  = viewModel.editorMode,
                onModeChange = viewModel::changeEditorMode,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onFitView = viewModel::fitView,
                onDrc = viewModel::runDrc,
                onLibrary = viewModel::toggleLibraryPanel
            )
        },
        containerColor = Color(0xFF1A1A2E)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            when (viewModel.editorMode) {
                EditorMode.SCHEMATIC -> SchematicCanvas(viewModel)
                EditorMode.LAYOUT    -> LayoutCanvas(viewModel)
                EditorMode.THREED    -> View3DScreen(viewModel)
            }

            if (viewModel.editorMode != EditorMode.THREED) {
                ToolPalette(
                    mode = viewModel.editorMode,
                    activeTool = viewModel.activeTool,
                    onToolSelected = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setTool(it)
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                )
            }

            if (viewModel.editorMode == EditorMode.LAYOUT) {
                LayerPanel(
                    layers = PcbLayer.allLayers,
                    activeLayer = viewModel.activeLayer,
                    visibleLayers = viewModel.visibleLayers,
                    onLayerClick = viewModel::changeActiveLayer,
                    onToggleVisibility = viewModel::toggleLayerVisibility,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                )
            }

            StatusBar(
                tool = viewModel.activeTool,
                gridSize = viewModel.gridSize,
                scale = viewModel.canvasTransform.scale,
                drcErrors = viewModel.drcViolations.count { it.severity == DrcSeverity.ERROR },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            AnimatedVisibility(
                visible = viewModel.isLibraryPanelOpen,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                LibraryPanel(
                    onSelected = { viewModel.startPlacingComponent(it); viewModel.toggleLibraryPanel() },
                    onClose = viewModel::toggleLibraryPanel
                )
            }

            AnimatedVisibility(
                visible = viewModel.isPropertiesPanelOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PropertiesPanel(
                    selectedIds = viewModel.selection.selectedIds,
                    project = viewModel.project,
                    onClose = viewModel::togglePropertiesPanel,
                    onDelete = viewModel::deleteSelected
                )
            }
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcbTopBar(
    projectName: String, editorMode: EditorMode,
    onModeChange: (EditorMode) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit,
    onFitView: () -> Unit, onDrc: () -> Unit, onLibrary: () -> Unit
) {
    Column {
        TopAppBar(
            title = { Text(projectName, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { IconButton(onClick = {}) { Icon(Icons.Default.Menu, "Menu") } },
            actions = {
                IconButton(onClick = onUndo)    { Icon(Icons.Default.Undo,      "Undo") }
                IconButton(onClick = onRedo)    { Icon(Icons.Default.Redo,      "Redo") }
                IconButton(onClick = onFitView) { Icon(Icons.Default.ZoomOutMap,"Fit")  }
                IconButton(onClick = onLibrary) { Icon(Icons.Default.Add,       "Library") }
                IconButton(onClick = onDrc)     { Icon(Icons.Default.CheckCircle,"DRC")  }
                IconButton(onClick = {})        { Icon(Icons.Default.Save,      "Save") }
                IconButton(onClick = {})        { Icon(Icons.Default.MoreVert,  "More") }
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = Color(0xFF16213E),
                titleContentColor = Color.White,
                actionIconContentColor = Color(0xFF00D4FF)
            )
        )
        // Mode tabs
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF0F3460))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorMode.values().forEach { mode ->
                val selected = mode == editorMode
                Surface(
                    onClick = { onModeChange(mode) },
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) Color(0xFF00D4FF) else Color.Transparent,
                    border = if (!selected) BorderStroke(1.dp, Color(0xFF00D4FF).copy(0.4f)) else null,
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val icon = when (mode) {
                            EditorMode.SCHEMATIC -> Icons.Default.Edit
                            EditorMode.LAYOUT    -> Icons.Default.GridView
                            EditorMode.THREED    -> Icons.Default.Refresh
                        }
                        Icon(icon, null,
                            tint = if (selected) Color.Black else Color(0xFF00D4FF),
                            modifier = Modifier.size(14.dp))
                        Text(mode.name,
                            color = if (selected) Color.Black else Color(0xFF00D4FF),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
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
        modifier = modifier,
        color = Color(0xFF16213E).copy(alpha = 0.95f),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFF00D4FF).copy(0.2f))
    ) {
        Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            tools.forEach { tool -> ToolButton(tool, tool == activeTool) { onToolSelected(tool) } }
        }
    }
}

@Composable
fun ToolButton(tool: EditorTool, selected: Boolean, onClick: () -> Unit) {
    val icon = when (tool) {
        EditorTool.SELECT           -> Icons.Default.NearMe
        EditorTool.WIRE             -> Icons.Default.ShowChart
        EditorTool.BUS              -> Icons.Default.AccountTree
        EditorTool.LABEL            -> Icons.Default.Label
        EditorTool.POWER            -> Icons.Default.Bolt
        EditorTool.COMPONENT        -> Icons.Default.Widgets
        EditorTool.JUNCTION         -> Icons.Default.CircleNotifications
        EditorTool.NO_CONNECT       -> Icons.Default.Close
        EditorTool.TEXT             -> Icons.Default.TextFormat
        EditorTool.ROUTE_SINGLE     -> Icons.Default.Timeline
        EditorTool.ROUTE_INTERACTIVE-> Icons.Default.AutoFixHigh
        EditorTool.ROUTE_DIFF_PAIR  -> Icons.Default.CompareArrows
        EditorTool.ADD_VIA          -> Icons.Default.RadioButtonChecked
        EditorTool.POUR_ZONE        -> Icons.Default.FormatColorFill
        EditorTool.MEASURE          -> Icons.Default.Straighten
        EditorTool.DRC              -> Icons.Default.FactCheck
        else                        -> Icons.Default.Build
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) Color(0xFF00D4FF) else Color.Transparent,
        contentColor = if (selected) Color.Black else Color(0xFFB0BEC5)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, tool.name, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Status Bar ───────────────────────────────────────────────────────────────
@Composable
fun StatusBar(tool: EditorTool, gridSize: Float, scale: Float, drcErrors: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = Color(0x881A1A2E)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tool: ${tool.name}", color = Color(0xFF00D4FF), style = MaterialTheme.typography.labelSmall)
            Text("Grid: ${gridSize}mil", color = Color(0xFF90A4AE), style = MaterialTheme.typography.labelSmall)
            Text("Zoom: ${"%.1f".format(scale * 100)}%", color = Color(0xFF90A4AE), style = MaterialTheme.typography.labelSmall)
            if (drcErrors > 0)
                Text("⚠ $drcErrors DRC", color = Color(0xFFFF5252), style = MaterialTheme.typography.labelSmall)
            else
                Text("✓ No DRC errors", color = Color(0xFF69F0AE), style = MaterialTheme.typography.labelSmall)
        }
    }
}

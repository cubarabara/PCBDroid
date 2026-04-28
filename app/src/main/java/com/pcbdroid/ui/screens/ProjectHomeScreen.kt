@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.screens

import android.app.Application
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pcbdroid.data.model.*
import com.pcbdroid.domain.project.ProjectManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─── Home ViewModel ───────────────────────────────────────────────────────────

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    var projects by mutableStateOf<List<ProjectManager.ProjectInfo>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set

    init { loadProjects() }

    fun loadProjects() {
        viewModelScope.launch {
            isLoading = true
            projects = ProjectManager.listProjects(getApplication())
            isLoading = false
        }
    }

    fun deleteProject(path: String) {
        ProjectManager.delete(path)
        loadProjects()
    }
}

// ─── Project Home Screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectHomeScreen(
    onOpenEditor: (PcbProject) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    var showNewDialog by remember { mutableStateOf(false) }
    var showBoardSetup by remember { mutableStateOf(false) }
    var pendingProject by remember { mutableStateOf<PcbProject?>(null) }

    if (showNewDialog) {
        NewProjectDialog(
            onConfirm = { name ->
                val p = PcbProject(id = UUID.randomUUID().toString(), name = name)
                pendingProject = p
                showNewDialog = false
                showBoardSetup = true
            },
            onDismiss = { showNewDialog = false }
        )
    }

    if (showBoardSetup && pendingProject != null) {
        com.pcbdroid.ui.components.BoardSetupDialog(
            currentRules = pendingProject!!.board.designRules,
            onConfirm = { w, h, layers, rules ->
                val pts = boardOutlinePoints(w, h)
                val board = pendingProject!!.board.copy(
                    designRules = rules,
                    elements = mutableListOf<PcbElement>().also { list ->
                        if (pts.size >= 2) list.add(
                            BoardOutline(UUID.randomUUID().toString(),
                                pts[0], PcbLayer.EDGE_CUTS, pts))
                    }
                )
                val project = pendingProject!!.copy(board = board)
                showBoardSetup = false
                pendingProject = null
                onOpenEditor(project)
            },
            onDismiss = {
                showBoardSetup = false
                pendingProject?.let { onOpenEditor(it) }
                pendingProject = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PCBDroid", style = MaterialTheme.typography.titleLarge)
                        Text("v1.0 — Professional PCB Design",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF546E7A))
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, null)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = Color(0xFF16213E),
                    titleContentColor = Color(0xFF00D4FF),
                    actionIconContentColor = Color(0xFF546E7A)
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewDialog = true },
                containerColor = Color(0xFF00D4FF),
                contentColor = Color.Black,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Project", style = MaterialTheme.typography.labelMedium) }
            )
        },
        containerColor = Color(0xFF1A1A2E)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard("Projects", "${vm.projects.size}", Color(0xFF00D4FF), Modifier.weight(1f))
                    StatCard("Layers", "2-8", Color(0xFF00FF88), Modifier.weight(1f))
                    StatCard("Format", "KiCad", Color(0xFFFFD740), Modifier.weight(1f))
                }
            }

            // Feature pills
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Schematic" to Color(0xFF4CAF50),
                        "PCB Layout" to Color(0xFF2196F3),
                        "3D View" to Color(0xFFFF9800),
                        "Auto Router" to Color(0xFF9C27B0),
                        "DRC/ERC" to Color(0xFFFF5252),
                        "Gerber Export" to Color(0xFFFFD740),
                        "SnapMagic" to Color(0xFF00E676),
                        "KiCad Import" to Color(0xFF26C6DA)
                    ).forEach { (label, color) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = color.copy(alpha = .12f),
                            border = BorderStroke(1.dp, color.copy(alpha = .3f))
                        ) {
                            Text(label, color = color,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }

            // Recent projects
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Projects", color = Color(0xFF00D4FF),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f))
                    if (vm.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF00D4FF),
                            strokeWidth = 2.dp)
                    }
                }
            }

            if (vm.projects.isEmpty() && !vm.isLoading) {
                item {
                    EmptyProjectsCard { showNewDialog = true }
                }
            } else {
                items(vm.projects) { info ->
                    ProjectCard(
                        info = info,
                        onOpen = {
                            ProjectManager.load(info.filePath).getOrNull()?.let { onOpenEditor(it) }
                        },
                        onDelete = { vm.deleteProject(info.filePath) }
                    )
                }
            }

            // Quick start templates
            item {
                Text("Quick Start", color = Color(0xFF00D4FF),
                    style = MaterialTheme.typography.titleSmall)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickStartCard("Blank Schematic", Icons.Default.Grain,
                        Color(0xFF00D4FF), Modifier.weight(1f)) { showNewDialog = true }
                    QuickStartCard("Blank PCB", Icons.Default.Apps,
                        Color(0xFF00FF88), Modifier.weight(1f)) { showNewDialog = true }
                    QuickStartCard("Import KiCad", Icons.Default.Folder,
                        Color(0xFFFFD740), Modifier.weight(1f)) { /* file picker */ }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun boardOutlinePoints(wMm: Float, hMm: Float): List<PcbPoint> {
    val w = wMm / 0.0254f; val h = hMm / 0.0254f
    return listOf(
        PcbPoint(0f, 0f), PcbPoint(w, 0f),
        PcbPoint(w, h), PcbPoint(0f, h), PcbPoint(0f, 0f)
    )
}

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = Color(0xFF16213E),
        border = BorderStroke(1.dp, color.copy(alpha=.2f))) {
        Column(modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace)
            Text(label, color = Color(0xFF546E7A), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ProjectCard(
    info: ProjectManager.ProjectInfo,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateStr = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())
        .format(Date(info.modifiedAt))

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF16213E),
            title = { Text("Delete Project?", color = Color.White) },
            text = { Text("\"${info.name}\" will be permanently deleted.",
                color = Color(0xFF90A4AE)) },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color(0xFF90A4AE))
                }
            }
        )
    }

    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF16213E),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF2A3F5F))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF00D4FF).copy(alpha=.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Grain, null, tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(24.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (info.schElementCount > 0)
                        Text("SCH:${info.schElementCount}", color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    if (info.boardElementCount > 0)
                        Text("PCB:${info.boardElementCount}", color = Color(0xFF2196F3),
                            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    Text(dateStr, color = Color(0xFF546E7A),
                        style = MaterialTheme.typography.labelSmall)
                }
            }

            IconButton(onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFF546E7A),
                    modifier = Modifier.size(16.dp))
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = Color(0xFF546E7A),
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun EmptyProjectsCard(onCreate: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF16213E),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF2A3F5F).copy(alpha=.5f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Add, null, tint = Color(0xFF2A3F5F),
                modifier = Modifier.size(48.dp))
            Text("No projects yet", color = Color(0xFF546E7A),
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))) {
                Text("Create First Project", color = Color.Black)
            }
        }
    }
}

@Composable
fun QuickStartCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = modifier,
        color = Color(0xFF16213E), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha=.25f))) {
        Column(modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.labelSmall,
                maxLines = 1)
        }
    }
}

// ─── New Project Dialog ───────────────────────────────────────────────────────

@Composable
fun NewProjectDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213E),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Add, null, tint = Color(0xFF00D4FF))
                Text("New Project", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Project Name") },
                    placeholder = { Text("My PCB Design", color = Color(0xFF546E7A)) },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFF00D4FF),
                        unfocusedBorderColor = Color(0xFF2A3F5F),
                        focusedLabelColor = Color(0xFF00D4FF), unfocusedLabelColor = Color(0xFF546E7A),
                        cursorColor = Color(0xFF00D4FF))
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
            ) { Text("Create", color = Color.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF90A4AE)) }
        }
    )
}

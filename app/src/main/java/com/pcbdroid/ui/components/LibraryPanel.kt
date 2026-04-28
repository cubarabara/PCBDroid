@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pcbdroid.data.model.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// ─── ViewModel ────────────────────────────────────────────────────────────────
class LibraryViewModel : ViewModel() {
    var query       by mutableStateOf("")
    var results     by mutableStateOf<List<LibraryComponent>>(emptyList())
    var isLoading   by mutableStateOf(false)
    var error       by mutableStateOf<String?>(null)
    var source      by mutableStateOf(LibrarySource.LOCAL)

    val builtIn = listOf(
        LibraryComponent("R_0402","R_0402","Resistor 0402","","","Resistor",
            "Device:R","Resistor_SMD:R_0402_1005Metric",source=LibrarySource.LOCAL),
        LibraryComponent("C_0402","C_0402","Capacitor 0402","","","Capacitor",
            "Device:C","Capacitor_SMD:C_0402_1005Metric",source=LibrarySource.LOCAL),
        LibraryComponent("LED_0805","LED_0805","LED 0805","","","Diode",
            "Device:LED","LED_SMD:LED_0805_2012Metric",source=LibrarySource.LOCAL),
        LibraryComponent("STM32F103","STM32F103C8T6","ARM Cortex-M3",
            "STMicro","STM32F103C8T6","IC",
            "MCU_ST_STM32F1:STM32F103C8Tx","Package_QFP:LQFP-48_7x7mm_P0.5mm",
            source=LibrarySource.LOCAL),
        LibraryComponent("ESP32","ESP32-WROOM-32","WiFi+BT Module",
            "Espressif","ESP32-WROOM-32","Module",
            "RF_Module:ESP32-WROOM-32","RF_Module:ESP32-WROOM-32",source=LibrarySource.LOCAL),
        LibraryComponent("AMS1117","AMS1117-3.3","LDO 3.3V",
            "AMS","AMS1117-3.3","IC",
            "Regulator_Linear:AMS1117-3.3","Package_TO_SOT_SMD:SOT-223-3_TabPin2",
            source=LibrarySource.LOCAL),
        LibraryComponent("USB_C","USB_C_Receptacle","USB-C Connector","","","Connector",
            "Connector_USB:USB_C_Receptacle","Connector_USB:USB_C_Receptacle_GCT_USB4105",
            source=LibrarySource.LOCAL),
        LibraryComponent("ATMEGA328","ATmega328P","AVR 8-bit MCU",
            "Microchip","ATmega328P-AU","IC",
            "MCU_Microchip_ATmega:ATmega328P-AU","Package_QFP:TQFP-32_7x7mm_P0.8mm",
            source=LibrarySource.LOCAL),
    )


    fun search() {
        if (query.isBlank()) { results = emptyList(); return }
        viewModelScope.launch {
            isLoading = true; error = null
            results = when (source) {
                LibrarySource.LOCAL -> builtIn.filter {
                    it.name.contains(query, true) || it.description.contains(query, true) ||
                    it.mpn.contains(query, true)
                }
                LibrarySource.SNAPMAGIC -> searchSnapMagic(query)
                else -> emptyList()
            }
            isLoading = false
        }
    }

    private suspend fun searchSnapMagic(q: String): List<LibraryComponent> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://www.snapmagic.com/api/v1/components/search?q=${
                    java.net.URLEncoder.encode(q, "UTF-8")}&limit=20"
                val resp = OkHttpClient().newCall(
                    Request.Builder().url(url).header("Accept","application/json").build()
                ).execute()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val arr = JSONObject(body).optJSONArray("components") ?: return@withContext emptyList()
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    LibraryComponent(
                        id = o.optString("uid",""), name = o.optString("name",""),
                        description = o.optString("description",""),
                        manufacturer = o.optString("manufacturer",""),
                        mpn = o.optString("mpn",""), category = o.optString("category",""),
                        symbolId = o.optString("symbol_id",""),
                        footprintId = o.optString("footprint_id",""),
                        model3dUrl = o.optString("3d_model_url",null),
                        datasheet = o.optString("datasheet_url",null),
                        source = LibrarySource.SNAPMAGIC
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
}

// ─── Library Panel UI ─────────────────────────────────────────────────────────
@Composable
fun LibraryPanel(
    onSelected: (LibraryComponent) -> Unit,
    onClose: () -> Unit,
    vm: LibraryViewModel = viewModel()
) {
    Surface(
        modifier = Modifier.fillMaxHeight().width(320.dp),
        color = Color(0xFF16213E),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF0F3460)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LibraryAdd, null, tint = Color(0xFF00D4FF))
                Spacer(Modifier.width(8.dp))
                Text("Component Library", color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = Color(0xFF90A4AE))
                }
            }

            // Source tabs
            TabRow(
                selectedTabIndex = listOf(LibrarySource.LOCAL,
                    LibrarySource.SNAPMAGIC).indexOf(vm.source).coerceAtLeast(0),
                containerColor = Color(0xFF0D1B2A),
                contentColor = Color(0xFF00D4FF)
            ) {
                listOf("Local" to LibrarySource.LOCAL,
                    "SnapMagic" to LibrarySource.SNAPMAGIC).forEach { (label, src) ->
                    Tab(selected = vm.source == src, onClick = { { vm.source = src; vm.search() } },
                        text = { Text(label, style = MaterialTheme.typography.labelSmall) })
                }
            }

            // Search
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text("Search components...", color = Color(0xFF546E7A)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF546E7A)) },
                trailingIcon = {
                    if (vm.query.isNotEmpty())
                        IconButton(onClick = { vm.query = "" }) {
                            Icon(Icons.Default.Clear, null, tint = Color(0xFF546E7A))
                        }
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor   = Color(0xFF00D4FF),
                    unfocusedBorderColor = Color(0xFF546E7A),
                    textColor            = Color.White,
                    cursorColor          = Color(0xFF00D4FF)
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() })
            )

            // List
            Box(modifier = Modifier.weight(1f)) {
                when {
                    vm.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF00D4FF))
                    vm.error != null -> Text(vm.error!!, color = Color(0xFFFF5252),
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        style = MaterialTheme.typography.bodySmall)
                    else -> {
                        val items = if (vm.query.isBlank()) vm.builtIn else vm.results
                        if (items.isEmpty() && vm.query.isNotBlank()) {
                            Text("No results found", color = Color(0xFF546E7A),
                                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(items) { comp ->
                                    ComponentCard(comp) { onSelected(comp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComponentCard(comp: LibraryComponent, onClick: () -> Unit) {
    val color = when (comp.source) {
        LibrarySource.SNAPMAGIC -> Color(0xFF00E676)
        LibrarySource.LOCAL     -> Color(0xFF40C4FF)
        else                    -> Color(0xFFFFD740)
    }
    Surface(onClick = onClick, color = Color(0xFF1E2D4A),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.1f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(when {
                        comp.category.contains("Resistor",true) -> Icons.Default.LinearScale
                        comp.category.contains("Capacitor",true) -> Icons.Default.BatteryChargingFull
                        comp.category.contains("IC",true) -> Icons.Default.Memory
                        comp.category.contains("Connector",true) -> Icons.Default.SettingsEthernet
                        else -> Icons.Default.ElectricalServices
                    }, null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(comp.name, color = Color.White,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (comp.description.isNotBlank())
                    Text(comp.description, color = Color(0xFF90A4AE),
                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                if (comp.manufacturer.isNotBlank())
                    Text("${comp.manufacturer} · ${comp.mpn}",
                        color = Color(0xFF546E7A),
                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.AddCircle, "Add", tint = Color(0xFF00D4FF))
            }
        }
    }
}

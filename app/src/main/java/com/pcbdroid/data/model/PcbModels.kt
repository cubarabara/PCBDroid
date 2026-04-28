package com.pcbdroid.data.model

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

// ─── Coordinate ───────────────────────────────────────────────────────────────
@Serializable
data class PcbPoint(val x: Float, val y: Float) {
    operator fun plus(o: PcbPoint)  = PcbPoint(x + o.x, y + o.y)
    operator fun minus(o: PcbPoint) = PcbPoint(x - o.x, y - o.y)
    fun toOffset() = Offset(x, y)
}

// ─── Layers ───────────────────────────────────────────────────────────────────
@Serializable
enum class PcbLayer(val displayName: String, val color: Long, val zIndex: Int) {
    F_CU("F.Cu",        0xFFB5121B, 0),
    B_CU("B.Cu",        0xFF006FAD, 1),
    F_SILKSCREEN("F.SilkS", 0xFFFFFFFF, 2),
    B_SILKSCREEN("B.SilkS", 0xFFC2C2C2, 3),
    F_COURTYARD("F.CrtYd",  0xFFFF26FF, 4),
    B_COURTYARD("B.CrtYd",  0xFF26E5E5, 5),
    F_FABRICATION("F.Fab",  0xFF8080FF, 6),
    B_FABRICATION("B.Fab",  0xFF00808C, 7),
    EDGE_CUTS("Edge.Cuts",  0xFFFFFF00, 8),
    IN1_CU("In1.Cu",        0xFF52284B, 9),
    IN2_CU("In2.Cu",        0xFF43522B, 10),
    DRILL("Drill",          0xFFFFFFFF, 11);

    companion object {
        val allLayers get() = values().toList()
    }
}

// ─── Net ──────────────────────────────────────────────────────────────────────
@Serializable
data class Net(val id: Int, val name: String, val color: Long = 0xFF00FF00)

// ─── Schematic Elements ───────────────────────────────────────────────────────
@Serializable
sealed class SchematicElement {
    abstract val id: String
    abstract var position: PcbPoint
}

@Serializable
data class SchematicComponent(
    override val id: String,
    override var position: PcbPoint,
    val reference: String,
    val value: String,
    val symbolId: String,
    val footprintId: String,
    var rotation: Float = 0f,
    val pins: List<ComponentPin> = emptyList(),
    val properties: Map<String, String> = emptyMap()
) : SchematicElement()

@Serializable
data class SchematicWire(
    override val id: String,
    override var position: PcbPoint,
    val start: PcbPoint,
    val end: PcbPoint,
    val netId: Int = -1
) : SchematicElement()

@Serializable
data class SchematicLabel(
    override val id: String,
    override var position: PcbPoint,
    val text: String,
    val netId: Int = -1,
    val labelType: LabelType = LabelType.LOCAL
) : SchematicElement()

@Serializable
data class SchematicJunction(
    override val id: String,
    override var position: PcbPoint
) : SchematicElement()

@Serializable
data class PowerSymbol(
    override val id: String,
    override var position: PcbPoint,
    val netName: String,
    val symbolType: PowerType = PowerType.GND
) : SchematicElement()

@Serializable
data class BusLine(
    override val id: String,
    override var position: PcbPoint,
    val points: List<PcbPoint>
) : SchematicElement()

@Serializable
data class NoConnect(
    override val id: String,
    override var position: PcbPoint
) : SchematicElement()

enum class LabelType { LOCAL, GLOBAL, HIERARCHICAL }
enum class PowerType  { GND, VCC, PWR_FLAG, CUSTOM }

// ─── Pin ──────────────────────────────────────────────────────────────────────
@Serializable
data class ComponentPin(
    val number: String,
    val name: String,
    val position: PcbPoint,
    val direction: PinDirection,
    val type: PinType = PinType.BIDIRECTIONAL,
    var connectedNetId: Int = -1
)
enum class PinDirection { LEFT, RIGHT, UP, DOWN }
enum class PinType { INPUT, OUTPUT, BIDIRECTIONAL, POWER_IN, POWER_OUT, PASSIVE,
    OPEN_COLLECTOR, OPEN_EMITTER, NOT_CONNECTED }

// ─── PCB Layout Elements ──────────────────────────────────────────────────────
@Serializable
sealed class PcbElement {
    abstract val id: String
    abstract var position: PcbPoint
    abstract val layer: PcbLayer
}

@Serializable
data class Footprint(
    override val id: String,
    override var position: PcbPoint,
    override val layer: PcbLayer = PcbLayer.F_CU,
    val reference: String,
    val value: String,
    val footprintId: String,
    var rotation: Float = 0f,
    val pads: List<Pad> = emptyList(),
    val courtyard: List<PcbPoint> = emptyList(),
    val silkscreen: List<SilkLine> = emptyList(),
    val model3dPath: String? = null
) : PcbElement()

@Serializable
data class Pad(
    val number: String,
    val position: PcbPoint,
    val size: PcbPoint,
    val shape: PadShape = PadShape.ROUND,
    val type: PadType = PadType.SMD,
    val drill: Float = 0f,
    var netId: Int = -1,
    val layers: List<PcbLayer> = listOf(PcbLayer.F_CU)
)
enum class PadShape { ROUND, RECT, OVAL, TRAPEZOID, ROUNDRECT }
enum class PadType  { SMD, THROUGH_HOLE, EDGE_CONNECTOR, NPTH }

@Serializable
data class SilkLine(val start: PcbPoint, val end: PcbPoint, val width: Float = 0.12f)

@Serializable
data class Track(
    override val id: String,
    override var position: PcbPoint,
    override val layer: PcbLayer,
    val start: PcbPoint,
    val end: PcbPoint,
    val width: Float,
    val netId: Int
) : PcbElement()

@Serializable
data class Via(
    override val id: String,
    override var position: PcbPoint,
    override val layer: PcbLayer = PcbLayer.F_CU,
    val size: Float,
    val drill: Float,
    val netId: Int,
    val fromLayer: PcbLayer = PcbLayer.F_CU,
    val toLayer: PcbLayer = PcbLayer.B_CU
) : PcbElement()

@Serializable
data class Zone(
    override val id: String,
    override var position: PcbPoint,
    override val layer: PcbLayer,
    val points: List<PcbPoint>,
    val netId: Int,
    val fillType: ZoneFillType = ZoneFillType.SOLID,
    val clearance: Float = 0.508f
) : PcbElement()

@Serializable
data class BoardOutline(
    override val id: String,
    override var position: PcbPoint,
    override val layer: PcbLayer = PcbLayer.EDGE_CUTS,
    val points: List<PcbPoint>
) : PcbElement()

@Serializable
data class TextLabel(
    override val id: String,
    override var position: PcbPoint,
    override val layer: PcbLayer,
    val text: String,
    val size: Float = 1.0f,
    var rotation: Float = 0f
) : PcbElement()

enum class ZoneFillType { SOLID, HATCHED }

// ─── Ratsnest ─────────────────────────────────────────────────────────────────
data class Ratsnest(
    val fromPad: Pair<String, String>,
    val toPad: Pair<String, String>,
    val netId: Int,
    val fromPos: PcbPoint,
    val toPos: PcbPoint
)

// ─── DRC ──────────────────────────────────────────────────────────────────────
data class DrcViolation(
    val type: DrcType,
    val description: String,
    val position: PcbPoint,
    val severity: DrcSeverity = DrcSeverity.ERROR
)
enum class DrcType     { CLEARANCE, SHORT_CIRCUIT, UNCONNECTED_NET, FOOTPRINT_OVERLAP, BOARD_EDGE }
enum class DrcSeverity { ERROR, WARNING, INFO }

// ─── Library Component ────────────────────────────────────────────────────────
@Serializable
data class LibraryComponent(
    val id: String,
    val name: String,
    val description: String,
    val manufacturer: String = "",
    val mpn: String = "",
    val category: String = "",
    val symbolId: String,
    val footprintId: String,
    val model3dUrl: String? = null,
    val datasheet: String? = null,
    val tags: List<String> = emptyList(),
    val source: LibrarySource = LibrarySource.LOCAL
)
enum class LibrarySource { LOCAL, SNAPMAGIC, ULTRA_LIBRARIAN, OCTOPART, USER_IMPORTED }

// ─── Project ──────────────────────────────────────────────────────────────────
@Serializable
data class PcbProject(
    val id: String,
    val name: String,
    val schematic: SchematicSheet = SchematicSheet(),
    val board: PcbBoard = PcbBoard(),
    val nets: List<Net> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SchematicSheet(
    val elements: MutableList<SchematicElement> = mutableListOf(),
    val pageSize: String = "A4"
)

@Serializable
data class PcbBoard(
    val elements: MutableList<PcbElement> = mutableListOf(),
    val layers: List<PcbLayer> = PcbLayer.allLayers,
    val designRules: DesignRules = DesignRules()
)

@Serializable
data class DesignRules(
    val minTrackWidth: Float = 0.25f,
    val minClearance: Float  = 0.25f,
    val minViaDrill: Float   = 0.3f,
    val minViaSize: Float    = 0.6f,
    val copperWeight: Float  = 35f
)

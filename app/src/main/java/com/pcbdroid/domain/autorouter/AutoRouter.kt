package com.pcbdroid.domain.autorouter

import com.pcbdroid.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.LinkedList
import java.util.UUID
import kotlin.math.*

// ─── Config ───────────────────────────────────────────────────────────────────

data class RouterConfig(
    val trackWidth: Float   = 0.25f,   // mm
    val clearance: Float    = 0.25f,
    val viaSize: Float      = 0.8f,
    val viaDrill: Float     = 0.4f,
    val gridStep: Float     = 0.25f,
    val allowVias: Boolean  = true,
    val routingAngle: RoutingAngle = RoutingAngle.FORTY_FIVE,
    val maxPasses: Int      = 5
)

enum class RoutingAngle { ORTHOGONAL, FORTY_FIVE }

data class RouterProgress(
    val total: Int        = 0,
    val routed: Int       = 0,
    val failed: Int       = 0,
    val current: String   = "",
    val done: Boolean     = false,
    val elapsedMs: Long   = 0L
) {
    val pct: Float get() = if (total == 0) 0f else routed.toFloat() / total
}

// ─── Router ───────────────────────────────────────────────────────────────────

class PcbAutoRouter(
    private val board: PcbBoard,
    private val nets: List<Net>,
    private val cfg: RouterConfig
) {
    private val _progress = MutableStateFlow(RouterProgress())
    val progress: StateFlow<RouterProgress> = _progress.asStateFlow()

    private val LAYERS = 2
    private val GRID_W = 300
    private val GRID_H = 300
    private lateinit var grid: Array<Array<BooleanArray>>  // [layer][y][x]

    suspend fun route(): Pair<List<Track>, List<Via>> = withContext(Dispatchers.Default) {
        val t0 = System.currentTimeMillis()
        grid = Array(LAYERS) { Array(GRID_H) { BooleanArray(GRID_W) } }
        markObstacles()

        val connections = buildConnections()
        _progress.value = RouterProgress(total = connections.size)

        val allTracks = mutableListOf<Track>()
        val allVias   = mutableListOf<Via>()
        var routed = 0; var failed = 0

        connections.forEachIndexed { idx, conn ->
            if (!isActive) return@forEachIndexed
            val netName = nets.find { it.id == conn.netId }?.name ?: "Net${conn.netId}"
            _progress.value = RouterProgress(connections.size, routed, failed, netName,
                elapsedMs = System.currentTimeMillis() - t0)

            val result = routePair(conn.from, conn.to, conn.netId)
            if (result != null) {
                allTracks.addAll(result.first); allVias.addAll(result.second)
                markOnGrid(result.first, result.second)
                routed++
            } else failed++

            delay(1)
        }

        _progress.value = RouterProgress(connections.size, routed, failed, done = true,
            elapsedMs = System.currentTimeMillis() - t0)

        Pair(allTracks, allVias)
    }

    // ── Obstacles ─────────────────────────────────────────────────────────────

    private fun markObstacles() {
        board.elements.filterIsInstance<Track>().forEach { t ->
            val li = if (t.layer == PcbLayer.F_CU) 0 else 1
            bresenham(wg(t.start.x), wg(t.start.y), wg(t.end.x), wg(t.end.y)) { x,y -> setG(li,y,x) }
        }
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            fp.pads.forEach { pad ->
                val gx = wg(fp.position.x + pad.position.x)
                val gy = wg(fp.position.y + pad.position.y)
                val pr = ceil(pad.size.x / cfg.gridStep / 2).toInt() + 1
                repeat(LAYERS) { l -> for (dy in -pr..pr) for (dx in -pr..pr) setG(l, gy+dy, gx+dx) }
            }
        }
    }

    private data class Connection(val from: PcbPoint, val to: PcbPoint, val netId: Int)

    private fun buildConnections(): List<Connection> {
        val res = mutableListOf<Connection>()
        val padsByNet = mutableMapOf<Int, MutableList<PcbPoint>>()
        board.elements.filterIsInstance<Footprint>().forEach { fp ->
            fp.pads.filter { it.netId >= 0 }.forEach { pad ->
                padsByNet.getOrPut(pad.netId) { mutableListOf() }
                    .add(PcbPoint(fp.position.x + pad.position.x, fp.position.y + pad.position.y))
            }
        }
        padsByNet.forEach { (netId, pads) ->
            for (i in 0 until pads.size - 1)
                res.add(Connection(pads[i], pads[i+1], netId))
        }
        return res
    }

    // ── Lee BFS ───────────────────────────────────────────────────────────────

    private fun routePair(from: PcbPoint, to: PcbPoint, netId: Int): Pair<List<Track>, List<Via>>? {
        val sx = wg(from.x).coerceIn(0, GRID_W-1)
        val sy = wg(from.y).coerceIn(0, GRID_H-1)
        val ex = wg(to.x).coerceIn(0, GRID_W-1)
        val ey = wg(to.y).coerceIn(0, GRID_H-1)
        if (sx == ex && sy == ey) return Pair(emptyList(), emptyList())

        val dist = Array(LAYERS) { Array(GRID_H) { IntArray(GRID_W) { Int.MAX_VALUE } } }
        val prev = Array(LAYERS) { Array(GRID_H) { arrayOfNulls<Triple<Int,Int,Int>>(GRID_W) } }
        val queue: LinkedList<Triple<Int,Int,Int>> = LinkedList() // x,y,layer

        dist[0][sy][sx] = 0
        queue.add(Triple(sx, sy, 0))

        val dx45 = intArrayOf(1,1,0,-1,-1,-1,0,1)
        val dy45 = intArrayOf(0,1,1,1,0,-1,-1,-1)
        val dxOr = intArrayOf(1,0,-1,0)
        val dyOr = intArrayOf(0,1,0,-1)
        val dxA = if (cfg.routingAngle == RoutingAngle.FORTY_FIVE) dx45 else dxOr
        val dyA = if (cfg.routingAngle == RoutingAngle.FORTY_FIVE) dy45 else dyOr

        var found = false
        var endLayer = 0

        while (queue.isNotEmpty() && !found) {
            val (cx,cy,cl) = queue.poll()!!
            val cd = dist[cl][cy][cx]

            for (i in dxA.indices) {
                val nx = cx + dxA[i]; val ny = cy + dyA[i]
                if (nx !in 0 until GRID_W || ny !in 0 until GRID_H) continue
                if (grid[cl][ny][nx]) continue
                val nd = cd + 1
                if (nd < dist[cl][ny][nx]) {
                    dist[cl][ny][nx] = nd; prev[cl][ny][nx] = Triple(cx,cy,cl)
                    queue.add(Triple(nx,ny,cl))
                    if (nx == ex && ny == ey) { found = true; endLayer = cl; break }
                }
            }
            // Via
            if (cfg.allowVias) {
                val ol = 1 - cl
                if (!grid[ol][cy][cx]) {
                    val nd = cd + 5
                    if (nd < dist[ol][cy][cx]) {
                        dist[ol][cy][cx] = nd; prev[ol][cy][cx] = Triple(cx,cy,cl)
                        queue.add(Triple(cx,cy,ol))
                    }
                }
            }
        }

        if (!found) return null

        // Traceback
        val path = mutableListOf<Triple<Int,Int,Int>>()
        var cur: Triple<Int,Int,Int>? = Triple(ex, ey, endLayer)
        while (cur != null) {
            path.add(cur)
            val (x,y,l) = cur
            cur = prev[l][y][x]
        }
        path.reverse()

        return pathToTracksVias(path, netId)
    }

    private fun pathToTracksVias(path: List<Triple<Int,Int,Int>>, netId: Int): Pair<List<Track>, List<Via>> {
        val tracks = mutableListOf<Track>()
        val vias   = mutableListOf<Via>()
        var i = 0
        while (i < path.size - 1) {
            val (cx,cy,cl) = path[i]; val (nx,ny,nl) = path[i+1]
            if (cl != nl) {
                vias.add(Via(UUID.randomUUID().toString(),
                    PcbPoint(gw(cx), gw(cy)), size=cfg.viaSize, drill=cfg.viaDrill, netId=netId,
                    fromLayer = if(cl==0) PcbLayer.F_CU else PcbLayer.B_CU,
                    toLayer   = if(nl==0) PcbLayer.F_CU else PcbLayer.B_CU))
                i++; continue
            }
            val layer = if (cl==0) PcbLayer.F_CU else PcbLayer.B_CU
            val ddx = nx-cx; val ddy = ny-cy
            var j = i+1
            while (j < path.size-1 && path[j].third == cl
                && path[j].first-path[j-1].first == ddx
                && path[j].second-path[j-1].second == ddy) j++
            val end = path[j-1]
            tracks.add(Track(UUID.randomUUID().toString(),
                PcbPoint(gw(cx),gw(cy)), layer,
                PcbPoint(gw(cx),gw(cy)), PcbPoint(gw(end.first),gw(end.second)),
                cfg.trackWidth, netId))
            i = j
        }
        return Pair(tracks, vias)
    }

    private fun markOnGrid(tracks: List<Track>, vias: List<Via>) {
        tracks.forEach { t ->
            val l = if (t.layer==PcbLayer.F_CU) 0 else 1
            bresenham(wg(t.start.x),wg(t.start.y),wg(t.end.x),wg(t.end.y)) { x,y -> setG(l,y,x) }
        }
        vias.forEach { v -> val gx=wg(v.position.x); val gy=wg(v.position.y); repeat(LAYERS){l->setG(l,gy,gx)} }
    }

    private fun wg(w: Float): Int = (w / cfg.gridStep).toInt().coerceIn(0, GRID_W-1)
    private fun gw(g: Int): Float = g * cfg.gridStep
    private fun setG(l: Int, y: Int, x: Int) { if (l in 0 until LAYERS && y in 0 until GRID_H && x in 0 until GRID_W) grid[l][y][x]=true }

    private fun bresenham(x0:Int,y0:Int,x1:Int,y1:Int, action:(Int,Int)->Unit) {
        var x=x0; var y=y0
        val dx=abs(x1-x0); val dy=abs(y1-y0)
        val sx=if(x0<x1)1 else -1; val sy=if(y0<y1)1 else -1
        var err=dx-dy
        while(true) { action(x,y); if(x==x1&&y==y1) break; val e2=2*err; if(e2>-dy){err-=dy;x+=sx}; if(e2<dx){err+=dx;y+=sy} }
    }
}

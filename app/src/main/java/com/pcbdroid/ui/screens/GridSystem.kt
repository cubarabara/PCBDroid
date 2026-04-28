package com.pcbdroid.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pcbdroid.data.model.PcbPoint

object GridSystem {

    val GRID_SIZES = listOf(5f, 10f, 25f, 50f, 100f, 200f, 500f)
    const val DEFAULT_GRID = 50f
    const val FINE_GRID    = 10f
    private const val MIN_PIX_DOT  = 6f
    private const val MIN_PIX_LINE = 8f

    fun snap(value: Float, gridSize: Float): Float =
        Math.round(value / gridSize).toFloat() * gridSize

    fun snap(point: PcbPoint, gridSize: Float): PcbPoint =
        PcbPoint(snap(point.x, gridSize), snap(point.y, gridSize))

    fun snapBest(point: PcbPoint, coarseGrid: Float): PcbPoint {
        val cx = snap(point.x, coarseGrid); val cy = snap(point.y, coarseGrid)
        val fx = snap(point.x, FINE_GRID);  val fy = snap(point.y, FINE_GRID)
        return PcbPoint(
            if (Math.abs(point.x - cx) <= Math.abs(point.x - fx)) cx else fx,
            if (Math.abs(point.y - cy) <= Math.abs(point.y - fy)) cy else fy
        )
    }

    fun DrawScope.drawSchematicGrid(t: CanvasTransform, gridSize: Float) {
        val ps = gridSize * t.scale
        when {
            ps >= MIN_PIX_LINE -> drawLineGrid(t, gridSize)
            ps >= MIN_PIX_DOT  -> drawDotGrid(t, gridSize)
            else -> GRID_SIZES.firstOrNull { it * t.scale >= MIN_PIX_DOT }
                ?.let { drawDotGrid(t, it) }
        }
        val o = t.worldToScreen(PcbPoint(0f, 0f))
        drawLine(Color(0xFF546E7A), o - Offset(10f,0f), o + Offset(10f,0f), 1f)
        drawLine(Color(0xFF546E7A), o - Offset(0f,10f), o + Offset(0f,10f), 1f)
    }

    private fun DrawScope.drawLineGrid(t: CanvasTransform, gs: Float) {
        val px = gs * t.scale
        val sx = ((-t.offsetX/px).toInt()-1)*px + t.offsetX
        val sy = ((-t.offsetY/px).toInt()-1)*px + t.offsetY
        var x = sx; while(x <= size.width+px)  { drawLine(Color(0x30FFFFFF), Offset(x,0f), Offset(x,size.height), 0.5f); x+=px }
        var y = sy; while(y <= size.height+px) { drawLine(Color(0x30FFFFFF), Offset(0f,y), Offset(size.width,y), 0.5f); y+=px }
    }

    private fun DrawScope.drawDotGrid(t: CanvasTransform, gs: Float) {
        val px = gs * t.scale
        val sx = ((-t.offsetX/px).toInt()-1)*px + t.offsetX
        val sy = ((-t.offsetY/px).toInt()-1)*px + t.offsetY
        var x = sx; while(x <= size.width+px) { var y=sy; while(y<=size.height+px){ drawCircle(Color(0x40FFFFFF),1f,Offset(x,y)); y+=px }; x+=px }
    }

    fun DrawScope.drawPcbGrid(t: CanvasTransform, gs: Float) {
        val px = gs * t.scale; if(px < MIN_PIX_DOT) return
        val sx = ((-t.offsetX/px).toInt()-1)*px + t.offsetX
        val sy = ((-t.offsetY/px).toInt()-1)*px + t.offsetY
        var x = sx; while(x <= size.width+px) { var y=sy; while(y<=size.height+px){ drawCircle(Color(0x50FFFFFF),1.5f,Offset(x,y)); y+=px }; x+=px }
    }
}

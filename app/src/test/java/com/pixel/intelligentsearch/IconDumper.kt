package com.pixel.intelligentsearch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Test

class IconDumper {

    @Test
    fun dumpIcons() {
        val icons = listOf(
            "Weather" to Icons.Default.WbSunny,
            "Sports" to Icons.Default.SportsBasketball,
            "Dictionary" to Icons.AutoMirrored.Filled.MenuBook,
            "Live" to Icons.Default.AutoAwesome,
            "Translate" to Icons.Default.Translate,
            "DocumentScanner" to Icons.Default.DocumentScanner
        )

        for ((name, icon) in icons) {
            println("=== $name ===")
            val root = icon.root
            dumpNode(root)
            println()
        }
    }

    private fun dumpNode(node: androidx.compose.ui.graphics.vector.VectorNode) {
        if (node is VectorPath) {
            val pathStr = StringBuilder()
            for (p in node.pathData) {
                when (p) {
                    is PathNode.MoveTo -> pathStr.append("M${p.x},${p.y} ")
                    is PathNode.RelativeMoveTo -> pathStr.append("m${p.dx},${p.dy} ")
                    is PathNode.LineTo -> pathStr.append("L${p.x},${p.y} ")
                    is PathNode.RelativeLineTo -> pathStr.append("l${p.dx},${p.dy} ")
                    is PathNode.HorizontalTo -> pathStr.append("H${p.x} ")
                    is PathNode.RelativeHorizontalTo -> pathStr.append("h${p.dx} ")
                    is PathNode.VerticalTo -> pathStr.append("V${p.y} ")
                    is PathNode.RelativeVerticalTo -> pathStr.append("v${p.dy} ")
                    is PathNode.CurveTo -> pathStr.append("C${p.x1},${p.y1} ${p.x2},${p.y2} ${p.x3},${p.y3} ")
                    is PathNode.RelativeCurveTo -> pathStr.append("c${p.dx1},${p.dy1} ${p.dx2},${p.dy2} ${p.dx3},${p.dy3} ")
                    is PathNode.ReflectiveCurveTo -> pathStr.append("S${p.x1},${p.y1} ${p.x2},${p.y2} ")
                    is PathNode.RelativeReflectiveCurveTo -> pathStr.append("s${p.dx1},${p.dy1} ${p.dx2},${p.dy2} ")
                    is PathNode.QuadTo -> pathStr.append("Q${p.x1},${p.y1} ${p.x2},${p.y2} ")
                    is PathNode.RelativeQuadTo -> pathStr.append("q${p.dx1},${p.dy1} ${p.dx2},${p.dy2} ")
                    is PathNode.ReflectiveQuadTo -> pathStr.append("T${p.x},${p.y} ")
                    is PathNode.RelativeReflectiveQuadTo -> pathStr.append("t${p.dx},${p.dy} ")
                    is PathNode.ArcTo -> pathStr.append("A${p.horizontalEllipseRadius},${p.verticalEllipseRadius} ${p.theta} ${if (p.isMoreThanHalf) 1 else 0},${if (p.isPositiveArc) 1 else 0} ${p.arcStartX},${p.arcStartY} ")
                    is PathNode.RelativeArcTo -> pathStr.append("a${p.horizontalEllipseRadius},${p.verticalEllipseRadius} ${p.theta} ${if (p.isMoreThanHalf) 1 else 0},${if (p.isPositiveArc) 1 else 0} ${p.arcStartDx},${p.arcStartDy} ")
                    is PathNode.Close -> pathStr.append("Z ")
                }
            }
            println("PATH: " + pathStr.toString().trim())
        } else if (node is VectorGroup) {
            for (child in node) {
                dumpNode(child)
            }
        }
    }
}

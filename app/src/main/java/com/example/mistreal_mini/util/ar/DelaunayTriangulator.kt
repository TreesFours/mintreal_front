package com.example.mistreal_mini.util.ar

import android.graphics.PointF

/**
 * Bowyer-Watson Delaunay triangulation over a plain point list, used once
 * per AI FaceSwap target selection (not per frame — see
 * [com.example.mistreal_mini.ui.camera.ar.ArCameraViewModel]) to turn a
 * face's [FaceMesh] points into a triangle mesh whose vertex indices get
 * reused every live frame against a different face's point positions.
 * Pure CPU math, no native/GL dependency.
 */
object DelaunayTriangulator {

    /** Each returned IntArray is exactly 3 indices into [points]. */
    fun triangulate(points: List<PointF>): List<IntArray> {
        val n = points.size
        if (n < 3) return emptyList()

        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        val deltaMax = (maxOf(maxX - minX, maxY - minY)) * 10f + 1f
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f

        // Super-triangle large enough to enclose every real point.
        val all = points.toMutableList()
        val superA = all.size; all.add(PointF(midX - 2 * deltaMax, midY - deltaMax))
        val superB = all.size; all.add(PointF(midX, midY + 2 * deltaMax))
        val superC = all.size; all.add(PointF(midX + 2 * deltaMax, midY - deltaMax))

        var triangles = mutableListOf(Triple(superA, superB, superC))

        for (pi in 0 until n) {
            val p = all[pi]
            val bad = triangles.filter { inCircumcircle(p, all[it.first], all[it.second], all[it.third]) }
            if (bad.isEmpty()) continue

            val edgeCount = HashMap<Pair<Int, Int>, Int>()
            fun addEdge(a: Int, b: Int) {
                val key = if (a < b) a to b else b to a
                edgeCount[key] = (edgeCount[key] ?: 0) + 1
            }
            for (t in bad) {
                addEdge(t.first, t.second)
                addEdge(t.second, t.third)
                addEdge(t.third, t.first)
            }
            val boundary = edgeCount.filterValues { it == 1 }.keys

            triangles = (triangles - bad).toMutableList()
            for ((a, b) in boundary) {
                triangles.add(Triple(a, b, pi))
            }
        }

        return triangles
            .filter { it.first < n && it.second < n && it.third < n }
            .map { intArrayOf(it.first, it.second, it.third) }
    }

    /** True if [p] lies inside the circumcircle of triangle (a, b, c), regardless of their winding order. */
    private fun inCircumcircle(p: PointF, a: PointF, b: PointF, c: PointF): Boolean {
        val ax = a.x - p.x; val ay = a.y - p.y
        val bx = b.x - p.x; val by = b.y - p.y
        val cx = c.x - p.x; val cy = c.y - p.y
        val aSq = ax * ax + ay * ay
        val bSq = bx * bx + by * by
        val cSq = cx * cx + cy * cy
        val det = aSq * (bx * cy - cx * by) - bSq * (ax * cy - cx * ay) + cSq * (ax * by - bx * ay)
        val orientation = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        return if (orientation > 0) det > 0 else det < 0
    }
}

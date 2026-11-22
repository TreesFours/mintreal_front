package com.example.mistreal_mini.util.ar

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * Warps [sourceBitmap] onto [canvas] triangle-by-triangle so its face lines
 * up with [destPoints] — the standard Delaunay-triangulation face-swap
 * technique. [sourcePoints]/[destPoints] must be the same length and in the
 * same order (see [FaceMesh]); [triangles] are vertex indices into both,
 * computed once by [DelaunayTriangulator] against the source face and
 * reused every call.
 *
 * Each triangle is still an independent affine warp ([Matrix.setPolyToPoly]),
 * so there are no seams *between* triangles beyond ordinary affine
 * approximation error — but the outer boundary used to be a hard cutout
 * against whatever's underneath. [destOvalPoints] (the destination face's
 * outer contour) now gets feathered with a [BlurMaskFilter] + DST_IN mask,
 * so the swap fades into the real scene at the jawline/forehead instead of
 * looking pasted on. [colorGain] (optional per-channel R/G/B multiplier)
 * nudges the source bitmap's color toward the destination's — a cheap
 * stand-in for real color transfer, not anything like OpenCV's
 * seamlessClone. Both are unverified against a real device: BlurMaskFilter
 * only renders on a hardware-accelerated canvas on API 28+ (degrades to a
 * hard edge below that, same as before — never worse), and color matching
 * here is only ever driven by a caller that actually has pixel access to
 * both faces (the still-capture bake path does; the live preview overlay
 * doesn't, since it draws on top of a separate PreviewView layer it can't
 * read pixels from).
 */
object FaceSwapCompositor {

    fun drawSwap(
        canvas: Canvas,
        sourceBitmap: Bitmap,
        sourcePoints: List<PointF>,
        destPoints: List<PointF>,
        triangles: List<IntArray>,
        destOvalPoints: List<PointF>? = null,
        colorGain: FloatArray? = null,
        featherRadiusPx: Float = 18f
    ) {
        if (sourcePoints.size != destPoints.size || sourcePoints.isEmpty()) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (colorGain != null && colorGain.size == 3) {
            paint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        colorGain[0], 0f, 0f, 0f, 0f,
                        0f, colorGain[1], 0f, 0f, 0f,
                        0f, 0f, colorGain[2], 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        val useFeather = destOvalPoints != null && destOvalPoints.size >= 3
        if (useFeather) canvas.saveLayer(null, null) else canvas.save()

        val srcArr = FloatArray(6)
        val dstArr = FloatArray(6)
        val matrix = Matrix()
        val path = Path()

        for (tri in triangles) {
            val i0 = tri[0]; val i1 = tri[1]; val i2 = tri[2]
            if (i0 >= sourcePoints.size || i1 >= sourcePoints.size || i2 >= sourcePoints.size) continue

            val s0 = sourcePoints[i0]; val s1 = sourcePoints[i1]; val s2 = sourcePoints[i2]
            val d0 = destPoints[i0]; val d1 = destPoints[i1]; val d2 = destPoints[i2]

            srcArr[0] = s0.x; srcArr[1] = s0.y
            srcArr[2] = s1.x; srcArr[3] = s1.y
            srcArr[4] = s2.x; srcArr[5] = s2.y
            dstArr[0] = d0.x; dstArr[1] = d0.y
            dstArr[2] = d1.x; dstArr[3] = d1.y
            dstArr[4] = d2.x; dstArr[5] = d2.y

            matrix.reset()
            if (!matrix.setPolyToPoly(srcArr, 0, dstArr, 0, 3)) continue

            path.reset()
            path.moveTo(d0.x, d0.y)
            path.lineTo(d1.x, d1.y)
            path.lineTo(d2.x, d2.y)
            path.close()

            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(sourceBitmap, matrix, paint)
            canvas.restore()
        }

        if (useFeather) {
            val ovalPath = Path().apply {
                moveTo(destOvalPoints!![0].x, destOvalPoints[0].y)
                for (i in 1 until destOvalPoints.size) lineTo(destOvalPoints[i].x, destOvalPoints[i].y)
                close()
            }
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                maskFilter = BlurMaskFilter(featherRadiusPx, BlurMaskFilter.Blur.NORMAL)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawPath(ovalPath, maskPaint)
        }
        canvas.restore()
    }

    /**
     * Coarse average color (0..255 per channel) sampled on a grid across
     * [points]' bounding box — deliberately the bounding box, not an exact
     * polygon fill, since this only needs to be "close enough" to drive a
     * cheap color-gain correction, not pixel-accurate.
     */
    fun averageColor(bitmap: Bitmap, points: List<PointF>): FloatArray {
        val minX = points.minOf { it.x }.toInt().coerceIn(0, bitmap.width - 1)
        val maxX = points.maxOf { it.x }.toInt().coerceIn(0, bitmap.width - 1)
        val minY = points.minOf { it.y }.toInt().coerceIn(0, bitmap.height - 1)
        val maxY = points.maxOf { it.y }.toInt().coerceIn(0, bitmap.height - 1)
        if (maxX <= minX || maxY <= minY) return floatArrayOf(128f, 128f, 128f)

        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0L
        val stepX = maxOf(1, (maxX - minX) / 40)
        val stepY = maxOf(1, (maxY - minY) / 40)
        var y = minY
        while (y <= maxY) {
            var x = minX
            while (x <= maxX) {
                val pixel = bitmap.getPixel(x, y)
                rSum += Color.red(pixel); gSum += Color.green(pixel); bSum += Color.blue(pixel)
                count++
                x += stepX
            }
            y += stepY
        }
        if (count == 0L) return floatArrayOf(128f, 128f, 128f)
        return floatArrayOf(rSum.toFloat() / count, gSum.toFloat() / count, bSum.toFloat() / count)
    }

    /** Per-channel multiplier to nudge [source]'s average color toward [dest]'s, clamped to avoid wild swings on a bad sample. */
    fun colorGainFrom(dest: FloatArray, source: FloatArray): FloatArray = floatArrayOf(
        (dest[0] / source[0].coerceAtLeast(1f)).coerceIn(0.5f, 1.8f),
        (dest[1] / source[1].coerceAtLeast(1f)).coerceIn(0.5f, 1.8f),
        (dest[2] / source[2].coerceAtLeast(1f)).coerceIn(0.5f, 1.8f)
    )
}

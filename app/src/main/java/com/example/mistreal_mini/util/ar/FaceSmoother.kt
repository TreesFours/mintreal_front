package com.example.mistreal_mini.util.ar

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.sqrt

/**
 * Softens skin texture inside [rect] (the tracked face's bounding box) on
 * an already-captured [Bitmap] — a plain 3-pass box blur (a cheap Gaussian
 * approximation, no new dependency), blended back against the original
 * pixels with an elliptical falloff centered on the box so the effect
 * fades out toward the edges instead of leaving a visible blurred
 * rectangle. Only possible on a real bitmap with real pixel access — see
 * [StickerAnchor.FACE_SMOOTH]'s doc for why this can't run on the live
 * preview. O(w·h·radius); fine for a one-off still-photo bake, unverified
 * for visual quality on a real device/real face.
 */
object FaceSmoother {

    fun smooth(bitmap: Bitmap, rect: Rect) {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val right = rect.right.coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
        val w = right - left
        val h = bottom - top
        if (w <= 4 || h <= 4) return

        val original = IntArray(w * h)
        bitmap.getPixels(original, 0, w, left, top, w, h)
        val blurred = original.copyOf()
        val radius = (minOf(w, h) / 40).coerceIn(2, 8)
        repeat(3) { boxBlurPass(blurred, w, h, radius) }

        val cx = w / 2f
        val cy = h / 2f
        val rx = w / 2f
        val ry = h / 2f
        val blended = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val nx = (x - cx) / rx
                val ny = (y - cy) / ry
                val dist = sqrt(nx * nx + ny * ny).coerceIn(0f, 1.3f)
                val t = (1f - dist / 1.3f).coerceIn(0f, 1f) // 1 at center, 0 at/outside the ellipse
                val idx = y * w + x
                blended[idx] = mixArgb(original[idx], blurred[idx], t)
            }
        }
        bitmap.setPixels(blended, 0, w, left, top, w, h)
    }

    private fun mixArgb(original: Int, blurred: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val o = (original ushr shift) and 0xFF
            val b = (blurred ushr shift) and 0xFF
            return (o + (b - o) * t).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun boxBlurPass(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val tmp = IntArray(pixels.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                for (dx in -radius..radius) {
                    val xx = x + dx
                    if (xx in 0 until w) {
                        val p = pixels[y * w + xx]
                        a += (p ushr 24) and 0xFF
                        r += (p ushr 16) and 0xFF
                        g += (p ushr 8) and 0xFF
                        b += p and 0xFF
                        count++
                    }
                }
                tmp[y * w + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                for (dy in -radius..radius) {
                    val yy = y + dy
                    if (yy in 0 until h) {
                        val p = tmp[yy * w + x]
                        a += (p ushr 24) and 0xFF
                        r += (p ushr 16) and 0xFF
                        g += (p ushr 8) and 0xFF
                        b += p and 0xFF
                        count++
                    }
                }
                pixels[y * w + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }
}

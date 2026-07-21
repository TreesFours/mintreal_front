package com.example.mistreal_mini.util

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ScreenshotHelper {
    fun captureAndSave(activity: Activity): Uri? {
        return try {
            val view: View = activity.window.decorView.rootView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            val file = File(activity.cacheDir, "screenshot_${System.currentTimeMillis()}.png")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}

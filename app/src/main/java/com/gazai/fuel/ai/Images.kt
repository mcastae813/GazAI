package com.gazai.fuel.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

object Images {
    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** Downscale (keeps EXIF orientation) and return a JPEG data URI small enough to send. */
    fun toDataUri(ctx: Context, uri: Uri, maxSide: Int = 1280): String = toDataUri(decode(ctx, uri, maxSide))

    private fun toDataUri(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** A drawn pump display showing 10.000 L for 20.00, used by Settings > Test AI connection. */
    fun testPumpPhoto(): String {
        val bmp = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(24, 24, 24))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = 40f }
        val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 196, 40)
            textSize = 100f
            typeface = Typeface.MONOSPACE
        }
        c.drawText("TOTAL SALE  $", 40f, 70f, label)
        c.drawText("20.00", 40f, 180f, digits)
        c.drawText("LITERS", 40f, 260f, label)
        c.drawText("10.000", 40f, 370f, digits)
        c.drawText("PRICE PER LITER   2.000", 40f, 460f, label)
        return toDataUri(bmp)
    }

    /** Copy a picked photo into app storage (downscaled) so it survives the picker URI expiring. */
    fun saveVehiclePhoto(ctx: Context, uri: Uri): String {
        val dir = File(ctx.filesDir, "vehicles").apply { mkdirs() }
        val file = File(dir, "vehicle_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { decode(ctx, uri, 900).compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return file.absolutePath
    }

    /** A fresh cache file plus a content URI for the camera app to write into. */
    fun newCameraUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.lastModified() < System.currentTimeMillis() - 24 * 3600_000L) it.delete() }
        val file = File(dir, "pump_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    }
}

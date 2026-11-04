package com.gazai.fuel.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

object Images {
    private fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap {
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
    fun toDataUri(ctx: Context, uri: Uri, maxSide: Int = 1280): String {
        val out = ByteArrayOutputStream()
        decode(ctx, uri, maxSide).compress(Bitmap.CompressFormat.JPEG, 80, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
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

/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.Constants
import com.dot.gallery.feature_node.domain.model.Media
import java.io.File
import java.io.IOException

@Throws(IOException::class)
fun getExifInterface(context: Context, uri: Uri): ExifInterface? {
    if (uri.isFromApps()) return null
    return try {
        ExifInterface(context.uriToPath(uri).toString())
    } catch (_: IOException) {
        null
    }
}

fun Context.uriToPath(uri: Uri?): String? {
    if (uri == null) return null
    val proj = arrayOf(MediaStore.MediaColumns.DATA)
    var path: String? = null
    val cursor: Cursor? = contentResolver.query(uri, proj, null, null, null)
    if (cursor != null && cursor.count != 0) {
        cursor.moveToFirst()
        path = try {
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            cursor.getString(columnIndex)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    cursor?.close()
    return path ?: FileUtils(this).getPath(uri)
}

fun Context.shareMedia(media: Media) {
    val uri = if (media.uri.isFromApps()) media.uri else FileProvider.getUriForFile(
        this,
        Constants.AUTHORITY,
        File(media.path)
    )
    ShareCompat
        .IntentBuilder(this)
        .setType(media.mimeType)
        .addStream(uri)
        .startChooser()
}

fun Context.shareMedia(mediaList: List<Media>) {
    val mimeTypes =
        if (mediaList.find { it.duration != null } != null) {
            if (mediaList.find { it.duration == null } != null) "video/*,image/*" else "video/*"
        } else "image/*"

    val shareCompat = ShareCompat
        .IntentBuilder(this)
        .setType(mimeTypes)
    mediaList.forEach {
        val uri = if (it.uri.isFromApps()) it.uri else FileProvider.getUriForFile(
            this,
            Constants.AUTHORITY,
            File(it.path)
        )
        shareCompat.addStream(uri)
    }
    shareCompat.startChooser()
}
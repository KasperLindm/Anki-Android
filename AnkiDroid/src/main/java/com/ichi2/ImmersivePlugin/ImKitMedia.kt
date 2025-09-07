/* **************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>                    *
 * Copyright (c) 2014–15 Roland Sieker <ospalh@gmail.com>                               *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2016 Mark Carter <mark@marcardar.com>                                  *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.immersivePlugin

import com.ichi2.immersivePlugin.ImKitApi.makeHttpRequest
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.collections.iterator

object ImKitMedia {

    fun downloadCard(
        name: String,
        urlString: String,
        folder: File,
        fileExtension: String,
    ): File? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false // we handle redirects manually
            connection.setRequestProperty("User-Agent", "Mozilla/5.0") // some servers require it
            connection.connect()

            Timber.i("Response code for $urlString: ${connection.responseCode}")

            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                    -> {
                    val newUrl = connection.getHeaderField("Location")
                    return downloadCard(name, newUrl, folder, fileExtension) // **return here**
                }
                HttpURLConnection.HTTP_OK -> {
                    if (!folder.exists()) folder.mkdirs()
                    val randomString = UUID.randomUUID().toString().take(8)
                    val file = File(folder, "${name}_$randomString.$fileExtension")

                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    file
                }
                else -> {
                    Timber.i("Failed to download file: ${connection.responseCode}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.i("Error downloading file: $urlString - ${e.message}")
            null
        }
    }

    fun downloadFile(
        name: String,
        urlString: String,
        folder: File,
        fileExtension: String,
    ): String? =
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()
            Timber.i("Response code for $urlString: ${connection.responseCode}")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                // Make sure folder exists
                if (!folder.exists()) {
                    folder.mkdirs()
                }

                // Create unique file name
                val randomString = UUID.randomUUID().toString().take(8)
                val fileName = name + "_" + "$randomString.$fileExtension"

                val file = File(folder, fileName)

                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                fileName
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.i("Error downloading file: $urlString - ${e.message}")
            null
        }

    fun extractMediaFromApkg(
        apkgFile: File,
        folder: File,
    ): Pair<File?, File?> {
        val zip = ZipFile(apkgFile)
        val mediaJson = zip.getInputStream(zip.getEntry("media")).bufferedReader().use { it.readText() }
        val mediaMap = JSONObject(mediaJson)

        var picFile: File? = null
        var audioFile: File? = null

        // Helper to check file extensions
        fun String.isImage() = endsWith(".png", true) || endsWith(".jpg", true) || endsWith(".jpeg", true)

        fun String.isAudio() = endsWith(".mp3", true) || endsWith(".wav", true)

        // Extract media files from the zip using the integer keys
        for (key in mediaMap.keys()) {
            val filename = mediaMap.getString(key)
            val entry = zip.getEntry(key) ?: continue
            val outputFile = File(folder, filename)

            outputFile.outputStream().use { output ->
                zip.getInputStream(entry).use { input ->
                    input.copyTo(output)
                }
            }

            if (picFile == null && filename.isImage()) {
                picFile = outputFile
            }
            if (audioFile == null && filename.isAudio()) {
                audioFile = outputFile
            }
        }

        return Pair(picFile, audioFile)
    }

    fun handleCardDownloadAndUpdate(
        fieldNames: Array<String>,
        note: Note,
        col: Collection,
        picFieldName: String,
        audioFieldName: String,
        keyword: String,
        mediaUrl: String,
        folder: File,
        tagBuilder: (String) -> String,
    ): Boolean {
        val picIndex = fieldNames.indexOf(picFieldName).takeIf { it >= 0 } ?: return false
        val audioIndex = fieldNames.indexOf(audioFieldName).takeIf { it >= 0 } ?: return false
        val apkgFile = downloadCard(keyword, mediaUrl, folder, "apkg")
        if (apkgFile == null) {
            return false
        }

        val (pic, audio) = extractMediaFromApkg(apkgFile, folder)

        // Only set fields if the files were actually extracted
        pic?.let { note.setField(picIndex, "<img src=\"${it.name}\">") }
        audio?.let { note.setField(audioIndex, tagBuilder("[sound:${it.name}]")) }
        val c = col.updateNote(note)

        return true
    }

    fun handleMediaDownloadAndUpdate(
        fieldNames: Array<String>,
        fieldValues: MutableList<String>,
        note: Note,
        col: Collection,
        fieldName: String,
        keyword: String,
        mediaUrl: String,
        folder: File,
        extension: String,
        tagBuilder: (String) -> String
    ): Boolean {
        val index = fieldNames.indexOf(fieldName).takeIf { it >= 0 } ?: return false
        val result = downloadFile(keyword, mediaUrl, folder, extension)
        if (result != null) {
                val fileName = File(result).name
                val content = tagBuilder(fileName)

                note.setField(index, content)
                fieldValues[index] = content
                Timber.i("${extension.uppercase()} added successfully: $content")
            } else {
                note.setField(index, "")
                fieldValues[index] = ""
                Timber.i("Failed to find $extension")
            }
            note.fields = fieldValues
            val new_col = col.updateNote(note)
        return result != null
    }
}
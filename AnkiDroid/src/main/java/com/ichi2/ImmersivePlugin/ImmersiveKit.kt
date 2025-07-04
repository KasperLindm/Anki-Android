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

package com.ichi2.anki

import android.content.Context
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.let

object ImmersiveKit {
    fun showImmersiveKit(context: Context) {
        Timber.i("ImmersiveKit:: Showing immersive kit settings")

        // Create the dialog layout
        val dialogLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 50, 50, 50)
            }

        // Create checkboxes
        val exactSearchCheckbox =
            CheckBox(context).apply {
                text = "Exact Search"
                textSize = 16f
            }

        val highlightingCheckbox =
            CheckBox(context).apply {
                text = "Highlighting"
                textSize = 16f
                isChecked = true
            }

        val dramaCheckbox =
            CheckBox(context).apply {
                text = "Check Drama"
                textSize = 16f
                isChecked = true
            }

        val animeCheckbox =
            CheckBox(context).apply {
                text = "Check Anime"
                textSize = 16f
            }

        val gamesCheckbox =
            CheckBox(context).apply {
                text = "Check Games"
                textSize = 16f
            }

        // Create RUN button
        val runButton =
            Button(context).apply {
                text = "GET SENTENCE"
                textSize = 16f
            }

        // Add views to layout
        dialogLayout.addView(exactSearchCheckbox)
        dialogLayout.addView(highlightingCheckbox)
        dialogLayout.addView(animeCheckbox)
        dialogLayout.addView(dramaCheckbox)
        dialogLayout.addView(gamesCheckbox)
        dialogLayout.addView(runButton)

        // Create and show dialog
        val dialog =
            AlertDialog
                .Builder(context)
                .setTitle("Immersion Kit Settings")
                .setView(dialogLayout)
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }.create()

        // Set RUN button click listener
        runButton.setOnClickListener {
            val exactSearch = exactSearchCheckbox.isChecked
            val highlighting = highlightingCheckbox.isChecked

            val anime = animeCheckbox.isChecked
            val drama = dramaCheckbox.isChecked
            val games = gamesCheckbox.isChecked

            Timber.i("ImmersionKit:: Running with settings - Exact: $exactSearch, Highlighting: $highlighting")

            // Make API call with these settings
            makeApiCall(context, exactSearch, highlighting, anime, drama, games)

            dialog.dismiss()
        }

        dialog.show()
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
                val fileName = "$name" + "_" + "$randomString.$fileExtension"

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
            Timber.i("Error downloading file: $urlString")
            null
        }

    fun boldKeywords(
        sentence: String,
        keywords: List<String>,
        highlight: Boolean,
    ): String {
        if (!highlight) {
            return sentence
        }

        var result = sentence
        for (keyword in keywords) {
            // Use Regex to match the keyword as a whole word and ignore case if needed
            val regex = Regex("$keyword\\[(.*?)]")
            result = result.replace(regex, "<b>$keyword</b>")
        }
        return result
    }

    private fun makeApiCall(
        context: Context,
        exactSearch: Boolean,
        highlighting: Boolean,
        anime: Boolean,
        drama: Boolean,
        games: Boolean,
    ) {
        // Show loading message
        showThemedToast(context, "Fetching sentence...", true)

        // Use coroutines for async API call
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context is AbstractFlashcardViewer) {
                    // Example keyword - you can get this from the current note
                    val card = context.currentCard
                    val col = context.getColUnsafe // Get collection from viewer
                    val note = card?.note(col) // Pass collection to note()

                    val fieldNames = note?.keys()
                    val fieldValues = note?.fields?.toMutableList()

                    val keyword =
                        fieldNames?.indexOf("Entry")?.takeIf { it >= 0 }?.let { index ->
                            fieldValues?.get(index)?.split(",")[0]
                        }
                    // Build API URL
                    val apiUrl =
                        if (exactSearch) {
                            "https://api.immersionkit.com/look_up_dictionary?keyword=「$keyword」&sort=shortness"
                        } else {
                            "https://api.immersionkit.com/look_up_dictionary?keyword=$keyword &sort="
                        }

                    // Make API call
                    val response = makeHttpRequest(apiUrl)

                    if (response != null) {
                        val jsonResponse = JSONObject(response)
                        val data = jsonResponse.optJSONArray("data")

                        if (data != null && data.length() > 0) {
                            val firstResult = data.getJSONObject(0)
                            val examples = firstResult.optJSONArray("examples")

                            if (examples != null && examples.length() > 0) {
                                val allowedSources = mutableSetOf<String>()
                                if (anime) allowedSources.add("anime")
                                if (drama) allowedSources.add("drama")
                                if (games) allowedSources.add("games")
                                val filteredExamples = mutableListOf<JSONObject>()
                                for (i in 0 until examples.length()) {
                                    val item = examples.optJSONObject(i)
                                    val sourceType = item?.optString("source_type")
                                    if (sourceType in allowedSources) {
                                        filteredExamples.add(item)
                                    }
                                }

                                // Pick example: from filteredExamples if not empty, else from original examples
                                val example =
                                    if (filteredExamples.isNotEmpty()) {
                                        filteredExamples.random()
                                    } else {
                                        examples.getJSONObject((0 until examples.length()).random())
                                    }

                                // Extract data
                                val sentenceWithFurigana =
                                    boldKeywords(example.optString("sentence_with_furigana", ""), listOf(keyword!!), highlighting)
                                val translation = example.optString("translation", "")
                                val prevSentence = example.optString("prev_sentence", "")
                                val nextSentence = example.optString("next_sentence", "")
                                val source = example.optString("deck_name", "")
                                val audioUrl = example.optString("sound_url", "")
                                val imageUrl =
                                    "https://api.immersionkit.com/download_sentence_image?id=${
                                        example.optString(
                                            "id",
                                            "",
                                        )
                                    }"

                                // Update UI on main thread
                                withContext(Dispatchers.Main) {
                                    updateNoteFields(
                                        context,
                                        sentenceWithFurigana,
                                        translation,
                                        imageUrl,
                                        audioUrl,
                                        source,
                                        prevSentence,
                                        nextSentence,
                                    )
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    showThemedToast(context, "No examples found", true)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                showThemedToast(context, "No examples found", true)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showThemedToast(context, "No examples found", true)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "ImmersiveKit:: Error making API call")
                withContext(Dispatchers.Main) {
                    showThemedToast(context, "Error: ${e.message}", true)
                }
            }
        }
    }

    private fun makeHttpRequest(urlString: String): String? =
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "HTTP request failed")
            null
        }

    private fun updateNoteFields(
        context: Context,
        sentenceWithFurigana: String,
        translation: String,
        pictureUrl: String,
        audioUrl: String,
        source: String,
        prevSentence: String,
        nextSentence: String,
    ) {
        if (context is AbstractFlashcardViewer) {
            val card = context.currentCard ?: return
            val col = context.getColUnsafe ?: return // Get collection from viewer
            val note = card.note(col) ?: return // Pass collection to note()

            val fieldNames = note.keys()
            val fieldValues = note.fields.toMutableList()

            val keyword =
                fieldNames
                    .indexOf("Entry")
                    .takeIf { it >= 0 }
                    ?.let { index -> fieldValues.get(index).split(",")[0] }

            fieldNames.indexOf("KitSentence").takeIf { it >= 0 }?.let { fieldValues[it] = sentenceWithFurigana }
            fieldNames.indexOf("KitTrans").takeIf { it >= 0 }?.let { fieldValues[it] = translation }
            fieldNames.indexOf("KitSource").takeIf { it >= 0 }?.let { fieldValues[it] = source }
            fieldNames.indexOf("KitPrev").takeIf { it >= 0 }?.let { fieldValues[it] = prevSentence }
            fieldNames.indexOf("KitNext").takeIf { it >= 0 }?.let { fieldValues[it] = nextSentence }

            val folderPath = col.media.dir
            fieldNames.indexOf("KitPic").takeIf { it >= 0 }?.let { index ->
                CoroutineScope(Dispatchers.IO).launch {
                    val result = downloadFile(keyword!!, pictureUrl, folderPath, "jpg")
                    withContext(Dispatchers.Main) {
                        if (result != null) {
                            val fileName = File(result).name
                            fieldValues[index] = """<img src="$fileName">"""
                            note.fields = fieldValues.toMutableList()
                            val no = col.updateNote(note)
                            note.setField(index, """<img src="$fileName">""")

                            Timber.i("Image added successfully: $fileName")
                        }
                    }
                }
            }
            fieldNames.indexOf("KitSound").takeIf { it >= 0 }?.let { index ->
                CoroutineScope(Dispatchers.IO).launch {
                    val result = downloadFile(keyword!!, audioUrl, folderPath, "mp3") // <-- use audioUrl here!
                    withContext(Dispatchers.Main) {
                        if (result != null) {
                            val fileName = File(result).name
                            val audioTag = "[sound:$fileName]"
                            fieldValues[index] = audioTag

                            note.fields = fieldValues.toMutableList()
                            val no = col.updateNote(note)
                            note.setField(index, audioTag)

                            context.updateCardAndRedraw()
                            Timber.i("Audio added successfully: $fileName")
                        }
                    }
                }
            }
            note.fields = fieldValues.toMutableList()
            val updatedCount = col.updateNote(note)
            Timber.d("Note updated: $updatedCount card(s) affected")

            // Refresh the card display
            context.runOnUiThread {
                context.runOnUiThread {
                    (context as Reviewer).refreshRequired
                }
                showThemedToast(context, "Fields updated!", true)
            }
        }
    }
}

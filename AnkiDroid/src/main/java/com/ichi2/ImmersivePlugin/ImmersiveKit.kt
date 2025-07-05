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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.LinearLayout.*
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
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
import android.R.layout
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.Reviewer
import com.ichi2.anki.showThemedToast

object ImmersiveKit {

    data class ImmersiveKitSettings(
        // Checkbox settings
        val exactSearch: Boolean = false,
        val highlighting: Boolean = true,
        val drama: Boolean = true,
        val anime: Boolean = false,
        val games: Boolean = false,

        // Spinner selections (field names)
        val keywordField: String = "",
        val sentenceField: String = "",
        val translationField: String = "",
        val pictureField: String = "",
        val audioField: String = "",
        val sourceField: String = "",
        val prevSentenceField: String = "",
        val nextSentenceField: String = "")

    fun showImmersiveKit(context: Context) {
        Timber.i("ImmersiveKit:: Showing immersive kit settings")

        // Get SharedPreferences for saving selections
        val prefs = context.getSharedPreferences("immersive_kit_prefs", Context.MODE_PRIVATE)

        // Create a ScrollView to handle potential overflow
        val scrollView = ScrollView(context)
        val dialogLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(32, 24, 32, 24) // Better padding
        }

        // Helper function to create labeled spinners
        fun createLabeledSpinner(labelText: String): Pair<LinearLayout, Spinner> {
            val container = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(0, 12, 0, 12) // Vertical spacing between items
            }

            val label = TextView(context).apply {
                text = labelText
                textSize = 14f
                setTextColor("#666666".toColorInt()) // Gray color for labels
                setPadding(0, 0, 0, 8) // Space between label and spinner
            }

            val spinner = Spinner(context).apply {
                setPadding(0, 8, 0, 8) // Padding inside spinner
            }

            container.addView(label)
            container.addView(spinner)
            return Pair(container, spinner)
        }

        // Create checkboxes with better styling and load saved values
        val exactSearchCheckbox = CheckBox(context).apply {
            text = "Exact Search"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = prefs.getBoolean("exact_search", false)
        }

        val highlightingCheckbox = CheckBox(context).apply {
            text = "Highlighting"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = prefs.getBoolean("highlighting", true)
        }

        val dramaCheckbox = CheckBox(context).apply {
            text = "Check Drama"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = prefs.getBoolean("drama", true)
        }

        val animeCheckbox = CheckBox(context).apply {
            text = "Check Anime"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = prefs.getBoolean("anime", false)
        }

        val gamesCheckbox = CheckBox(context).apply {
            text = "Check Games"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = prefs.getBoolean("games", false)
        }

        // Add a separator line
        val separator = View(context).apply {
            setBackgroundColor("#E0E0E0".toColorInt())
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 16, 0, 16)
            }
        }

        // Add toggle button for field mappings
        val toggleFieldsButton = Button(context).apply {
            text = "Show Field Mappings"
            textSize = 14f
            setPadding(16, 12, 16, 12)
            setBackgroundColor("#4CAF50".toColorInt()) // Green background
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }

        // Create dropdown options from card field names
        val dropdownItems = (context as AbstractFlashcardViewer).currentCard?.note?.keys()?: arrayOf("No fields available")
        val spinnerLabels = listOf(
            "Keyword",
            "Sentence Furigana",
            "Translation",
            "Picture",
            "Audio",
            "Source",
            "Previous sentence",
            "Next sentence"
        )

        val spinners = mutableListOf<Spinner>()
        val spinnerContainers = mutableListOf<LinearLayout>()

        spinnerLabels.forEach { label ->
            val (container, spinner) = createLabeledSpinner(label)
            val adapter = ArrayAdapter(context, layout.simple_spinner_item, dropdownItems)
            adapter.setDropDownViewResource(layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter

            container.visibility = GONE

            // Load saved spinner selection
            val savedSelection = prefs.getInt("spinner_${label.replace(" ", "_").lowercase()}", 0)
            if (savedSelection < dropdownItems.size) {
                spinner.setSelection(savedSelection)
            }

            spinners.add(spinner)
            spinnerContainers.add(container)
        }

        // Create RUN button with better styling
        val runButton = Button(context).apply {
            text = "GET SENTENCE"
            textSize = 16f
            setPadding(24, 16, 24, 16)
            setBackgroundColor("#2196F3".toColorInt()) // Blue background
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
            }
        }
        // Add toggle functionality
        var fieldsVisible = false
        toggleFieldsButton.setOnClickListener {
            fieldsVisible = !fieldsVisible
            val visibility = if (fieldsVisible) VISIBLE else GONE
            spinnerContainers.forEach { container ->
                container.visibility = visibility
            }
            toggleFieldsButton.text = if (fieldsVisible) "Hide Field Mappings" else "Show Field Mappings"
        }

        // Add all views to layout
        dialogLayout.addView(exactSearchCheckbox)
        dialogLayout.addView(highlightingCheckbox)
        dialogLayout.addView(dramaCheckbox)
        dialogLayout.addView(animeCheckbox)
        dialogLayout.addView(gamesCheckbox)

        // Add separator
        dialogLayout.addView(separator)
        dialogLayout.addView(toggleFieldsButton)
        // Add all spinner containers
        spinnerContainers.forEach { container ->
            dialogLayout.addView(container)
        }

        dialogLayout.addView(runButton)

        // Add the layout to ScrollView
        scrollView.addView(dialogLayout)

        // Create and show dialog
        val dialog = AlertDialog.Builder(context)
            .setTitle("Immersion Kit Settings")
            .setView(scrollView)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        // Set RUN button click listener
        runButton.setOnClickListener {
            val exactSearch = exactSearchCheckbox.isChecked
            val highlighting = highlightingCheckbox.isChecked
            val anime = animeCheckbox.isChecked
            val drama = dramaCheckbox.isChecked
            val games = gamesCheckbox.isChecked

            // Save all selections to SharedPreferences
            prefs.edit().apply {
                putBoolean("exact_search", exactSearch)
                putBoolean("highlighting", highlighting)
                putBoolean("anime", anime)
                putBoolean("drama", drama)
                putBoolean("games", games)

                // Save spinner selections
                spinners.forEachIndexed { index, spinner ->
                    val prefKey = "spinner_${spinnerLabels[index].replace(" ", "_").lowercase()}"
                    putInt(prefKey, spinner.selectedItemPosition)
                }

                apply() // Save asynchronously
            }

            // Get all spinner selections
            val spinnerSelections = spinners.mapIndexed { index, spinner -> spinner.selectedItem.toString()}

            Timber.i("ImmersiveKit:: Running with settings - Exact: $exactSearch, Highlighting: $highlighting")
            Timber.i("ImmersiveKit:: Spinner selections: ${spinnerSelections.joinToString(", ")}")

            // Make API call with these settings
            val settings = ImmersiveKitSettings(
                exactSearch,
                highlighting,
                drama,
                anime,
                games,
                spinnerSelections[0],
                spinnerSelections[1],
                spinnerSelections[2],
                spinnerSelections[3],
                spinnerSelections[4],
                spinnerSelections[5],
                spinnerSelections[6],
                spinnerSelections[7],
            )

            // Check if card is locked by tag
            if (context.currentCard?.note?.tags?.contains("Locked") == true) {
                showThemedToast(context, "Card is locked by tag - cannot update fields", true)
                dialog.dismiss()
            } else {
                makeApiCall(context, settings)
                dialog.dismiss()
            }
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
        settings : ImmersiveKitSettings
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
                        fieldNames?.indexOf(settings.keywordField)?.takeIf { it >= 0 }?.let { index ->
                            fieldValues?.get(index)?.split(",")[0]
                        }
                    // Build API URL
                    val apiUrl =
                        if (settings.exactSearch) {
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
                                if (settings.anime) allowedSources.add("anime")
                                if (settings.drama) allowedSources.add("drama")
                                if (settings.games) allowedSources.add("games")
                                val filteredExamples = mutableListOf<JSONObject>()
                                for (i in 0 until examples.length()) {
                                    val item = examples.optJSONObject(i)
                                    val sourceType = item?.optString("category")
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
                                    boldKeywords(example.optString("sentence_with_furigana", ""),
                                        listOf(keyword!!), settings.highlighting)
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
                                        settings
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

    @SuppressLint("CheckResult")
    private fun updateNoteFields(
        context: Context,
        sentenceWithFurigana: String,
        translation: String,
        pictureUrl: String,
        audioUrl: String,
        source: String,
        prevSentence: String,
        nextSentence: String,
        settings: ImmersiveKitSettings,
    ) {
        if (context is AbstractFlashcardViewer) {
            val card = context.currentCard ?: return
            val col = context.getColUnsafe // Get collection from viewer
            val note = card.note(col) // Pass collection to note()

            val fieldNames = note.keys()
            val fieldValues = note.fields.toMutableList()

            val keyword =
                fieldNames
                    .indexOf("Entry")
                    .takeIf { it >= 0 }
                    ?.let { index -> fieldValues[index].split(",")[0] }

            fieldNames.indexOf(settings.sentenceField).takeIf { it >= 0 }?.let { fieldValues[it] = sentenceWithFurigana }
            fieldNames.indexOf(settings.translationField).takeIf { it >= 0 }?.let { fieldValues[it] = translation }
            fieldNames.indexOf(settings.sourceField).takeIf { it >= 0 }?.let { fieldValues[it] = source }
            fieldNames.indexOf(settings.prevSentenceField).takeIf { it >= 0 }?.let { fieldValues[it] = prevSentence }
            fieldNames.indexOf(settings.nextSentenceField).takeIf { it >= 0 }?.let { fieldValues[it] = nextSentence }

            val folderPath = col.media.dir
            fieldNames.indexOf(settings.pictureField).takeIf { it >= 0 }?.let { index ->
                CoroutineScope(Dispatchers.IO).launch {
                    val result = downloadFile(keyword!!, pictureUrl, folderPath, "jpg")
                    withContext(Dispatchers.Main) {
                        val fileName : String
                        if (result != null) {
                            fileName = """<img src="${File(result).name}">"""
                            note.setField(index, fileName)
                            Timber.i("Image added successfully: $fileName")
                        } else {
                            note.setField(index, "")
                            fileName = ""
                            Timber.i("Failed to find image")
                        }
                        fieldValues[index] = fileName
                        note.fields = fieldValues
                        col.updateNote(note)
                    }
                }
            }
            fieldNames.indexOf(settings.audioField).takeIf { it >= 0 }?.let { index ->
                CoroutineScope(Dispatchers.IO).launch {
                    val result = downloadFile(keyword!!, audioUrl, folderPath, "mp3")
                    withContext(Dispatchers.Main) {
                        if (result != null) {
                            val fileName = File(result).name
                            val audioTag = "[sound:$fileName]"
                            fieldValues[index] = audioTag

                            note.fields = fieldValues.toMutableList()
                            col.updateNote(note)
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

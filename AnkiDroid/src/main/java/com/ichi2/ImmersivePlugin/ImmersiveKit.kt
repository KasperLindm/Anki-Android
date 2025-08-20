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

import android.R.layout
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModelProvider
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.PreviewerViewModel
import com.ichi2.anki.showThemedToast
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipFile

// TODO
/*
SET PREFERRED FIELDS PER CARD TYPE
*/

object ImmersiveKit {
    val apiUrlV2 = "https://apiv2.immersionkit.com"

    data class ImmersiveKitSettings(
        val noteType: String = "default",
        // Checkbox settings
        val exactSearch: Boolean = false,
        val highlighting: Boolean = true,
        val drama: Boolean = true,
        val anime: Boolean = false,
        val games: Boolean = false,
        // Spinner selections (field names)
        val keywordField: String = "",
        val keywordFuriganaField: String = "",
        val sentenceField: String = "",
        val translationField: String = "",
        val pictureField: String = "",
        val audioField: String = "",
        val sourceField: String = "",
        val prevSentenceField: String = "",
        val nextSentenceField: String = "",
    )

    // Helper functions for card type specific preferences
    fun getnoteTypeSpecificKey(
        baseKey: String,
        noteType: String,
    ): String = "${baseKey}_$noteType"

    fun saveNotetypeSettings(
        context: Context,
        noteType: String,
        settings: ImmersiveKitSettings,
    ) {
        val prefs = context.getSharedPreferences("immersive_kit_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            // Save boolean settings
            putBoolean(getnoteTypeSpecificKey("exact_search", noteType), settings.exactSearch)
            putBoolean(getnoteTypeSpecificKey("highlighting", noteType), settings.highlighting)
            putBoolean(getnoteTypeSpecificKey("anime", noteType), settings.anime)
            putBoolean(getnoteTypeSpecificKey("drama", noteType), settings.drama)
            putBoolean(getnoteTypeSpecificKey("games", noteType), settings.games)

            // Save field mappings
            putString(getnoteTypeSpecificKey("keyword_field", noteType), settings.keywordField)
            putString(getnoteTypeSpecificKey("keyword_furigana_field", noteType), settings.keywordFuriganaField)
            putString(getnoteTypeSpecificKey("sentence_field", noteType), settings.sentenceField)
            putString(getnoteTypeSpecificKey("translation_field", noteType), settings.translationField)
            putString(getnoteTypeSpecificKey("picture_field", noteType), settings.pictureField)
            putString(getnoteTypeSpecificKey("audio_field", noteType), settings.audioField)
            putString(getnoteTypeSpecificKey("source_field", noteType), settings.sourceField)
            putString(getnoteTypeSpecificKey("prev_sentence_field", noteType), settings.prevSentenceField)
            putString(getnoteTypeSpecificKey("next_sentence_field", noteType), settings.nextSentenceField)

            apply()
        }
    }

    fun loadnoteTypeSettings(
        context: Context,
        noteType: String,
    ): ImmersiveKitSettings {
        val prefs = context.getSharedPreferences("immersive_kit_prefs", Context.MODE_PRIVATE)

        return ImmersiveKitSettings(
            noteType = noteType,
            exactSearch = prefs.getBoolean(getnoteTypeSpecificKey("exact_search", noteType), false),
            highlighting = prefs.getBoolean(getnoteTypeSpecificKey("highlighting", noteType), true),
            drama = prefs.getBoolean(getnoteTypeSpecificKey("drama", noteType), true),
            anime = prefs.getBoolean(getnoteTypeSpecificKey("anime", noteType), false),
            games = prefs.getBoolean(getnoteTypeSpecificKey("games", noteType), false),
            keywordField = prefs.getString(getnoteTypeSpecificKey("keyword_field", noteType), "Ignore") ?: "Ignore",
            keywordFuriganaField = prefs.getString(getnoteTypeSpecificKey("keyword_furigana_field", noteType), "Ignore") ?: "Ignore",
            sentenceField = prefs.getString(getnoteTypeSpecificKey("sentence_field", noteType), "Ignore") ?: "Ignore",
            translationField = prefs.getString(getnoteTypeSpecificKey("translation_field", noteType), "Ignore") ?: "Ignore",
            pictureField = prefs.getString(getnoteTypeSpecificKey("picture_field", noteType), "Ignore") ?: "Ignore",
            audioField = prefs.getString(getnoteTypeSpecificKey("audio_field", noteType), "Ignore") ?: "Ignore",
            sourceField = prefs.getString(getnoteTypeSpecificKey("source_field", noteType), "Ignore") ?: "Ignore",
            prevSentenceField = prefs.getString(getnoteTypeSpecificKey("prev_sentence_field", noteType), "Ignore") ?: "Ignore",
            nextSentenceField = prefs.getString(getnoteTypeSpecificKey("next_sentence_field", noteType), "Ignore") ?: "Ignore",
        )
    }

    fun generateCacheKey(
        keyword: String?,
        settings: ImmersiveKitSettings,
    ): String =
        listOf(
            keyword,
            settings.exactSearch,
            settings.highlighting,
            settings.drama,
            settings.anime,
            settings.games,
            settings.keywordField,
        ).joinToString(separator = "|")

    fun saveToCache(
        context: Context,
        key: String,
        json: String,
    ) {
        val cachePrefs = context.getSharedPreferences("immersive_kit_api_cache", Context.MODE_PRIVATE)
        cachePrefs.edit {
            putString(key, json)
        }
    }

    fun getFromCache(
        context: Context,
        key: String,
    ): String? {
        val cachePrefs = context.getSharedPreferences("immersive_kit_api_cache", Context.MODE_PRIVATE)
        return cachePrefs.getString(key, null)
    }

    fun showImmersiveKit(
        context: Context,
        selectedCard: Card?,
    ) {
        Timber.i("ImmersiveKit:: Showing immersive kit settings")
        val note = selectedCard?.note
        val col = CollectionManager.getColUnsafe()

        // Get the actual card type (note type) name
        val noteType = note?.notetype.toString()

        val isLocked = note?.tags?.contains("Locked") == true
        if (isLocked) {
            showThemedToast(context, "Card is locked by tag - cannot update fields", true)
            return
        }

        // Load card type specific settings
        val currentSettings = loadnoteTypeSettings(context, noteType)

        // Create a ScrollView to handle potential overflow
        val scrollView = ScrollView(context)
        scrollView.apply {
            setPadding(0, 0, 0, 0)
        }
        Timber.i("scrollView added successfully")
        val dialogLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24) // Better padding
            }
        Timber.i("dialogLayout added successfully")

        // Helper function to create labeled spinners
        fun createLabeledSpinner(labelText: String): Pair<LinearLayout, Spinner> {
            val container =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 12, 0, 12) // Vertical spacing between items
                }

            val label =
                TextView(context).apply {
                    text = labelText
                    textSize = 14f
                    setTextColor("#666666".toColorInt()) // Gray color for labels
                    setPadding(0, 0, 0, 8) // Space between label and spinner
                }

            val spinner =
                Spinner(context).apply {
                    setPadding(0, 8, 0, 8) // Padding inside spinner
                }

            container.addView(label)
            container.addView(spinner)
            return Pair(container, spinner)
        }

        // Create checkboxes with loaded values for this card type
        val exactSearchCheckbox =
            CheckBox(context).apply {
                text = "Exact Search"
                textSize = 16f
                setPadding(0, 8, 0, 8)
                isChecked = currentSettings.exactSearch
            }

        val highlightingCheckbox =
            CheckBox(context).apply {
                text = "Highlighting"
                textSize = 16f
                setPadding(0, 8, 0, 8)
                isChecked = currentSettings.highlighting
            }

        val dramaCheckbox =
            CheckBox(context).apply {
                text = "Check Drama"
                textSize = 16f
                setPadding(0, 8, 0, 8)
                isChecked = currentSettings.drama
            }

        val animeCheckbox =
            CheckBox(context).apply {
                text = "Check Anime"
                textSize = 16f
                setPadding(0, 8, 0, 8)
                isChecked = currentSettings.anime
            }

        val gamesCheckbox =
            CheckBox(context).apply {
                text = "Check Games"
                textSize = 16f
                setPadding(0, 8, 0, 8)
                isChecked = currentSettings.games
            }

        // Add a separator line
        val separator =
            View(context).apply {
                setBackgroundColor("#E0E0E0".toColorInt())
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        2,
                    ).apply {
                        setMargins(0, 16, 0, 16)
                    }
            }

        // Add toggle button for field mappings
        val toggleFieldsButton =
            Button(context).apply {
                text = "Show Field Mappings"
                textSize = 14f
                setPadding(16, 12, 16, 12)
                setBackgroundColor("#4CAF50".toColorInt()) // Green background
                setTextColor(Color.WHITE)
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    ).apply {
                        setMargins(0, 8, 0, 0)
                    }
            }

        // Create dropdown options from card field names
        val keysArray =
            selectedCard
                ?.note
                ?.keys()
                ?.asSequence()
                ?.toList()
                ?.toTypedArray()
                ?: arrayOf("No fields available")
        val dropdownItems = arrayOf("Ignore") + keysArray
        val spinnerLabels =
            listOf(
                "Keyword (Required)",
                "Keyword with Furigana",
                "Sentence (Required)",
                "Translation",
                "Picture",
                "Audio",
                "Source",
                "Previous sentence",
                "Next sentence",
            )

        val spinners = mutableListOf<Spinner>()
        val spinnerContainers = mutableListOf<LinearLayout>()
        val adapters = mutableListOf<ArrayAdapter<String>>()

        // Create spinners with loaded settings for this card type
        val settingsFields =
            listOf(
                currentSettings.keywordField,
                currentSettings.keywordFuriganaField,
                currentSettings.sentenceField,
                currentSettings.translationField,
                currentSettings.pictureField,
                currentSettings.audioField,
                currentSettings.sourceField,
                currentSettings.prevSentenceField,
                currentSettings.nextSentenceField,
            )

        spinnerLabels.forEachIndexed { index, label ->
            val (container, spinner) = createLabeledSpinner(label)
            val adapter = ArrayAdapter(context, layout.simple_spinner_item, dropdownItems.toMutableList())
            adapter.setDropDownViewResource(layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter

            adapters.add(adapter)
            container.visibility = LinearLayout.GONE

            // Set spinner selection from loaded settings
            val savedField = settingsFields[index]
            val savedIndex = dropdownItems.indexOf(savedField)
            if (savedIndex >= 0) {
                spinner.setSelection(savedIndex)
            }

            spinners.add(spinner)
            spinnerContainers.add(container)
        }

        // Create RUN button with better styling
        val runButton =
            Button(context).apply {
                text = "GET SENTENCE"
                textSize = 16f
                setPadding(24, 16, 24, 16)
                setBackgroundColor("#2196F3".toColorInt()) // Blue background
                setTextColor(Color.WHITE)
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    ).apply {
                        setMargins(0, 16, 0, 0)
                    }
            }

        // Add toggle functionality
        var fieldsVisible = false
        toggleFieldsButton.setOnClickListener {
            fieldsVisible = !fieldsVisible
            val visibility = if (fieldsVisible) LinearLayout.VISIBLE else LinearLayout.GONE
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

        spinnerContainers.forEachIndexed { index, container ->
            if (index == 0) {
                val title =
                    TextView(context).apply {
                        text = "Keywords to Search"
                        textSize = 14f
                        setTextColor("#E0E0E0".toColorInt())
                        setPadding(0, 0, 0, 0)
                    }
                container.addView(title, 0)
            }

            if (index == 1) {
                val separator =
                    View(context).apply {
                        setBackgroundColor("#E0E0E0".toColorInt())
                        layoutParams =
                            LayoutParams(
                                LayoutParams.MATCH_PARENT,
                                2,
                            ).apply {
                                setMargins(0, 16, 0, 16)
                            }
                    }
                val label =
                    TextView(context).apply {
                        text = "Fields to Map Gathered Data to"
                        textSize = 14f
                        setTextColor("#E0E0E0".toColorInt())
                        setPadding(0, 0, 0, 0)
                    }
                container.addView(separator)
                container.addView(label)
            }
            dialogLayout.addView(container)
        }

        dialogLayout.addView(runButton)

        // Create and show dialog
        val dialog =
            AlertDialog
                .Builder(context)
                .setTitle("Immersion Kit Settings")
                .setView(scrollView)
                .create()

        // Custom cancel button
        val cancelTextButton =
            TextView(context).apply {
                text = "Cancel"
                textSize = 16f
                setTextColor("#2196F3".toColorInt()) // Match default Material color
                gravity = Gravity.END
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    ).apply {
                        setMargins(0, 8, 4, 16)
                    }
                setPadding(16, 32, 28, 14)
                setOnClickListener {
                    dialog.dismiss()
                }
            }
        dialogLayout.addView(cancelTextButton)

        // Add the layout to ScrollView
        scrollView.addView(dialogLayout)

        var isUpdatingSpinners = false

        fun updateSpinnerAdapters() {
            if (isUpdatingSpinners) return

            isUpdatingSpinners = true

            val selectedItems = spinners.map { it.selectedItem?.toString() ?: "Ignore" }.toSet()

            spinners.forEachIndexed { index, spinner ->
                val currentSelection = spinner.selectedItem?.toString() ?: "Ignore"

                // Filter dropdownItems:
                // Keep "Ignore" always
                // Keep the spinner's current selection (even if selected elsewhere)
                // Remove all other selected field names
                val filteredOptions =
                    dropdownItems.filter { option ->
                        option == "Ignore" || option == currentSelection || !selectedItems.contains(option)
                    }

                // Only reset adapter if options have actually changed (avoid unnecessary resets)
                val oldOptions =
                    (spinner.adapter as? ArrayAdapter<*>)?.let { adapter ->
                        (0 until adapter.count).map { adapter.getItem(it) }
                    }

                if (oldOptions != filteredOptions) {
                    val adapter = ArrayAdapter(spinner.context, layout.simple_spinner_item, filteredOptions)
                    adapter.setDropDownViewResource(layout.simple_spinner_dropdown_item)
                    spinner.adapter = adapter
                }

                // Set selection to currentSelection or default to "Ignore"
                val position =
                    filteredOptions.indexOf(currentSelection).takeIf { it >= 0 } ?: filteredOptions.indexOf("Ignore").takeIf { it >= 0 }
                        ?: 0
                spinner.setSelection(position)
            }

            isUpdatingSpinners = false
        }

        spinners.forEach { spinner ->
            spinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        if (!isUpdatingSpinners) {
                            updateSpinnerAdapters()
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
        }

        updateSpinnerAdapters()
        spinners.forEach { spinner ->
            spinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        updateSpinnerAdapters()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
        }

        // Modified RUN button click listener
        runButton.setOnClickListener {
            val newSettings =
                ImmersiveKitSettings(
                    noteType = noteType,
                    exactSearch = exactSearchCheckbox.isChecked,
                    highlighting = highlightingCheckbox.isChecked,
                    drama = dramaCheckbox.isChecked,
                    anime = animeCheckbox.isChecked,
                    games = gamesCheckbox.isChecked,
                    keywordField = spinners[0].selectedItem.toString(),
                    keywordFuriganaField = spinners[1].selectedItem.toString(),
                    sentenceField = spinners[2].selectedItem.toString(),
                    translationField = spinners[3].selectedItem.toString(),
                    pictureField = spinners[4].selectedItem.toString(),
                    audioField = spinners[5].selectedItem.toString(),
                    sourceField = spinners[6].selectedItem.toString(),
                    prevSentenceField = spinners[7].selectedItem.toString(),
                    nextSentenceField = spinners[8].selectedItem.toString(),
                )

            // Save settings for this card type
            saveNotetypeSettings(context, noteType, newSettings)

            Timber.i(
                "ImmersiveKit:: Running with settings for card type '$noteType' - Exact: ${newSettings.exactSearch}, Highlighting: ${newSettings.highlighting}",
            )
            Timber.i("ImmersiveKit:: Field mappings: Keyword=${newSettings.keywordField}, Sentence=${newSettings.sentenceField}")

            if (newSettings.keywordField != "Ignore" && newSettings.sentenceField != "Ignore") {
                if (selectedCard == null) {
                    showThemedToast(context, "No card selected", true)
                    dialog.dismiss()
                    return@setOnClickListener
                }
                try {
                    makeApiCall(context, selectedCard, newSettings)
                } catch (e: Exception) {
                    Timber.e(e, "Error in makeApiCall")
                    showThemedToast(context, "Failed to get sentence: ${e.localizedMessage}", true)
                }
                dialog.dismiss()
            } else {
                showThemedToast(context, "Please select a keyword and sentence field", true)
            }
        }

        dialog.show()
    }

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

    // remove the current sentence to not get a duplicate
    fun removeExampleBySentence(
        examples: JSONArray,
        stringToRemove: String,
    ): JSONArray {
        val newArray = JSONArray()
        for (i in 0 until examples.length()) {
            val item = examples.optJSONObject(i)
            if (item != null) {
                val sentence = item.optString("sentence_with_furigana")
                if (sentence != stringToRemove) {
                    newArray.put(item)
                }
            }
        }
        return newArray
    }

    fun styleKeyword(
        sentence: String,
        keywordPair: Pair<String, String>,
        highlighting: Boolean,
    ): String {
        if (!highlighting) return sentence

        val highlightingSymbol = "u"
        val plain = keywordPair.first.split(",")[0]
        val plainKanji = plain.replace(Regex("[^\\p{InCJKUnifiedIdeographs}]"), "")
        val furigana = keywordPair.second.split(",")[0]
        var stylized: String

        // 1. Try to bold kanji[furigana] pattern in sentence (e.g. 噴水[ふんすい])
        val furiganaPattern = Regex("${Regex.escape(plainKanji)}\\[[^]]+]")
        stylized =
            furiganaPattern.replace(sentence) { matchResult ->
                "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
            }
        if (stylized != sentence) {
            return stylized
        }

        // 2. Try to bold exact furigana string (from field)
        if (furigana.isNotBlank() && furigana != plain) {
            val furiganaPattern = Regex(Regex.escape(furigana))
            stylized =
                furiganaPattern.replace(sentence) { matchResult ->
                    "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
                }
            if (stylized != sentence) {
                return stylized
            }
        }

        // 3. Try to bold just the plain kanji
        if (plain.isNotBlank()) {
            val plainPattern = Regex(Regex.escape(plain))
            stylized =
                plainPattern.replace(sentence) { matchResult ->
                    "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
                }
            if (stylized != sentence) {
                return stylized
            }
        }

        // 4. Try to bold just the plain kanji, but if it has furigana attached, bold that whole block
        if (plainKanji.isNotBlank()) {
            // Try to match kanji + [furigana] in one go
            val kanjiWithFuriganaPattern = Regex("${Regex.escape(plainKanji)}\\[[^]]+]")
            stylized =
                kanjiWithFuriganaPattern.replace(sentence) { matchResult ->
                    "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
                }
            if (stylized != sentence) {
                return stylized
            }

            // If no match, just bold the kanji
            val plainPattern = Regex(Regex.escape(plainKanji))
            stylized =
                plainPattern.replace(sentence) { matchResult ->
                    "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
                }
            if (stylized != sentence) {
                return stylized
            }
        }

        // No match, return sentence as is

        return stylized
    }

    fun highlightMatchedWords(
        sentence: String,
        wordList: List<String>,
        matchedIndexes: List<Pair<Int, Int>>,
        highlighting: Boolean,
        highlightingSymbol: String = "u",
    ): String {
        if (!highlighting) return sentence

        // Step 1: Build a set of indexes to highlight
        val highlightRange = mutableSetOf<Int>()
        for ((start, len) in matchedIndexes) {
            for (i in start until (start + len)) {
                highlightRange.add(i)
            }
        }

        // Step 2: Reconstruct sentence from wordList, emboldening matched indexes
        val result = StringBuilder()
        for ((i, word) in wordList.withIndex()) {
            if (highlightRange.contains(i) && word.isNotEmpty()) {
                result.append("<$highlightingSymbol>$word</$highlightingSymbol>")
            } else {
                result.append(word)
            }
        }
        return result.toString()
    }

    fun getContext(id: String): Pair<String, String>? {
        val urlString = apiUrlV2 + "/sentence_with_context?sentenceId=$id"
        val url = URL(urlString)

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val pretext = json.optJSONArray("pretext_sentences")
                val posttext = json.optJSONArray("posttext_sentences")

                val prev =
                    if (pretext != null && pretext.length() > 0) {
                        pretext.getJSONObject(pretext.length() - 1).optString("sentence_with_furigana", "")
                    } else {
                        ""
                    }

                val next =
                    if (posttext != null && posttext.length() > 0) {
                        posttext.getJSONObject(0).optString("sentence_with_furigana", "")
                    } else {
                        ""
                    }

                return Pair(prev, next)
            } else {
                println("HTTP error: $responseCode")
                return null
            }
        }
    }

    suspend fun processAndDisplayExample(
        context: Context,
        selectedCard: Card,
        exampleData: JSONArray,
        note: Note,
        fieldNames: Array<String>,
        settings: ImmersiveKitSettings,
        keyword: String?,
        keywordFurigana: String?,
        toast: Toast,
    ) {
        val sentenceFieldIdx = fieldNames.indexOf(settings.sentenceField)
        val examples =
            removeExampleBySentence(
                exampleData,
                note.fields[sentenceFieldIdx],
            )

        if (examples.length() > 0) {
            val allowedSources = mutableSetOf<String>()
            if (settings.anime) allowedSources.add("anime")
            if (settings.drama) allowedSources.add("drama")
            if (settings.games) allowedSources.add("games")

            val filteredExamples = mutableListOf<JSONObject>()
            for (i in 0 until examples.length()) {
                val item = examples.optJSONObject(i)
                val sourceType = item?.optString("id")?.split("_")?.firstOrNull()
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
            val wordList =
                example.optJSONArray("word_list")?.let { arr ->
                    List(arr.length()) { arr.optString(it) }
                } ?: emptyList()
            val matchedIndexes =
                example.optJSONArray("matched_indexes")?.let { arr ->
                    List(arr.length()) {
                        val obj = arr.optJSONObject(it)
                        Pair(obj.optInt("index"), obj.optInt("length"))
                    }
                } ?: emptyList()

            val prevAndNext = getContext(example.optString("id", ""))
            val sentenceWithFurigana =
                styleKeyword(
                    example.optString("sentence_with_furigana", ""),
                    Pair(keyword ?: "", keywordFurigana ?: ""),
                    settings.highlighting,
                )
            val newSentenceWithFurigana =
                highlightMatchedWords(
                    example.optString("sentence_with_furigana", ""),
                    wordList,
                    matchedIndexes,
                    settings.highlighting,
                )
            val translation = example.optString("translation", "")
            val source = example.optString("title", "")
            val sourceType = example?.optString("id")?.split("_")?.firstOrNull()
            val ikId = example.optString("id", "")
            val audioUrl = example.optString("sound_url", "")
            val imageUrl =
                "https://apiv2.immersionkit.com/download_sentence_image?id=${
                    example.optString(
                        "id",
                        "",
                    )
                }"

            withContext(Dispatchers.Main) {
                if (settings.sentenceField == "Ignore") {
                    showThemedToast(context, "Sentence field is set to Ignore", true)
                    return@withContext
                }

                if (note.fields[sentenceFieldIdx] != sentenceWithFurigana) {
                    updateNoteFields(
                        context,
                        selectedCard,
                        sentenceWithFurigana,
                        translation,
                        "http://apiv2.immersionkit.com/download_sentence?id=$ikId&modelType=$sourceType",
                        example.optString("image"),
                        example.optString("sound"),
                        source,
                        prevAndNext?.first ?: "",
                        prevAndNext?.second ?: "",
                        ikId,
                        settings,
                        toast,
                    )
                } else if (sentenceFieldIdx >= 0) {
                    showThemedToast(context, "Invalid sentence", true)
                } else {
                    showThemedToast(context, "Sentence field is set to Ignore — skipping update", false)
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                showThemedToast(context, "No examples found", true)
            }
        }
    }

    @SuppressLint("DirectToastMakeTextUsage")
    private fun makeApiCall(
        context: Context,
        selectedCard: Card,
        settings: ImmersiveKitSettings,
    ) {
        val toast = Toast.makeText(context, "Fetching sentence...", Toast.LENGTH_SHORT)
        toast.show()

        // Use coroutines for async API call
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Example keyword - you can get this from the current note
                val card = selectedCard
                val note = card.note // Pass collection to note()

                val fieldNames = note?.keys()
                val fieldValues = note?.fields?.toMutableList()

                val keyword =
                    fieldNames?.indexOf(settings.keywordField)?.takeIf { it >= 0 }?.let { index ->
                        fieldValues?.get(index)?.split(",")[0]
                    }
                val keywordFurigana =
                    fieldNames?.indexOf(settings.keywordFuriganaField)?.takeIf { it >= 0 }?.let { index ->
                        fieldValues?.get(index)?.split(",")[0]
                    }

                // Build API URL
                val searchUrl =
                    if (settings.exactSearch) {
                        "$apiUrlV2/search?q=$keyword&exactMatch=true"
                    } else {
                        "$apiUrlV2/search?q=$keyword"
                    }

                // Make API call
                val response = makeHttpRequest(searchUrl)
                val cacheKey = generateCacheKey(keyword, settings)
                val cachedJson = getFromCache(context, cacheKey)

                if (response != null) {
                    val data = JSONObject(response)
                    val examples = data.optJSONArray("examples")
                    if (examples != null && examples.length() > 0) {
                        saveToCache(context, cacheKey, examples.toString())
                        processAndDisplayExample(
                            context,
                            selectedCard,
                            examples,
                            note!!,
                            fieldNames!!,
                            settings,
                            keyword,
                            keywordFurigana,
                            toast,
                        )
                    } else if (cachedJson != null) {
                        val examples = JSONArray(cachedJson)
                        processAndDisplayExample(
                            context,
                            selectedCard,
                            examples,
                            note!!,
                            fieldNames!!,
                            settings,
                            keyword,
                            keywordFurigana,
                            toast,
                        )
                    } else {
                        // Show toast here if no examples and no cached data
                        withContext(Dispatchers.Main) {
                            showThemedToast(context, "No examples found in api or cache", true)
                        }
                    }
                } else if (cachedJson != null) {
                    val examples = JSONArray(cachedJson)
                    processAndDisplayExample(
                        context,
                        selectedCard,
                        examples,
                        note!!,
                        fieldNames!!,
                        settings,
                        keyword,
                        keywordFurigana,
                        toast,
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        showThemedToast(context, "No examples found in api or cache", true)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "ImmersiveKit:: Error making API call")
                withContext(Dispatchers.Main) {
                    showThemedToast(context, "API Error: ${e.message}", true)
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

    private suspend fun refreshCard(
        context: Context,
        startToast: Toast,
    ) {
        withContext(Dispatchers.Main) {
            startToast.cancel()
            showThemedToast(context, "Fields updated!", true)
        }
        when (context) {
            is CardViewerActivity -> {
                val fragment = context.fragment
                if (fragment != null && fragment.isAdded) {
                    val viewModel = ViewModelProvider(fragment)[PreviewerViewModel::class.java]
                    withContext(Dispatchers.Main) {
                        viewModel.onPageFinished(true)
                    }
                } else {
                    Timber.w("CardViewerActivity fragment is null or not added")
                }
            }
            is AbstractFlashcardViewer -> {
                withContext(Dispatchers.Main) {
                    context.updateCardAndRedraw()
                }
            }
            else -> {
                Timber.i("ImmersionKit called unexpectedly with context: ${context::class.java.name}")
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun updateNoteFields(
        context: Context,
        selectedCard: Card,
        sentenceWithFurigana: String,
        translation: String,
        immersionCardUrl: String,
        picFileName: String,
        audioFileName: String,
        source: String,
        prevSentence: String,
        nextSentence: String,
        immersionKitId: String,
        settings: ImmersiveKitSettings,
        startToast: Toast,
    ) {
        val note = selectedCard.note!! // Pass collection to note()
        val col = CollectionManager.getColUnsafe() // Get collection from viewer

        val fieldNames = note.keys()
        val fieldValues = note.fields.toMutableList()

        val entryIndex = fieldNames.indexOf("Entry")
        val keyword =
            if (entryIndex >= 0 && fieldValues[entryIndex] != "Ignore") {
                fieldValues[entryIndex].split(",")[0]
            } else {
                null // or return here if inside a function
            }

        fun updateField(
            fieldName: String,
            newValue: String,
        ) {
            if (fieldName != "Ignore") {
                fieldNames.indexOf(fieldName).takeIf { it >= 0 }?.let {
                    fieldValues[it] = newValue
                }
            }
        }

        updateField(settings.sentenceField, sentenceWithFurigana)
        updateField(settings.translationField, translation)
        updateField(settings.sourceField, source)
        updateField(settings.prevSentenceField, prevSentence)
        updateField(settings.nextSentenceField, nextSentence)

        if (fieldNames.contains("KitID")) {
            fieldNames.indexOf("KitID").takeIf { it >= 0 }?.let { fieldValues[it] = immersionKitId }
        }

        // set gathered data
        note.fields = fieldValues.toMutableList()
        col.updateNote(note)

        // Download audio and pictures
        val folderPath = col.media.dir
        CoroutineScope(Dispatchers.IO).launch {
            val cardDeferred =
                async {
                    handleCardDownloadAndUpdate(
                        fieldNames,
                        fieldValues,
                        note,
                        col,
                        settings.pictureField,
                        settings.audioField,
                        keyword!!,
                        immersionCardUrl,
                        folderPath,
                    ) { fileName ->
                        "[sound:$fileName]"
                    }
                }
            refreshCard(context, startToast)

            val cardSuccess = cardDeferred.await()

            withContext(Dispatchers.Main) {
                if (cardSuccess) {
                    refreshCard(context, startToast)
                }
            }
        }
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

    private fun handleCardDownloadAndUpdate(
        fieldNames: Array<String>,
        fieldValues: MutableList<String>,
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

        val zip = ZipFile(apkgFile)
        val mediaJson =
            zip.getInputStream(zip.getEntry("media")).bufferedReader().use {
                it.readText()
            }
        val (pic, audio) = extractMediaFromApkg(apkgFile, folder)

        // Only set fields if the files were actually extracted
        pic?.let { note.setField(picIndex, tagBuilder(it.name)) }
        audio?.let { note.setField(audioIndex, tagBuilder(it.name)) }
        val c = col.updateNote(note)

        return true
    }
}

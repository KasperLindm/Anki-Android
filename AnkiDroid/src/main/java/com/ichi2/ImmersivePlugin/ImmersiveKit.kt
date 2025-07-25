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
import android.widget.LinearLayout.*
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import androidx.core.content.edit

// TODO
/*
IGNORE FIELDS
TOAST MESSAGE
SAME FIELD CRASH
*/

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
        val keywordFuriganaField: String = "",
        val sentenceField: String = "",
        val translationField: String = "",
        val pictureField: String = "",
        val audioField: String = "",
        val sourceField: String = "",
        val prevSentenceField: String = "",
        val nextSentenceField: String = "")

    fun generateCacheKey(keyword: String?, settings: ImmersiveKitSettings): String {
        return listOf(
            keyword,
            settings.exactSearch,
            settings.highlighting,
            settings.drama,
            settings.anime,
            settings.games,
            settings.keywordField,
        ).joinToString(separator = "|")
    }

    fun saveToCache(context: Context, key: String, json: String) {
        val cachePrefs = context.getSharedPreferences("immersive_kit_api_cache", Context.MODE_PRIVATE)
        cachePrefs.edit {
            putString(key, json)
        }
    }

    fun getFromCache(context: Context, key: String): String? {
        val cachePrefs = context.getSharedPreferences("immersive_kit_api_cache", Context.MODE_PRIVATE)
        return cachePrefs.getString(key, null)
    }

    fun showImmersiveKit(context: Context, selectedCard : Card?) {
        Timber.i("ImmersiveKit:: Showing immersive kit settings")
        val note = selectedCard?.note
        val isLocked = note?.tags?.contains("Locked") == true
        if (isLocked) {
            showThemedToast(context, "Card is locked by tag - cannot update fields", true)
            return
        }

        // Get SharedPreferences for saving selections
        val prefs = context.getSharedPreferences("immersive_kit_prefs", Context.MODE_PRIVATE)

        // Create a ScrollView to handle potential overflow
        val scrollView = ScrollView(context)
        scrollView.apply {
            setPadding(0, 0, 0, 0)
        }
        Timber.i("scrollView added successfully")
        val dialogLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(32, 24, 32, 24) // Better padding
        }
        Timber.i("dialogLayout added successfully")

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
                setMargins(0, 8, 0, 0)
            }
        }

        // Create dropdown options from card field names
        val keysArray = selectedCard?.note?.keys()?.asSequence()?.toList()?.toTypedArray()
            ?: arrayOf("No fields available")
        val dropdownItems = arrayOf("Ignore") + keysArray
        val spinnerLabels = listOf(
            "Keyword (Required)",
            "Keyword with Furigana",
            "Sentence (Required)",
            "Translation",
            "Picture",
            "Audio",
            "Source",
            "Previous sentence",
            "Next sentence"
        )

        val spinners = mutableListOf<Spinner>()
        val spinnerContainers = mutableListOf<LinearLayout>()
        val adapters = mutableListOf<ArrayAdapter<String>>()

        spinnerLabels.forEachIndexed { index, label ->
            val (container, spinner) = createLabeledSpinner(label)
            val adapter = ArrayAdapter(context, layout.simple_spinner_item, dropdownItems.toMutableList())
            adapter.setDropDownViewResource(layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter

            adapters.add(adapter)

            container.visibility = GONE

            // Load saved spinner selection
            val prefKey = "spinner_${label.replace(" ", "_").lowercase()}"
            val savedField = when (val raw = prefs.all[prefKey]) {
                is String -> raw
                is Int -> null  // Previously stored as position; now ignored
                else -> null
            } ?: "Ignore"

            val savedIndex = dropdownItems.indexOf(savedField)
            if (savedIndex >= 0) {
                spinner.setSelection(savedIndex)
            }
            if (savedIndex >= 0) {
                spinner.setSelection(savedIndex)
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

        spinnerContainers.forEachIndexed { index, container ->
            if (index == 0) {
                val title = TextView(context).apply {
                    text = "Keywords to Search"
                    textSize = 14f
                    setTextColor("#E0E0E0".toColorInt())
                    setPadding(0, 0, 0, 0)
                }
                container.addView(title, 0)
            }

            if (index == 1) {
                val separator = View(context).apply {
                    setBackgroundColor("#E0E0E0".toColorInt())
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        2
                    ).apply {
                        setMargins(0, 16, 0, 16)
                    }
                }
                val label = TextView(context).apply {
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
        val dialog = AlertDialog.Builder(context)
            .setTitle("Immersion Kit Settings")
            .setView(scrollView)
            .create()

        // Custom cancel button
        val cancelTextButton = TextView(context).apply {
            text = "Cancel"
            textSize = 16f
            setTextColor("#2196F3".toColorInt()) // Match default Material color
            gravity = Gravity.END
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
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
                val filteredOptions = dropdownItems.filter { option ->
                    option == "Ignore" || option == currentSelection || !selectedItems.contains(option)
                }

                // Only reset adapter if options have actually changed (avoid unnecessary resets)
                val oldOptions = (spinner.adapter as? ArrayAdapter<*>)?.let { adapter ->
                    (0 until adapter.count).map { adapter.getItem(it) }
                }

                if (oldOptions != filteredOptions) {
                    val adapter = ArrayAdapter(spinner.context, layout.simple_spinner_item, filteredOptions)
                    adapter.setDropDownViewResource(layout.simple_spinner_dropdown_item)
                    spinner.adapter = adapter
                }

                // Set selection to currentSelection or default to "Ignore"
                val position = filteredOptions.indexOf(currentSelection).takeIf { it >= 0 } ?: filteredOptions.indexOf("Ignore").takeIf { it >= 0 } ?: 0
                spinner.setSelection(position)
            }

            isUpdatingSpinners = false
        }

        spinners.forEach { spinner ->
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!isUpdatingSpinners) {
                        updateSpinnerAdapters()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        updateSpinnerAdapters()
        spinners.forEach { spinner ->
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateSpinnerAdapters()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

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
                    putString(prefKey, spinner.selectedItem.toString())
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
                spinnerSelections.getOrNull(0) ?: "Ignore",
                spinnerSelections.getOrNull(1) ?: "Ignore",
                spinnerSelections.getOrNull(2) ?: "Ignore",
                spinnerSelections.getOrNull(3) ?: "Ignore",
                spinnerSelections.getOrNull(4) ?: "Ignore",
                spinnerSelections.getOrNull(5) ?: "Ignore",
                spinnerSelections.getOrNull(6) ?: "Ignore",
                spinnerSelections.getOrNull(7) ?: "Ignore",
            )

            if (settings.keywordField != "Ignore" && settings.sentenceField != "Ignore") {
                if (selectedCard == null) {
                    showThemedToast(context, "No card selected", true)
                    dialog.dismiss()
                    return@setOnClickListener
                }
                try {
                    makeApiCall(context, selectedCard, settings)
                } catch (e: Exception) {
                    Timber.e(e, "Error in makeApiCall")
                    showThemedToast(context, "Failed to get sentence: ${e.localizedMessage}", true)
                }
                dialog.dismiss()
            }
            else {
                showThemedToast(context, "Please select a keyword and sentence field", true)
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

    // remove the current sentence to not get a duplicate
    fun removeExampleBySentence(examples: JSONArray, stringToRemove: String): JSONArray {
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

    fun styleKeyword(sentence: String, keywordPair: Pair<String, String>, highlighting: Boolean): String {
        if (!highlighting) return sentence

        val highlightingSymbol = "u"
        val plain = keywordPair.first.split(",")[0]
        val plainKanji = plain.replace(Regex("[^\\p{InCJKUnifiedIdeographs}]"), "")
        val furigana = keywordPair.second.split(",")[0]
        var stylized: String

        // 1. Try to bold kanji[furigana] pattern in sentence (e.g. 噴水[ふんすい])
        val furiganaPattern = Regex("${Regex.escape(plainKanji)}\\[[^]]+]")
        stylized = furiganaPattern.replace(sentence) { matchResult ->
            "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
        }
        if (stylized != sentence)
            return  stylized

        // 2. Try to bold exact furigana string (from field)
        if (furigana.isNotBlank() && furigana != plain) {
            val furiganaPattern = Regex(Regex.escape(furigana))
            stylized = furiganaPattern.replace(sentence) { matchResult ->
                "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
            }
            if (stylized != sentence)
                return  stylized
        }

        // 3. Try to bold just the plain kanji
        if (plain.isNotBlank()) {
            val plainPattern = Regex(Regex.escape(plain))
            stylized = plainPattern.replace(sentence) { matchResult ->
                    "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
            }
            if (stylized != sentence)
                return  stylized
        }

        // 4. Try to bold just the plain kanji, but if it has furigana attached, bold that whole block
        if (plainKanji.isNotBlank()) {
            // Try to match kanji + [furigana] in one go
            val kanjiWithFuriganaPattern = Regex("${Regex.escape(plainKanji)}\\[[^]]+]")
            stylized = kanjiWithFuriganaPattern.replace(sentence) { matchResult ->
                "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
            }
            if (stylized != sentence)
                return stylized

            // If no match, just bold the kanji
            val plainPattern = Regex(Regex.escape(plainKanji))
            stylized = plainPattern.replace(sentence) { matchResult ->
                "<$highlightingSymbol>${matchResult.value}</$highlightingSymbol>"
            }
            if (stylized != sentence)
                return stylized
        }

        // No match, return sentence as is
        return stylized
    }

    fun getContext(id: String): Pair<String, String>? {
        val urlString = "https://api.immersionkit.com/sentence_with_context?id=$id"
        val url = URL(urlString)

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val pretext = json.optJSONArray("pretext_sentences")
                val posttext = json.optJSONArray("posttext_sentences")

                val prev = if (pretext != null && pretext.length() > 0) {
                    pretext.getJSONObject(pretext.length() - 1).optString("sentence_with_furigana", "")
                } else ""

                val next = if (posttext != null && posttext.length() > 0) {
                    posttext.getJSONObject(0).optString("sentence_with_furigana", "")
                } else ""

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
        toast: Toast
    ) {
        val sentenceFieldIdx = fieldNames.indexOf(settings.sentenceField)
        val examples = removeExampleBySentence(
            exampleData,
            note.fields[sentenceFieldIdx]
        )

        if (examples.length() > 0) {
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

            val prevAndNext = getContext(example.optString("id", ""))
            val sentenceWithFurigana =
                styleKeyword(
                    example.optString("sentence_with_furigana", ""),
                    Pair(keyword ?: "", keywordFurigana ?: ""),
                    settings.highlighting
                )
            val translation = example.optString("translation", "")
            val source = example.optString("deck_name", "")
            val ik_id = example.optString("id", "")
            val audioUrl = example.optString("sound_url", "")
            val imageUrl =
                "https://api.immersionkit.com/download_sentence_image?id=${
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
                        imageUrl,
                        audioUrl,
                        source,
                        prevAndNext?.first ?: "",
                        prevAndNext?.second ?: "",
                        ik_id,
                        settings,
                        toast
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
        selectedCard : Card,
        settings : ImmersiveKitSettings
    ) {
        val toast = Toast.makeText(context,"Fetching sentence...", Toast.LENGTH_SHORT)
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
                val apiUrl =
                    if (settings.exactSearch) {
                        "https://api.immersionkit.com/look_up_dictionary?keyword=「$keyword」"
                    } else {
                        "https://api.immersionkit.com/look_up_dictionary?keyword=$keyword"
                    }

                // Make API call
                val response = makeHttpRequest(apiUrl)
                val cacheKey = generateCacheKey(keyword, settings)
                val cachedJson = getFromCache(context, cacheKey)

                if (response != null) {
                    val jsonResponse = JSONObject(response)
                    val data = jsonResponse.optJSONArray("data")

                    if (data != null && data.length() > 0) {
                        val examples = data.getJSONObject(0).optJSONArray("examples")
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
                                toast
                            )
                        } else if (cachedJson != null) {
                            val examples = JSONArray(cachedJson)
                            processAndDisplayExample(context, selectedCard, examples, note!!, fieldNames!!, settings, keyword, keywordFurigana, toast)
                        }
                    }
                } else if (cachedJson != null) {
                    val examples = JSONArray(cachedJson)
                    processAndDisplayExample(context, selectedCard, examples, note!!, fieldNames!!, settings, keyword, keywordFurigana, toast)
                }
                else {
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

    @SuppressLint("CheckResult")
    private fun updateNoteFields(
        context: Context,
        selectedCard: Card,
        sentenceWithFurigana: String,
        translation: String,
        pictureUrl: String,
        audioUrl: String,
        source: String,
        prevSentence: String,
        nextSentence: String,
        immersionKitId: String,
        settings: ImmersiveKitSettings,
        startToast : Toast,
    ) {
        val note = selectedCard.note!! // Pass collection to note()
        val col = CollectionManager.getColUnsafe() // Get collection from viewer

        val fieldNames = note.keys()
        val fieldValues = note.fields.toMutableList()

        val entryIndex = fieldNames.indexOf("Entry")
        val keyword = if (entryIndex >= 0 && fieldValues[entryIndex] != "Ignore") {
            fieldValues[entryIndex].split(",")[0]
        } else {
            null  // or return here if inside a function
        }

        fun updateField(fieldName: String, newValue: String) {
            if (fieldName != "Ignore") {
                fieldNames.indexOf(fieldName).takeIf {it >= 0 }?.let {
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

        //Download audio and pictures
        val folderPath = col.media.dir
        CoroutineScope(Dispatchers.IO).launch {
            val audioDeferred = async {
                handleMediaDownloadAndUpdate(
                    fieldNames,
                    fieldValues,
                    note,
                    col,
                    settings.audioField,
                    keyword!!,
                    audioUrl,
                    folderPath,
                    "mp3"
                ) { fileName -> "[sound:$fileName]" }
            }

            val pictureDeferred = async {
                handleMediaDownloadAndUpdate(
                    fieldNames,
                    fieldValues,
                    note,
                    col,
                    settings.pictureField,
                    keyword!!,
                    pictureUrl,
                    folderPath,
                    "jpg"
                ) { fileName -> """<img src="$fileName">""" }
            }

            val audioSuccess = audioDeferred.await()
            val pictureSuccess = pictureDeferred.await()

            withContext(Dispatchers.Main) {
                if (audioSuccess || pictureSuccess) {
                    startToast.cancel()
                    showThemedToast(context, "Fields updated!", true)
                    when (context) {
                        is CardViewerActivity -> {
                            val viewModel = (context.fragment?.let {
                                ViewModelProvider(it)[PreviewerViewModel::class.java]
                            })
                            viewModel?.onPageFinished(true)
                        }
                        is AbstractFlashcardViewer -> {
                            context.updateCardAndRedraw()
                        }
                        else -> {
                            Timber.i("ImmersionKit called unexpectedly")
                        }
                    }
                }
            }
        }
    }
    private suspend fun handleMediaDownloadAndUpdate(
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
        withContext(Dispatchers.Main) {
            if (result != null) {
                val fileName = File(result).name
                val content = tagBuilder(fileName)
                note.setField(index, content)
                fieldValues[index] = content
                Timber.i("${extension.uppercase()} added successfully: $content")
                content
            } else {
                note.setField(index, "")
                fieldValues[index] = ""
                Timber.i("Failed to find $extension")
                ""
            }
            note.fields = fieldValues
            col.updateNote(note)
        }
        return result != null
    }
}


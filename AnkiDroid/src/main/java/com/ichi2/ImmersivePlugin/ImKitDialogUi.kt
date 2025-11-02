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
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import com.ichi2.immersivePlugin.ImKitSetting.ImmersiveKitSettings
import com.ichi2.immersivePlugin.ImKitSetting.saveNotetypeSettings
import com.ichi2.libanki.Card
import com.ichi2.libanki.NoteTypeId

object ImKitDialogUi {
    fun showImmersiveKitDialog(
        context: Context,
        selectedCard: Card?,
        currentSettings: ImmersiveKitSettings,
        noteType: NoteTypeId,
        onRun: (ImmersiveKitSettings) -> Unit
    ) {
        val scrollView = ScrollView(context).apply { setPadding(0, 0, 0, 0) }
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        // Helper for spinner + label
        fun createLabeledSpinner(labelText: String): Pair<LinearLayout, Spinner> {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }
            val label = TextView(context).apply {
                text = labelText
                textSize = 14f
                setTextColor("#666666".toColorInt())
                setPadding(0, 0, 0, 8)
            }
            val spinner = Spinner(context).apply { setPadding(0, 8, 0, 8) }
            container.addView(label)
            container.addView(spinner)
            return Pair(container, spinner)
        }

        val exactSearchCheckbox = CheckBox(context).apply {
            text = "Exact Search"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = currentSettings.exactSearch
        }
        val highlightingCheckbox = CheckBox(context).apply {
            text = "Highlighting"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = currentSettings.highlighting
        }
        val dramaCheckbox = CheckBox(context).apply {
            text = "Check Drama"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = currentSettings.drama
        }
        val animeCheckbox = CheckBox(context).apply {
            text = "Check Anime"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = currentSettings.anime
        }
        val gamesCheckbox = CheckBox(context).apply {
            text = "Check Games"
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isChecked = currentSettings.games
        }

        val separator = View(context).apply {
            setBackgroundColor("#E0E0E0".toColorInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
            ).apply { setMargins(0, 16, 0, 16) }
        }

        val toggleFieldsButton = Button(context).apply {
            text = "Show Field Mappings"
            textSize = 14f
            setPadding(16, 12, 16, 12)
            setBackgroundColor("#4CAF50".toColorInt())
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 0) }
        }
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
        val settingsFields = listOf(
            currentSettings.keywordField,
            currentSettings.keywordFuriganaField,
            currentSettings.sentenceField,
            currentSettings.translationField,
            currentSettings.pictureField,
            currentSettings.audioField,
            currentSettings.sourceField,
            currentSettings.prevSentenceField,
            currentSettings.nextSentenceField
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
            if (savedIndex >= 0) spinner.setSelection(savedIndex)
            spinners.add(spinner)
            spinnerContainers.add(container)
        }

        val runButton = Button(context).apply {
            text = "GET SENTENCE"
            textSize = 16f
            setPadding(24, 16, 24, 16)
            setBackgroundColor("#2196F3".toColorInt())
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 0) }
        }

        // Toggle
        var fieldsVisible = false
        toggleFieldsButton.setOnClickListener {
            fieldsVisible = !fieldsVisible
            val visibility = if (fieldsVisible) LinearLayout.VISIBLE else LinearLayout.GONE
            spinnerContainers.forEach { it.visibility = visibility }
            toggleFieldsButton.text = if (fieldsVisible) "Hide Field Mappings" else "Show Field Mappings"
        }

        // Add views
        dialogLayout.addView(exactSearchCheckbox)
        dialogLayout.addView(highlightingCheckbox)
        dialogLayout.addView(dramaCheckbox)
        dialogLayout.addView(animeCheckbox)
        dialogLayout.addView(gamesCheckbox)
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
                val sep = View(context).apply {
                    setBackgroundColor("#E0E0E0".toColorInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 2
                    ).apply { setMargins(0, 16, 0, 16) }
                }
                val label = TextView(context).apply {
                    text = "Fields to Map Gathered Data to"
                    textSize = 14f
                    setTextColor("#E0E0E0".toColorInt())
                    setPadding(0, 0, 0, 0)
                }
                container.addView(sep)
                container.addView(label)
            }
            dialogLayout.addView(container)
        }
        dialogLayout.addView(runButton)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Immersion Kit Settings")
            .setView(scrollView)
            .create()

        val cancelTextButton = TextView(context).apply {
            text = "Cancel"
            textSize = 16f
            setTextColor("#2196F3".toColorInt())
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 4, 16) }
            setPadding(16, 32, 28, 14)
            setOnClickListener { dialog.dismiss() }
        }
        dialogLayout.addView(cancelTextButton)
        scrollView.addView(dialogLayout)

        // Spinner logic: no duplicate fields
        var isUpdatingSpinners = false
        fun updateSpinnerAdapters() {
            if (isUpdatingSpinners) return
            isUpdatingSpinners = true
            val selectedItems = spinners.map { it.selectedItem?.toString() ?: "Ignore" }.toSet()
            spinners.forEachIndexed { index, spinner ->
                val currentSelection = spinner.selectedItem?.toString() ?: "Ignore"
                val filteredOptions = dropdownItems.filter { option ->
                    option == "Ignore" || option == currentSelection || !selectedItems.contains(option)
                }
                val oldOptions = (spinner.adapter as? ArrayAdapter<*>)?.let { adapter ->
                    (0 until adapter.count).map { adapter.getItem(it) }
                }
                if (oldOptions != filteredOptions) {
                    val adapter = ArrayAdapter(spinner.context, layout.simple_spinner_item, filteredOptions)
                    adapter.setDropDownViewResource(layout.simple_spinner_dropdown_item)
                    spinner.adapter = adapter
                }
                val position = filteredOptions.indexOf(currentSelection).takeIf { it >= 0 }
                    ?: filteredOptions.indexOf("Ignore").takeIf { it >= 0 } ?: 0
                spinner.setSelection(position)
            }
            isUpdatingSpinners = false
        }
        spinners.forEach { spinner ->
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateSpinnerAdapters()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        updateSpinnerAdapters()

        // RUN button listener
        runButton.setOnClickListener {
            val newSettings = ImmersiveKitSettings(
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
            saveNotetypeSettings(context, noteType, newSettings)
            onRun(newSettings)
            dialog.dismiss()
        }

        dialog.show()
    }
}
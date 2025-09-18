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
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.PreviewerViewModel
import com.ichi2.anki.showThemedToast
import com.ichi2.immersivePlugin.ImKitApi.getContext
import com.ichi2.immersivePlugin.ImKitApi.getMeta
import com.ichi2.libanki.Card
import com.ichi2.libanki.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import kotlin.collections.contains
import kotlin.collections.indexOf
import com.ichi2.immersivePlugin.ImKitUtils.removeExampleBySentence
import com.ichi2.immersivePlugin.ImKitUtils.styleKeyword
import com.ichi2.immersivePlugin.ImKitMedia.handleMediaDownloadAndUpdate

object ImKitNoteUpdater {
    suspend fun processAndDisplayExample(
        context: Context,
        selectedCard: Card,
        exampleData: JSONArray,
        note: Note,
        fieldNames: Array<String>,
        settings: ImKitSetting.ImmersiveKitSettings,
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

            val prevAndNext = getContext(example.optString("id", ""))

            val sentenceWithFurigana =
                styleKeyword(
                    example.optString("sentence_with_furigana", ""),
                    Pair(keyword ?: "", keywordFurigana ?: ""),
                    settings.highlighting,
                )

            val translation = example.optString("translation", "")
            val source = example.optString("title", "")
            val sourceType = example?.optString("id")?.split("_")?.firstOrNull()
            val ikId = example.optString("id", "")

            withContext(Dispatchers.Main) {
                if (settings.sentenceField == "Ignore") {
                    showThemedToast(context, "Sentence field is set to Ignore", true)
                    return@withContext
                }

                val meta = getMeta(example["title"].toString())
                val title = meta?.first
                val category = meta?.second

                val audio_filepath = example.optString("sound", "")
                val pic_filepath = example.optString("image", "")

                val audio_url = "https://us-southeast-1.linodeobjects.com/immersionkit/media/${category}/${title}/media/$audio_filepath"
                val pic_url = "https://us-southeast-1.linodeobjects.com/immersionkit/media/${category}/${title}/media/$pic_filepath"

                if (note.fields[sentenceFieldIdx] != sentenceWithFurigana) {
                    updateNoteFields(
                        context,
                        selectedCard,
                        sentenceWithFurigana,
                        translation,
                        "http://apiv2.immersionkit.com/download_sentence?id=$ikId&modelType=$sourceType",
                        audio_url,
                        pic_url,
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

    private suspend fun refreshCard(
        context: Context
    ) {
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
        audioUrl: String,
        picUrl: String,
        source: String,
        prevSentence: String,
        nextSentence: String,
        immersionKitId: String,
        settings: ImKitSetting.ImmersiveKitSettings,
        startToast: Toast,
    ) {
        val note = selectedCard.note!! // Pass collection to note()
        val col = CollectionManager.getColUnsafe() // Get collection from viewer

        val fieldNames = note.keys()
        val fieldValues = note.fields.toMutableList()

        // Replace "NULL" with empty strings, for cards made through KanjiStudy
        for (i in fieldValues.indices) {
            val v = fieldValues[i]
            if (v.equals("NULL", ignoreCase = true)) {
                fieldValues[i] = ""
            }
        }

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
            val mediaDeferred = async {
                var audioResult = true
                var pictureResult = true
                if (settings.audioField != "Ignore") {
                    pictureResult = handleMediaDownloadAndUpdate(
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
                if (settings.pictureField != "Ignore") {
                    audioResult = handleMediaDownloadAndUpdate(
                        fieldNames,
                        fieldValues,
                        note,
                        col,
                        settings.pictureField,
                        keyword!!,
                        picUrl,
                        folderPath,
                        "jpg"
                    ) { fileName -> """<img src="$fileName">""" }
                }
                audioResult && pictureResult
            }

            val cardSuccess = mediaDeferred.await()

            refreshCard(context)
            withContext(Dispatchers.Main) {
                startToast.cancel()
                showThemedToast(context, "Fields updated!", true)
            }
            withContext(Dispatchers.Main) {
                if (cardSuccess) {
                    refreshCard(context)
                }
            }
        }
    }
}
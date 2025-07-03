package com.ichi2.anki

import android.content.Context
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

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
            }

        val animeCheckbox =
            CheckBox(context).apply {
                text = "Check Anime"
                textSize = 16f
            }

        val dramaCheckbox =
            CheckBox(context).apply {
                text = "Check Drama"
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
                .setTitle("Immersive Kit Settings")
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

            Timber.i("ImmersiveKit:: Running with settings - Exact: $exactSearch, Highlighting: $highlighting")

            // Make API call with these settings
            makeApiCall(context, exactSearch, highlighting, anime, drama, games)

            dialog.dismiss()
        }

        dialog.show()
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
        Toast.makeText(context, "Fetching sentence...", Toast.LENGTH_SHORT).show()

        // Use coroutines for async API call
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Example keyword - you can get this from the current note
                val keyword = "情報"

                // Build API URL
                val apiUrl =
                    if (exactSearch) {
                        "https://api.immersionkit.com/look_up_dictionary?keyword=「$keyword」&sort=shortness"
                    } else {
                        "https://api.immersionkit.com/look_up_dictionary?keyword=$keyword&sort=shortness"
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
                            val example = examples.getJSONObject(0)

                            // Extract data
                            val sentence = example.optString("sentence", "")
                            val sentenceWithFurigana = example.optString("sentence_with_furigana", "")
                            val translation = example.optString("translation", "")
                            val deckName = example.optString("deck_name", "")
                            val audioUrl = example.optString("sound_url", "")
                            val imageUrl = "https://api.immersionkit.com/download_sentence_image?id=${example.optString("id", "")}"

                            // Update UI on main thread
                            withContext(Dispatchers.Main) {
                                updateNoteFields(
                                    context,
                                    sentence,
                                    sentenceWithFurigana,
                                    translation,
                                    deckName,
                                    audioUrl,
                                    imageUrl,
                                    highlighting,
                                    keyword,
                                )
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "No examples found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No data found for keyword", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "API request failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "ImmersiveKit:: Error making API call")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun getContext(id: String): JSONObject? =
        try {
            val url = "https://api.immersionkit.com/sentence_with_context?id=$id"
            val response = makeHttpRequest(url)
            if (response != null) {
                JSONObject(response)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get context")
            null
        }

    private fun updateNoteFields(
        context: Context,
        sentence: String,
        sentenceWithFurigana: String,
        translation: String,
        deckName: String,
        audioUrl: String,
        imageUrl: String,
        highlighting: Boolean,
        keyword: String,
    ) {
        // This is where you would update the actual note fields
        // For now, just show the results

        val resultMessage =
            buildString {
                append("Sentence: $sentence\n")
                append("Translation: $translation\n")
                append("Source: $deckName\n")
            }

        Toast.makeText(context, resultMessage, Toast.LENGTH_LONG).show()
    }
}

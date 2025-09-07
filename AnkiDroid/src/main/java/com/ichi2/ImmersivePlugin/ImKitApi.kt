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
import androidx.core.content.edit
import com.ichi2.anki.showThemedToast
import com.ichi2.immersivePlugin.ImKitNoteUpdater.processAndDisplayExample
import com.ichi2.libanki.Card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import kotlin.collections.indexOf

val apiUrlV2 = "https://apiv2.immersionkit.com"

object ImKitApi {
    @SuppressLint("DirectToastMakeTextUsage")
    fun makeApiCall(
        context: Context,
        selectedCard: Card,
        settings: ImKitSetting.ImmersiveKitSettings,
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

    // fix
    fun makeHttpRequest(urlString: String, redirectCount: Int = 0): String? {
        // Prevent infinite loops
        if (redirectCount > 5) {
            Timber.e("Too many redirects for $urlString")
            return null
        }

        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false // handle manually
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            Timber.d("DEBUG URL: ${url}")

            connection.connect()

            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP -> {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl != null) {
                        makeHttpRequest(newUrl, redirectCount + 1) // retry with new URL
                    } else {
                        null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Timber.e(e, "HTTP request failed")
            null
        }
    }

    fun generateCacheKey(
        keyword: String?,
        settings: ImKitSetting.ImmersiveKitSettings,
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

    suspend fun getMeta(titleKey: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val url = "$apiUrlV2/index_meta"
            Timber.d("getMeta1: $url")

            val response = makeHttpRequest(url)
            if (response != null) {
                try {
                    val json = JSONObject(response)
                        .getJSONObject("data")
                        .getJSONObject(titleKey)

                    val title = json.optString("title", "")
                    val category = json.optString("category", "")

                    Pair(title, category)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse JSON for key=$titleKey")
                    null
                }
            } else {
                null
            }
        }
}
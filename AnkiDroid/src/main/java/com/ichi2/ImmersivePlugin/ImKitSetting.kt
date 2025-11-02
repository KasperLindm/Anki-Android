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

import android.content.Context
import com.ichi2.libanki.NoteTypeId

object ImKitSetting {
    data class ImmersiveKitSettings(
        val noteType: NoteTypeId = -1,
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
        noteType: NoteTypeId,
    ): String = "${baseKey}_$noteType"

    fun saveNotetypeSettings(
        context: Context,
        noteType: NoteTypeId,
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

    fun loadNotetypeSettings(
        context: Context,
        noteType: NoteTypeId,
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
}
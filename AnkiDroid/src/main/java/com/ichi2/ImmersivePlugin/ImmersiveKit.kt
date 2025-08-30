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
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.showThemedToast
import com.ichi2.immersivePlugin.ImKitApi.makeApiCall
import com.ichi2.immersivePlugin.ImKitDialogUi.showImmersiveKitDialog
import com.ichi2.immersivePlugin.ImKitSetting.loadnoteTypeSettings
import com.ichi2.libanki.Card
import timber.log.Timber

object ImmersiveKit {
    fun showImmersiveKit(
        context: Context,
        selectedCard: Card?,
    ) {
        Timber.i("ImmersiveKit:: Showing immersive kit settings")
        val note = selectedCard?.note
        val col = CollectionManager.getColUnsafe()
        val noteType = note?.notetype.toString()
        val isLocked = note?.tags?.contains("Locked") == true
        if (isLocked) {
            showThemedToast(context, "Card is locked by tag - cannot update fields", true)
            return
        }
        val currentSettings = loadnoteTypeSettings(context, noteType)
        showImmersiveKitDialog(
            context,
            selectedCard,
            currentSettings,
            noteType
        ) { newSettings ->
            Timber.i(
                "ImmersiveKit:: Running with settings for card type '$noteType' - Exact: ${newSettings.exactSearch}, Highlighting: ${newSettings.highlighting}",
            )
            Timber.i("ImmersiveKit:: Field mappings: Keyword=${newSettings.keywordField}, Sentence=${newSettings.sentenceField}")
            if (newSettings.keywordField != "Ignore" && newSettings.sentenceField != "Ignore") {
                if (selectedCard == null) {
                    showThemedToast(context, "No card selected", true)
                    return@showImmersiveKitDialog
                }
                try {
                    makeApiCall(context, selectedCard, newSettings)
                } catch (e: Exception) {
                    Timber.e(e, "Error in makeApiCall")
                    showThemedToast(context, "Failed to get sentence: ${e.localizedMessage}", true)
                }
            } else {
                showThemedToast(context, "Please select a keyword and sentence field", true)
            }
        }
    }
}
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

import org.json.JSONArray

object ImKitUtils {
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
}
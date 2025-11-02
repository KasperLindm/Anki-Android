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

        val highlightTag = "u"
        val plain = keywordPair.first.split(",")[0]
            .replace(Regex("[^\\p{L}\\p{N}\\p{InCJKUnifiedIdeographs}]"), "")
            .trim()

        val plainKanji = plain.replace(Regex("[^\\p{InCJKUnifiedIdeographs}]"), "")

        val furigana = keywordPair.second.split(",")[0]

        // Helper: wrap matched text in <u> tag
        fun highlight(match: MatchResult) = "<$highlightTag>${match.value}</$highlightTag>"

        var stylized = sentence

        // 0. Extended compound + [furigana] + previous kanji if no [] before
        val pattern = Regex(
            "(?<!(\\]))" +  // not immediately after ]
                    "([\\p{InCJKUnifiedIdeographs}]*" +
                    Regex.escape(plain) +
                    "[\\p{InCJKUnifiedIdeographs}]*)" +
                    "(\\[[^\\]]*\\])?"
        )
        val temp = pattern.replace(stylized, ::highlight)
        if (temp != stylized) return temp

        // 1. Kanji + [furigana] pattern (only if kanji exists)
        if (plainKanji.isNotBlank()) {
            val kanjiWithFuriganaPattern = Regex("${Regex.escape(plainKanji)}\\[[^]]+]")
            val temp = kanjiWithFuriganaPattern.replace(stylized, ::highlight)
            if (temp != stylized) return temp
        }

        // 2. Exact furigana string (only if furigana differs from plain)
        if (furigana.isNotBlank() && furigana != plain) {
            val furiganaPattern = Regex(Regex.escape(furigana))
            val temp = furiganaPattern.replace(stylized, ::highlight)
            if (temp != stylized) return temp
        }

        // 3. Kana-only or mixed keywords: match outside brackets
        if (plain.isNotBlank()) {
            // Negative lookbehind/lookahead to avoid brackets
            val plainOutsideBrackets = Regex("(?<!\\[)${Regex.escape(plain)}(?![^\\[]*\\])")
            val temp = plainOutsideBrackets.replace(stylized, ::highlight)
            if (temp != stylized) return temp
        }

        // 4. Kanji-only fallback: highlight just the kanji anywhere
        if (plainKanji.isNotBlank()) {
            val plainKanjiPattern = Regex(Regex.escape(plainKanji))
            val temp = plainKanjiPattern.replace(stylized, ::highlight)
            if (temp != stylized) return temp
        }

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
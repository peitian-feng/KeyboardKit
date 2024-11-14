package com.fpt.keyboard.ui.keyboards

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.fpt.keyboard.ui.keyboards.KeyboardInterface.CandidatesResult
import com.fpt.keyboard.ui.keyboards.KeyboardInterface.Words
import java.util.Locale

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 14:39
 * desc    : abc latin
 */
abstract class BaseLatinKeyboard(context: Context) : BaseKeyboard(context) {

    private val mKeymaps:HashMap<String, ArrayList<Words>> by lazy {
        HashMap()
    }

    override fun supportsAutoCompletion(): Boolean = true

    override fun usesComposingText(): Boolean = true

    override fun getCandidates(composingText: String?): CandidatesResult? {
        if (!usesComposingText() || composingText.isNullOrEmpty()) {
            return null
        }

        // Autocomplete when special characters are clicked
        val lastChar = composingText[composingText.length - 1]
        val autocompose = ("" + lastChar).matches("[^\\p{L}]".toRegex())

        val aComposingText = composingText.replace("\\s".toRegex(), "")
        if (aComposingText.isEmpty()) {
            return null
        }

        val words: ArrayList<Words> = ArrayList()
        words.add(Words(1, aComposingText, aComposingText))

        var tempKey = aComposingText
        while (tempKey.isNotEmpty()) {
            words.addAll(getDisplays(tempKey))
            tempKey = tempKey.substring(0, tempKey.length - 1)
        }
        cleanCandidates(words)

        val result = CandidatesResult()
        result.words = words
        result.action = if (autocompose) CandidatesResult.Action.AUTO_COMPOSE else CandidatesResult.Action.SHOW_CANDIDATES
        result.composing = aComposingText
        return result
    }

    private fun getDisplays(key: String): List<Words> {
        if (key.matches("^[^\\p{L}]+$".toRegex())) {
            // Allow completion of numbers and symbols
            return listOf(Words(1, key, key))
        }
        loadKeymapIfNotLoaded(key)
        return mKeymaps[key] ?: arrayListOf()
    }

    private fun loadKeymapIfNotLoaded(key: String) {
        if (mKeymaps.containsKey(key)) {
            return
        }
        loadAutoCorrectTable(key)
    }

    private fun loadAutoCorrectTable(key: String) {
        mDB?.let { reader ->
            val sqliteArgs = arrayOfNulls<String>(1)
            sqliteArgs[0] = key.lowercase(Locale.getDefault()) + "%"
            reader.rawQuery("SELECT word FROM autocorrect where LOWER(word) LIKE ? ORDER BY originalFreq DESC LIMIT 20", sqliteArgs)
                .use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return
                    }
                    do {
                        val word: String? = if (cursor.isNull(0)) null else cursor.getString(0)
                        addToKeyMap(key, word)
                    } while (cursor.moveToNext())
                }
        }
    }

    private fun addToKeyMap(aKey: String, aCode: String?) {
        if (aKey.isEmpty()) {
            return
        }
        if (aCode.isNullOrEmpty()) {
            return
        }
        val keyMap = mKeymaps.computeIfAbsent(aKey) { _: String? -> ArrayList() }
        keyMap.add(Words(1, aKey, "$aCode "))
    }

    private fun cleanCandidates(candidates: ArrayList<Words>) {
        val words: HashSet<String> = HashSet()
        var n = candidates.size
        var i = 0
        while (i < n) {
            val candidate = candidates[i]
            if (words.contains(candidate.value.lowercase(getLocale()))) {
                candidates.removeAt(i)
                i--
                n--
            } else {
                words.add(candidate.value.lowercase(getLocale()))
                // Make sure capital cases matches the composing text
                var newValue = StringBuilder(candidate.value)
                if (candidate.code.length > 1 && candidate.code.uppercase(getLocale()) == candidate.code) {
                    newValue = java.lang.StringBuilder(candidate.value.uppercase(getLocale()))
                } else {
                    for (k in 0 until candidate.code.length) {
                        if (Character.isUpperCase(candidate.code[k]) && newValue.length > k) {
                            newValue.setCharAt(k, newValue[k].uppercaseChar())
                        }
                    }
                }
                candidate.value = newValue.toString()
                candidates[i] = candidate
            }
            ++i
        }
    }

}
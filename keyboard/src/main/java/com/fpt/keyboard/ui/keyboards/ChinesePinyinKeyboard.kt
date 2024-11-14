package com.fpt.keyboard.ui.keyboards

import android.content.Context
import com.fpt.keyboard.R
import com.fpt.keyboard.StringUtils
import com.fpt.keyboard.input.Keyboard
import com.fpt.keyboard.ui.keyboards.KeyboardInterface.CandidatesResult
import com.fpt.keyboard.ui.keyboards.KeyboardInterface.Words
import java.util.Locale
import java.util.regex.Pattern

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 15:00
 * desc    : pinyin
 */
class ChinesePinyinKeyboard(private val context: Context) : BaseKeyboard(context) {

    private val mKeyboard: Keyboard by lazy {
        Keyboard(context, R.xml.keyboard_qwerty_pinyin)
    }

    private val mKeymaps: HashMap<String, KeyMap> by lazy {
        HashMap()
    }

    private val mExtraKeymaps: HashMap<String, KeyMap> by lazy {
        HashMap()
    }

    private val sqliteArgs = arrayOfNulls<String>(1)

    override fun getDBName(): String = "google_pinyin.db"

    override fun getAlphabeticKeyboard(): Keyboard {
        addExtraKeyMaps()
        return mKeyboard
    }

    override fun getLocale(): Locale = Locale.SIMPLIFIED_CHINESE;

    override fun supportsAutoCompletion(): Boolean = true

    override fun usesComposingText(): Boolean = true

    override fun getKeyboardTitle(): String = StringUtils.getStringByLocale(context, R.string.settings_language_simplified_chinese, getLocale())

    override fun getSpaceKeyText(): String = getKeyboardTitle()

    override fun getModeChangeKeyText(): String = context.getString(R.string.pinyin_keyboard_mode_change)

    override fun getEnterKeyText(imeOptions: Int): String = context.getString(R.string.pinyin_enter_completion)

    override fun getDomains(vararg domains: String): Array<String> = super.getDomains(".cn")

    override fun getCandidates(composingText: String?): CandidatesResult? {
        if (composingText.isNullOrEmpty()) {
            return null
        }

        // Autocomplete when special characters are clicked
        val lastChar: Char = composingText[composingText.length - 1]
        val autocompose = ("" + lastChar).matches("[^a-z]".toRegex())

        var aComposingText = composingText.replace("\\s".toRegex(), "")
        if (aComposingText.isEmpty()) {
            return null
        }

        val displayList: ArrayList<String> = getDisplayCode(aComposingText)
        val code = StringBuilder()
        val syllables = displayList.size
        for (display in displayList) {
            if (code.isNotEmpty()) {
                code.append(' ')
            }
            code.append(display)
        }

        val words = ArrayList<Words>()
        val candidate = StringBuilder()
        var tempKey = aComposingText
        var remainKey = ""

        // First candidate
        while (tempKey.isNotEmpty()) {
            val displays = getDisplays(tempKey)
            if (!displays.isNullOrEmpty()) {
                candidate.append(displays[0].value)
                tempKey = remainKey
                remainKey = ""
            } else {
                remainKey = tempKey[tempKey.length - 1].toString() + remainKey
                tempKey = tempKey.substring(0, tempKey.length - 1)
            }
        }

        // We can't find available candidates, so using the composing text
        // as the only item of candidates.
        if (candidate.isEmpty()) {
            candidate.append(aComposingText)
        }
        words.add(Words(syllables, code.toString(), candidate.toString()))

        // Extra candidates
        tempKey = aComposingText
        while (tempKey.isNotEmpty()) {
            val displays = getDisplays(tempKey)
            if (displays != null) {
                words.addAll(displays)
            }
            val map: KeyMap? = mKeymaps.get(tempKey)
            if (map != null && map.candidates.size > 0) {
                words.addAll(map.candidates)
            }
            tempKey = tempKey.substring(0, tempKey.length - 1)
        }
        cleanCandidates(words)

        val result = CandidatesResult()
        result.words = words
        result.action = if (autocompose) CandidatesResult.Action.AUTO_COMPOSE else CandidatesResult.Action.SHOW_CANDIDATES
        result.composing = aComposingText
        result.words?.let {
            if (it.size > 0) {
                val kBackslashCode = 92.toChar()
                var newCode = it[0].code

                // When using backslashes ({@code \}) in the replacement string
                // will cause crash at `replaceFirst()`, so we need to replace it first.
                if (it[0].code.isNotEmpty() && (it[0].code[it[0].code.length - 1] == kBackslashCode)) {
                    newCode = it[0].code.replace("\\", "\\\\")
                    aComposingText = aComposingText.replace("\\", "\\\\")
                }
                val codeWithoutSpaces = StringUtils.removeSpaces(newCode)
                result.composing = aComposingText.replaceFirst(Pattern.quote(codeWithoutSpaces).toRegex(), newCode)
            }
        }
        return result
    }

    private fun addExtraKeyMaps() {
        addExtraKeyMap("a", "a", "a|A")
        addExtraKeyMap("b", "b", "b|B")
        addExtraKeyMap("c", "c", "c|C")
        addExtraKeyMap("d", "d", "d|D")
        addExtraKeyMap("e", "e", "e|E")
        addExtraKeyMap("f", "f", "f|F")
        addExtraKeyMap("g", "g", "g|G")
        addExtraKeyMap("h", "h", "h|H")
        addExtraKeyMap("i", "i", "i|I", "喔|哦|噢")
        addExtraKeyMap("j", "j", "j|J")
        addExtraKeyMap("k", "k", "k|K")
        addExtraKeyMap("l", "l", "l|L")
        addExtraKeyMap("m", "m", "m|M")
        addExtraKeyMap("n", "n", "n|N")
        addExtraKeyMap("o", "o", "o|O")
        addExtraKeyMap("p", "p", "p|P")
        addExtraKeyMap("q", "q", "q|Q")
        addExtraKeyMap("r", "r", "r|R")
        addExtraKeyMap("s", "s", "s|S")
        addExtraKeyMap("t", "t", "t|T")
        addExtraKeyMap("u", "u", "u|U", "有|要")
        addExtraKeyMap("v", "v", "v|V", "吧|被")
        addExtraKeyMap("w", "w", "w|W")
        addExtraKeyMap("x", "x", "x|X")
        addExtraKeyMap("y", "y", "y|Y")
        addExtraKeyMap("z", "z", "z|Z")
    }

    private fun addExtraKeyMap(key: String, code: String, displays: String, candidates: String? = null) {
        val extra = KeyMap()

        if (displays.isNotEmpty()) {
            val displayList = displays.split("\\|".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            for (display in displayList) {
                extra.displays.add(Words(syllableCount(code), code, display))
            }
        }

        if (!candidates.isNullOrEmpty()) {
            val candidateList = candidates.split("\\|".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            for (candidate in candidateList) {
                extra.candidates.add(Words(syllableCount(code), code, candidate))
            }
        }

        mExtraKeymaps[key] = extra
    }

    private fun syllableCount(code: String?): Int {
        var aCode = code ?: return 0
        aCode = aCode.trim { it <= ' ' }
        if (aCode.isEmpty()) {
            return 0
        }
        return aCode.chars().filter { ch: Int -> ch == ' '.code }.count().toInt() + 1
    }

    private fun getDisplayCode(key: String): ArrayList<String> {
        val result = ArrayList<String>()
        var remain = ""
        var aKey = key
        while (aKey.isNotEmpty()) {
            val displays = getDisplays(aKey)
            if (!displays.isNullOrEmpty()) {
                result.add(displays[0].code)
                aKey = remain
                remain = ""
            } else {
                remain = aKey[aKey.length - 1].toString() + remain
                aKey = aKey.substring(0, aKey.length - 1)
            }
        }
        return result
    }

    private fun getDisplays(key: String): List<Words>? {
        if (key.matches("^[^a-z]+$".toRegex())) {
            // Allow completion of uppercase letters, numbers and symbols
            return listOf(Words(1, key, key))
        }
        loadKeymapIfNotLoaded(key)
        return mKeymaps[key]?.displays
    }

    private fun loadKeymapIfNotLoaded(key: String) {
        if (mKeymaps.containsKey(key)) {
            return
        }
        loadKeymapTable(key)
        loadAutoCorrectTable(key)
        val extra: KeyMap? = mExtraKeymaps[key]
        if (extra != null) {
            val map: KeyMap? = mKeymaps[key]
            if (map != null) {
                map.displays.addAll(extra.displays)
                map.candidates.addAll(extra.candidates)
            }
        }
    }

    private fun loadKeymapTable(aKey: String) {
        val reader = mDB?:return
        sqliteArgs[0] = aKey
        reader.rawQuery("SELECT keymap, display, candidates FROM keymaps where keymap = ? ORDER BY _id ASC", sqliteArgs).use { cursor ->
            if (!cursor.moveToFirst()) {
                return
            }
            do {
                val key: String = cursor.getString(0)
                val displays: String = cursor.getString(1)
                val candidates: String = cursor.getString(2)
                addToKeyMap(key, key, displays, candidates)
            } while (cursor.moveToNext())
        }
    }

    private fun addToKeyMap(key: String, code: String, displays: String, candidates: String? = null) {
        if (key.isEmpty()) {
            return
        }
        if (code.isEmpty()) {
            return
        }
        var keyMap: KeyMap? = mKeymaps[key]
        if (keyMap == null) {
            keyMap = KeyMap()
            mKeymaps[key] = keyMap
        }

        if (displays.isNotEmpty()) {
            val displayList = displays.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (display in displayList) {
                keyMap.displays.add(Words(syllableCount(code), code, display))
            }
        }

        if (!candidates.isNullOrEmpty()) {
            val candidateList = candidates.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (candidate in candidateList) {
                keyMap.candidates.add(Words(syllableCount(code), code, candidate))
            }
        }
    }

    private fun loadAutoCorrectTable(aKey: String) {
        val reader = mDB?:return
        sqliteArgs[0] = aKey
        reader.rawQuery("SELECT inputcode, displaycode, display FROM autocorrect where inputcode = ? ORDER BY _id ASC", sqliteArgs).use { cursor ->
            if (!cursor.moveToFirst()) {
                return
            }
            do {
                val key: String = cursor.getString(0)
                val code: String = cursor.getString(1)
                val displays: String = cursor.getString(2)
                addToKeyMap(key, code, displays)
            } while (cursor.moveToNext())
        }
    }

    private fun cleanCandidates(aCandidates: ArrayList<Words>) {
        // Remove potential repeated value between first candidate and first extra
        if (aCandidates.size > 1 && aCandidates[0].value == (aCandidates[1].value)) {
            aCandidates.removeAt(0)
        }

        var n: Int = aCandidates.size
        var i = 0
        while (i < n) {
            val candidate: Words = aCandidates.get(i)
            if (candidate.value.matches(Regex("^[a-z]+$"))) {
                // Move latin char fallbacks to the end of the list
                aCandidates.removeAt(i)
                aCandidates.add(candidate)
                i--
                n--
            } else if (candidate.value.matches(Regex("^[A-Z]$")) && !candidate.code.contains(candidate.value)) {
                // Move uppercase latin char fallback to the end only when generated via lowercase input.
                aCandidates.removeAt(i)
                aCandidates.add(candidate)
                i--
                n--
            } else if (candidate.value.matches(Regex(".*[a-z]+$"))) {
                // Discard latin char fallback at the end of chinese char fallbacks
                candidate.value = candidate.value.replaceAll("[a-z]+$", "").trim()
                candidate.code = candidate.code.replaceAll("[a-z]+$", "").trim()
            }
            ++i
        }

        // Remove another potential repeated value between first candidate and first extra
        if (aCandidates.size > 1 && aCandidates[0].value == (aCandidates[1].value)) {
            aCandidates.removeAt(0)
        }
    }

    private fun String.replaceAll(regex: String, replacement: String): String =
        Pattern.compile(regex).matcher(this).replaceAll(replacement)

    class KeyMap {
        val displays: ArrayList<Words> = ArrayList()
        val candidates: ArrayList<Words> = ArrayList()
    }
}
package com.fpt.keyboard.ui.keyboards

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
 * time    : 2024/11/13 15:02
 * desc    : zhuyin
 */
class ChineseZhuyinKeyboard(private val context: Context) : BaseKeyboard(context) {

    companion object {
        private const val nonZhuyinReg: String = "[^ㄅ-ㄩ˙ˊˇˋˉ]"
    }

    private val mKeyboard: Keyboard by lazy {
        Keyboard(context, R.xml.keyboard_qwerty_zhuyin);
    }

    private var mPhraseDB: SQLiteDatabase? = null

    private val mKeyCodes: HashMap<String, Words> by lazy {
        HashMap()
    }

    private val mKeymaps: HashMap<String, KeyMap> by lazy {
        HashMap()
    }

    private val sqliteArgs = arrayOfNulls<String>(2)

    private val roughSqliteArgs = arrayOfNulls<String>(3)

    override fun getDBName(): String = "zhuyin_words.db"

    override fun getAlphabeticKeyboard(): Keyboard {
        try {
            mPhraseDB = loadDatabase("zhuyin_phrases.db")
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        addExtraKeyMaps()
        return mKeyboard
    }

    override fun getLocale(): Locale = Locale.TRADITIONAL_CHINESE

    override fun supportsAutoCompletion(): Boolean = true

    override fun usesComposingText(): Boolean = true

    override fun getKeyboardTitle(): String = StringUtils.getStringByLocale(context, R.string.settings_language_traditional_chinese, getLocale())

    override fun getSpaceKeyText(): String = getKeyboardTitle()

    override fun getModeChangeKeyText(): String = context.getString(R.string.zhuyin_keyboard_mode_change);

    override fun getEnterKeyText(imeOptions: Int): String = context.getString(R.string.zhuyin_enter_completion)

    override fun getDomains(vararg domains: String): Array<String> = super.getDomains(".tw")

    override fun getCandidates(composingText: String?): CandidatesResult? {
        if (composingText.isNullOrEmpty()) {
            return null
        }

        // Replacing all spaces to the first tone because Zhuyin input doesn't use spaces.
        val aComposingText = composingText.replace("\\s".toRegex(), "ˉ")
        if (aComposingText.isEmpty()) {
            return null
        }

        // If using non-Zhuyin symbols like numeric, abc, special symbols,
        // we just need to compose them.
        val lastChar = "${aComposingText[aComposingText.length - 1]}"
        if (lastChar.matches(nonZhuyinReg.toRegex())) {
            val result = CandidatesResult()
            result.words = getDisplays(aComposingText)
            result.action = CandidatesResult.Action.AUTO_COMPOSE
            result.composing = aComposingText
            return result
        }

        val words = ArrayList<Words>()
        if (aComposingText.isNotEmpty()) {
            val displays: List<Words> = getDisplays(aComposingText)
            if (displays.isNotEmpty()) {
                words.addAll(displays)
            }
        }

        val result = CandidatesResult()
        result.words = words
        result.action = CandidatesResult.Action.SHOW_CANDIDATES
        result.composing = aComposingText
        result.words?.let {
            if (it.size > 0) {
                val codeWithoutSpaces = StringUtils.removeSpaces(it[0].code)
                result.composing = aComposingText.replaceFirst(Pattern.quote(codeWithoutSpaces).toRegex(), it[0].code)
            }
        }

        return result
    }

    override fun getComposingText(aComposing: String, aCode: String): String {
        if (aComposing.matches(nonZhuyinReg.toRegex())) {
            return aComposing.replaceFirst(Pattern.quote(aCode).toRegex(), "")
        }

        var sub: String
        var display = ""
        var value: Words
        val shift = 2 // In Zhuyin input, we have two digits for every symbol.
        var i = 0
        while (i <= aCode.length - shift) {
            sub = aCode.substring(i, i + shift)
            for ((_, value1) in mKeyCodes) {
                value = value1
                if (value.code == sub) {
                    display += value.value
                }
            }
            i += shift
        }

        // Finding the item in aComposing that is the same with display.
        val result = if (display.length < aComposing.length) display else aComposing
        return aComposing.replaceFirst(Pattern.quote(result).toRegex(), "")
    }

    private fun addExtraKeyMaps() {
        addKeyCode("ㄅ", "10", "ㄅ")
        addKeyCode("ㄆ", "11", "ㄆ")
        addKeyCode("ㄇ", "12", "ㄇ")
        addKeyCode("ㄈ", "13", "ㄈ")
        addKeyCode("ㄉ", "14", "ㄉ")
        addKeyCode("ㄊ", "15", "ㄊ")
        addKeyCode("ㄋ", "16", "ㄋ")
        addKeyCode("ㄌ", "17", "ㄌ")
        addKeyCode("ㄍ", "18", "ㄍ")
        addKeyCode("ㄎ", "19", "ㄎ")
        addKeyCode("ㄏ", "1A", "ㄏ")
        addKeyCode("ㄐ", "1B", "ㄐ")
        addKeyCode("ㄑ", "1C", "ㄑ")
        addKeyCode("ㄒ", "1D", "ㄒ")
        addKeyCode("ㄓ", "1E", "ㄓ")
        addKeyCode("ㄔ", "1F", "ㄔ")
        addKeyCode("ㄕ", "1G", "ㄕ")
        addKeyCode("ㄖ", "1H", "ㄖ")
        addKeyCode("ㄗ", "1I", "ㄗ")
        addKeyCode("ㄘ", "1J", "ㄘ")
        addKeyCode("ㄙ", "1K", "ㄙ")
        addKeyCode("ㄚ", "20", "ㄚ")
        addKeyCode("ㄛ", "21", "ㄛ")
        addKeyCode("ㄜ", "22", "ㄜ")
        addKeyCode("ㄝ", "23", "ㄝ")
        addKeyCode("ㄞ", "24", "ㄞ")
        addKeyCode("ㄟ", "25", "ㄟ")
        addKeyCode("ㄠ", "26", "ㄠ")
        addKeyCode("ㄡ", "27", "ㄡ")
        addKeyCode("ㄢ", "28", "ㄢ")
        addKeyCode("ㄣ", "29", "ㄣ")
        addKeyCode("ㄤ", "2A", "ㄤ")
        addKeyCode("ㄥ", "2B", "ㄥ")
        addKeyCode("ㄦ", "2C", "ㄦ")
        addKeyCode("ㄧ", "30", "ㄧ")
        addKeyCode("ㄨ", "31", "ㄨ")
        addKeyCode("ㄩ", "32", "ㄩ")

        addKeyCode("˙", "40", "˙")
        addKeyCode("ˊ", "41", "ˊ")
        addKeyCode("ˇ", "42", "ˇ")
        addKeyCode("ˋ", "43", "ˋ")
        addKeyCode("ˉ", "44", "ˉ")
    }

    private fun addKeyCode(key: String, code: String, display: String) {
        mKeyCodes[key] = Words(syllableCount(code), code, display)
    }

    private fun syllableCount(code: String): Int {
        var aCode = code
        aCode = aCode.trim { it <= ' ' }
        if (aCode.isEmpty()) {
            return 0
        }
        // An empty cell indicates that the corresponding syllable does not exist.
        return aCode.chars().filter { ch: Int -> ch == ' '.code }.count().toInt() + 1
    }

    private fun getDisplays(aKey: String): ArrayList<Words> {
        // Allow completion of uppercase/lowercase letters numbers, and symbols
        // aKey.length() > 1 only happens when switching from other keyboard.
        if (aKey.matches(nonZhuyinReg.toRegex()) || (aKey.length > 1 && mKeymaps.size == 0)) {
            return arrayListOf(Words(1, aKey, aKey))
        }

        var code: String = aKey.replace(nonZhuyinReg.toRegex(), "")
        code = getTransCode(code)
        loadKeymapIfNotLoaded(code)
        val map: KeyMap = mKeymaps[code] ?: return arrayListOf(Words(1, aKey, aKey))

        // When detecting special symbols at the last character, and
        // because special symbols are not defined in our code book. We
        // need to add it back to our generated word for doing following
        // AUTO_COMPOSE.
        val lastChar = "${aKey[aKey.length - 1]}"
        if (lastChar.matches(nonZhuyinReg.toRegex())) {
            val word: Words = map.displays[0]
            return arrayListOf(Words(1, word.code + lastChar, word.value + lastChar))
        }
        return map.displays
    }

    private fun getTransCode(aText: String): String {
        var code: String = aText
        var transCode = ""
        while (code.isNotEmpty()) {
            transCode += mKeyCodes[code.substring(0, 1)]!!.code
            code = code.replaceFirst(code.substring(0, 1).toRegex(), "")
        }
        return transCode
    }

    private fun loadKeymapIfNotLoaded(aKey: String) {
        if (mKeymaps.containsKey(aKey)) {
            return
        }
        loadKeymapTable(aKey)
    }

    private fun loadKeymapTable(aKey: String) {
        var reader = mDB?:return
        var transCode = aKey
        var limit = 50
        var exactQuery = false
        val firstKeyCodeInTones = '4' // the first keycode of tones[˙, ˊ, ˋ, ˉ].


        // Finding if aKey contains tones.
        if (transCode[transCode.length - 2] == firstKeyCodeInTones) {
            exactQuery = true
        }

        // We didn't store the first tone in DB.
        transCode = transCode.replace("44".toRegex(), "")

        sqliteArgs[0] = transCode
        sqliteArgs[1] = "" + limit

        // Query word exactly
        try {
            reader.rawQuery("SELECT code, word FROM words_" + transCode.substring(0, 2)
                    + " WHERE code = ? GROUP BY word ORDER BY frequency DESC LIMIT ?", sqliteArgs).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val key: String = cursor.getString(0)
                        val displays: String = cursor.getString(1)
                        addToKeyMap(aKey, key, displays)
                        --limit
                    } while (limit >= 0 && cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!exactQuery) {
            // Query word roughly
            roughSqliteArgs[0] = "$transCode%"
            roughSqliteArgs[1] = transCode
            roughSqliteArgs[2] = "$limit"
            try {
                reader.rawQuery("SELECT code, word FROM words_" + transCode.substring(0, 2)
                        + " WHERE code like ? and code!= ? GROUP BY word ORDER BY frequency DESC LIMIT ?", roughSqliteArgs).use { cursor ->
                    if (cursor.moveToFirst()) {
                        do {
                            val key: String = cursor.getString(0)
                            val word: String = cursor.getString(1)
                            addToKeyMap(aKey, key, word)
                            --limit
                        } while (limit >= 0 && cursor.moveToNext())
                    }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        if (limit <= 0) {
            return
        }

        // Query phrase
        reader = mPhraseDB?:return
        sqliteArgs[0] = "$transCode%"
        sqliteArgs[1] = "$limit"
        try {
            reader.rawQuery("SELECT code, word FROM phrases_" + transCode.substring(0, 2)
                    + " WHERE code like ? GROUP BY word ORDER BY frequency DESC LIMIT ?", sqliteArgs).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val key: String = cursor.getString(0)
                        val word: String = cursor.getString(1)
                        addToKeyMap(aKey, key, word)
                        --limit
                    } while (limit >= 0 && cursor.moveToNext())
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun addToKeyMap(aKey: String, aCode: String, aDisplays: String) {
        if (aKey.isEmpty()) {
            return
        }
        if (aCode.isEmpty()) {
            return
        }
        var keyMap: KeyMap? = mKeymaps[aKey]
        if (keyMap == null) {
            keyMap = KeyMap()
            mKeymaps[aKey] = keyMap
        }

        if (aDisplays.isNotEmpty()) {
            val displayList: Array<String> = aDisplays.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            for (display in displayList) {
                keyMap.displays.add(Words(syllableCount(aCode), aCode, display))
            }
        }
    }

    class KeyMap {
        var displays: ArrayList<Words> = ArrayList()
    }
}
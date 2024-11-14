package com.fpt.keyboard.ui.keyboards

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.view.inputmethod.EditorInfo
import com.fpt.keyboard.DictManager
import com.fpt.keyboard.R
import com.fpt.keyboard.StringUtils
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 14:34
 * desc    : base keyboard
 */
abstract class BaseKeyboard(private val context: Context) : KeyboardInterface {

    protected var mDB: SQLiteDatabase? = null

    init {
        mDB = loadDatabase(getDBName())
    }

    protected fun loadDatabase(name: String): SQLiteDatabase? {
        val dict: File = context.getDatabasePath(name) ?: return null
        try {
            return SQLiteDatabase.openDatabase(dict.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (ex: SQLiteException) {
            //Log.e(TAG, "Error reading $dbName database: $ex.message")
        }
        return null
    }

    protected abstract fun getDBName(): String

    override fun getEnterKeyText(imeOptions: Int): String {
        val locale: Locale = getLocale()
        return when (imeOptions and (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            EditorInfo.IME_ACTION_GO -> StringUtils.getStringByLocale(context, R.string.keyboard_go_label, locale)
            EditorInfo.IME_ACTION_NEXT -> StringUtils.getStringByLocale(context, R.string.keyboard_next_label, locale)
            EditorInfo.IME_ACTION_SEARCH -> StringUtils.getStringByLocale(context, R.string.keyboard_search_label, locale)
            EditorInfo.IME_ACTION_SEND -> StringUtils.getStringByLocale(context, R.string.keyboard_send_label, locale)
            else -> StringUtils.getStringByLocale(context, R.string.keyboard_enter_label, locale)
        }
    }

    override fun getSpaceKeyText(): String {
        val locale: Locale = getLocale()
        return StringUtils.getStringByLocale(context, R.string.keyboard_space_label, locale).uppercase(locale)
    }

    override fun getComposingText(composing: String, code: String): String {
        // If we don't have a text code from the code book,
        // just return an empty string to do composing.
        if (code.isEmpty()) {
            return ""
        }
        return composing.replaceFirst(Pattern.quote(code), "")
    }

    override fun getModeChangeKeyText(): String {
        return context.getString(R.string.keyboard_mode_change)
    }

    override fun getDomains(vararg domains: String): Array<String> {
        return arrayListOf(".com", ".net", ".org", ".co").also { it.addAll(domains) }.toTypedArray()
    }

}
package com.fpt.keyboard.ui.keyboards

import android.content.Context
import com.fpt.keyboard.R
import com.fpt.keyboard.StringUtils
import com.fpt.keyboard.input.Keyboard
import java.util.Locale

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 14:59
 * desc    : english
 */
class EnglishKeyboard(private val context: Context) : BaseLatinKeyboard(context) {

    private val mKeyboard: Keyboard by lazy {
        Keyboard(context, R.xml.keyboard_qwerty)
    }

    override fun getDBName(): String = "en_wordlist.db"

    override fun getAlphabeticKeyboard(): Keyboard = mKeyboard

    override fun getLocale(): Locale = Locale.ENGLISH

    override fun getKeyboardTitle(): String = StringUtils.getStringByLocale(context, R.string.settings_language_english, getLocale())

    override fun getSpaceKeyText(): String = getKeyboardTitle()

    override fun getDomains(vararg domains: String): Array<String> = super.getDomains(".uk", ".us")

}
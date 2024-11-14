package com.fpt.keyboard

import android.content.res.AssetManager
import java.util.Locale

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 16:16
 * desc    :
 */
object DictManager {

    private val BUILTIN_DICS_MAP: Map<String, Array<String>> = mapOf(
        "zh_CN" to arrayOf("google_pinyin.db"),
        "zh_TW" to arrayOf("zhuyin_words.db", "zhuyin_phrases.db"),
        "en_US" to arrayOf("en_wordlist.db")
    )

    fun getBuiltinDicName(locale: Locale): Array<String>? {
        return BUILTIN_DICS_MAP[locale.toString()]
    }

    fun copyDictToLocal(assets: AssetManager?) {
        TODO("Not yet implemented")
    }


}
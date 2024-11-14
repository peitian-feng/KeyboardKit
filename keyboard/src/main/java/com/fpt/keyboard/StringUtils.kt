package com.fpt.keyboard

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 14:15
 * desc    : string工具集
 */
object StringUtils {

    fun getStringByLocale(context: Context, id: Int, locale: Locale): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration).resources.getString(id)
    }

    fun removeSpaces(aText: String): String = aText.replace("\\s".toRegex(), "")

}
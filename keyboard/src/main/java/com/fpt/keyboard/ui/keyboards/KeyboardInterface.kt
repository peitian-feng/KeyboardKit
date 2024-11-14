package com.fpt.keyboard.ui.keyboards

import com.fpt.keyboard.input.Keyboard
import java.util.Locale

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 14:30
 * desc    : keyboard
 */
interface KeyboardInterface {

    data class Words(var syllable: Int, var code: String, var value: String)

    class CandidatesResult {
        enum class Action {
            SHOW_CANDIDATES,
            AUTO_COMPOSE
        }
        var words: ArrayList<Words>? = null
        var action: Action = Action.SHOW_CANDIDATES
        var composing: String? = null
    }

    fun getAlphabeticKeyboard(): Keyboard
    fun getAlphabeticCapKeyboard(): Keyboard? = null
    fun getSymbolsKeyboard(): Keyboard? = null

    fun getLocale(): Locale

    fun getKeyboardTitle(): String

    fun getEnterKeyText(imeOptions: Int): String

    fun getSpaceKeyText(): String

    fun getComposingText(composing: String, code: String): String

    fun getModeChangeKeyText(): String

    fun getDomains(vararg domains: String): Array<String>

    fun getCandidates(composingText: String?): CandidatesResult? = null
    fun getEmojiCandidates(composingText: String?): CandidatesResult? = null

    fun supportsAutoCompletion(): Boolean = false

    fun usesComposingText(): Boolean = false

}
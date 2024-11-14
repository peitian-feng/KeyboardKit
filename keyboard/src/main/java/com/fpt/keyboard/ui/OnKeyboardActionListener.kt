package com.fpt.keyboard.ui

import com.fpt.keyboard.input.Keyboard.Key

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 14:21
 * desc    : Listener for virtual keyboard events
 */
interface OnKeyboardActionListener {
    /**
     * Called when the user presses a key. This is sent before the {@link #onKey} is called.
     * For keys that repeat, this is only called once.
     * @param primaryCode the unicode of the key being pressed. If the touch is not on a valid
     * key, the value will be zero.
     */
    fun onPress(primaryCode: Int)
    /**
     * Called when a key is long pressed. By default this will open any popup keyboard associated
     * with this key through the attributes popupLayout and popupCharacters.
     * @param popupKey the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    fun onLongPress(popupKey: Key): Boolean
    /**
     * Called when the user releases a key. This is sent after the {@link #onKey} is called.
     * For keys that repeat, this is only called once.
     * @param primaryCode the code of the key that was released
     */
    fun onRelease(primaryCode: Int)
    /**
     * Send a key press to the listener.
     * @param primaryCode this is the key that was pressed
     * @param keyCodes the codes for all the possible alternative keys
     * with the primary code being the first. If the primary key code is
     * a single character such as an alphabet or number or symbol, the alternatives
     * will include other characters that may be on the same key or adjacent keys.
     * These codes are useful to correct for accidental presses of a key adjacent to
     * the intended key.
     */
    fun onKey(primaryCode: Int, keyCodes: IntArray, hasPopup: Boolean)

    fun onNoKey()
    /**
     * Sends a sequence of characters to the listener.
     * @param text the sequence of characters to be displayed.
     */
    fun onText(text: CharSequence)
}
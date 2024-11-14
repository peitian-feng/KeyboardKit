package com.fpt.keyboard.input

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.util.Xml
import android.view.KeyEvent
import androidx.annotation.XmlRes
import com.fpt.keyboard.R
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.StringTokenizer
import kotlin.math.ceil

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 14:16
 * desc    : 读取xml解析成keyboard struct
 */
class Keyboard {

    companion object {
        private const val TAG_KEYBOARD: String = "Keyboard"
        private const val TAG_ROW: String = "Row"
        private const val TAG_KEY: String = "Key"

        const val KEYCODE_SHIFT: Int = -1
        const val KEYCODE_MODE_CHANGE: Int = -2
        const val KEYCODE_CANCEL: Int = -3
        const val KEYCODE_DONE: Int = -4
        const val KEYCODE_DELETE: Int = -5
        const val KEYCODE_ALT: Int = -6
        const val KEYCODE_SPACE: Int = 32

        const val KEYCODE_SYMBOLS_CHANGE: Int = -10
        const val KEYCODE_LANGUAGE_CHANGE: Int = -12

        const val KEYCODE_EMOJI: Int = -13
        const val KEYCODE_DOMAIN: Int = -14

        const val EDGE_LEFT: Int = 0x01
        const val EDGE_RIGHT: Int = 0x02
        const val EDGE_TOP: Int = 0x04
        const val EDGE_BOTTOM: Int = 0x08

        /**
         * Number of key widths from current touch point to search for nearest keys.
         */
        private const val SEARCH_DISTANCE: Float = 1.8f

        private const val DEFAULT_CHARS_PER_POPUP_LINE: Int = 10

        fun getDimensionOrFraction(a: TypedArray, index: Int, base: Int, defValue: Int): Int {
            val value = a.peekValue(index) ?: return defValue
            if (value.type == TypedValue.TYPE_DIMENSION) {
                return a.getDimensionPixelOffset(index, defValue)
            } else if (value.type == TypedValue.TYPE_FRACTION) {
                // Round it to avoid values like 47.9999 from getting truncated
                return Math.round(a.getFraction(index, base, base, defValue.toFloat()))
            }
            return defValue
        }
    }

    /**
     * Keyboard mode, or zero, if none.
     */
    private var mKeyboardMode = 0

    /**
     * Width of the screen available to fit the keyboard
     */
    protected var mDisplayWidth = 0

    /**
     * Height of the screen
     */
    private var mDisplayHeight = 0

    /**
     * Horizontal gap default for all rows
     */
    protected var mDefaultHorizontalGap = 0

    /**
     * Default gap between rows
     */
    protected var mDefaultVerticalGap = 0

    /**
     * Default key width
     */
    protected var mDefaultWidth = 0

    /**
     * Default key height
     */
    protected var mDefaultHeight = 0

    /**
     * List of keys in this keyboard
     */
    protected val mKeys: ArrayList<Key>

    /**
     * List of modifier keys such as Shift & Alt, if any
     */
    private val mModifierKeys: ArrayList<Key>

    protected val rows: ArrayList<Row>

    /**
     * Key instance for the shift key, if present
     */
    private val mShiftKeys: Array<Key?> = arrayOf(null, null)

    /**
     * Key index for the shift key, if present
     */
    private val mShiftKeyIndices = intArrayOf(-1, -1)

    /**
     * Total width of the keyboard, including left side gaps and keys,
     * but not any gaps on the right side.
     */
    private var mTotalWidth = 0

    /**
     * Total height of the keyboard, including the padding and keys
     */
    private var mTotalHeight = 0

    private var mProximityThreshold = 0

    constructor(context: Context, @XmlRes xmlLayoutResId: Int) : this(
        context.applicationContext, xmlLayoutResId, 0)

    constructor(context: Context, @XmlRes xmlLayoutResId: Int, modeId: Int) : this(
        context, xmlLayoutResId, modeId,
        context.resources.displayMetrics.widthPixels,
        context.resources.displayMetrics.heightPixels)

    constructor(context: Context, @XmlRes xmlLayoutResId: Int, modeId: Int,
                width: Int, height: Int) {
        mKeyboardMode = modeId
        mDisplayWidth = width
        mDisplayHeight = height
        mDefaultHorizontalGap = 0
        mDefaultVerticalGap = 0
        mDefaultWidth = mDisplayWidth / 10
        mDefaultHeight = mDefaultWidth
        mKeys = ArrayList()
        mModifierKeys = ArrayList()
        rows = ArrayList()
        loadKeyboard(context, context.resources.getXml(xmlLayoutResId))
    }

    private var mMaxColumns = 0

    constructor(context: Context, @XmlRes xmlLayoutResId: Int, characters: CharSequence, columns: Int, horizontalPadding: Int, verticalGap: Int) : this(context, xmlLayoutResId) {
        mTotalWidth = 0

        if (verticalGap >= 0) {
            mDefaultVerticalGap = verticalGap
        }

        mMaxColumns = if (columns <= 0) DEFAULT_CHARS_PER_POPUP_LINE else columns
        val mRows: Array<Row?> = arrayOfNulls(mMaxColumns)
        val rowsNum = ceil((characters.length + 0.1) / columns).toInt()
        for (i in 0 until rowsNum) {
            val row = Row(this)
            row.defaultHeight = mDefaultHeight
            row.defaultWidth = mDefaultWidth
            row.defaultHorizontalGap = mDefaultHorizontalGap
            row.verticalGap = mDefaultVerticalGap
            row.rowEdgeFlags = EDGE_TOP
            mRows[i] = row
        }

        var x = 0
        var y = 0
        var column = 0
        for (i in characters.indices) {
            val rowIndex = i / columns
            val row = mRows[rowIndex]?:continue
            if (column >= mMaxColumns || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
                row.rowEdgeFlags = EDGE_BOTTOM

                x = 0
                y += mDefaultVerticalGap + mDefaultHeight
                column = 0
            }

            val c = characters[i]
            val key = Key(row)
            key.x = x
            key.y = y
            key.label = c.toString()
            key.codes = intArrayOf(c.code)
            column++

            // 所有的key
            getKeys().add(key)
            // 一行的key
            row.mKeys.add(key)

            x += key.width + key.gap
            if (x > mTotalWidth) {
                mTotalWidth = x
            }

            rows.add(row)
        }

        mTotalHeight = y + mDefaultHeight
    }

    private fun loadKeyboard(context: Context, parser: XmlResourceParser) {
        var inKey = false
        var inRow = false
        var x = 0
        var y = 0
        var key: Key? = null
        var currentRow: Row? = null
        val res: Resources = context.resources
        var skipRow: Boolean

        try {
            var event: Int
            while ((parser.next().also { event = it }) != XmlResourceParser.END_DOCUMENT) {
                if (event == XmlResourceParser.START_TAG) {
                    val tag = parser.name
                    if (TAG_ROW == tag) {
                        inRow = true
                        x = 0
                        currentRow = createRowFromXml(res, parser)
                        rows.add(currentRow)
                        skipRow = currentRow.mode != 0 && currentRow.mode != mKeyboardMode
                        if (skipRow) {
                            skipToEndOfRow(parser)
                            inRow = false
                        }
                    } else if (TAG_KEY == tag) {
                        inKey = true
                        key = createKeyFromXml(res, currentRow, x, y, parser)
                        mKeys.add(key)
                        val codes = key.codes
                        if (codes != null && codes.isNotEmpty()) {
                            if (codes[0] == KEYCODE_SHIFT) {
                                // Find available shift key slot and put this shift key in it
                                for (i in mShiftKeys.indices) {
                                    if (mShiftKeys[i] == null) {
                                        mShiftKeys[i] = key
                                        mShiftKeyIndices[i] = mKeys.size - 1
                                        break
                                    }
                                }
                                mModifierKeys.add(key)
                            } else if (codes[0] == KEYCODE_ALT) {
                                mModifierKeys.add(key)
                            }
                        }
                        currentRow?.mKeys?.add(key)
                    } else if (TAG_KEYBOARD == tag) {
                        parseKeyboardAttributes(res, parser)
                    }
                } else if (event == XmlResourceParser.END_TAG) {
                    if (inKey) {
                        inKey = false
                        if (key != null) {
                            x += key.gap + key.width
                        }
                        if (x > mTotalWidth) {
                            mTotalWidth = x
                        }
                    } else if (inRow) {
                        inRow = false
                        if (currentRow != null) {
                            y += currentRow.verticalGap
                            y += currentRow.defaultHeight
                        }
                    } else {
                        // TODO: error or extend?
                    }
                }
            }
        } catch (e: Exception) {
            //todo Log.e(TAG, "Parse Error: $e")
            //e.printStackTrace()
        }

        mTotalHeight = y - mDefaultVerticalGap
    }

    private fun createRowFromXml(res: Resources, parser: XmlResourceParser): Row = Row(res, this, parser)

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skipToEndOfRow(parser: XmlResourceParser) {
        var event: Int
        while ((parser.next().also { event = it }) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.END_TAG && parser.name == TAG_ROW) {
                break
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun createKeyFromXml(res: Resources, parent: Row?, x: Int, y: Int, parser: XmlResourceParser): Key {
        if (parent == null) {
            throw IllegalStateException("parent row not be null!")
        }
        val key = Key(res, parent, x, y, parser)
        key.codes?.let { codes ->
            if (codes[0] == KeyEvent.KEYCODE_ENTER || codes[0] == KEYCODE_DONE) {
                mEnterKey = key
            } else if (codes[0] == KEYCODE_SPACE) {
                mSpaceKey = key
            } else if (codes[0] == KEYCODE_MODE_CHANGE) {
                mModeChangeKey = key
            }
        }
        return key;
    }

    private fun parseKeyboardAttributes(res: Resources, parser: XmlResourceParser) {
        val ta = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard)
        mDefaultWidth = getDimensionOrFraction(ta, R.styleable.Keyboard_keyWidth, mDisplayWidth, mDisplayWidth / 10)
        mDefaultHeight = getDimensionOrFraction(ta, R.styleable.Keyboard_keyHeight, mDisplayHeight, 50)
        mDefaultHorizontalGap = getDimensionOrFraction(ta, R.styleable.Keyboard_horizontalGap, mDisplayWidth, 0)
        mDefaultVerticalGap = getDimensionOrFraction(ta, R.styleable.Keyboard_verticalGap, mDisplayHeight, 0)
        mProximityThreshold = (mDefaultWidth * SEARCH_DISTANCE).toInt()
        mProximityThreshold *= mProximityThreshold // Square it for comparison
        ta.recycle()
    }

    fun getWidth(): Int = mTotalWidth

    fun getHeight(): Int = mTotalHeight

    fun resize(newWidth: Int, newHeight: Int) {
        val numRows = rows.size
        for (rowIndex in 0 until numRows) {
            val row: Row = rows[rowIndex]
            val numKeys: Int = row.mKeys.size
            var totalGap = 0
            var totalWidth = 0
            for (keyIndex in 0 until numKeys) {
                val key: Key = row.mKeys[keyIndex]
                if (keyIndex > 0) {
                    totalGap += key.gap
                }
                totalWidth += key.width
            }
            if (totalGap + totalWidth > newWidth) {
                var x = 0
                val scaleFactor = (newWidth - totalGap).toFloat() / totalWidth
                for (keyIndex in 0 until numKeys) {
                    val key: Key = row.mKeys[keyIndex]
                    key.width = (key.width * scaleFactor).toInt()
                    key.x = x
                    x += key.width + key.gap
                }
            }
        }
        mTotalWidth = newWidth
        // TODO: This does not adjust the vertical placement according to the new size.
        // The main problem in the previous code was horizontal placement/size, but we should
        // also recalculate the vertical sizes/positions when we get this resize call.
    }

    fun getKeys(): ArrayList<Key> = mKeys

    private var mDisabledKeysIndexes: IntArray = intArrayOf()

    fun disableKeys(disabledKeyIndexes: IntArray) {
        mDisabledKeysIndexes = disabledKeyIndexes
    }

    fun isKeyEnabled(keyIndex: Int): Boolean {
        for (key in mDisabledKeysIndexes) {
            if (key == keyIndex) {
                return false
            }
        }
        return true
    }

    /** Is the keyboard in the shifted state  */
    private var mShifted = false

    fun setShifted(shiftState: Boolean): Boolean {
        for (shiftKey in mShiftKeys) {
            if (shiftKey != null) {
                shiftKey.on = shiftState
            }
        }
        if (mShifted != shiftState) {
            mShifted = shiftState
            return true
        }
        return false
    }

    fun isShifted(): Boolean = mShifted

    fun getShiftKeyIndices(): IntArray = mShiftKeyIndices

    /**
     * Returns the indices of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    fun getNearestKeys(x: Int, y: Int): IntArray {
        for ((i, key) in mKeys.withIndex()) {
            if (key.isInside(x, y)) {
                return intArrayOf(i)
            }
        }
        return IntArray(0)
    }

    private var mEnterKey: Key? = null
    private var mSpaceKey: Key? = null
    private var mModeChangeKey: Key? = null

    fun setEnterKeyLabel(aText: String): Boolean {
        if (mEnterKey != null) {
            val changed = !aText.equals(mEnterKey!!.label.toString(), ignoreCase = true)
            mEnterKey!!.label = aText
            return changed
        }
        return false
    }

    fun setSpaceKeyLabel(aText: String): Boolean {
        if (mSpaceKey != null) {
            val changed = !aText.equals(mSpaceKey!!.label.toString(), ignoreCase = true)
            mSpaceKey!!.label = aText
            return changed
        }
        return false
    }

    fun setModeChangeKeyLabel(aText: String): Boolean {
        if (mModeChangeKey != null) {
            val changed = !aText.equals(mModeChangeKey!!.label.toString(), ignoreCase = true)
            mModeChangeKey!!.label = aText
            return changed
        }
        return false
    }

    fun getMaxColumns(): Int = mMaxColumns

    class Key {
        companion object {
            private
            val KEY_STATE_NORMAL_ON: IntArray = intArrayOf(
                android.R.attr.state_checkable,
                android.R.attr.state_checked
            )

            private
            val KEY_STATE_PRESSED_ON: IntArray = intArrayOf(
                android.R.attr.state_pressed,
                android.R.attr.state_checkable,
                android.R.attr.state_checked
            )

            private
            val KEY_STATE_NORMAL_OFF: IntArray = intArrayOf(
                android.R.attr.state_checkable
            )

            private
            val KEY_STATE_PRESSED_OFF: IntArray = intArrayOf(
                android.R.attr.state_pressed,
                android.R.attr.state_checkable
            )

            private
            val KEY_STATE_NORMAL: IntArray = intArrayOf()

            private
            val KEY_STATE_PRESSED: IntArray = intArrayOf(
                android.R.attr.state_pressed
            )
        }
        /**
         * All the key codes (unicode or custom code) that this key could generate, zero'th
         * being the most important.
         */
        var codes: IntArray? = null

        /** Label to display  */
        var label: CharSequence? = null

        /** Icon to display instead of a label. Icon takes precedence over a label  */
        var icon: Drawable? = null

        /** Width of the key, not including the gap  */
        var width: Int = 0

        /** Height of the key, not including the gap  */
        var height: Int = 0

        /** X coordinate of the key in the keyboard layout  */
        var x: Int = 0

        /** Y coordinate of the key in the keyboard layout  */
        var y: Int = 0

        /** The current pressed state of this key  */
        var pressed: Boolean = false

        /** The horizontal gap before this key  */
        var gap: Int = 0

        /** Text to output when pressed. This can be multiple characters, like ".com"  */
        var text: CharSequence? = null

        /** Popup characters, like "yýỳŷÿỹ" */
        var popupCharacters: CharSequence? = null

        /** If this is a sticky key, is it on?  */
        var on: Boolean = false

        /**
         * The keyboard that this key belongs to
         */
        private val keyboard: Keyboard

        /**
         * Flags that specify the anchoring to edges of the keyboard for detecting touch events
         * that are just out of the boundary of the key. This is a bit mask of
         * [Keyboard.EDGE_LEFT], [Keyboard.EDGE_RIGHT], [Keyboard.EDGE_TOP] and
         * [Keyboard.EDGE_BOTTOM].
         */
        var edgeFlags: Int = 0

        /**
         * Preview version of the icon, for the preview popup
         */
        var iconPreview: Drawable? = null

        /**
         * If this key pops up a mini keyboard,
         * this is the resource id for the XML layout for that keyboard.
         */
        var popupResId: Int = 0

        /**
         * Whether this key repeats itself when held down
         */
        var repeatable: Boolean = false

        /**
         * Whether this is a modifier key, such as Shift or Alt
         */
        var modifier: Boolean = false

        /**
         * Whether this key is sticky, i.e., a toggle key
         * 持续按着没松手 显示字母预览时使用
         */
        var sticky: Boolean = false

        /**
         * Create an empty key with no attributes.
         */
        constructor(parent: Row) {
            keyboard = parent.parent
            width = parent.defaultWidth
            height = parent.defaultHeight
            gap = parent.defaultHorizontalGap
            edgeFlags = parent.rowEdgeFlags
        }

        constructor(res: Resources, parent: Row, x: Int, y: Int, parser: XmlResourceParser): this(parent) {
            this.x = x
            this.y = y

            var ta = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard)
            width = getDimensionOrFraction(ta, R.styleable.Keyboard_keyWidth, keyboard.mDisplayWidth, parent.defaultWidth)
            height = getDimensionOrFraction(ta, R.styleable.Keyboard_keyHeight, keyboard.mDisplayHeight, parent.defaultHeight)
            gap = getDimensionOrFraction(ta, R.styleable.Keyboard_horizontalGap, keyboard.mDisplayWidth, parent.defaultHorizontalGap)
            ta.recycle()

            ta = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard_Key)
            this.x += gap
            val codesValue = TypedValue()
            ta.getValue(R.styleable.Keyboard_Key_codes, codesValue)
            if (codesValue.type == TypedValue.TYPE_INT_DEC
                || codesValue.type == TypedValue.TYPE_INT_HEX) {
                codes = intArrayOf(codesValue.data)
            } else if (codesValue.type == TypedValue.TYPE_STRING) {
                codes = parseCSV(codesValue.string.toString())
            }

            popupCharacters = ta.getText(R.styleable.Keyboard_Key_popupCharacters)
            iconPreview = ta.getDrawable(R.styleable.Keyboard_Key_iconPreview)
            iconPreview?.let {
                it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
            }
            popupResId = ta.getResourceId(R.styleable.Keyboard_Key_popupKeyboard, 0)
            repeatable = ta.getBoolean(R.styleable.Keyboard_Key_isRepeatable, false)
            modifier = ta.getBoolean(R.styleable.Keyboard_Key_isModifier, false)
            sticky = ta.getBoolean(R.styleable.Keyboard_Key_isSticky, false)
            edgeFlags = ta.getInt(R.styleable.Keyboard_Key_keyEdgeFlags, 0)
            edgeFlags = edgeFlags or parent.rowEdgeFlags
            icon = ta.getDrawable(R.styleable.Keyboard_Key_keyIcon)
            icon?.let {
                it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
            }
            label = ta.getText(R.styleable.Keyboard_Key_keyLabel)
            text = ta.getText(R.styleable.Keyboard_Key_keyOutputText)

            if (codes == null && !label.isNullOrEmpty()) {
                codes = intArrayOf(label!![0].code)
            }
            ta.recycle()
        }

        private fun parseCSV(value: String): IntArray {
            var count = 0
            var lastIndex = 0
            if (value.isNotEmpty()) {
                count++
                while ((value.indexOf(",", lastIndex + 1).also { lastIndex = it }) > 0) {
                    count++
                }
            }
            val values = IntArray(count)
            count = 0
            val st = StringTokenizer(value, ",")
            while (st.hasMoreTokens()) {
                try {
                    values[count++] = st.nextToken().toInt()
                } catch (nfe: NumberFormatException) {
                    //todo Log.e(TAG, "Error parsing keycodes $value")
                }
            }
            return values
        }

        /**
         * Returns the drawable state for the key, based on the current state and type of the key.
         * @return the drawable state of the key.
         * @see android.graphics.drawable.StateListDrawable.setState
         */
        fun getCurrentDrawableState(): IntArray {
            var states: IntArray = KEY_STATE_NORMAL
            if (on) {
                states = if (pressed) {
                    KEY_STATE_PRESSED_ON
                } else {
                    KEY_STATE_NORMAL_ON
                }
            } else {
                if (sticky) {
                    states = if (pressed) {
                        KEY_STATE_PRESSED_OFF
                    } else {
                        KEY_STATE_NORMAL_OFF
                    }
                } else {
                    if (pressed) {
                        states = KEY_STATE_PRESSED
                    }
                }
            }
            return states
        }

        /**
         * Returns the square of the distance between the center of the key and the given point.
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return the square of the distance of the point from the center of the key
         */
        fun squaredDistanceFrom(x: Int, y: Int): Int {
            val xDist = this.x + width / 2 - x
            val yDist = this.y + height / 2 - y
            return xDist * xDist + yDist * yDist
        }

        /**
         * Detects if a point falls inside this key.
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return whether or not the point falls inside the key. If the key is attached to an edge,
         * it will assume that all points between the key and the edge are considered to be inside
         * the key.
         */
        fun isInside(x: Int, y: Int): Boolean {
            val leftEdge = (edgeFlags and EDGE_LEFT) > 0
            val rightEdge = (edgeFlags and EDGE_RIGHT) > 0
            val topEdge = (edgeFlags and EDGE_TOP) > 0
            val bottomEdge = (edgeFlags and EDGE_BOTTOM) > 0
            return ((x >= this.x || (leftEdge && x <= this.x + this.width))
                    && (x < this.x + this.width || (rightEdge && x >= this.x))
                    && (y >= this.y || (topEdge && y <= this.y + this.height))
                    && (y < this.y + this.height || (bottomEdge && y >= this.y)))
        }
    }

    class Row {

        val parent: Keyboard

        /**
         * Default width of a key in this row.
         */
        var defaultWidth: Int = 0

        /**
         * Default height of a key in this row.
         */
        var defaultHeight: Int = 0

        /**
         * Vertical gap following this row.
         */
        var verticalGap: Int = 0

        /**
         * Horizontal gap between keys in this row.
         */
        var defaultHorizontalGap: Int = 0

        /**
         * Edge flags for this row of keys. Possible values that can be assigned are
         * [EDGE_TOP][Keyboard.EDGE_TOP] and [EDGE_BOTTOM][Keyboard.EDGE_BOTTOM]
         */
        var rowEdgeFlags: Int = 0

        /**
         * The keyboard mode for this row
         */
        var mode: Int = 0

        /**
         * for popup
         */
        val mKeys: ArrayList<Key> = ArrayList()

        constructor(parent: Keyboard) {
            this.parent = parent
        }

        constructor(res: Resources, parent: Keyboard, parser: XmlResourceParser): this(parent) {
            var ta = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard)
            defaultWidth = getDimensionOrFraction(ta, R.styleable.Keyboard_keyWidth, parent.mDisplayWidth, parent.mDefaultWidth)
            defaultHeight = getDimensionOrFraction(ta, R.styleable.Keyboard_keyHeight, parent.mDisplayHeight, parent.mDefaultHeight)
            defaultHorizontalGap = getDimensionOrFraction(ta, R.styleable.Keyboard_horizontalGap, parent.mDisplayWidth, parent.mDefaultHorizontalGap)
            verticalGap = getDimensionOrFraction(ta, R.styleable.Keyboard_verticalGap, parent.mDisplayHeight, parent.mDefaultVerticalGap)
            ta.recycle()

            ta = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard_Row)
            rowEdgeFlags = ta.getInt(R.styleable.Keyboard_Row_rowEdgeFlags, 0)
            mode = ta.getResourceId(R.styleable.Keyboard_Row_keyboardMode, 0)
            ta.recycle()
        }
    }

}
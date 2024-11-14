package com.fpt.keyboard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.fpt.keyboard.R
import com.fpt.keyboard.input.Keyboard
import com.fpt.keyboard.input.Keyboard.Key
import java.util.Arrays
import java.util.Locale
import kotlin.math.max

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/13 14:27
 * desc    :
 */
class KeyboardView : View {

    companion object {
        private const val NOT_A_KEY: Int = -1

        private val KEY_STATE_HOVERED: IntArray = intArrayOf(
            android.R.attr.state_hovered,
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        )

        private val KEY_STATE_NORMAL: IntArray = intArrayOf()

        private const val MAX_NEARBY_KEYS: Int = 12

        private const val DEBOUNCE_TIME: Int = 70

        private const val MSG_LONGPRESS: Int = 4
    }

    private var mKeyBackground: Drawable? = null

    private var mKeyTextColor: ColorStateList? = null

    private var mKeyTextSize = 0

    private var mLabelTextSize = 0

    private val mPadding: Rect = Rect()

    private val mHoveredKey: IntArray = IntArray(3)

    private val mPrevHoveredKey = IntArray(3)

    private var mKeyboard: Keyboard? = null

    private var mBuffer: Bitmap? = null

    private var mCanvas: Canvas? = null

    private var mDrawPending = false

    private var mKeyboardChanged = false

    private val mPaint: Paint = Paint().also {
        it.isAntiAlias = true
        it.textAlign = Align.CENTER
        it.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
    }

    private var mInvalidatedKey: Key? = null

    private val mClipRegion: Rect = Rect()

    private val mDirtyRect = Rect()

    private var mKeys: Array<Key>? = null

    private var mFeaturedKeyBackground: Drawable? = null

    private var mFeaturedKeyCodes = HashSet<Int>()

    // Variables for dealing with multiple pointers
    private var mOldPointerCount = 1
    private var mOldPointerX = 0f
    private var mOldPointerY = 0f

    private var mProximityCorrectOn = false
    private var mProximityThreshold = 0

    private val mDistances = IntArray(MAX_NEARBY_KEYS)

    private var mAbortKey = false

    private var mMiniKeyboardOnScreen = false
    private val mBackgroundDimAmount = 0.5f

    private val mKeyboardActionListener: OnKeyboardActionListener? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        val ta: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.KeyboardView, defStyleAttr, defStyleRes)
        mKeyBackground = ta.getDrawable(R.styleable.KeyboardView_keyBackground)
        mKeyTextColor = ta.getColorStateList(R.styleable.KeyboardView_keyTextColor)
        // a-z、1-9、*、#...
        mKeyTextSize = ta.getDimensionPixelSize(R.styleable.KeyboardView_keyTextSize, 18)
        // enter、done、space..
        mLabelTextSize = ta.getDimensionPixelSize(R.styleable.KeyboardView_labelTextSize, 14)
        ta.recycle()

        mKeyBackground?.getPadding(mPadding)
        clearHover()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mKeyboard?.let {
            setMeasuredDimension(
                it.getWidth() + paddingLeft + paddingRight,
                it.getHeight() + paddingTop + paddingBottom
            )
        } ?: super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mKeyboard?.resize(w, h)
        // Release the buffer, if any and it will be reallocated on the next draw
        mBuffer?.recycle()
        mBuffer = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mKeyboard == null) {
            return
        }
        if (mDrawPending || mBuffer == null || mKeyboardChanged) {
            onBufferDraw()
        }
        mBuffer?.let {
            canvas.drawBitmap(it, 0f, 0f, mPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        var result = super.onTouchEvent(event)
        if (event == null) {
            return result
        }
        val me = event
        // Convert multi-pointer up/down events to single up/down events to
        // deal with the typical multi-pointer behavior of two-thumb typing
        val pointerCount: Int = me.getPointerCount()
        val action: Int = me.getAction()
        val now: Long = me.getEventTime()

        if (pointerCount != mOldPointerCount) {
            if (pointerCount == 1) {
                // Send a down event for the latest pointer
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                    me.x, me.y, me.metaState)
                result = onModifiedTouchEvent(down, false)
                down.recycle()

                // If it's an up action, then deliver the up as well.
                if (action == MotionEvent.ACTION_UP) {
                    result = onModifiedTouchEvent(me, true)
                }
            } else {
                // Send an up event for the last pointer
                val up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                    mOldPointerX, mOldPointerY, me.metaState)
                result = onModifiedTouchEvent(up, true)
                up.recycle()
            }
        } else {
            if (pointerCount == 1) {
                result = onModifiedTouchEvent(me, false)
                mOldPointerX = me.x
                mOldPointerY = me.y
            } else {
                // Don't do anything when 2 pointers are down and moving.
                result = true
            }
        }
        mOldPointerCount = pointerCount

        return result
    }

    override fun onHoverEvent(motionEvent: MotionEvent?): Boolean {
        val result = super.onHoverEvent(motionEvent)
        motionEvent?.let { event ->
            var keyIndex: Int = NOT_A_KEY
            if (event.action != MotionEvent.ACTION_HOVER_EXIT) {
                val touchX = event.x.toInt() - paddingLeft
                val touchY = event.y.toInt() - paddingTop
                keyIndex = getKeyIndices(touchX, touchY, null)
            }

            mPrevHoveredKey[event.deviceId] = mHoveredKey[event.deviceId]
            mHoveredKey[event.deviceId] = keyIndex
            val prevHovered = mPrevHoveredKey[event.deviceId]
            val currentHovered = mHoveredKey[event.deviceId]
            if (currentHovered != NOT_A_KEY && prevHovered != currentHovered) {
                invalidateKey(currentHovered)
            }
            if (prevHovered != NOT_A_KEY && prevHovered != currentHovered) {
                invalidateKey(prevHovered)
            }
        }
        return result
    }

    override fun isHovered(): Boolean {
        var isHovered = false
        for (value in mHoveredKey) {
            isHovered = isHovered or (value != NOT_A_KEY)
        }
        return isHovered
    }

    override fun setHovered(hovered: Boolean) {
        if (!hovered) {
            var hasChanged = false
            for (i in mHoveredKey.indices) {
                hasChanged = hasChanged or (mPrevHoveredKey[i] != mHoveredKey[i])
            }

            if (hasChanged) {
                clearHover()
                invalidateAllKeys()
            }
        }
        super.setHovered(hovered)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // todo handler -> openPopupIfRequired
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        closing()
    }

    //----------------------------------------------------------------------------------------

    private fun onBufferDraw() {
        assert(mKeyboard == null)

        if (mKeyboardChanged) {
            if (mBuffer?.getWidth() != width || mBuffer?.getHeight() != height) {
                mBuffer?.recycle()
                mBuffer = null
            }
            mKeyboardChanged = false
        }

        if (mBuffer == null) {
            // Make sure our bitmap is at least 1x1
            val width = max(1.0, width.toDouble()).toInt()
            val height = max(1.0, height.toDouble()).toInt()
            mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            mCanvas = mBuffer?.let { Canvas(it) }

            invalidateAllKeys()
        }

        val canvas: Canvas = mCanvas ?: return
        canvas.clipRect(mDirtyRect)
        canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR)

        val paint = mPaint
        val kbdPaddingLeft = paddingLeft
        val kbdPaddingTop = paddingTop

        var drawSingleKey = false
        mInvalidatedKey?.let { invalidKey ->
            mClipRegion.setEmpty()
            if (canvas.getClipBounds(mClipRegion)) {
                // Is clipRegion completely contained within the invalidated key?
                if (invalidKey.x + kbdPaddingLeft - 1 <= mClipRegion.left &&
                    invalidKey.y + kbdPaddingTop - 1 <= mClipRegion.top &&
                    invalidKey.x + invalidKey.width + kbdPaddingLeft + 1 >= mClipRegion.right &&
                    invalidKey.y + invalidKey.height + kbdPaddingTop + 1 >= mClipRegion.bottom
                ) {
                    drawSingleKey = true
                }
            }
        }

        // draw Keys
        mKeys?.let { keys ->
            var key: Key
            for (i in keys.indices) {
                key = keys[i]
                if (drawSingleKey && mInvalidatedKey != key) {
                    continue
                }

                var drawableState: IntArray = key.getCurrentDrawableState()
                if (mKeyboard!!.isKeyEnabled(i)) {
                    if (isKeyHovered(i) && !key.pressed) {
                        drawableState = KEY_STATE_HOVERED
                    }
                } else {
                    drawableState = KEY_STATE_NORMAL
                }

                var keyBackground = mKeyBackground
                if (mFeaturedKeyBackground != null) {
                    val isFeaturedKey = key.codes?.get(0)?.let {
                        mFeaturedKeyCodes.contains(it)
                    } ?: false
                    if (isFeaturedKey) {
                        keyBackground = mFeaturedKeyBackground
                    }
                }
                keyBackground?.let {
                    it.setState(drawableState)
                    // draw keyBackground
                    val bounds = it.bounds
                    if (key.width != bounds.right || key.height != bounds.bottom) {
                        it.setBounds(0, 0, key.width, key.height)
                    }
                    canvas.translate((key.x + kbdPaddingLeft).toFloat(), (key.y + kbdPaddingTop).toFloat())
                    it.draw(canvas)
                }

                val targetColor = mKeyTextColor?.getColorForState(drawableState, Color.WHITE) ?: Color.WHITE
                key.label?.let {
                    // Switch the character to uppercase if shift is pressed
                    val label: String = adjustCase(it).toString()
                    val descent: Float
                    // For characters, use large font. For labels like "Done", use small font.
                    if (label.length > 1 && key.codes!!.size < 2) {
                        paint.textSize = mLabelTextSize.toFloat()
                        paint.setTypeface(Typeface.DEFAULT_BOLD)
                        descent = mLabelTextSize * 0.1f
                    } else {
                        paint.textSize = mKeyTextSize.toFloat()
                        paint.setTypeface(Typeface.DEFAULT)
                        descent = paint.descent()
                    }
                    // Draw the text
                    paint.color = targetColor
                    canvas.drawText(label,
                        (key.width - mPadding.left - mPadding.right) / 2.0f + mPadding.left,
                        ((key.height - mPadding.top - mPadding.bottom) / 2.0f + (paint.textSize / 2) - descent) + mPadding.top,
                        paint)
                } ?: key.icon?.let { icon ->
                    val drawableX: Float =
                        (key.width - mPadding.left - mPadding.right - icon.intrinsicWidth) / 2.0f + mPadding.left
                    val drawableY: Float =
                        (key.height - mPadding.top - mPadding.bottom - icon.intrinsicHeight) / 2.0f + mPadding.top
                    canvas.translate(drawableX, drawableY)
                    icon.colorFilter = BlendModeColorFilter(targetColor, BlendMode.MODULATE)
                    icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                    icon.draw(canvas)
                    canvas.translate(-drawableX, -drawableY)
                }

                canvas.translate((-key.x - kbdPaddingLeft).toFloat(), (-key.y - kbdPaddingTop).toFloat())
            }
        }

        mInvalidatedKey = null

        // Overlay a dark rectangle to dim the keyboard
        if (mMiniKeyboardOnScreen) {
            paint.color = (mBackgroundDimAmount * 0xFF).toInt() shl 24
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        mDrawPending = false
        mDirtyRect.setEmpty()
    }

    fun invalidateAllKeys() {
        clearHover()
        mDirtyRect.union(0, 0, width, height)
        mDrawPending = true
        invalidate()
    }

    private fun clearHover() {
        Arrays.fill(mHoveredKey, NOT_A_KEY)
        Arrays.fill(mPrevHoveredKey, NOT_A_KEY)
    }

    private fun isKeyHovered(keyIndex: Int): Boolean {
        var isHovered = false
        for (value in mHoveredKey) {
            isHovered = isHovered or (value == keyIndex)
        }

        return isHovered
    }

    private fun getKeyIndices(x: Int, y: Int, allKeys: IntArray?): Int {
        val keys: Array<Key> = mKeys?: return NOT_A_KEY
        var primaryIndex: Int = NOT_A_KEY
        var closestKey: Int = NOT_A_KEY
        var closestKeyDist: Int = mProximityThreshold + 1
        Arrays.fill(mDistances, Int.MAX_VALUE)
        val nearestKeyIndices: IntArray = mKeyboard?.getNearestKeys(x, y)?:return NOT_A_KEY
        val keyCount = nearestKeyIndices.size

        for (i in 0 until keyCount) {
            val key: Key = keys[nearestKeyIndices[i]]
            var dist = 0
            val isInside: Boolean = isInside(key, x, y)
            if (isInside) {
                primaryIndex = nearestKeyIndices[i]
            }

            if (((mProximityCorrectOn &&
                        (key.squaredDistanceFrom(x, y).also { dist = it }) < mProximityThreshold) ||
                        isInside)) {
                if (allKeys == null) continue

                key.codes?.let {
                    if (it[0] > 32) {
                        // Find insertion point
                        if (dist < closestKeyDist) {
                            closestKeyDist = dist
                            closestKey = nearestKeyIndices[i]
                        }

                        val nCodes: Int = it.size
                        for (j in mDistances.indices) {
                            if (mDistances[j] > dist) {
                                // Make space for nCodes codes
                                System.arraycopy(mDistances, j, mDistances,
                                    j + nCodes, mDistances.size - j - nCodes)
                                System.arraycopy(allKeys, j, allKeys,
                                    j + nCodes, allKeys.size - j - nCodes)
                                for (c in 0 until nCodes) {
                                    allKeys[j + c] = key.codes!![c]
                                    mDistances[j + c] = dist
                                }
                                break
                            }
                        }
                    }
                }
            }
        }

        if (primaryIndex == NOT_A_KEY) {
            primaryIndex = closestKey
        }
        return primaryIndex
    }

    private fun isInside(key: Key, x: Int, y: Int): Boolean {
        return (x >= key.x) && (x < key.x + key.width) && (y >= key.y) && (y < key.y + key.height)
    }

    private fun adjustCase(label: CharSequence): CharSequence {
        val isShifted = mKeyboard?.isShifted()?:false
        if (isShifted && label.isNotEmpty() &&
            label.length < 3 &&
            Character.isLowerCase(label[0])) {
            return label.toString().uppercase(Locale.getDefault())
        }
        return label
    }



    private var mLastCodeX = 0
    private var mLastCodeY = 0
    private var mLastKeyTime: Long = 0
    private var mLastKey = 0
    private var mCurrentKeyTime: Long = 0
    private var mCurrentKey: Int = NOT_A_KEY
    private var mDownTime: Long = 0
    private var mLastMoveTime: Long = 0
    private val LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout()

    private fun onModifiedTouchEvent(event: MotionEvent, possiblePoly: Boolean): Boolean {
        val mPaddingLeft = paddingLeft
        val mPaddingTop = paddingTop
        var touchX: Int = event.x.toInt() - mPaddingLeft
        var touchY: Int = event.y.toInt() - mPaddingTop
        val action: Int = event.action
        val eventTime: Long = event.eventTime
        val keyIndex = getKeyIndices(touchX, touchY, null)

        if (keyIndex != NOT_A_KEY && mKeyboard != null && !mKeyboard!!.isKeyEnabled(keyIndex)) {
            return true
        }

        // 直到DOWN事件前忽略所有motion事件
        if (mAbortKey && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
            return true
        }

        // Needs to be called after the gesture detector gets a turn,
        // as it may have displayed the mini keyboard
        if (mMiniKeyboardOnScreen && action != MotionEvent.ACTION_CANCEL) {
            return true
        }

        when(action) {
            MotionEvent.ACTION_DOWN -> {
                mAbortKey = false
                mLastCodeX = touchX
                mLastCodeY = touchY
                mLastKeyTime = 0
                mCurrentKeyTime = 0
                mLastKey = NOT_A_KEY
                mCurrentKey = keyIndex
                mDownTime = eventTime
                mLastMoveTime = mDownTime

                mKeyboardActionListener?.onPress(if (keyIndex != NOT_A_KEY) mKeys!![keyIndex].codes!![0] else 0)

                if (mCurrentKey != NOT_A_KEY) {
                    val msg: Message = handler.obtainMessage(MSG_LONGPRESS)
                    handler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT.toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                var continueLongPress = false
                if (keyIndex != NOT_A_KEY) {
                    if (mCurrentKey == NOT_A_KEY) {
                        mCurrentKey = keyIndex
                        mCurrentKeyTime = eventTime - mDownTime
                    } else if (keyIndex == mCurrentKey) {
                        mCurrentKeyTime += eventTime - mLastMoveTime
                        continueLongPress = true
                    }
                }
                if (!continueLongPress) {
                    // Cancel old longpress
                    handler.removeMessages(MSG_LONGPRESS)
                    // Start new longpress if key has changed
                    if (keyIndex != NOT_A_KEY) {
                        val msg: Message = handler.obtainMessage(MSG_LONGPRESS)
                        handler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT.toLong())
                    }
                }
                mLastMoveTime = eventTime
            }
            MotionEvent.ACTION_UP -> {
                removeMessages()
                if (keyIndex == mCurrentKey) {
                    mCurrentKeyTime += eventTime - mLastMoveTime
                } else {
                    mLastKey = mCurrentKey
                    mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime
                    mCurrentKey = keyIndex
                    mCurrentKeyTime = 0
                }
                if (mCurrentKeyTime < mLastKeyTime && mCurrentKeyTime < DEBOUNCE_TIME && mLastKey != NOT_A_KEY) {
                    mCurrentKey = mLastKey
                    touchX = mLastCodeX
                    touchY = mLastCodeY
                }
                // If we're not on a repeating key (which sends on a DOWN event)
                if (!mMiniKeyboardOnScreen && !mAbortKey) {
                    detectAndSendKey(mCurrentKey, touchX, touchY, eventTime)
                }
                invalidateKey(keyIndex)
            }
            MotionEvent.ACTION_CANCEL -> {
                removeMessages()
                dismissPopupKeyboard()
                mAbortKey = true
                invalidateKey(mCurrentKey)
            }
            else -> {}
        }

        return true
    }

    private fun detectAndSendKey(index: Int, x: Int, y: Int, eventTime: Long) {
        if (index != NOT_A_KEY && index < mKeys!!.size) {
            val key = mKeys!![index]
            if (key.text != null && (key.popupCharacters.isNullOrEmpty())) {
                mKeyboardActionListener?.onText(key.text!!)
                mKeyboardActionListener?.onRelease(NOT_A_KEY)
            } else {
                val code = key.codes!![0]
                val codes = IntArray(MAX_NEARBY_KEYS)
                Arrays.fill(codes, NOT_A_KEY)
                getKeyIndices(x, y, codes)
                val hasPopup = !key.popupCharacters.isNullOrEmpty()
                mKeyboardActionListener?.onKey(code, codes, hasPopup)
                mKeyboardActionListener?.onRelease(code)
            }
        } else {
            mKeyboardActionListener?.onNoKey()
        }
    }

    private fun dismissPopupKeyboard() {
        if (mMiniKeyboardOnScreen) {
            mMiniKeyboardOnScreen = false
            invalidateAllKeys()
        }
    }

    //----------------------------------------------------------------------------------------

    fun setKeyboard(keyboard: Keyboard) {
        // Remove any pending messages
        removeMessages()
        mKeyboard = keyboard
        mKeys = keyboard.getKeys().toTypedArray()
        requestLayout()
        // Hint to reallocate the buffer if the size changed
        mKeyboardChanged = true
        invalidateAllKeys()
        computeProximityThreshold()

        // Switching to a different keyboard should abort any pending keys so that the key up
        // doesn't get delivered to the old or new keyboard
        mAbortKey = true // Until the next ACTION_DOWN
    }

    private fun openPopupIfRequired(): Boolean {
        if (mCurrentKey < 0 || mCurrentKey >= mKeys!!.size) {
            return false
        }
        val popupKey = mKeys!![mCurrentKey]
        val result: Boolean = mKeyboardActionListener?.onLongPress(popupKey)?:false
        if (result) {
            mAbortKey = true
        }
        return result
    }

    private fun removeMessages() {
        handler.removeMessages(MSG_LONGPRESS)
    }

    private fun computeProximityThreshold() {
        val keys = mKeys ?: return
        val length = keys.size
        var dimensionSum = 0
        for (i in 0 until length) {
            val key: Key = keys[i]
            dimensionSum += Math.min(key.width, key.height) + key.gap
        }
        if (dimensionSum < 0 || length == 0) return
        mProximityThreshold = (dimensionSum * 1.4f / length).toInt()
        mProximityThreshold *= mProximityThreshold // Square it
    }

    fun invalidateKey(keyIndex: Int) {
        mKeys?.let { keys ->
            if (keyIndex < 0 || keyIndex >= keys.size) {
                return
            }
            val key = keys[keyIndex]
            mInvalidatedKey = key
            mDirtyRect.union(
                key.x + paddingLeft,
                key.y + paddingTop,
                key.x + key.width + paddingLeft,
                key.y + key.height + paddingTop
            )
            onBufferDraw()
            invalidate()
        }
    }

    fun closing() {
        removeMessages()

        mBuffer?.recycle()
        mBuffer = null
        mCanvas = null
    }

    //-----------------------------------------------------------------

    /**
     * Returns the current keyboard being displayed by this view.
     * @return the currently attached keyboard
     * @see .setKeyboard
     */
    fun getKeyboard(): Keyboard? {
        TODO("Not yet implemented")
    }

    fun isShifted(): Boolean {
        TODO("Not yet implemented")
    }

    fun setShifted(shifted: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    //------popupkeboardview
    fun setKeyBackground(resId: Drawable?) {
        TODO("Not yet implemented")
    }

    fun setKeyCapStartBackground(resId: Drawable?) {
        TODO("Not yet implemented")
    }

    fun setKeyCapEndBackground(drawable: Drawable?) {
        TODO("Not yet implemented")
    }

    fun setKeySingleStartBackground(drawable: Drawable?) {
        TODO("Not yet implemented")
    }

    fun setKeyTextColor(color: Int) {
        TODO("Not yet implemented")
    }

    fun setSelectedForegroundColor(color: Int) {
        TODO("Not yet implemented")
    }

    fun setForegroundColor(color: Int) {
        TODO("Not yet implemented")
    }

    fun setKeyboardHoveredPadding(padding: Int) {
        TODO("Not yet implemented")
    }

    fun setKeyboardPressedPadding(padding: Int) {
        TODO("Not yet implemented")
    }

    fun setFeaturedKeyBackground(resId: Int, keyCodes: IntArray) {
        TODO("Not yet implemented")
    }

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        TODO("Not yet implemented")
    }

}
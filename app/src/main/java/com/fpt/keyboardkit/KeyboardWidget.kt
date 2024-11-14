package com.fpt.keyboardkit

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/14 10:18
 * desc    : keyboard widget
 */
class KeyboardWidget : FrameLayout {

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(context)
    }

    private fun initialize(aContext: Context) {
        LayoutInflater.from(aContext).inflate(R.layout.widget_keyboard, this, true)

    }

}
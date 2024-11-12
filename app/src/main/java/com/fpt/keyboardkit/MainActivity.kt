package com.fpt.keyboardkit

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * @author : peitian
 * e-mail  : peitian_feng@htc.com
 * time    : 2024/11/12 11:23
 * desc    :
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar()
        setContentView(R.layout.activity_main)

    }

    private fun hideStatusBar() {
        val wlp = window.attributes.also { layoutParams->
            // 设置全屏模式
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
            // 隐藏导航栏和系统UI元素
            layoutParams.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        window.attributes = wlp
    }

}
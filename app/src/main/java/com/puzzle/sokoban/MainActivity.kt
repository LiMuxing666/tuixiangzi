package com.puzzle.sokoban

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher

class MainActivity : Activity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全面屏：内容画到系统栏下面，由 GameView 根据 insets 自行留边
        if (Build.VERSION.SDK_INT >= 35) {
            // targetSdk 35+ 在 Android 15 上默认就是全面屏
        } else if (Build.VERSION.SDK_INT >= 30) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        gameView = GameView(this)
        setContentView(gameView)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                handleBack()
            }
        }
    }

    private fun handleBack() {
        if (!gameView.onBack()) finish()
    }

    @Deprecated("Android 13 以下仍走这里")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.sound.release()
    }
}

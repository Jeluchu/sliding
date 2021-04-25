package com.jeluchu.sliding

import android.animation.ArgbEvaluator
import android.app.Activity
import android.os.Build
import androidx.annotation.ColorInt
import com.jeluchu.sliding.widget.SliderPanel.OnPanelSlideListener

open class ColorPanelSlideListener(
        private val activity: Activity,
        @param:ColorInt protected open val primaryColor: Int,
        @param:ColorInt protected open val secondaryColor: Int
) : OnPanelSlideListener {

    private val evaluator = ArgbEvaluator()

    override fun onOpened() {}
    override fun onStateChanged(state: Int) {}

    override fun onClosed() {
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }

    override fun onSlideChange(percent: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && areColorsValid()) {
            val newColor = evaluator.evaluate(percent, primaryColor, secondaryColor) as Int
            activity.window.statusBarColor = newColor
        }
    }

    private fun areColorsValid(): Boolean = primaryColor != -1 && secondaryColor != -1

}
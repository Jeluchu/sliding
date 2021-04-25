package com.jeluchu.sliding

import android.app.Activity
import com.jeluchu.sliding.model.Config

internal class ConfigPanelSlideListener(activity: Activity, private val config: Config
) : ColorPanelSlideListener(activity, -1, -1) {

    override fun onStateChanged(state: Int) {
        if (config.listener != null) config.listener?.onSlideStateChanged(state)
    }

    override fun onClosed() {
        if (config.listener != null) if (config.listener?.onSlideClosed() == true) return
        super.onClosed()
    }

    override fun onOpened() {
        if (config.listener != null) config.listener?.onSlideOpened()
    }

    override fun onSlideChange(percent: Float) {
        super.onSlideChange(percent)
        if (config.listener != null) config.listener?.onSlideChange(percent)
    }

    override val primaryColor: Int
        get() = config.primaryColor

    override val secondaryColor: Int
        get() = config.secondaryColor

}
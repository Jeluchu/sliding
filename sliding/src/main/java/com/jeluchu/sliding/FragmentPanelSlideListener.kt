package com.jeluchu.sliding

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.jeluchu.sliding.model.Config
import com.jeluchu.sliding.widget.SliderPanel.OnPanelSlideListener

internal class FragmentPanelSlideListener(
        private val view: View,
        private val config: Config
) : OnPanelSlideListener {

    override fun onStateChanged(state: Int) {
        if (config.listener != null) config.listener?.onSlideStateChanged(state)
    }

    override fun onClosed() {

        if (config.listener != null) if (config.listener?.onSlideClosed() == true) return
        if (view.context is FragmentActivity) {
            with(view.context as FragmentActivity) {
                if (supportFragmentManager.backStackEntryCount == 0) {
                    finish()
                    overridePendingTransition(0, 0)
                } else { supportFragmentManager.popBackStack() }
            }
        }
    }

    override fun onOpened() {
        if (config.listener != null) config.listener?.onSlideOpened()
    }

    override fun onSlideChange(percent: Float) {
        if (config.listener != null) { config.listener?.onSlideChange(percent) }
    }
}
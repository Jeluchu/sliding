package com.jeluchu.sliding

import android.app.Activity
import android.view.ViewGroup
import com.jeluchu.sliding.model.Config
import com.jeluchu.sliding.model.Interface
import com.jeluchu.sliding.widget.SliderPanel

object Sliding {

    @JvmStatic
    fun attach(activity: Activity, config: Config): Interface {
        val panel = attachSliderPanel(activity, config)
        panel.setOnPanelSlideListener(ConfigPanelSlideListener(activity, config))
        return panel.defaultInterface
    }

    private fun attachSliderPanel(activity: Activity, config: Config): SliderPanel {

        val decorView = activity.window.decorView as ViewGroup
        val oldScreen = decorView.getChildAt(0)
        decorView.removeViewAt(0)

        val panel = SliderPanel(activity, oldScreen, config)
        panel.id = R.id.slidable_panel
        oldScreen.id = R.id.slidable_content
        panel.addView(oldScreen)
        decorView.addView(panel, 0)
        return panel

    }

}
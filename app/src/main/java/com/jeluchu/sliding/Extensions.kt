package com.jeluchu.sliding

import android.content.res.Resources
import androidx.annotation.Dimension

var displayMetrics = Resources.getSystem().displayMetrics

val Int.dpToPx: Int
    @Dimension(unit = Dimension.PX) get() = (this * displayMetrics.density).toInt()
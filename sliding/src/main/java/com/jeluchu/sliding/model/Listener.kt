package com.jeluchu.sliding.model

interface Listener {
    fun onSlideStateChanged(state: Int)
    fun onSlideChange(percent: Float)
    fun onSlideOpened()
    fun onSlideClosed(): Boolean
}
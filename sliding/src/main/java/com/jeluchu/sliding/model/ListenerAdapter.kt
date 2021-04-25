package com.jeluchu.sliding.model

class ListenerAdapter : Listener {
    override fun onSlideStateChanged(state: Int) {}
    override fun onSlideChange(percent: Float) {}
    override fun onSlideOpened() {}
    override fun onSlideClosed(): Boolean = false
}
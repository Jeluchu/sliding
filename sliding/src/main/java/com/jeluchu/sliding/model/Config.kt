package com.jeluchu.sliding.model

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange

class Config private constructor() {

    private var touchSize = -1f
    private var sensitivity = 1f
    private var scrimColor = Color.BLACK
    private var scrimStartAlpha = 0.8f
    private var scrimEndAlpha = 0f
    private var velocityThreshold = 5f
    private var distanceThreshold = 0.25f
    private var edgeSize = 0.18f

    var primaryColor = -1
    var secondaryColor = -1
    var isEdgeOnly = false
    var position = Position.LEFT
    var listener: Listener? = null

    @ColorInt
    fun getScrimColor(): Int = scrimColor
    fun getScrimStartAlpha(): Float = scrimStartAlpha
    fun getScrimEndAlpha(): Float = scrimEndAlpha
    fun getTouchSize(): Float = touchSize
    fun getVelocityThreshold(): Float = velocityThreshold
    fun getDistanceThreshold(): Float = distanceThreshold
    fun getSensitivity(): Float = sensitivity
    fun getEdgeSize(size: Float): Float = edgeSize * size
    fun setColorPrimary(colorPrimary: Int) { primaryColor = colorPrimary }
    fun setColorSecondary(colorSecondary: Int) { secondaryColor = colorSecondary }
    fun setTouchSize(touchSize: Float) { this.touchSize = touchSize }
    fun setSensitivity(sensitivity: Float) { this.sensitivity = sensitivity }
    fun setScrimColor(@ColorInt scrimColor: Int) { this.scrimColor = scrimColor }
    fun setScrimStartAlpha(scrimStartAlpha: Float) { this.scrimStartAlpha = scrimStartAlpha }
    fun setScrimEndAlpha(scrimEndAlpha: Float) { this.scrimEndAlpha = scrimEndAlpha }
    fun setVelocityThreshold(velocityThreshold: Float) { this.velocityThreshold = velocityThreshold }
    fun setDistanceThreshold(distanceThreshold: Float) { this.distanceThreshold = distanceThreshold }

    class Builder {

        private val config: Config = Config()

        fun primaryColor(@ColorInt color: Int): Builder {
            config.primaryColor = color
            return this
        }

        fun secondaryColor(@ColorInt color: Int): Builder {
            config.secondaryColor = color
            return this
        }

        fun position(position: Position): Builder {
            config.position = position
            return this
        }

        fun touchSize(size: Float): Builder {
            config.touchSize = size
            return this
        }

        fun sensitivity(sensitivity: Float): Builder {
            config.sensitivity = sensitivity
            return this
        }

        fun scrimColor(@ColorInt color: Int): Builder {
            config.scrimColor = color
            return this
        }

        fun scrimStartAlpha(@FloatRange(from = 0.0, to = 1.0) alpha: Float): Builder {
            config.scrimStartAlpha = alpha
            return this
        }

        fun scrimEndAlpha(@FloatRange(from = 0.0, to = 1.0) alpha: Float): Builder {
            config.scrimEndAlpha = alpha
            return this
        }

        fun velocityThreshold(threshold: Float): Builder {
            config.velocityThreshold = threshold
            return this
        }

        fun distanceThreshold(@FloatRange(from = 0.1, to = 0.9) threshold: Float): Builder {
            config.distanceThreshold = threshold
            return this
        }

        fun edge(flag: Boolean): Builder {
            config.isEdgeOnly = flag
            return this
        }

        fun edgeSize(@FloatRange(from = 0.0, to = 1.0) edgeSize: Float): Builder {
            config.edgeSize = edgeSize
            return this
        }

        fun listener(listener: Listener?): Builder {
            config.listener = listener
            return this
        }

        fun build(): Config = config

    }
}
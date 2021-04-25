package com.jeluchu.sliding.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.jeluchu.sliding.model.Config
import com.jeluchu.sliding.model.Interface
import com.jeluchu.sliding.model.Position
import com.jeluchu.sliding.util.ViewDragHelper
import com.jeluchu.sliding.util.ViewDragHelper.Companion.create
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SliderPanel : FrameLayout {

    private var screenWidth = 0
    private var screenHeight = 0
    private var decorView: View? = null
    private var dragHelper: ViewDragHelper? = null
    private var listener: OnPanelSlideListener? = null
    private var scrimPaint: Paint? = null
    private var scrimRenderer: ScrimRenderer? = null
    private var isLocked = false
    private var isEdgeTouched = false
    private var edgePosition = 0
    private var config: Config? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, decorView: View?, config: Config?) : super(context) {
        this.decorView = decorView
        this.config = config ?: Config.Builder().build()
        init()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {

        if (isLocked) return false
        if (config?.isEdgeOnly == true) isEdgeTouched = canDragFromEdge(ev)

        val interceptForDrag: Boolean = try {
            dragHelper?.shouldInterceptTouchEvent(ev) ?: false
        } catch (e: Exception) {
            false
        }
        return interceptForDrag && !isLocked

    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLocked) return false
        try { dragHelper?.processTouchEvent(event) }
        catch (e: IllegalArgumentException) { return false }
        return true
    }

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper?.continueSettling(true) == true)
            ViewCompat.postInvalidateOnAnimation(this)
    }

    override fun onDraw(canvas: Canvas) {
        scrimRenderer?.render(canvas, config!!.position, scrimPaint!!)
    }

    fun setOnPanelSlideListener(listener: OnPanelSlideListener?) {
        this.listener = listener
    }

    val defaultInterface: Interface = object : Interface {
        override fun lock() = this@SliderPanel.lock()
        override fun unlock() = this@SliderPanel.unlock()
    }

    private val leftCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View?, pointerId: Int): Boolean {
            val edgeCase = !config!!.isEdgeOnly || dragHelper!!.isEdgeTouched(edgePosition, pointerId)
            return child?.id == decorView?.id && edgeCase
        }

        override fun clampViewPositionHorizontal(child: View?, left: Int, dx: Int): Int =
            clamp(left, 0, screenWidth)

        override fun getViewHorizontalDragRange(child: View?): Int = screenWidth

        override fun onViewReleased(releasedChild: View?, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val left = releasedChild!!.left
            var settleLeft = 0
            val leftThreshold = (width * config!!.getDistanceThreshold()).toInt()
            val isVerticalSwiping = abs(yvel) > config!!.getVelocityThreshold()
            if (xvel > 0) {
                if (abs(xvel) > config!!.getVelocityThreshold() && !isVerticalSwiping) {
                    settleLeft = screenWidth
                } else if (left > leftThreshold) {
                    settleLeft = screenWidth
                }
            } else if (xvel == 0f) {
                if (left > leftThreshold) {
                    settleLeft = screenWidth
                }
            }
            dragHelper!!.settleCapturedViewAt(settleLeft, releasedChild.top)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View?, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - left.toFloat() / screenWidth.toFloat()
            if (listener != null) listener!!.onSlideChange(percent)

            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            if (listener != null) listener!!.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView!!.left == 0) {
                    if (listener != null) listener!!.onOpened()
                } else {
                    if (listener != null) listener!!.onClosed()
                }
                ViewDragHelper.STATE_DRAGGING -> {}
                ViewDragHelper.STATE_SETTLING -> {}
            }
        }
    }

    private val rightCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View?, pointerId: Int): Boolean {
            val edgeCase = !config!!.isEdgeOnly || dragHelper!!.isEdgeTouched(edgePosition, pointerId)
            return child!!.id == decorView!!.id && edgeCase
        }

        override fun clampViewPositionHorizontal(child: View?, left: Int, dx: Int): Int =
            clamp(left, -screenWidth, 0)

        override fun getViewHorizontalDragRange(child: View?): Int = screenWidth

        override fun onViewReleased(releasedChild: View?, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val left = releasedChild!!.left
            var settleLeft = 0
            val leftThreshold = (width * config!!.getDistanceThreshold()).toInt()
            val isVerticalSwiping = abs(yvel) > config!!.getVelocityThreshold()
            if (xvel < 0) {
                if (abs(xvel) > config!!.getVelocityThreshold() && !isVerticalSwiping) {
                    settleLeft = -screenWidth
                } else if (left < -leftThreshold) {
                    settleLeft = -screenWidth
                }
            } else if (xvel == 0f) {
                if (left < -leftThreshold) {
                    settleLeft = -screenWidth
                }
            }
            dragHelper!!.settleCapturedViewAt(settleLeft, releasedChild.top)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View?, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - abs(left).toFloat() / screenWidth.toFloat()
            if (listener != null) listener!!.onSlideChange(percent)

            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            if (listener != null) listener!!.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView!!.left == 0) {
                    if (listener != null) listener!!.onOpened()
                } else {
                    if (listener != null) listener!!.onClosed()
                }
                ViewDragHelper.STATE_DRAGGING -> {}
                ViewDragHelper.STATE_SETTLING -> {}
            }
        }
    }

    /**
     * The drag helper callbacks for dragging the slidr attachment from the top of the screen
     */
    private val topCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View?, pointerId: Int): Boolean {
            return child!!.id == decorView!!.id && (!config!!.isEdgeOnly || isEdgeTouched)
        }

        override fun clampViewPositionVertical(child: View?, top: Int, dy: Int): Int =
            clamp(top, 0, screenHeight)

        override fun getViewVerticalDragRange(child: View?): Int = screenHeight

        override fun onViewReleased(releasedChild: View?, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val top = releasedChild!!.top
            var settleTop = 0
            val topThreshold = (height * config!!.getDistanceThreshold()).toInt()
            val isSideSwiping = abs(xvel) > config!!.getVelocityThreshold()
            if (yvel > 0) {
                if (abs(yvel) > config!!.getVelocityThreshold() && !isSideSwiping) {
                    settleTop = screenHeight
                } else if (top > topThreshold) {
                    settleTop = screenHeight
                }
            } else if (yvel == 0f) {
                if (top > topThreshold) {
                    settleTop = screenHeight
                }
            }
            dragHelper!!.settleCapturedViewAt(releasedChild.left, settleTop)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View?, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - abs(top).toFloat() / screenHeight.toFloat()
            if (listener != null) listener!!.onSlideChange(percent)

            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            if (listener != null) listener!!.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView!!.top == 0) {
                    // State Open
                    if (listener != null) listener!!.onOpened()
                } else {
                    // State Closed
                    if (listener != null) listener!!.onClosed()
                }
                ViewDragHelper.STATE_DRAGGING -> {
                }
                ViewDragHelper.STATE_SETTLING -> {
                }
            }
        }
    }

    /**
     * The drag helper callbacks for dragging the slidr attachment from the bottom of hte screen
     */
    private val bottomCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View?, pointerId: Int): Boolean =
            child!!.id == decorView!!.id && (!config!!.isEdgeOnly || isEdgeTouched)

        override fun clampViewPositionVertical(child: View?, top: Int, dy: Int): Int =
            clamp(top, -screenHeight, 0)

        override fun getViewVerticalDragRange(child: View?): Int = screenHeight

        override fun onViewReleased(releasedChild: View?, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val top = releasedChild!!.top
            var settleTop = 0
            val topThreshold = (height * config!!.getDistanceThreshold()).toInt()
            val isSideSwiping = abs(xvel) > config!!.getVelocityThreshold()
            if (yvel < 0) {
                if (abs(yvel) > config!!.getVelocityThreshold() && !isSideSwiping) {
                    settleTop = -screenHeight
                } else if (top < -topThreshold) {
                    settleTop = -screenHeight
                }
            } else if (yvel == 0f) {
                if (top < -topThreshold) {
                    settleTop = -screenHeight
                }
            }
            dragHelper!!.settleCapturedViewAt(releasedChild.left, settleTop)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View?, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - abs(top).toFloat() / screenHeight.toFloat()
            if (listener != null) listener!!.onSlideChange(percent)

            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            if (listener != null) listener!!.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView!!.top == 0) {
                    if (listener != null) listener!!.onOpened()
                } else {
                    if (listener != null) listener!!.onClosed()
                }
                ViewDragHelper.STATE_DRAGGING -> {}
                ViewDragHelper.STATE_SETTLING -> {}
            }
        }
    }

    private val verticalCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View?, pointerId: Int): Boolean =
            child!!.id == decorView!!.id && (!config!!.isEdgeOnly || isEdgeTouched)

        override fun clampViewPositionVertical(child: View?, top: Int, dy: Int): Int =
            clamp(top, -screenHeight, screenHeight)

        override fun getViewVerticalDragRange(child: View?): Int =
            screenHeight

        override fun onViewReleased(releasedChild: View?, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val top = releasedChild!!.top
            var settleTop = 0
            val topThreshold = (height * config!!.getDistanceThreshold()).toInt()
            val isSideSwiping = abs(xvel) > config!!.getVelocityThreshold()
            if (yvel > 0) {

                if (abs(yvel) > config!!.getVelocityThreshold() && !isSideSwiping) {
                    settleTop = screenHeight
                } else if (top > topThreshold) {
                    settleTop = screenHeight
                }
            } else if (yvel < 0) {
                if (abs(yvel) > config!!.getVelocityThreshold() && !isSideSwiping) {
                    settleTop = -screenHeight
                } else if (top < -topThreshold) {
                    settleTop = -screenHeight
                }
            } else {
                if (top > topThreshold) {
                    settleTop = screenHeight
                } else if (top < -topThreshold) {
                    settleTop = -screenHeight
                }
            }
            dragHelper!!.settleCapturedViewAt(releasedChild.left, settleTop)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View?, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - abs(top).toFloat() / screenHeight.toFloat()
            if (listener != null) listener!!.onSlideChange(percent)

            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            if (listener != null) listener!!.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView!!.top == 0) {
                    if (listener != null) listener!!.onOpened()
                } else {
                    if (listener != null) listener!!.onClosed()
                }
                ViewDragHelper.STATE_DRAGGING -> {}
                ViewDragHelper.STATE_SETTLING -> {}
            }
        }
    }

    private val horizontalCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View?, pointerId: Int): Boolean {
            val edgeCase = !config!!.isEdgeOnly || dragHelper!!.isEdgeTouched(edgePosition, pointerId)
            return child!!.id == decorView!!.id && edgeCase
        }

        override fun clampViewPositionHorizontal(child: View?, left: Int, dx: Int): Int =
            clamp(left, -screenWidth, screenWidth)

        override fun getViewHorizontalDragRange(child: View?): Int =
            screenWidth

        override fun onViewReleased(releasedChild: View?, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            val left = releasedChild!!.left
            var settleLeft = 0
            val leftThreshold = (width * config!!.getDistanceThreshold()).toInt()
            val isVerticalSwiping = abs(yvel) > config!!.getVelocityThreshold()
            if (xvel > 0) {
                if (abs(xvel) > config!!.getVelocityThreshold() && !isVerticalSwiping) {
                    settleLeft = screenWidth
                } else if (left > leftThreshold) {
                    settleLeft = screenWidth
                }
            } else if (xvel < 0) {
                if (abs(xvel) > config!!.getVelocityThreshold() && !isVerticalSwiping) {
                    settleLeft = -screenWidth
                } else if (left < -leftThreshold) {
                    settleLeft = -screenWidth
                }
            } else {
                if (left > leftThreshold) {
                    settleLeft = screenWidth
                } else if (left < -leftThreshold) {
                    settleLeft = -screenWidth
                }
            }
            dragHelper!!.settleCapturedViewAt(settleLeft, releasedChild.top)
            invalidate()
        }

        override fun onViewPositionChanged(changedView: View?, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            val percent = 1f - abs(left).toFloat() / screenWidth.toFloat()
            if (listener != null) listener!!.onSlideChange(percent)

            applyScrim(percent)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            if (listener != null) listener!!.onStateChanged(state)
            when (state) {
                ViewDragHelper.STATE_IDLE -> if (decorView!!.left == 0) {
                    if (listener != null) listener!!.onOpened()
                } else {
                    if (listener != null) listener!!.onClosed()
                }
                ViewDragHelper.STATE_DRAGGING -> {}
                ViewDragHelper.STATE_SETTLING -> {}
            }
        }
    }

    private fun init() {
        setWillNotDraw(false)
        screenWidth = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density
        val minVel = MIN_FLING_VELOCITY * density
        val callback: ViewDragHelper.Callback
        when (config?.position) {
            Position.LEFT -> {
                callback = leftCallback
                edgePosition = ViewDragHelper.EDGE_LEFT
            }
            Position.RIGHT -> {
                callback = rightCallback
                edgePosition = ViewDragHelper.EDGE_RIGHT
            }
            Position.TOP -> {
                callback = topCallback
                edgePosition = ViewDragHelper.EDGE_TOP
            }
            Position.BOTTOM -> {
                callback = bottomCallback
                edgePosition = ViewDragHelper.EDGE_BOTTOM
            }
            Position.VERTICAL -> {
                callback = verticalCallback
                edgePosition = ViewDragHelper.EDGE_TOP or ViewDragHelper.EDGE_BOTTOM
            }
            Position.HORIZONTAL -> {
                callback = horizontalCallback
                edgePosition = ViewDragHelper.EDGE_LEFT or ViewDragHelper.EDGE_RIGHT
            }
            else -> {
                callback = leftCallback
                edgePosition = ViewDragHelper.EDGE_LEFT
            }
        }
        dragHelper = create(this, config!!.getSensitivity(), callback)
        dragHelper!!.minVelocity = minVel
        dragHelper!!.setEdgeTrackingEnabled(edgePosition)
        this.isMotionEventSplittingEnabled = false

        scrimPaint = Paint()
        scrimPaint!!.color = config!!.getScrimColor()
        scrimPaint!!.alpha = toAlpha(config!!.getScrimStartAlpha())
        scrimRenderer = ScrimRenderer(this, decorView!!)

        post { screenHeight = height }

    }

    private fun lock() {
        dragHelper!!.abort()
        isLocked = true
    }

    private fun unlock() {
        dragHelper!!.abort()
        isLocked = false
    }

    private fun canDragFromEdge(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        return when (config!!.position) {
            Position.LEFT -> x < config!!.getEdgeSize(width.toFloat())
            Position.RIGHT -> x > width - config!!.getEdgeSize(width.toFloat())
            Position.BOTTOM -> y > height - config!!.getEdgeSize(height.toFloat())
            Position.TOP -> y < config!!.getEdgeSize(height.toFloat())
            Position.HORIZONTAL -> x < config!!.getEdgeSize(width.toFloat()) || x > width - config!!.getEdgeSize(width.toFloat())
            Position.VERTICAL -> y < config!!.getEdgeSize(height.toFloat()) || y > height - config!!.getEdgeSize(height.toFloat())
        }
    }

    private fun applyScrim(percent: Float) {
        val alpha = percent * (config!!.getScrimStartAlpha() - config!!.getScrimEndAlpha()) + config!!.getScrimEndAlpha()
        scrimPaint!!.alpha = toAlpha(alpha)
        invalidate()
    }

    interface OnPanelSlideListener {
        fun onStateChanged(state: Int)
        fun onClosed()
        fun onOpened()
        fun onSlideChange(percent: Float)
    }

    companion object {
        private const val MIN_FLING_VELOCITY = 400
        private fun clamp(value: Int, min: Int, max: Int): Int = max(min, min(max, value))
        private fun toAlpha(percentage: Float): Int = (percentage * 255).toInt()
    }

}
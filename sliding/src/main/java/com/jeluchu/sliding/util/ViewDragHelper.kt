package com.jeluchu.sliding.util

import android.content.Context
import android.util.Log
import android.view.*
import android.view.animation.Interpolator
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import java.util.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class ViewDragHelper private constructor(context: Context, forParent: ViewGroup?, cb: Callback?) {

    var minVelocity: Float

    private var viewDragState = 0
    private var touchSlop: Int
    private var activePointerId = INVALID_POINTER
    private var mInitialMotionX: FloatArray? = null
    private var mInitialMotionY: FloatArray? = null
    private lateinit var mLastMotionX: FloatArray
    private lateinit var mLastMotionY: FloatArray
    private lateinit var mInitialEdgesTouched: IntArray
    private lateinit var mEdgeDragsInProgress: IntArray
    private lateinit var mEdgeDragsLocked: IntArray
    private var mPointersDown = 0
    private var mVelocityTracker: VelocityTracker? = null
    private val mMaxVelocity: Float
    private val edgeSize: Int
    private var mTrackingEdges = 0
    private val mScroller: OverScroller
    private val mCallback: Callback
    private var capturedView: View? = null
    private var mReleaseInProgress = false
    private val mParentView: ViewGroup

    abstract class Callback {
        open fun onViewDragStateChanged(state: Int) {}
        open fun onViewPositionChanged(changedView: View?, left: Int, top: Int, dx: Int, dy: Int) {}
        fun onViewCaptured(capturedChild: View?, activePointerId: Int) {}
        open fun onViewReleased(releasedChild: View?, xvel: Float, yvel: Float) {}
        fun onEdgeTouched(edgeFlags: Int, pointerId: Int) {}
        fun onEdgeLock(edgeFlags: Int): Boolean = false
        fun onEdgeDragStarted(edgeFlags: Int, pointerId: Int) {}
        fun getOrderedChildIndex(index: Int): Int = index
        open fun getViewHorizontalDragRange(child: View?): Int = 0
        open fun getViewVerticalDragRange(child: View?): Int = 0
        abstract fun tryCaptureView(child: View?, pointerId: Int): Boolean
        open fun clampViewPositionHorizontal(child: View?, left: Int, dx: Int): Int = 0
        open fun clampViewPositionVertical(child: View?, top: Int, dy: Int): Int = 0
    }

    private val mSetIdleRunnable = Runnable { setDragState(STATE_IDLE) }
    
    fun setEdgeTrackingEnabled(edgeFlags: Int) {
        mTrackingEdges = edgeFlags
    }

    private fun captureChildView(childView: View, activePointerId: Int) {
        require(!(childView.parent !== mParentView)) {
            "captureChildView: parameter must be a descendant " +
                    "of the ViewDragHelper's tracked parent view (" + mParentView + ")"
        }
        capturedView = childView
        this.activePointerId = activePointerId
        mCallback.onViewCaptured(childView, activePointerId)
        setDragState(STATE_DRAGGING)
    }
    
    private fun cancel() {
        activePointerId = INVALID_POINTER
        clearMotionHistory()
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
    }

    fun abort() {
        cancel()
        if (viewDragState == STATE_SETTLING) {
            val oldX = mScroller.currX
            val oldY = mScroller.currY
            mScroller.abortAnimation()
            val newX = mScroller.currX
            val newY = mScroller.currY
            mCallback.onViewPositionChanged(capturedView, newX, newY, newX - oldX, newY - oldY)
        }
        setDragState(STATE_IDLE)
    }

    fun smoothSlideViewTo(child: View?, finalLeft: Int, finalTop: Int): Boolean {
        capturedView = child
        activePointerId = INVALID_POINTER
        val continueSliding = forceSettleCapturedViewAt(finalLeft, finalTop, 0, 0)
        if (!continueSliding && viewDragState == STATE_IDLE && capturedView != null)
            capturedView = null
        return continueSliding
    }

    fun settleCapturedViewAt(finalLeft: Int, finalTop: Int): Boolean {
        check(mReleaseInProgress) {
            "Cannot settleCapturedViewAt outside of a call to " +
                    "Callback#onViewReleased"
        }
        return forceSettleCapturedViewAt(finalLeft, finalTop,
            mVelocityTracker?.getXVelocity(activePointerId)?.toInt() ?: 0,
            mVelocityTracker?.getYVelocity(activePointerId)?.toInt() ?: 0
        )
    }

    private fun forceSettleCapturedViewAt(finalLeft: Int, finalTop: Int, xvel: Int, yvel: Int): Boolean {
        val startLeft = capturedView!!.left
        val startTop = capturedView!!.top
        val dx = finalLeft - startLeft
        val dy = finalTop - startTop
        if (dx == 0 && dy == 0) {
            mScroller.abortAnimation()
            setDragState(STATE_IDLE)
            return false
        }
        val duration = computeSettleDuration(capturedView, dx, dy, xvel, yvel)
        mScroller.startScroll(startLeft, startTop, dx, dy, duration)
        setDragState(STATE_SETTLING)
        return true
    }

    private fun computeSettleDuration(child: View?, dx: Int, dy: Int, xvel: Int, yvel: Int): Int {
        var xvel = xvel
        var yvel = yvel
        xvel = clampMag(xvel, minVelocity.toInt(), mMaxVelocity.toInt())
        yvel = clampMag(yvel, minVelocity.toInt(), mMaxVelocity.toInt())
        val absDx = abs(dx)
        val absDy = abs(dy)
        val absXVel = abs(xvel)
        val absYVel = abs(yvel)
        val addedVel = absXVel + absYVel
        val addedDistance = absDx + absDy
        val xweight = if (xvel != 0) absXVel.toFloat() / addedVel else absDx.toFloat() / addedDistance
        val yweight = if (yvel != 0) absYVel.toFloat() / addedVel else absDy.toFloat() / addedDistance
        val xduration = computeAxisDuration(dx, xvel, mCallback.getViewHorizontalDragRange(child))
        val yduration = computeAxisDuration(dy, yvel, mCallback.getViewVerticalDragRange(child))
        return (xduration * xweight + yduration * yweight).toInt()
    }

    private fun computeAxisDuration(delta: Int, velocity: Int, motionRange: Int): Int {
        var velocity = velocity
        if (delta == 0) return 0
        val width = mParentView.width
        val halfWidth = width / 2
        val distanceRatio = min(1f, abs(delta).toFloat() / width)
        val distance = halfWidth + halfWidth *
                distanceInfluenceForSnapDuration(distanceRatio)
        velocity = abs(velocity)
        val duration: Int = if (velocity > 0) {
            4 * (1000 * abs(distance / velocity)).roundToInt()
        } else {
            val range = abs(delta).toFloat() / motionRange
            ((range + 1) * BASE_SETTLE_DURATION).toInt()
        }
        return min(duration, MAX_SETTLE_DURATION)
    }
    
    private fun clampMag(value: Int, absMin: Int, absMax: Int): Int {
        val absValue = abs(value)
        if (absValue < absMin) return 0
        return if (absValue > absMax) if (value > 0) absMax else -absMax else value
    }

    private fun clampMag(value: Float, absMin: Float, absMax: Float): Float {
        val absValue = abs(value)
        if (absValue < absMin) return 0F
        return if (absValue > absMax) if (value > 0) absMax else -absMax else value
    }

    private fun distanceInfluenceForSnapDuration(f: Float): Float {
        var f = f
        f -= 0.5f
        f *= (0.3f * Math.PI / 2.0f).toFloat()
        return sin(f.toDouble()).toFloat()
    }

    fun flingCapturedView(minLeft: Int, minTop: Int, maxLeft: Int, maxTop: Int) {
        check(mReleaseInProgress) {
            "Cannot flingCapturedView outside of a call to " +
                    "Callback#onViewReleased"
        }
        mScroller.fling(capturedView!!.left, capturedView!!.top,
            mVelocityTracker?.getXVelocity(activePointerId)?.toInt() ?: 0,
            mVelocityTracker?.getYVelocity(activePointerId)?.toInt() ?: 0,
            minLeft, maxLeft, minTop, maxTop)
        setDragState(STATE_SETTLING)
    }

    fun continueSettling(deferCallbacks: Boolean): Boolean {
        if (viewDragState == STATE_SETTLING) {
            var keepGoing = mScroller.computeScrollOffset()
            val x = mScroller.currX
            val y = mScroller.currY
            val dx = x - capturedView!!.left
            val dy = y - capturedView!!.top
            if (dx != 0) ViewCompat.offsetLeftAndRight(capturedView!!, dx)
            if (dy != 0) ViewCompat.offsetTopAndBottom(capturedView!!, dy)
            if (dx != 0 || dy != 0) mCallback.onViewPositionChanged(capturedView, x, y, dx, dy)
            if (keepGoing && x == mScroller.finalX && y == mScroller.finalY) {
                mScroller.abortAnimation()
                keepGoing = false
            }
            if (!keepGoing) {
                if (deferCallbacks) mParentView.post(mSetIdleRunnable)
                else setDragState(STATE_IDLE)
            }
        }
        return viewDragState == STATE_SETTLING
    }
    
    private fun dispatchViewReleased(xvel: Float, yvel: Float) {
        mReleaseInProgress = true
        mCallback.onViewReleased(capturedView, xvel, yvel)
        mReleaseInProgress = false
        if (viewDragState == STATE_DRAGGING) setDragState(STATE_IDLE)
    }

    private fun clearMotionHistory() {
        if (mInitialMotionX == null) return
        Arrays.fill(mInitialMotionX, 0f)
        Arrays.fill(mInitialMotionY, 0f)
        Arrays.fill(mLastMotionX, 0f)
        Arrays.fill(mLastMotionY, 0f)
        Arrays.fill(mInitialEdgesTouched, 0)
        Arrays.fill(mEdgeDragsInProgress, 0)
        Arrays.fill(mEdgeDragsLocked, 0)
        mPointersDown = 0
    }

    private fun clearMotionHistory(pointerId: Int) {
        if (mInitialMotionX == null || !isPointerDown(pointerId)) return
        mInitialMotionX!![pointerId] = 0F
        mInitialMotionY!![pointerId] = 0F
        mLastMotionX[pointerId] = 0F
        mLastMotionY[pointerId] = 0F
        mInitialEdgesTouched[pointerId] = 0
        mEdgeDragsInProgress[pointerId] = 0
        mEdgeDragsLocked[pointerId] = 0
        mPointersDown = mPointersDown and (1 shl pointerId).inv()
    }

    private fun ensureMotionHistorySizeForId(pointerId: Int) {
        if (mInitialMotionX == null || mInitialMotionX!!.size <= pointerId) {
            val imx = FloatArray(pointerId + 1)
            val imy = FloatArray(pointerId + 1)
            val lmx = FloatArray(pointerId + 1)
            val lmy = FloatArray(pointerId + 1)
            val iit = IntArray(pointerId + 1)
            val edip = IntArray(pointerId + 1)
            val edl = IntArray(pointerId + 1)
            if (mInitialMotionX != null) {
                System.arraycopy(mInitialMotionX, 0, imx, 0, mInitialMotionX!!.size)
                System.arraycopy(mInitialMotionY, 0, imy, 0, mInitialMotionY!!.size)
                System.arraycopy(mLastMotionX, 0, lmx, 0, mLastMotionX.size)
                System.arraycopy(mLastMotionY, 0, lmy, 0, mLastMotionY.size)
                System.arraycopy(mInitialEdgesTouched, 0, iit, 0, mInitialEdgesTouched.size)
                System.arraycopy(mEdgeDragsInProgress, 0, edip, 0, mEdgeDragsInProgress.size)
                System.arraycopy(mEdgeDragsLocked, 0, edl, 0, mEdgeDragsLocked.size)
            }
            mInitialMotionX = imx
            mInitialMotionY = imy
            mLastMotionX = lmx
            mLastMotionY = lmy
            mInitialEdgesTouched = iit
            mEdgeDragsInProgress = edip
            mEdgeDragsLocked = edl
        }
    }

    private fun saveInitialMotion(x: Float, y: Float, pointerId: Int) {
        ensureMotionHistorySizeForId(pointerId)
        mLastMotionX[pointerId] = x
        mInitialMotionX!![pointerId] = mLastMotionX[pointerId]
        mLastMotionY[pointerId] = y
        mInitialMotionY!![pointerId] = mLastMotionY[pointerId]
        mInitialEdgesTouched[pointerId] = getEdgesTouched(x.toInt(), y.toInt())
        mPointersDown = mPointersDown or (1 shl pointerId)
    }

    private fun saveLastMotion(ev: MotionEvent) {
        val pointerCount = ev.pointerCount
        for (i in 0 until pointerCount) {
            val pointerId = ev.getPointerId(i)
            if (!isValidPointerForActionMove(pointerId)) {
                continue
            }
            val x = ev.x
            val y = ev.y
            mLastMotionX[pointerId] = x
            mLastMotionY[pointerId] = y
        }
    }

    private fun isPointerDown(pointerId: Int): Boolean = mPointersDown and 1 shl pointerId != 0

    private fun setDragState(state: Int) {
        mParentView.removeCallbacks(mSetIdleRunnable)
        if (viewDragState != state) {
            viewDragState = state
            mCallback.onViewDragStateChanged(state)
            if (viewDragState == STATE_IDLE) {
                capturedView = null
            }
        }
    }

    private fun tryCaptureViewForDrag(toCapture: View?, pointerId: Int): Boolean {
        if (toCapture === capturedView && activePointerId == pointerId) return true
        if (toCapture != null && mCallback.tryCaptureView(toCapture, pointerId)) {
            activePointerId = pointerId
            captureChildView(toCapture, pointerId)
            return true
        }
        return false
    }

    private fun canScroll(v: View, checkV: Boolean, dx: Int, dy: Int, x: Int, y: Int): Boolean {
        if (v is ViewGroup) {
            val scrollX = v.getScrollX()
            val scrollY = v.getScrollY()
            val count = v.childCount
            for (i in count - 1 downTo 0) {
                val child = v.getChildAt(i)
                if (x + scrollX >= child.left && x + scrollX < child.right && y + scrollY >= child.top && y + scrollY < child.bottom &&
                    canScroll(child, true, dx, dy, x + scrollX - child.left,
                        y + scrollY - child.top)) {
                    return true
                }
            }
        }
        return checkV && (v.canScrollHorizontally(-dx) || v.canScrollHorizontally(-dy))
    }
    
    fun shouldInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        val actionIndex = ev.actionIndex
        if (action == MotionEvent.ACTION_DOWN) cancel()
        if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain()
        mVelocityTracker!!.addMovement(ev)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y
                val pointerId = ev.getPointerId(0)
                saveInitialMotion(x, y, pointerId)
                val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                if (toCapture === capturedView && viewDragState == STATE_SETTLING) 
                    tryCaptureViewForDrag(toCapture, pointerId)
                val edgesTouched = mInitialEdgesTouched[pointerId]
                if (edgesTouched and mTrackingEdges != 0) 
                    mCallback.onEdgeTouched(edgesTouched and mTrackingEdges, pointerId)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = ev.getPointerId(actionIndex)
                val x = ev.x
                val y = ev.y
                saveInitialMotion(x, y, pointerId)
                if (viewDragState == STATE_IDLE) {
                    val edgesTouched = mInitialEdgesTouched[pointerId]
                    if (edgesTouched and mTrackingEdges != 0) {
                        mCallback.onEdgeTouched(edgesTouched and mTrackingEdges, pointerId)
                    }
                } else if (viewDragState == STATE_SETTLING) {
                    val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                    if (toCapture === capturedView) {
                        tryCaptureViewForDrag(toCapture, pointerId)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerCount = ev.pointerCount
                var i = 0
                while (i < pointerCount) {
                    val pointerId = ev.getPointerId(i)
                    if (!isValidPointerForActionMove(pointerId)) {
                        i++
                        continue
                    }
                    val x = ev.x
                    val y = ev.y
                    val dx = x - mInitialMotionX!![pointerId]
                    val dy = y - mInitialMotionY!![pointerId]
                    val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                    val pastSlop = toCapture != null && checkTouchSlop(toCapture, dx, dy)
                    if (pastSlop) {
                        val oldLeft = toCapture!!.left
                        val targetLeft = oldLeft + dx.toInt()
                        val newLeft = mCallback.clampViewPositionHorizontal(toCapture,
                            targetLeft, dx.toInt())
                        val oldTop = toCapture.top
                        val targetTop = oldTop + dy.toInt()
                        val newTop = mCallback.clampViewPositionVertical(toCapture, targetTop,
                            dy.toInt())
                        val horizontalDragRange = mCallback.getViewHorizontalDragRange(
                            toCapture)
                        val verticalDragRange = mCallback.getViewVerticalDragRange(toCapture)
                        if ((horizontalDragRange == 0 || horizontalDragRange > 0
                                    && newLeft == oldLeft) && (verticalDragRange == 0
                                    || verticalDragRange > 0 && newTop == oldTop)) {
                            break
                        }
                    }
                    reportNewEdgeDrags(dx, dy, pointerId)
                    if (viewDragState == STATE_DRAGGING) break
                    if (pastSlop && tryCaptureViewForDrag(toCapture, pointerId)) break
                    i++
                }
                saveLastMotion(ev)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = ev.getPointerId(actionIndex)
                clearMotionHistory(pointerId)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancel()
        }
        return viewDragState == STATE_DRAGGING
    }

    fun processTouchEvent(ev: MotionEvent) {
        val action = ev.actionMasked
        val actionIndex = ev.actionIndex
        if (action == MotionEvent.ACTION_DOWN) cancel()
        if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain()
        mVelocityTracker?.addMovement(ev)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y
                val pointerId = ev.getPointerId(0)
                val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                saveInitialMotion(x, y, pointerId)
                tryCaptureViewForDrag(toCapture, pointerId)
                val edgesTouched = mInitialEdgesTouched[pointerId]
                if (edgesTouched and mTrackingEdges != 0)
                    mCallback.onEdgeTouched(edgesTouched and mTrackingEdges, pointerId)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = ev.getPointerId(actionIndex)
                val x = ev.x
                val y = ev.y
                saveInitialMotion(x, y, pointerId)
                if (viewDragState == STATE_IDLE) {
                    val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                    tryCaptureViewForDrag(toCapture, pointerId)
                    val edgesTouched = mInitialEdgesTouched[pointerId]
                    if (edgesTouched and mTrackingEdges != 0)
                        mCallback.onEdgeTouched(edgesTouched and mTrackingEdges, pointerId)
                } else if (isCapturedViewUnder(x.toInt(), y.toInt())) {
                    tryCaptureViewForDrag(capturedView, pointerId)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (viewDragState == STATE_DRAGGING) {
                    val x = ev.x
                    val y = ev.y
                    val idx = (x - mLastMotionX[activePointerId]).toInt()
                    val idy = (y - mLastMotionY[activePointerId]).toInt()
                    dragTo(capturedView!!.left + idx, capturedView!!.top + idy, idx, idy)
                    saveLastMotion(ev)
                } else {
                    val pointerCount = ev.pointerCount
                    var i = 0
                    while (i < pointerCount) {
                        val pointerId = ev.getPointerId(i)
                        if (!isValidPointerForActionMove(pointerId)) {
                            i++
                            continue
                        }
                        val x = ev.x
                        val y = ev.y
                        val dx = x - mInitialMotionX!![pointerId]
                        val dy = y - mInitialMotionY!![pointerId]
                        reportNewEdgeDrags(dx, dy, pointerId)
                        if (viewDragState == STATE_DRAGGING) {
                            break
                        }
                        val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                        if (checkTouchSlop(toCapture, dx, dy) &&
                            tryCaptureViewForDrag(toCapture, pointerId)) {
                            break
                        }
                        i++
                    }
                    saveLastMotion(ev)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = ev.getPointerId(actionIndex)
                if (viewDragState == STATE_DRAGGING && pointerId == activePointerId) {
                    var newActivePointer = INVALID_POINTER
                    val pointerCount = ev.pointerCount
                    var i = 0
                    while (i < pointerCount) {
                        val id = ev.getPointerId(i)
                        if (id == activePointerId) {
                            i++
                            continue
                        }
                        val x = ev.x
                        val y = ev.y
                        if (findTopChildUnder(x.toInt(), y.toInt()) === capturedView &&
                            tryCaptureViewForDrag(capturedView, id)) {
                            newActivePointer = activePointerId
                            break
                        }
                        i++
                    }
                    if (newActivePointer == INVALID_POINTER) releaseViewForPointerUp()
                }
                clearMotionHistory(pointerId)
            }
            MotionEvent.ACTION_UP -> {
                if (viewDragState == STATE_DRAGGING) releaseViewForPointerUp()
                cancel()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (viewDragState == STATE_DRAGGING) dispatchViewReleased(0f, 0f)
                cancel()
            }
        }
    }

    private fun reportNewEdgeDrags(dx: Float, dy: Float, pointerId: Int) {
        var dragsStarted = 0
        if (checkNewEdgeDrag(dx, dy, pointerId, EDGE_LEFT)) dragsStarted = dragsStarted or EDGE_LEFT
        if (checkNewEdgeDrag(dy, dx, pointerId, EDGE_TOP)) dragsStarted = dragsStarted or EDGE_TOP
        if (checkNewEdgeDrag(dx, dy, pointerId, EDGE_RIGHT)) dragsStarted = dragsStarted or EDGE_RIGHT
        if (checkNewEdgeDrag(dy, dx, pointerId, EDGE_BOTTOM)) dragsStarted = dragsStarted or EDGE_BOTTOM
        if (dragsStarted != 0) {
            mEdgeDragsInProgress[pointerId] = mEdgeDragsInProgress[pointerId] or dragsStarted
            mCallback.onEdgeDragStarted(dragsStarted, pointerId)
        }
    }

    private fun checkNewEdgeDrag(delta: Float, odelta: Float, pointerId: Int, edge: Int): Boolean {
        val absDelta = abs(delta)
        val absODelta = abs(odelta)
        if (mInitialEdgesTouched[pointerId] and edge != edge || mTrackingEdges and edge == 0 ||
            mEdgeDragsLocked[pointerId] and edge == edge || mEdgeDragsInProgress[pointerId] and edge == edge ||
            absDelta <= touchSlop && absODelta <= touchSlop) {
            return false
        }
        if (absDelta < absODelta * 0.5f && mCallback.onEdgeLock(edge)) {
            mEdgeDragsLocked[pointerId] = mEdgeDragsLocked[pointerId] or edge
            return false
        }
        return mEdgeDragsInProgress[pointerId] and edge == 0 && absDelta > touchSlop
    }

    private fun checkTouchSlop(child: View?, dx: Float, dy: Float): Boolean {
        if (child == null) return false
        val checkHorizontal = mCallback.getViewHorizontalDragRange(child) > 0
        val checkVertical = mCallback.getViewVerticalDragRange(child) > 0
        var tempDy = dy
        if (tempDy < 0) tempDy = -tempDy
        if (checkHorizontal && checkVertical) return dx * dx + dy * dy > touchSlop * touchSlop
        else if (checkVertical) return kotlin.math.abs(dy) > touchSlop 
        else if (checkHorizontal && 3 * tempDy < dx) return kotlin.math.abs(dx) > touchSlop
        return false
    }

    fun checkTouchSlop(directions: Int): Boolean {
        val count = mInitialMotionX!!.size
        for (i in 0 until count) {
            if (checkTouchSlop(directions, i)) {
                return true
            }
        }
        return false
    }
    
    private fun checkTouchSlop(directions: Int, pointerId: Int): Boolean {
        if (!isPointerDown(pointerId)) return false
        val checkHorizontal = directions and DIRECTION_HORIZONTAL == DIRECTION_HORIZONTAL
        val checkVertical = directions and DIRECTION_VERTICAL == DIRECTION_VERTICAL
        val dx = mLastMotionX[pointerId] - mInitialMotionX!![pointerId]
        val dy = mLastMotionY[pointerId] - mInitialMotionY!![pointerId]
        if (checkHorizontal && checkVertical) return dx * dx + dy * dy > touchSlop * touchSlop
        else if (checkHorizontal) return abs(dx) > touchSlop
        else if (checkVertical) return abs(dy) > touchSlop
        return false
    }

    fun isEdgeTouched(edges: Int): Boolean {
        val count = mInitialEdgesTouched.size
        for (i in 0 until count) {
            if (isEdgeTouched(edges, i)) {
                return true
            }
        }
        return false
    }
    
    fun isEdgeTouched(edges: Int, pointerId: Int): Boolean =
        isPointerDown(pointerId) && mInitialEdgesTouched[pointerId] and edges != 0

    private fun releaseViewForPointerUp() {
        mVelocityTracker!!.computeCurrentVelocity(1000, mMaxVelocity)
        val xvel = clampMag(
            mVelocityTracker?.getXVelocity(activePointerId) ?: 0F,
            minVelocity, mMaxVelocity)
        val yvel = clampMag(
            mVelocityTracker?.getYVelocity(activePointerId) ?: 0F,
            minVelocity, mMaxVelocity)
        dispatchViewReleased(xvel, yvel)
    }

    private fun dragTo(left: Int, top: Int, dx: Int, dy: Int) {
        var clampedX = left
        var clampedY = top
        val oldLeft = capturedView!!.left
        val oldTop = capturedView!!.top
        if (dx != 0) {
            clampedX = mCallback.clampViewPositionHorizontal(capturedView, left, dx)
            ViewCompat.offsetLeftAndRight(capturedView!!, clampedX - oldLeft)
        }
        if (dy != 0) {
            clampedY = mCallback.clampViewPositionVertical(capturedView, top, dy)
            ViewCompat.offsetTopAndBottom(capturedView!!, clampedY - oldTop)
        }
        if (dx != 0 || dy != 0) {
            val clampedDx = clampedX - oldLeft
            val clampedDy = clampedY - oldTop
            mCallback.onViewPositionChanged(capturedView, clampedX, clampedY,
                clampedDx, clampedDy)
        }
    }
    
    private fun isCapturedViewUnder(x: Int, y: Int): Boolean = isViewUnder(capturedView, x, y)
    
    private fun isViewUnder(view: View?, x: Int, y: Int): Boolean {
        return if (view == null) {
            false
        } else x >= view.left && x < view.right && y >= view.top && y < view.bottom
    }
    
    private fun findTopChildUnder(x: Int, y: Int): View? {
        val childCount = mParentView.childCount
        for (i in childCount - 1 downTo 0) {
            val child = mParentView.getChildAt(mCallback.getOrderedChildIndex(i))
            if (x >= child.left && x < child.right && y >= child.top && y < child.bottom) {
                return child
            }
        }
        return null
    }

    private fun getEdgesTouched(x: Int, y: Int): Int {
        var result = 0
        if (x < mParentView.left + edgeSize) result = result or EDGE_LEFT
        if (y < mParentView.top + edgeSize) result = result or EDGE_TOP
        if (x > mParentView.right - edgeSize) result = result or EDGE_RIGHT
        if (y > mParentView.bottom - edgeSize) result = result or EDGE_BOTTOM
        return result
    }

    private fun isValidPointerForActionMove(pointerId: Int): Boolean {
        if (!isPointerDown(pointerId)) {
            Log.e(
                "ViewDragHelper", "Ignoring pointerId=" + pointerId + " because ACTION_DOWN was not received "
                    + "for this pointer before ACTION_MOVE. It likely happened because "
                    + " ViewDragHelper did not receive all the events in the event stream.")
            return false
        }
        return true
    }

    companion object {

        const val INVALID_POINTER = -1
        const val STATE_IDLE = 0
        const val STATE_DRAGGING = 1
        const val STATE_SETTLING = 2
        const val EDGE_LEFT = 1 shl 0
        const val EDGE_RIGHT = 1 shl 1
        const val EDGE_TOP = 1 shl 2
        const val EDGE_BOTTOM = 1 shl 3
        const val EDGE_ALL = EDGE_LEFT or EDGE_TOP or EDGE_RIGHT or EDGE_BOTTOM
        const val DIRECTION_HORIZONTAL = 1 shl 0
        const val DIRECTION_VERTICAL = 1 shl 1
        const val DIRECTION_ALL = DIRECTION_HORIZONTAL or DIRECTION_VERTICAL
        private const val EDGE_SIZE = 20
        private const val BASE_SETTLE_DURATION = 256
        private const val MAX_SETTLE_DURATION = 600

        private val sInterpolator = Interpolator { t ->
            var t = t
            t -= 1.0f
            t * t * t * t * t + 1.0f
        }

        fun create(forParent: ViewGroup, cb: Callback?): ViewDragHelper =
            ViewDragHelper(forParent.context, forParent, cb)

        fun create(forParent: ViewGroup, sensitivity: Float, cb: Callback?): ViewDragHelper {
            val helper = create(forParent, cb)
            helper.touchSlop = (helper.touchSlop * (1 / sensitivity)).toInt()
            return helper
        }
    }

    init {
        requireNotNull(forParent) { "Parent view may not be null" }
        requireNotNull(cb) { "Callback may not be null" }
        mParentView = forParent
        mCallback = cb
        val vc = ViewConfiguration.get(context)
        val density = context.resources.displayMetrics.density
        edgeSize = (EDGE_SIZE * density + 0.5f).toInt()
        touchSlop = vc.scaledTouchSlop
        mMaxVelocity = vc.scaledMaximumFlingVelocity.toFloat()
        minVelocity = vc.scaledMinimumFlingVelocity.toFloat()
        mScroller = OverScroller(context, sInterpolator)
    }
}
package com.jeluchu.sliding.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import com.jeluchu.sliding.model.Position

internal class ScrimRenderer(private val rootView: View, private val decorView: View) {

    private val dirtyRect: Rect = Rect()

    fun render(canvas: Canvas, position: Position?, paint: Paint) {
        when (position) {
            Position.LEFT -> renderLeft(canvas, paint)
            Position.RIGHT -> renderRight(canvas, paint)
            Position.TOP -> renderTop(canvas, paint)
            Position.BOTTOM -> renderBottom(canvas, paint)
            Position.VERTICAL -> renderVertical(canvas, paint)
            Position.HORIZONTAL -> renderHorizontal(canvas, paint)
        }
    }

    fun getDirtyRect(position: Position?): Rect {
        when (position) {
            Position.LEFT -> dirtyRect[0, 0, decorView.left] = rootView.measuredHeight
            Position.RIGHT -> dirtyRect[decorView.right, 0, rootView.measuredWidth] = rootView.measuredHeight
            Position.TOP -> dirtyRect[0, 0, rootView.measuredWidth] = decorView.top
            Position.BOTTOM -> dirtyRect[0, decorView.bottom, rootView.measuredWidth] = rootView.measuredHeight
            Position.VERTICAL -> if (decorView.top > 0) {
                dirtyRect[0, 0, rootView.measuredWidth] = decorView.top
            } else {
                dirtyRect[0, decorView.bottom, rootView.measuredWidth] = rootView.measuredHeight
            }
            Position.HORIZONTAL -> if (decorView.left > 0) {
                dirtyRect[0, 0, decorView.left] = rootView.measuredHeight
            } else {
                dirtyRect[decorView.right, 0, rootView.measuredWidth] = rootView.measuredHeight
            }
        }
        return dirtyRect
    }

    private fun renderLeft(canvas: Canvas, paint: Paint) {
        canvas.drawRect(0f, 0f, decorView.left.toFloat(), rootView.measuredHeight.toFloat(), paint)
    }

    private fun renderRight(canvas: Canvas, paint: Paint) {
        canvas.drawRect(decorView.right.toFloat(), 0f, rootView.measuredWidth.toFloat(), rootView.measuredHeight.toFloat(), paint)
    }

    private fun renderTop(canvas: Canvas, paint: Paint) {
        canvas.drawRect(0f, 0f, rootView.measuredWidth.toFloat(), decorView.top.toFloat(), paint)
    }

    private fun renderBottom(canvas: Canvas, paint: Paint) {
        canvas.drawRect(0f, decorView.bottom.toFloat(), rootView.measuredWidth.toFloat(), rootView.measuredHeight.toFloat(), paint)
    }

    private fun renderVertical(canvas: Canvas, paint: Paint) {
        if (decorView.top > 0) renderTop(canvas, paint)
        else renderBottom(canvas, paint)
    }

    private fun renderHorizontal(canvas: Canvas, paint: Paint) {
        if (decorView.left > 0) renderLeft(canvas, paint)
        else renderRight(canvas, paint)
    }

}
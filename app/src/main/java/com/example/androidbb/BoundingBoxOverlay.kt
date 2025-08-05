package com.example.androidbb

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class BoundingBoxOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDrawing = false
    private var box: RectF? = null  // ✅ stored box

    var onBoxSelected: ((RectF) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
                isDrawing = true
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                endX = event.x
                endY = event.y
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                endX = event.x
                endY = event.y
                isDrawing = false
                box = RectF(
                    minOf(startX, endX),
                    minOf(startY, endY),
                    maxOf(startX, endX),
                    maxOf(startY, endY)
                )
                onBoxSelected?.invoke(box!!)
                invalidate()
            }
        }

        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeWidth = 4f
        }

        if (isDrawing) {
            canvas.drawRect(startX, startY, endX, endY, paint)
        } else if (box != null) {
            canvas.drawRect(box!!, paint)
        }
    }

    fun clearBox() {
        box = null
        invalidate()
    }
}

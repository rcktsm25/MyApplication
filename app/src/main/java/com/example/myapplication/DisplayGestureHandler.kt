package com.example.myapplication

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Gestor de gestos para el display del reproductor.
 * Soporta swipe vertical (volumen), doble tap (visualizador), y long press (menú).
 */
class DisplayGestureHandler(
    private val onSwipeUp: (() -> Unit)? = null,
    private val onSwipeDown: (() -> Unit)? = null,
    private val onDoubleTap: (() -> Unit)? = null,
    private val onLongPress: (() -> Unit)? = null,
    private val onVolumeChanged: ((Int) -> Unit)? = null
) {
    
    private var initialY = 0f
    private var isTrackingSwipe = false
    private var volumeStart = 0
    
    fun attachToView(view: View, getVolume: () -> Int, maxVolume: Int = 100) {
        val gestureDetector = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                onDoubleTap?.invoke()
                return true
            }
            
            override fun onLongPress(e: MotionEvent) {
                onLongPress?.invoke()
            }
            
            override fun onDown(e: MotionEvent): Boolean {
                initialY = e.y
                isTrackingSwipe = true
                volumeStart = getVolume()
                return true
            }
            
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (!isTrackingSwipe) return false
                
                val deltaY = initialY - e2.y
                val sensitivity = 0.5f
                val volumeDelta = (deltaY * sensitivity).toInt()
                val newVolume = (volumeStart + volumeDelta).coerceIn(0, maxVolume)
                
                if (newVolume != volumeStart) {
                    onVolumeChanged?.invoke(newVolume)
                    if (newVolume > volumeStart) onSwipeUp?.invoke()
                    else onSwipeDown?.invoke()
                }
                
                return true
            }
        })
        
        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
        
        view.isClickable = true
        view.isLongClickable = true
    }
}

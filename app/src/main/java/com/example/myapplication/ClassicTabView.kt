package com.example.myapplication

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView

/** A single accessible button with a centered, consistently sized icon and caption. */
class ClassicTabView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val icon = AppCompatImageView(context)
    private val caption = TextView(context)
    init {
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val values = context.obtainStyledAttributes(attrs, R.styleable.ClassicTabView)
        val title = values.getText(R.styleable.ClassicTabView_android_text)
        val resource = values.getResourceId(R.styleable.ClassicTabView_android_drawableTop, 0)
        values.recycle()
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(3), dp(4), dp(3))
        isClickable = true; isFocusable = true
        contentDescription = title
        setBackgroundResource(R.drawable.panel_button)
        icon.setImageResource(resource)
        icon.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        if (resource == R.drawable.ic_spotify) icon.setPadding(dp(2), dp(2), dp(2), dp(2))
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(icon, LayoutParams(dp(20), dp(20)))
        caption.text = title; caption.textSize = 10f; caption.typeface = Typeface.MONOSPACE
        caption.gravity = Gravity.CENTER; caption.includeFontPadding = false; caption.setSingleLine()
        caption.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(caption, LayoutParams(-1, -2).apply { topMargin = dp(3) })
    }
    fun setTint(color: Int) {
        icon.imageTintList = ColorStateList.valueOf(color)
        caption.setTextColor(color)
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        info.isSelected = isSelected
    }
}

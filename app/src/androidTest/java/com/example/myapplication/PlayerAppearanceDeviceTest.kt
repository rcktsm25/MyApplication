package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.databinding.ActivityMainBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerAppearanceDeviceTest {
    @Test fun everyBackgroundUpdatesRealPanelsWithoutBreakingTitleLayers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_MyApplication)
            val prefs = context.getSharedPreferences("appearance_regression_test", 0)
            try {
                val binding = ActivityMainBinding.inflate(LayoutInflater.from(context))
                PlayerPanels(context, prefs).settings(binding.settingsContent, false, {}, {}, {}, {}, {}, {})
                val rendered = mutableSetOf<Int>()
                for (index in PlayerAppearance.surfaces.indices) {
                    prefs.edit().putInt("background_color", index).commit()
                    PlayerAppearance.applySurfaces(binding.main, prefs)
                    val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
                    binding.playerUnit.background.apply { setBounds(0, 0, 200, 100); draw(Canvas(bitmap)) }
                    rendered.add(bitmap.getPixel(100, 50))
                    assertTrue(bitmap.getPixel(1, 1) != bitmap.getPixel(100, 50))
                    bitmap.recycle()
                }
                assertEquals(PlayerAppearance.surfaces.size, rendered.size)
                prefs.edit().putInt("background_color", PlayerAppearance.surfaces.size).putInt("custom_background", 0xFFFFFFFF.toInt()).commit()
                PlayerAppearance.applySurfaces(binding.main, prefs)
                assertEquals("Personalizado", PlayerAppearance.backgroundName(prefs))
            } finally { prefs.edit().clear().commit() }
        }
    }
}

package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceThemesDeviceTest {
    @Test fun savedThemeRestoresAppearanceAndVisualizerWithoutChangingLibrary() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("theme_store_test", 0)
        try {
            prefs.edit().clear().putInt("display_color", 4).putInt("background_color", 6).putInt("visualizer_style", 3)
                .putBoolean("visualizer_grid", false).putString("music_folder_uri", "keep-this").commit()
            val store = AppearanceThemes(prefs)
            val theme = store.save("Noche")
            assertEquals("Noche (2)", store.save("Noche").name)
            prefs.edit().putInt("display_color", 0).putInt("visualizer_style", 0).commit()
            AppearanceThemes(prefs).apply(AppearanceThemes(prefs).list().first())
            assertEquals(4, prefs.getInt("display_color", 0))
            assertEquals(6, prefs.getInt("background_color", 0))
            assertEquals(3, prefs.getInt("visualizer_style", 0))
            assertFalse(prefs.getBoolean("visualizer_grid", true))
            assertEquals("keep-this", prefs.getString("music_folder_uri", null))
            store.delete(theme.id)
            assertEquals(1, store.list().size)
        } finally { prefs.edit().clear().commit() }
    }
}

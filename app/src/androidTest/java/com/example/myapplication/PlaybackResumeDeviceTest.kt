package com.example.myapplication

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackResumeDeviceTest {
    @Test fun restoresPausedSongAndPositionAndPersistsASeek() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("RocketSettings", 0)
        val keys = listOf("music_folder_uri", "spotify_mode", "resume_local_uri", "resume_local_position", "resume_local_shuffle", "resume_local_repeat")
        val saved = keys.associateWith { prefs.all[it] }
        val tree = DocumentsContract.buildTreeDocumentUri("com.example.myapplication.test.music", "root")
        val song = DocumentsContract.buildDocumentUriUsingTree(tree, "song").toString()
        prefs.edit().putString("music_folder_uri", tree.toString()).putBoolean("spotify_mode", false)
            .putString("resume_local_uri", song).putLong("resume_local_position", 500).commit()
        var scenario: ActivityScenario<MainActivity>? = null
        var controller: MediaController? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val future = instrumentation.runOnMainSyncResult {
                MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            controller = future.get(10, TimeUnit.SECONDS)
            val activeController = controller
            val deadline = SystemClock.elapsedRealtime() + 10000
            var restored = false
            while (!restored && SystemClock.elapsedRealtime() < deadline) {
                restored = instrumentation.runOnMainSyncResult {
                    activeController.playbackState == Player.STATE_READY && activeController.currentMediaItem?.mediaId == song && activeController.currentPosition in 450..550
                }
                if (!restored) SystemClock.sleep(50)
            }
            assertTrue("Saved song and position were not restored", restored)
            instrumentation.runOnMainSync { assertFalse(activeController.isPlaying); activeController.seekTo(700) }
            val saveDeadline = SystemClock.elapsedRealtime() + 5000
            while (prefs.getLong("resume_local_position", 0) !in 650..750 && SystemClock.elapsedRealtime() < saveDeadline) SystemClock.sleep(50)
            assertEquals(song, prefs.getString("resume_local_uri", null))
            assertTrue(prefs.getLong("resume_local_position", 0) in 650..750)
        } finally {
            instrumentation.runOnMainSync { controller?.release() }
            scenario?.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            instrumentation.waitForIdleSync()
            prefs.edit().apply {
                keys.forEach { remove(it) }
                saved.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                } }
            }.commit()
        }
    }
    private fun <T> android.app.Instrumentation.runOnMainSyncResult(action: () -> T): T {
        var result: T? = null
        runOnMainSync { result = action() }
        return result!!
    }
}

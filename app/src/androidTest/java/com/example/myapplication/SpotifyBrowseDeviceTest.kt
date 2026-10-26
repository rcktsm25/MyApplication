package com.example.myapplication

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.ListItem
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in integration check: requires an installed, previously authorized Spotify account. */
@RunWith(AndroidJUnit4::class)
class SpotifyBrowseDeviceTest {
    @Test fun readsLibraryFromAuthorizedSpotifyApp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("spotifyIntegration") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val connected = CountDownLatch(1)
        val remote = AtomicReference<SpotifyAppRemote>()
        val failure = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            SpotifyAppRemote.connect(context, ConnectionParams.Builder(SpotifyManager(context).getClientId())
                .setRedirectUri(SpotifyManager.REDIRECT_URI).showAuthView(false).build(),
                object : Connector.ConnectionListener {
                    override fun onConnected(value: SpotifyAppRemote) { remote.set(value); connected.countDown() }
                    override fun onFailure(error: Throwable) { failure.set(error); connected.countDown() }
                })
        }
        assertTrue("Connection timed out", connected.await(20, TimeUnit.SECONDS))
        assertNull("Connection failed: ${failure.get()}", failure.get())
        try {
            val loaded = CountDownLatch(1)
            instrumentation.runOnMainSync {
                val root = ListItem("com.spotify.your-library", "com.spotify.your-library", null,
                    "Tu biblioteca", "", false, true)
                remote.get().contentApi.getChildrenOfItem(root, 50, 0)
                    .setResultCallback { result ->
                        Log.i("SpotifyBrowseTest", "Library total=${result.total}, count=${result.items.size}")
                        result.items.forEach { Log.i("SpotifyBrowseTest", "id=${it.id} title=${it.title} playable=${it.playable} children=${it.hasChildren}") }
                        loaded.countDown()
                    }.setErrorCallback { failure.set(it); loaded.countDown() }
            }
            assertTrue("Library timed out", loaded.await(20, TimeUnit.SECONDS))
            assertNull("Library failed: ${failure.get()}", failure.get())
        } finally {
            instrumentation.runOnMainSync { remote.get()?.let { SpotifyAppRemote.disconnect(it) } }
        }
    }
}

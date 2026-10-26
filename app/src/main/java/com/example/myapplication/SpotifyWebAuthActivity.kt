package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import java.util.concurrent.Executors

/** Separate PKCE authorization for read-only song selection; no RocketPlayer account. */
class SpotifyWebAuthActivity : Activity() {
    private var launched = false
    private var processing = false
    private var state = ""
    private var verifier = ""
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launched = savedInstanceState?.getBoolean("launched") ?: false
        state = savedInstanceState?.getString("state") ?: SpotifyWebApi.STATE_PREFIX + SpotifyBrowserCallback.newState()
        verifier = savedInstanceState?.getString("verifier") ?: SpotifyBrowserCallback.newState().takeLast(64)
        if (intent.data != null && savedInstanceState == null) finishError("Respuesta de Spotify sin una solicitud activa.")
    }
    override fun onResume() {
        super.onResume()
        if (isFinishing || processing) return
        if (!launched) {
            launched = true
            val client = SpotifyManager(this).getClientId()
            if (!SpotifyManager.isValidClientId(client)) { finishError(getString(R.string.spotify_invalid_client_id)); return }
            val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
                .appendQueryParameter("client_id", client).appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", SpotifyManager.REDIRECT_URI)
                .appendQueryParameter("scope", "user-library-read app-remote-control")
                .appendQueryParameter("state", state).appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", SpotifyWebApi.challenge(verifier)).build()
            try { CustomTabsIntent.Builder().build().launchUrl(this, uri) }
            catch (_: Exception) { finishError(getString(R.string.spotify_browser_missing)) }
            return
        }
        val uri = intent.data
        if (uri == null) { setResult(RESULT_CANCELED); finish(); return }
        when (val result = SpotifyBrowserCallback.validate(uri.toString(), state)) {
            SpotifyBrowserCallback.Result.Approved -> {
                processing = true
                setContentView(android.widget.TextView(this).apply {
                    text = "Conectando tus canciones de Spotify…"; gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.WHITE); setBackgroundColor(0xFF171A20.toInt())
                })
                val code = uri.getQueryParameter("code")!!
                val client = SpotifyManager(this).getClientId()
                executor.execute {
                    val exchange = runCatching { SpotifyWebApi(this).exchange(code, verifier, client) }
                    runOnUiThread {
                        if (isDestroyed || isFinishing) return@runOnUiThread
                        exchange.fold({ setResult(RESULT_OK); finish() }, { finishError(it.localizedMessage ?: "No se pudo conectar Spotify.") })
                    }
                }
            }
            is SpotifyBrowserCallback.Result.Denied -> finishError("Spotify no concedió el permiso: ${result.error}")
            SpotifyBrowserCallback.Result.Invalid -> finishError(getString(R.string.spotify_invalid_callback))
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("launched", launched); outState.putString("state", state); outState.putString("verifier", verifier)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
    private fun finishError(error: String) { setResult(RESULT_CANCELED, Intent().putExtra("error", error)); finish() }
}

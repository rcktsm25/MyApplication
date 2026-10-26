package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

/**
 * Browser-only consent. Spotify's native SSO service is never used here.
 * The result contains only a success flag; authorization codes are not persisted.
 */
class SpotifyBrowserAuthActivity : Activity() {
    private var launched = false
    private var state = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launched = savedInstanceState?.getBoolean("launched") ?: false
        state = savedInstanceState?.getString("state") ?: SpotifyBrowserCallback.newState()
        // An unsolicited redirect cannot create an authorized session.
        if (intent.data != null && savedInstanceState == null) {
            finishWithError(getString(R.string.spotify_invalid_callback))
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        if (!launched) {
            val clientId = intent.getStringExtra(EXTRA_CLIENT_ID).orEmpty()
            if (!SpotifyManager.isValidClientId(clientId)) {
                finishWithError(getString(R.string.spotify_invalid_client_id))
                return
            }
            launched = true
            try {
                val request = AuthorizationRequest.Builder(
                    clientId, AuthorizationResponse.Type.CODE, SpotifyManager.REDIRECT_URI
                ).setScopes(arrayOf("app-remote-control"))
                    .setState(state)
                    .setShowDialog(true)
                    .build()
                val browser = CustomTabsClient.getPackageName(this, emptyList())
                if (browser != null) {
                    CustomTabsIntent.Builder().build().apply {
                        intent.setPackage(browser)
                    }.launchUrl(this, request.toUri())
                } else {
                    val browserIntent = Intent(Intent.ACTION_VIEW, request.toUri())
                    if (browserIntent.resolveActivity(packageManager) != null) {
                        startActivity(browserIntent)
                    } else {
                        throw IllegalStateException(getString(R.string.spotify_browser_missing))
                    }
                }
            } catch (error: Exception) {
                finishWithError(error.localizedMessage ?: getString(R.string.spotify_browser_missing))
            }
            return
        }

        val uri = intent.data
        if (uri == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        when (val result = SpotifyBrowserCallback.validate(uri.toString(), state)) {
            SpotifyBrowserCallback.Result.Approved -> {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_APPROVED, true))
                finish()
            }
            is SpotifyBrowserCallback.Result.Denied -> finishWithError(result.error)
            SpotifyBrowserCallback.Result.Invalid -> finishWithError(getString(R.string.spotify_invalid_callback))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("launched", launched)
        outState.putString("state", state)
        super.onSaveInstanceState(outState)
    }

    private fun finishWithError(message: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }

    companion object {
        const val EXTRA_CLIENT_ID = "spotify_client_id"
        const val EXTRA_APPROVED = "spotify_browser_approved"
        const val EXTRA_ERROR = "spotify_browser_error"
    }
}

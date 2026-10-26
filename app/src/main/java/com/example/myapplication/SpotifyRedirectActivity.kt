package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log

/** One redirect handler for both the SDK's login and our browser fallback. */
class SpotifyRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri != null) {
            // Routing must not depend on validation: a rejected browser response
            // still belongs to the browser flow, which will report the error.
            val browserResponse = SpotifyBrowserCallback.isBrowserResponse(uri.toString())
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                runCatching {
                    Log.d("SpotifyRedirect", "browser=$browserResponse rootPath=${uri.path in listOf("", "/")} " +
                        "code=${uri.getQueryParameter("code") != null} state=${uri.getQueryParameter("state") != null} " +
                        "error=${uri.getQueryParameter("error") != null} fragment=${uri.fragment != null}")
                }
            }
            val target = if (runCatching { uri.getQueryParameter("state")?.startsWith(SpotifyWebApi.STATE_PREFIX) }.getOrDefault(false) == true) {
                SpotifyWebAuthActivity::class.java
            } else if (browserResponse) {
                SpotifyBrowserAuthActivity::class.java
            } else {
                com.spotify.sdk.android.auth.LoginActivity::class.java
            }
            startActivity(Intent(this, target).setData(uri).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ))
        }
        finish()
    }
}

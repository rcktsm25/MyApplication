package com.example.myapplication

/** One explicit consent request per connection attempt, including after an unsuccessful retry. */
internal class SpotifyAuthorizationGate {
    private var attempted = false
    private var awaitingResult = false
    private var browserAttempted = false
    private var awaitingBrowserResult = false

    fun request(): Boolean {
        if (attempted) return false
        attempted = true
        awaitingResult = true
        return true
    }

    fun consumeResult(): Boolean {
        if (!awaitingResult) return false
        awaitingResult = false
        return true
    }

    fun requestBrowser(error: String?): Boolean {
        if (error != "AUTHENTICATION_SERVICE_UNAVAILABLE" || browserAttempted) return false
        browserAttempted = true
        awaitingBrowserResult = true
        return true
    }

    fun consumeBrowserResult(): Boolean {
        if (!awaitingBrowserResult) return false
        awaitingBrowserResult = false
        return true
    }

    fun reset() {
        attempted = false
        awaitingResult = false
        browserAttempted = false
        awaitingBrowserResult = false
    }
}

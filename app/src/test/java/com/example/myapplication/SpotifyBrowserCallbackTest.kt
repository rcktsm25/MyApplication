package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class SpotifyBrowserCallbackTest {
    private val redirect = SpotifyManager.REDIRECT_URI
    private val state = SpotifyBrowserCallback.newState()

    @Test fun acceptsCodeOnlyForTheCurrentAttempt() {
        assertEquals(SpotifyBrowserCallback.Result.Approved,
            SpotifyBrowserCallback.validate("$redirect?code=test-code&state=$state", state))
        assertEquals(SpotifyBrowserCallback.Result.Invalid,
            SpotifyBrowserCallback.validate("$redirect?code=test-code&state=another-attempt", state))
    }

    @Test fun acceptsBrowserNormalizedRootSlash() {
        val callback = "$redirect/?code=test-code&state=$state"
        assertTrue(SpotifyBrowserCallback.isBrowserResponse(callback))
        assertEquals(SpotifyBrowserCallback.Result.Approved,
            SpotifyBrowserCallback.validate(callback, state))
        assertEquals(SpotifyBrowserCallback.Result.Invalid,
            SpotifyBrowserCallback.validate(callback, "another-attempt"))
    }

    @Test fun preservesDenialInsteadOfRetrying() {
        assertEquals(SpotifyBrowserCallback.Result.Denied("access_denied"),
            SpotifyBrowserCallback.validate("$redirect?error=access_denied&state=$state", state))
    }

    @Test fun rejectsMissingAndAmbiguousResponses() {
        for (query in listOf(
            "code=test-code", "state=$state", "code=&state=$state",
            "code=test-code&error=access_denied&state=$state",
            "code=test-code&state=$state&state=wrong"
        )) {
            assertEquals(query, SpotifyBrowserCallback.Result.Invalid,
                SpotifyBrowserCallback.validate("$redirect?$query", state))
        }
    }

    @Test fun rejectsOtherEndpointsAndFragments() {
        for (base in listOf(
            "https://spotify-callback", "rocketplayer://spotify-callback.evil",
            "rocketplayer://user@spotify-callback", "$redirect/unexpected"
        )) {
            assertEquals(SpotifyBrowserCallback.Result.Invalid,
                SpotifyBrowserCallback.validate("$base?code=test-code&state=$state", state))
        }
        assertEquals(SpotifyBrowserCallback.Result.Invalid,
            SpotifyBrowserCallback.validate("$redirect#code=test-code&state=$state", state))
    }

    @Test fun routesBrowserRepliesSeparatelyFromTheSdk() {
        assertTrue(SpotifyBrowserCallback.isBrowserResponse("$redirect?code=test-code&state=$state"))
        assertFalse(SpotifyBrowserCallback.isBrowserResponse("$redirect?code=test-code&state=sdk-state"))
        assertFalse(SpotifyBrowserCallback.isBrowserResponse("https://evil.test?state=$state"))
    }

    @Test fun invalidBrowserResponsesStayInBrowserFlowToReportFailure() {
        for (callback in listOf(
            "$redirect/unexpected?code=test-code&state=$state",
            "$redirect?code=test-code&state=$state#unexpected",
            "$redirect?code=test-code&state=$state&state=duplicate"
        )) {
            assertTrue(SpotifyBrowserCallback.isBrowserResponse(callback))
            assertEquals(SpotifyBrowserCallback.Result.Invalid,
                SpotifyBrowserCallback.validate(callback, state))
        }
    }

    @Test fun eachAttemptHasAnIndependentNonce() {
        val states = (1..100).map { SpotifyBrowserCallback.newState() }
        assertEquals(100, states.distinct().size)
        assertTrue(states.all { it.length >= 64 })
    }
}

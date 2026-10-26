package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class SpotifyAuthorizationGateTest {
    @Test fun unavailableNativeServiceMayFallBackToBrowserOnlyOnce() {
        val gate = SpotifyAuthorizationGate()
        gate.request()
        gate.consumeResult()
        assertTrue(gate.requestBrowser("AUTHENTICATION_SERVICE_UNAVAILABLE"))
        assertFalse(gate.requestBrowser("AUTHENTICATION_SERVICE_UNAVAILABLE"))
        assertTrue(gate.consumeBrowserResult())
        assertFalse(gate.consumeBrowserResult())
        assertFalse(gate.requestBrowser("AUTHENTICATION_SERVICE_UNAVAILABLE"))
    }

    @Test fun cancellationAndConfigurationErrorsDoNotOpenBrowser() {
        val gate = SpotifyAuthorizationGate()
        for (error in listOf(null, "access_denied", "invalid_client", "invalid_redirect_uri")) {
            assertFalse(gate.requestBrowser(error))
        }
        assertFalse(gate.consumeBrowserResult())
    }

    @Test fun disconnectInvalidatesPendingBrowserResponse() {
        val gate = SpotifyAuthorizationGate()
        gate.requestBrowser("AUTHENTICATION_SERVICE_UNAVAILABLE")
        gate.reset()
        assertFalse(gate.consumeBrowserResult())
        assertTrue(gate.requestBrowser("AUTHENTICATION_SERVICE_UNAVAILABLE"))
    }

    @Test fun consentIsRequestedOnlyOnceEvenIfRemoteRetryFails() {
        val gate = SpotifyAuthorizationGate()
        assertTrue(gate.request())
        assertFalse(gate.request())
        assertTrue(gate.consumeResult())
        assertFalse(gate.request())
        assertFalse(gate.consumeResult())
    }

    @Test fun cancelledConnectionIgnoresLateResult() {
        val gate = SpotifyAuthorizationGate()
        gate.request()
        gate.reset()
        assertFalse(gate.consumeResult())
    }

    @Test fun userMayStartAnotherAttemptAfterCancellation() {
        val gate = SpotifyAuthorizationGate()
        gate.request()
        gate.consumeResult()
        gate.reset()
        assertTrue(gate.request())
        assertTrue(gate.consumeResult())
    }
}

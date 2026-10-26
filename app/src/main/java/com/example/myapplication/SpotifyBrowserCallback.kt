package com.example.myapplication

import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom

/** Validate a browser response before treating its authorization code as consent. */
internal object SpotifyBrowserCallback {
    private const val STATE_PREFIX = "rocket-browser-"

    sealed class Result {
        object Approved : Result()
        data class Denied(val error: String) : Result()
        object Invalid : Result()
    }

    fun newState(): String = STATE_PREFIX + ByteArray(32).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    // Choose the recipient even for a malformed response. Validation happens there,
    // so an invalid browser reply never reopens the SDK's unrelated login screen.
    fun isBrowserResponse(url: String): Boolean = runCatching {
        val uri = URI(url)
        val redirect = URI(SpotifyManager.REDIRECT_URI)
        uri.scheme == redirect.scheme && uri.rawAuthority == redirect.rawAuthority &&
            queryPairs(uri).any { (key, value) -> key == "state" && value.startsWith(STATE_PREFIX) }
    }.getOrDefault(false)

    fun validate(url: String, expectedState: String): Result {
        if (expectedState.isBlank()) return Result.Invalid
        val params = parameters(url) ?: return Result.Invalid
        if (params["state"] != expectedState) return Result.Invalid
        val code = params["code"]
        val error = params["error"]
        return when {
            !code.isNullOrBlank() && error == null -> Result.Approved
            !error.isNullOrBlank() && code == null -> Result.Denied(error)
            else -> Result.Invalid
        }
    }

    private fun parameters(url: String): Map<String, String>? = runCatching {
        val uri = URI(url)
        val redirect = URI(SpotifyManager.REDIRECT_URI)
        require(uri.scheme == redirect.scheme && uri.rawAuthority == redirect.rawAuthority)
        // Browsers may canonicalize a host-only redirect with a root slash.
        require(uri.rawPath in setOf(redirect.rawPath, "/") && uri.fragment == null)
        val pairs = queryPairs(uri)
        require(pairs.map { it.first }.distinct().size == pairs.size)
        pairs.toMap()
    }.getOrNull()

    private fun queryPairs(uri: URI): List<Pair<String, String>> =
        requireNotNull(uri.rawQuery).split("&").map {
            val pair = it.split("=", limit = 2)
            require(pair.size == 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
}

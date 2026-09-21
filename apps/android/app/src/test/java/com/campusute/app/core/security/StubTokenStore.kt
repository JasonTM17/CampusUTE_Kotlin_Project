package com.campusute.app.core.security

/** In-memory token store for ViewModel tests that never touch the real keystore. */
class StubTokenStore(
    private var access: String? = "test-access",
    private var refresh: String? = "test-refresh",
) : TokenStore {
    override fun saveTokens(accessToken: String, refreshToken: String) {
        access = accessToken
        refresh = refreshToken
    }

    override fun accessToken() = access

    override fun refreshToken() = refresh

    override fun clear() {
        access = null
        refresh = null
    }
}

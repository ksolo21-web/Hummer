package com.mystudycompanion.app.auth

import com.mystudycompanion.app.network.RequestHeadersProvider

class FirebaseAuthHeaders(
    private val authRepository: AuthRepository,
) : RequestHeadersProvider {
    override suspend fun headers(): Map<String, String> {
        val token = runCatching { authRepository.freshIdToken() }.getOrNull().orEmpty()
        return if (token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $token")
    }
}

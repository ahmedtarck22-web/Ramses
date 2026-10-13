package com.example.data.model

enum class AuthProvider {
    GUEST,
    GOOGLE,
    APPLE
}

data class UserProfile(
    val email: String? = null,
    val displayName: String = "زائر ملكي",
    val provider: AuthProvider = AuthProvider.GUEST,
    val isSignedIn: Boolean = false,
    val avatarUrl: String? = null
)

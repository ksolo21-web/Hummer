package com.kreativstudio.app.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.kreativstudio.app.BuildConfig
import com.kreativstudio.app.model.AppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val context: Context,
    private val firebaseReady: Boolean,
) {
    private val credentialManager = CredentialManager.create(context)
    private val state = MutableStateFlow(currentFirebaseUser())

    val user: StateFlow<AppUser?> = state.asStateFlow()

    val isGoogleConfigured: Boolean
        get() = firebaseReady && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signInWithGoogle(activity: Activity): Result<AppUser> = runCatching {
        check(isGoogleConfigured) {
            "Google sign-in is ready in the codebase but needs the private Firebase and OAuth values for this build."
        }

        val googleOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val result = try {
            credentialManager.getCredential(
                context = activity,
                request = request,
            )
        } catch (error: NoCredentialException) {
            throw IllegalStateException(
                "No Google credential is available. Add or select a Google account, then try again.",
                error,
            )
        }
        val credential = result.credential
        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Google did not return a supported ID credential." }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
        val firebaseUser = requireNotNull(authResult.user) { "Firebase did not return a signed-in user." }
        firebaseUser.toAppUser().also { state.value = it }
    }

    fun useOliviaPreview(): AppUser = AppUser(
        uid = "local-olivia-preview",
        displayName = "Olivia Franklin",
        email = null,
        isOliviaOwner = true,
        isLocalPreview = true,
    ).also { state.value = it }

    fun useGuestStudio(): AppUser = AppUser(
        uid = "local-guest",
        displayName = "Artist",
        email = null,
        isOliviaOwner = false,
        isLocalPreview = true,
    ).also { state.value = it }

    suspend fun signOut() {
        if (firebaseReady) runCatching { FirebaseAuth.getInstance().signOut() }
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        state.value = null
    }

    private fun currentFirebaseUser(): AppUser? = if (!firebaseReady) null else runCatching {
        FirebaseAuth.getInstance().currentUser?.toAppUser()
    }.getOrNull()

    private fun com.google.firebase.auth.FirebaseUser.toAppUser() = AppUser(
        uid = uid,
        displayName = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.replaceFirstChar { it.uppercase() }
            ?: "Artist",
        email = email,
        photoUrl = photoUrl?.toString(),
        isOliviaOwner = BuildConfig.OLIVIA_FIREBASE_UID.isNotBlank() && uid == BuildConfig.OLIVIA_FIREBASE_UID,
        isLocalPreview = false,
    )
}

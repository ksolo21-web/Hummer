package com.kreativstudio.app.auth

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.kreativstudio.app.BuildConfig
import com.kreativstudio.app.model.AppUser
import java.security.MessageDigest
import java.util.Locale
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
            "This KREATIV build is missing its registered Firebase/OAuth configuration."
        }
        check(hasRegisteredGoogleSigningCertificate()) {
            "This APK is signed with the wrong Android certificate. Install KREATIV 0.1.10 signed with the original Firebase-registered private-alpha key."
        }

        val result = requestGoogleCredential(activity)
        val credential = result.credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "Google did not return a supported ID credential." }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
        val firebaseUser = requireNotNull(authResult.user) { "Firebase did not return a signed-in user." }
        firebaseUser.toAppUser().also { state.value = it }
    }.recoverCatching { error ->
        val message = error.message.orEmpty()
        if (
            "DEVELOPER_ERROR" in message ||
            "10:" in message ||
            "configuration" in message.lowercase()
        ) {
            throw IllegalStateException(
                "Google rejected this APK's registered package or signing certificate. Install the authenticated KREATIV private-alpha build signed with the Firebase-registered key.",
                error,
            )
        }
        throw error
    }

    private suspend fun requestGoogleCredential(activity: Activity): GetCredentialResponse {
        return try {
            requestAnyGoogleAccount(activity)
        } catch (first: NoCredentialException) {
            requestExplicitAfterReset(activity, first)
        } catch (first: GetCredentialCancellationException) {
            // Some Samsung/foldable builds report a false cancellation while the
            // standard chooser is switching surfaces. Clear stale chooser state and
            // retry through the explicit Google button before treating it as final.
            requestExplicitAfterReset(activity, first)
        }
    }

    private suspend fun requestExplicitAfterReset(
        activity: Activity,
        firstFailure: Throwable,
    ): GetCredentialResponse {
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
        return try {
            requestExplicitGoogleSignIn(activity)
        } catch (cancelled: GetCredentialCancellationException) {
            throw IllegalStateException("Google sign-in was cancelled.", cancelled)
        } catch (noCredential: NoCredentialException) {
            throw IllegalStateException(
                "No Google account credential was available. Confirm Google Play services is current and try again.",
                noCredential,
            )
        } catch (secondFailure: Throwable) {
            secondFailure.addSuppressed(firstFailure)
            throw secondFailure
        }
    }

    private suspend fun requestAnyGoogleAccount(activity: Activity) = credentialManager.getCredential(
        context = activity,
        request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build(),
            )
            .build(),
    )

    private suspend fun requestExplicitGoogleSignIn(activity: Activity) = credentialManager.getCredential(
        context = activity,
        request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .build(),
            )
            .build(),
    )

    private fun hasRegisteredGoogleSigningCertificate(): Boolean {
        val expectedSha1 = BuildConfig.REGISTERED_SIGNER_SHA1.normalizedDigest()
        val expectedSha256 = BuildConfig.REGISTERED_SIGNER_SHA256.normalizedDigest()
        if (expectedSha1.isBlank() || expectedSha256.isBlank()) return false

        return installedSignatures().any { signature ->
            signature.digest("SHA-1") == expectedSha1 &&
                signature.digest("SHA-256") == expectedSha256
        }
    }

    private fun installedSignatures(): List<Signature> = runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        }
        val signingInfo = packageInfo.signingInfo ?: return@runCatching emptyList()
        val current = signingInfo.apkContentsSigners?.toList().orEmpty()
        val history = signingInfo.signingCertificateHistory?.toList().orEmpty()
        (current + history).distinctBy(Signature::toCharsString)
    }.getOrDefault(emptyList())

    private fun Signature.digest(algorithm: String): String =
        MessageDigest.getInstance(algorithm)
            .digest(toByteArray())
            .joinToString(separator = "") { byte -> "%02X".format(byte.toInt() and 0xFF) }

    private fun String.normalizedDigest(): String =
        replace(":", "").uppercase(Locale.US)

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

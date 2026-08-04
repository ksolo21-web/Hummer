package com.mystudycompanion.app.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GoogleSignInCoordinator(context: Context) {
    private val credentialManager = CredentialManager.create(context.applicationContext)

    suspend fun requestGoogleSignIn(activity: Activity, serverClientId: String): GoogleSignInPayload {
        require(serverClientId.isNotBlank()) { "Google web client ID is not configured." }
        val credential = try {
            request(activity, serverClientId, authorizedOnly = true, autoSelect = true)
        } catch (_: NoCredentialException) {
            request(activity, serverClientId, authorizedOnly = false, autoSelect = false)
        }
        val hints = runCatching { requestGoogleProfileHints(activity, credential.id) }
            .getOrDefault(GoogleProfileHints())
        return GoogleSignInPayload(idToken = credential.idToken, profileHints = hints)
    }

    suspend fun requestGoogleIdToken(activity: Activity, serverClientId: String): String =
        requestGoogleSignIn(activity, serverClientId).idToken

    suspend fun clearCredentialState() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun request(
        activity: Activity,
        serverClientId: String,
        authorizedOnly: Boolean,
        autoSelect: Boolean,
    ): GoogleIdTokenCredential {
        val googleOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setAutoSelectEnabled(autoSelect)
            .setNonce(UUID.randomUUID().toString())
            .build()
        val response = credentialManager.getCredential(
            context = activity,
            request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build(),
        )
        val credential = response.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("The selected credential was not a Google identity token.")
        }
        return GoogleIdTokenCredential.createFrom(credential.data)
    }

    private suspend fun requestGoogleProfileHints(activity: Activity, accountEmail: String): GoogleProfileHints {
        val componentActivity = activity as? ComponentActivity ?: return GoogleProfileHints()
        val client = Identity.getAuthorizationClient(activity)
        val request = AuthorizationRequest.Builder()
            .setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_BIRTHDAY),
                    Scope(SCOPE_AGE_RANGE),
                ),
            )
            .build()
        var result = client.authorize(request).awaitTask()
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent ?: return GoogleProfileHints()
            val activityResult = componentActivity.launchAuthorization(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
            ) ?: return GoogleProfileHints()
            if (activityResult.resultCode != Activity.RESULT_OK) return GoogleProfileHints()
            result = client.getAuthorizationResultFromIntent(activityResult.data ?: Intent())
        }
        val token = result.accessToken?.takeIf(String::isNotBlank) ?: return GoogleProfileHints()
        return fetchPeopleProfile(token)
    }

    private suspend fun ComponentActivity.launchAuthorization(
        request: IntentSenderRequest,
    ): androidx.activity.result.ActivityResult? = suspendCancellableCoroutine { continuation ->
        val key = "msc-google-profile-${UUID.randomUUID()}"
        lateinit var launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
        launcher = activityResultRegistry.register(
            key,
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            launcher.unregister()
            if (continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { launcher.unregister() }
        runCatching { launcher.launch(request) }
            .onFailure {
                launcher.unregister()
                if (continuation.isActive) continuation.resume(null)
            }
    }

    private suspend fun fetchPeopleProfile(accessToken: String): GoogleProfileHints = withContext(Dispatchers.IO) {
        val connection = (URL(PEOPLE_ME_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext GoogleProfileHints()
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            parsePeopleProfile(JSONObject(payload))
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePeopleProfile(payload: JSONObject): GoogleProfileHints {
        val birthdays = payload.optJSONArray("birthdays")
        var birthDateIso: String? = null
        if (birthdays != null) {
            for (index in 0 until birthdays.length()) {
                val date = birthdays.optJSONObject(index)?.optJSONObject("date") ?: continue
                val year = date.optInt("year", 0)
                val month = date.optInt("month", 0)
                val day = date.optInt("day", 0)
                if (year > 0 && month in 1..12 && day in 1..31) {
                    birthDateIso = runCatching { LocalDate.of(year, month, day).toString() }.getOrNull()
                    if (birthDateIso != null) break
                }
            }
        }
        val ageRanges = payload.optJSONArray("ageRanges")
        val ageRange = if (ageRanges != null) {
            (0 until ageRanges.length())
                .asSequence()
                .mapNotNull { ageRanges.optJSONObject(it)?.optString("ageRange")?.takeIf(String::isNotBlank) }
                .firstOrNull()
        } else null
        return GoogleProfileHints(birthDateIso = birthDateIso, googleAgeRange = ageRange)
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            val exception = task.exception
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            } else {
                continuation.resumeWithException(exception ?: IllegalStateException("Google authorization failed."))
            }
        }
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val SCOPE_BIRTHDAY = "https://www.googleapis.com/auth/user.birthday.read"
        const val SCOPE_AGE_RANGE = "https://www.googleapis.com/auth/profile.agerange.read"
        const val PEOPLE_ME_URL = "https://people.googleapis.com/v1/people/me?personFields=birthdays,ageRanges"
    }
}

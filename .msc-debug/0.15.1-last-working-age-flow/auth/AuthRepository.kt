package com.mystudycompanion.app.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.mystudycompanion.app.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

interface AuthRepository {
    val state: StateFlow<AuthState>
    val capabilities: AuthCapabilities

    suspend fun signInWithGoogle(payload: GoogleSignInPayload)
    fun signInAsPrivateOwner()
    fun signOut()
    suspend fun freshIdToken(): String?
}

class HybridAuthRepository(context: Context) : AuthRepository {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val localOwnerAllowed = BuildConfig.DEBUG || BuildConfig.LOCAL_OWNER_MODE_ALLOWED
    private val firebaseAuth: FirebaseAuth? = if (BuildConfig.FIREBASE_CONFIGURED) {
        runCatching { FirebaseAuth.getInstance() }.getOrNull()
    } else {
        null
    }
    private val listenerInstalled = AtomicBoolean(false)
    private val _state = MutableStateFlow<AuthState>(AuthState.Initializing)

    override val state: StateFlow<AuthState> = _state.asStateFlow()
    override val capabilities = AuthCapabilities(
        firebaseConfigured = firebaseAuth != null,
        googleWebClientConfigured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(),
        localOwnerModeAllowed = localOwnerAllowed,
    )

    init {
        installFirebaseListener()
        if (firebaseAuth == null) refreshFromAvailableSession()
    }

    override suspend fun signInWithGoogle(payload: GoogleSignInPayload) {
        val auth = firebaseAuth ?: error("Firebase Authentication is not configured for this build.")
        require(payload.idToken.isNotBlank()) { "Google identity token is empty." }
        val credential = GoogleAuthProvider.getCredential(payload.idToken, null)
        val result = auth.signInWithCredential(credential).awaitTask()
        val user = result.user ?: auth.currentUser ?: error("Google sign-in returned no user account.")
        storeProfileHints(user.uid, payload.profileHints)
        preferences.edit().remove(KEY_LOCAL_OWNER).apply()
        refreshFromAvailableSession()
    }

    override fun signInAsPrivateOwner() {
        check(localOwnerAllowed) { "Private owner mode is disabled for this build." }
        preferences.edit().putBoolean(KEY_LOCAL_OWNER, true).apply()
        firebaseAuth?.signOut()
        refreshFromAvailableSession()
    }

    override fun signOut() {
        preferences.edit().remove(KEY_LOCAL_OWNER).apply()
        firebaseAuth?.signOut()
        refreshFromAvailableSession()
    }

    override suspend fun freshIdToken(): String? {
        val user = firebaseAuth?.currentUser ?: return null
        return user.getIdToken(false).awaitTask().token
    }

    private fun installFirebaseListener() {
        val auth = firebaseAuth ?: return
        if (!listenerInstalled.compareAndSet(false, true)) return
        auth.addAuthStateListener { refreshFromAvailableSession() }
    }

    private fun refreshFromAvailableSession() {
        val firebaseUser = firebaseAuth?.currentUser
        _state.value = when {
            firebaseUser != null -> AuthState.SignedIn(firebaseUser.toAccount())
            localOwnerAllowed && preferences.getBoolean(KEY_LOCAL_OWNER, false) -> AuthState.SignedIn(privateOwnerAccount())
            else -> AuthState.SignedOut
        }
    }

    private fun storeProfileHints(uid: String, hints: GoogleProfileHints) {
        if (hints.birthDateIso == null && hints.googleAgeRange == null) return
        preferences.edit().apply {
            hints.birthDateIso?.let { putString("$KEY_BIRTH_DATE_PREFIX$uid", it) }
            hints.googleAgeRange?.let { putString("$KEY_AGE_RANGE_PREFIX$uid", it) }
        }.apply()
    }

    private fun profileHints(uid: String): GoogleProfileHints = GoogleProfileHints(
        birthDateIso = preferences.getString("$KEY_BIRTH_DATE_PREFIX$uid", null)?.ifBlank { null },
        googleAgeRange = preferences.getString("$KEY_AGE_RANGE_PREFIX$uid", null)?.ifBlank { null },
    )

    private fun FirebaseUser.toAccount(): UserAccount {
        val hints = profileHints(uid)
        return UserAccount(
            uid = uid,
            displayName = displayName?.trim().orEmpty().ifBlank { email?.substringBefore('@').orEmpty().ifBlank { "Family Member" } },
            email = email,
            photoUrl = photoUrl?.toString(),
            provider = AccountProvider.GOOGLE,
            householdId = null,
            householdRole = HouseholdRole.MEMBER,
            ageGroup = hints.ageGroup,
            ageSource = hints.ageSource,
        )
    }

    private fun privateOwnerAccount() = UserAccount(
        uid = PRIVATE_OWNER_UID,
        displayName = "Kaleb",
        email = null,
        photoUrl = null,
        provider = AccountProvider.PRIVATE_OWNER,
        householdId = "kaleb-family",
        householdRole = HouseholdRole.OWNER,
        ageGroup = AccountAgeGroup.ADULT,
        ageSource = AccountAgeSource.PRIVATE_OWNER,
    )

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            val exception = task.exception
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            } else {
                continuation.resumeWithException(exception ?: IllegalStateException("Authentication task failed."))
            }
        }
    }

    private companion object {
        const val PREFERENCES = "msc_auth_session"
        const val KEY_LOCAL_OWNER = "local_owner_signed_in"
        const val KEY_BIRTH_DATE_PREFIX = "google_birth_date_"
        const val KEY_AGE_RANGE_PREFIX = "google_age_range_"
        const val PRIVATE_OWNER_UID = "private-owner-alpha"
    }
}

package com.example.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val usesGoogle: Boolean
) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: email ?: "Cuenta de Helix"
}

/** El usuario cerró el selector de cuentas de Google: no es un error que mostrar. */
class SignInCancelled : Exception()

/**
 * Firebase Authentication: correo/contraseña (con restablecimiento) y Google (Credential Manager).
 * [auth] es null si Firebase no se pudo inicializar; en ese caso todo falla con un mensaje claro y la app sigue.
 */
class AuthRepository(private val auth: FirebaseAuth?) {

    private val _user = MutableStateFlow(auth?.currentUser?.toAuthUser())
    val user: StateFlow<AuthUser?> = _user

    val available: Boolean get() = auth != null

    init {
        auth?.addAuthStateListener { _user.value = it.currentUser?.toAuthUser() }
    }

    fun currentUid(): String? = auth?.currentUser?.uid

    suspend fun signIn(email: String, password: String) = guarded {
        requireAuth().signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String) = guarded {
        requireAuth().createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) = guarded {
        requireAuth().sendPasswordResetEmail(email.trim()).await()
    }

    /** Pide la cuenta de Google al sistema y la canjea por una sesión de Firebase. */
    suspend fun signInWithGoogle(activityContext: Context) = guarded {
        // default_web_client_id lo genera el plugin google-services a partir del OAuth client_type 3
        val webClientId = activityContext.getString(R.string.default_web_client_id)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelled()
        } catch (e: NoCredentialException) {
            throw AuthException("No hay ninguna cuenta de Google disponible en este teléfono. Añade una en Ajustes del sistema.", e)
        } catch (e: GetCredentialException) {
            throw AuthException("No se pudo abrir el inicio de sesión de Google (${e.type.substringAfterLast('.')}).", e)
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw AuthException("Google devolvió un tipo de credencial no esperado.")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        requireAuth().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    fun signOut() {
        auth?.signOut()
    }

    private fun requireAuth(): FirebaseAuth =
        auth ?: throw AuthException("Firebase no está disponible. Comprueba que app/google-services.json está en el proyecto.")

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SignInCancelled) {
            throw e
        } catch (e: AuthException) {
            throw e
        } catch (e: FirebaseAuthException) {
            throw AuthException(AuthErrors.messageForCode(e.errorCode), e)
        } catch (e: FirebaseNetworkException) {
            throw AuthException(AuthErrors.messageForCode("ERROR_NETWORK_REQUEST_FAILED"), e)
        } catch (e: Exception) {
            throw AuthException(e.message?.takeIf { it.isNotBlank() } ?: "Error desconocido", e)
        }
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
        usesGoogle = providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
    )
}

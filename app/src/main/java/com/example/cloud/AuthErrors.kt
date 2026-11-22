package com.example.cloud

/** Validación y mensajes de error de la cuenta, en español y sin depender de Firebase (testeable en JVM). */
object AuthValidation {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
    const val MIN_PASSWORD = 6

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Escribe tu correo"
        !EMAIL.matches(email.trim()) -> "El correo no es válido"
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "Escribe tu contraseña"
        password.length < MIN_PASSWORD -> "Mínimo $MIN_PASSWORD caracteres"
        else -> null
    }
}

object AuthErrors {
    /** Traduce el código de FirebaseAuthException (`ERROR_*`) o el código de error de la API REST. */
    fun messageForCode(code: String?): String = when (code?.uppercase()) {
        "ERROR_INVALID_EMAIL", "INVALID_EMAIL" -> "El correo no es válido."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND",
        "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND" -> "Correo o contraseña incorrectos."
        "ERROR_EMAIL_ALREADY_IN_USE", "EMAIL_EXISTS" -> "Ese correo ya tiene una cuenta. Inicia sesión o recupera tu contraseña."
        "ERROR_WEAK_PASSWORD", "WEAK_PASSWORD" -> "La contraseña es muy débil: usa al menos 6 caracteres."
        "ERROR_USER_DISABLED", "USER_DISABLED" -> "Esta cuenta está deshabilitada."
        "ERROR_TOO_MANY_REQUESTS", "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Demasiados intentos. Espera unos minutos e inténtalo de nuevo."
        "ERROR_OPERATION_NOT_ALLOWED", "OPERATION_NOT_ALLOWED" ->
            "Este método de acceso no está activado. Actívalo en Firebase → Authentication → Sign-in method."
        "ERROR_NETWORK_REQUEST_FAILED" -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            "Ese correo ya existe con otro método de acceso. Entra con el método que usaste al registrarte."
        "ERROR_INVALID_USER_TOKEN", "ERROR_USER_TOKEN_EXPIRED", "ERROR_REQUIRES_RECENT_LOGIN" ->
            "Tu sesión caducó. Vuelve a iniciar sesión."
        "ERROR_INVALID_CUSTOM_TOKEN", "ERROR_CUSTOM_TOKEN_MISMATCH" -> "No se pudo validar la cuenta de Google."
        else -> "No se pudo completar la operación${code?.let { " ($it)" }.orEmpty()}."
    }
}

/** Fallo de autenticación con un mensaje ya listo para mostrar al usuario. */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

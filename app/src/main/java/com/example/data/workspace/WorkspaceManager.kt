package com.example.data.workspace

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/** Estado del acceso al almacenamiento del dispositivo. */
enum class AccessStatus {
    /** Permiso global concedido: el agente trabaja sobre todo el almacenamiento compartido (`/sdcard`). */
    GRANTED,

    /** Falta el permiso global (Acceso a todos los archivos): archivos y shell quedan bloqueados. */
    MISSING,

    /** Android 10: no permite acceso total con este targetSdk; el agente usa la carpeta interna de la app. */
    INTERNAL_ONLY
}

data class WorkspaceInfo(
    /** Raíz que usan las herramientas ahora mismo. */
    val root: File,
    val status: AccessStatus,
    /** Rutas equivalentes a [root] que el shell también debe aceptar (`/sdcard`...). */
    val aliases: List<String>,
    val message: String?
) {
    /** Hay que pedir el permiso al usuario. */
    val needsAction: Boolean get() = status == AccessStatus.MISSING
}

/**
 * Acceso del agente al almacenamiento, estilo "gestor de archivos": un único permiso global
 * (`MANAGE_EXTERNAL_STORAGE` en Android 11+, permisos clásicos de almacenamiento en Android ≤ 9) y rutas
 * reales (`/sdcard/Download/...`). No usa Storage Access Framework ni pide carpetas concretas.
 *
 * Android 10 queda fuera: con targetSdk 36 no permite acceso total; se usa la carpeta interna con un aviso.
 */
class WorkspaceManager(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val internalRoot: File
) {

    fun supportsDirectAccess(): Boolean = directAccessSupported(Build.VERSION.SDK_INT)

    fun hasAllFilesAccess(): Boolean = when {
        // Si la consulta falla (entornos sin AppOps), se trata como no concedido: nunca se asume acceso
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        else -> false
    }

    /** Pantalla de ajustes "Acceso a todos los archivos" de esta app (Android 11+). */
    fun allFilesAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** Lista general de apps con ese acceso, por si el fabricante no admite la pantalla directa. */
    fun allFilesAccessFallbackIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    fun evaluate(): WorkspaceInfo = decide(
        supportsDirect = supportsDirectAccess(),
        granted = hasAllFilesAccess(),
        internalRoot = internalRoot,
        sharedRoot = Environment.getExternalStorageDirectory()
    )

    /**
     * Versiones anteriores usaban el selector de carpetas (SAF). Se descarta esa elección y se liberan
     * los permisos persistentes de URI que quedaran; ya no se usan.
     */
    fun cleanupLegacySaf() {
        if (prefs.contains(LEGACY_TREE_URI)) prefs.edit().remove(LEGACY_TREE_URI).apply()
        runCatching {
            context.contentResolver.persistedUriPermissions.forEach {
                context.contentResolver.releasePersistableUriPermission(
                    it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
    }

    companion object {
        // Clave nueva a propósito: quien vio el antiguo aviso de carpeta verá el nuevo diálogo global una vez.
        const val KEY_ONBOARDING_DONE = "storage_global_onboarding_done"
        const val LEGACY_TREE_URI = "workspace_tree_uri"

        /** Rutas que Android expone como alias del almacenamiento compartido principal. */
        val SHARED_ALIASES = listOf("/sdcard", "/storage/self/primary", "/storage/emulated/0", "/mnt/sdcard")

        fun directAccessSupported(sdk: Int): Boolean =
            sdk >= Build.VERSION_CODES.R || sdk <= Build.VERSION_CODES.P

        /** Lógica pura de decisión (sin Android), probada en tests JVM. */
        fun decide(supportsDirect: Boolean, granted: Boolean, internalRoot: File, sharedRoot: File): WorkspaceInfo = when {
            !supportsDirect -> WorkspaceInfo(
                root = internalRoot,
                status = AccessStatus.INTERNAL_ONLY,
                aliases = emptyList(),
                message = "Android 10 no permite acceso total al almacenamiento. El agente usa la carpeta interna de la app."
            )
            granted -> WorkspaceInfo(
                root = sharedRoot,
                status = AccessStatus.GRANTED,
                aliases = SHARED_ALIASES.filter { it != sharedRoot.path },
                message = null
            )
            else -> WorkspaceInfo(
                root = internalRoot,
                status = AccessStatus.MISSING,
                aliases = emptyList(),
                message = "Falta el permiso de almacenamiento: activa «Acceso a todos los archivos» para Helix " +
                    "en los ajustes de Android."
            )
        }
    }
}

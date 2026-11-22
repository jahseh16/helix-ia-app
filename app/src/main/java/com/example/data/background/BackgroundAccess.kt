package com.example.data.background

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Permiso para trabajar en segundo plano sin las restricciones de ahorro de batería (Doze / optimización de
 * batería). Sin él, algunos fabricantes congelan la app al ir al navegador y las operaciones largas
 * (p. ej. esperar la autorización de GitHub) se interrumpen.
 */
class BackgroundAccess(context: Context) {
    private val ctx = context.applicationContext
    private val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /** true si Android ya no aplica optimización de batería a Helix. */
    fun isUnrestricted(): Boolean = runCatching { pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true }.getOrDefault(false)

    /** Diálogo del sistema "¿Permitir que Helix se ejecute siempre en segundo plano?". */
    fun requestIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))

    /** Lista de apps con optimización de batería, por si el fabricante no admite el diálogo directo. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

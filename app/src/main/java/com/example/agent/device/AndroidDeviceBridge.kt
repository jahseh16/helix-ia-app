package com.example.agent.device

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Implementación real con PackageManager e Intents oficiales. Requiere `<queries>`/QUERY_ALL_PACKAGES en el manifiesto. */
class AndroidDeviceBridge(context: Context) : DeviceBridge {
    private val ctx = context.applicationContext
    private val pm: PackageManager = ctx.packageManager

    override fun installedApps(): List<AppInfo> {
        val launcherPackages = launcherIntentPackages()
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.getInstalledPackages(0)
        }.getOrDefault(emptyList())

        return installed.mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            if (info.packageName == ctx.packageName) return@mapNotNull null
            AppInfo(
                label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName),
                packageName = info.packageName,
                isSystem = (app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0,
                versionName = info.versionName,
                launchable = info.packageName in launcherPackages
            )
        }
    }

    private fun launcherIntentPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = runCatching {
            if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())
        return list.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    override fun launchApp(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    override fun openUri(uri: String): Boolean =
        start(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun start(intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    override fun deviceInfo(): Map<String, String> {
        val info = linkedMapOf<String, String>()
        info["Dispositivo"] = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        info["Android"] = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        bm?.let {
            val level = it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            info["Batería"] = "$level%" + if (it.isCharging) " (cargando)" else ""
        }

        runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            info["Almacenamiento"] = "${gb(stat.availableBytes)} libres de ${gb(stat.totalBytes)}"
        }

        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let { am ->
            val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
            info["RAM"] = "${gb(mem.availMem)} disponibles de ${gb(mem.totalMem)}"
        }

        info["Idioma"] = Locale.getDefault().toLanguageTag()
        info["Zona horaria"] = TimeZone.getDefault().id
        info["Fecha y hora"] = SimpleDateFormat("EEEE d 'de' MMMM yyyy, HH:mm", Locale("es")).format(Date())
        return info
    }

    private fun gb(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
}

package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Copia texto al portapapeles del sistema. Devuelve false si el sistema lo rechaza (no lanza). */
fun copyToClipboard(context: Context, label: String, text: String): Boolean = try {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    true
} catch (e: Exception) {
    false
}

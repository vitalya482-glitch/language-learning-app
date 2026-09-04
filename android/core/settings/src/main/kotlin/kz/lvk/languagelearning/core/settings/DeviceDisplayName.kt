package kz.lvk.languagelearning.core.settings

import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceDisplayName {
    fun resolve(context: Context): String {
        val systemName = runCatching {
            Settings.Global.getString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME,
            )
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (systemName != null) {
            return systemName
        }

        return Build.MODEL
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != Build.UNKNOWN }
            ?: "Android"
    }
}

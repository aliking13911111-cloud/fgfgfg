package dev.spatial.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class InstalledApp(
    val packageName: String,
    val componentName: String,
    val label: String,
    val category: String
) {
    val key: String
        get() = "$packageName/$componentName"
}

object AppDiscovery {
    fun discover(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        return resolveInfos
            .mapNotNull { resolve ->
                val activityInfo = resolve.activityInfo ?: return@mapNotNull null
                val label = resolve.loadLabel(pm).toString().trim()
                if (label.isEmpty()) return@mapNotNull null
                if (activityInfo.packageName == context.packageName) return@mapNotNull null

                InstalledApp(
                    packageName = activityInfo.packageName,
                    componentName = activityInfo.name,
                    label = label,
                    category = categorize(activityInfo.packageName)
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, app: InstalledApp): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.packageName, app.componentName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun categorize(packageName: String): String {
        val p = packageName.lowercase()
        return when {
            p.contains("game") -> "Games"
            p.contains("youtube") || p.contains("spotify") || p.contains("music") ||
                p.contains("video") || p.contains("media") || p.contains("player") -> "Media"
            p.contains("telegram") || p.contains("whatsapp") || p.contains("instagram") ||
                p.contains("messenger") || p.contains("chat") || p.contains("mail") -> "Communication"
            p.contains("calculator") || p.contains("notes") || p.contains("docs") ||
                p.contains("office") || p.contains("calendar") -> "Productivity"
            p.contains("camera") || p.contains("gallery") || p.contains("photos") -> "Media"
            p.contains("settings") || p.contains("system") -> "System"
            else -> "All Apps"
        }
    }
}

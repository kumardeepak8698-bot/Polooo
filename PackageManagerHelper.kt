package com.cloner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

object PackageManagerHelper {
    
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = mutableListOf<AppInfo>()
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val activities = pm.queryIntentActivities(intent, 0)
        for (resolveInfo in activities) {
            try {
                val pkgName = resolveInfo.activityInfo.packageName
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                apps.add(AppInfo(
                    name = pm.getApplicationLabel(appInfo).toString(),
                    packageName = pkgName,
                    version = pm.getPackageInfo(pkgName, 0)?.versionName ?: "1.0",
                    iconPath = appInfo.sourceDir
                ))
            } catch (_: Exception) {}
        }
        
        return apps.sortedBy { it.name }
    }
}

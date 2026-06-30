package com.callblocker.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.PermissionChecker

object PermissionUtils {
    fun hasContactsPermission(context: Context): Boolean {
        return PermissionChecker.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PermissionChecker.PERMISSION_GRANTED
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openCallScreeningSettings(context: Context) {
        openAppSettings(context)
    }
}

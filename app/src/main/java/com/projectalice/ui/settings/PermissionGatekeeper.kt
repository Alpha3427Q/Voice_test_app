package com.projectalice.ui.settings

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

data class PermissionSnapshot(
    val recordAudio: Boolean,
    val overlay: Boolean,
    val readPhoneState: Boolean,
    val usageStats: Boolean,
    val batteryOptimizationExempt: Boolean
) {
    val allGranted: Boolean
        get() = recordAudio && overlay && readPhoneState && usageStats && batteryOptimizationExempt
}

enum class PermissionPrompt {
    RECORD_AUDIO,
    READ_PHONE_STATE,
    OVERLAY,
    USAGE_STATS,
    BATTERY_OPTIMIZATION
}

object PermissionGatekeeper {
    fun snapshot(context: Context): PermissionSnapshot {
        return PermissionSnapshot(
            recordAudio = hasPermission(context, Manifest.permission.RECORD_AUDIO),
            overlay = Settings.canDrawOverlays(context),
            readPhoneState = hasPermission(context, Manifest.permission.READ_PHONE_STATE),
            usageStats = hasUsageStatsPermission(context),
            batteryOptimizationExempt = isIgnoringBatteryOptimizations(context)
        )
    }

    fun missingPrompts(snapshot: PermissionSnapshot): List<PermissionPrompt> {
        val prompts = mutableListOf<PermissionPrompt>()
        if (!snapshot.recordAudio) prompts += PermissionPrompt.RECORD_AUDIO
        if (!snapshot.readPhoneState) prompts += PermissionPrompt.READ_PHONE_STATE
        if (!snapshot.overlay) prompts += PermissionPrompt.OVERLAY
        if (!snapshot.usageStats) prompts += PermissionPrompt.USAGE_STATS
        if (!snapshot.batteryOptimizationExempt) prompts += PermissionPrompt.BATTERY_OPTIMIZATION
        return prompts
    }

    fun buildIntent(context: Context, prompt: PermissionPrompt): Intent? {
        return when (prompt) {
            PermissionPrompt.OVERLAY -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )

            PermissionPrompt.USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            PermissionPrompt.BATTERY_OPTIMIZATION -> Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )

            else -> null
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}

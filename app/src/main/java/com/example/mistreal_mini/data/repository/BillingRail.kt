package com.example.mistreal_mini.data.repository

import android.content.Context
import android.content.pm.InstallSourceInfo
import android.os.Build

/**
 * One app, two payment rails, chosen at runtime — not two separate builds.
 * Google Play policy requires Play Billing for digital goods when the app
 * was installed FROM the Play Store; any other install source (side-loaded
 * APK, direct web distribution) is free to use Stripe instead. This is the
 * single place that decision gets made so BillingRepository/SubscriptionViewModel
 * never have to know the detection mechanics.
 */
object BillingRail {
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun isPlayStoreInstall(context: Context): Boolean {
        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info: InstallSourceInfo = context.packageManager.getInstallSourceInfo(context.packageName)
                info.installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            null
        }
        return installer == PLAY_STORE_PACKAGE
    }
}

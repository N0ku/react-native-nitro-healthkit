package io.github.n0ku.nitrohealthkit.util

import android.annotation.SuppressLint
import android.content.Context

/**
 * Nitro hybrid objects are constructed by the JNI bridge without an Android [Context].
 * To talk to Health Connect, WorkManager and EncryptedSharedPreferences we need one
 * (the application context, specifically).
 *
 * The host React Native app is responsible for calling
 * [HealthConnectContextHolder.attach] once at startup with `context.applicationContext`.
 * This is wired automatically when the host registers the [com.margelo.nitro.packages.NitroPackagesPackage].
 *
 * Holding the application context is safe — it lives as long as the process.
 */
object HealthConnectContextHolder {
    @Volatile
    @SuppressLint("StaticFieldLeak")
    private var appContext: Context? = null

    fun attach(context: Context) {
        // Always normalise to the application context to avoid leaking activities.
        appContext = context.applicationContext
    }

    fun require(): Context =
        appContext ?: error(
            "HealthConnectContextHolder has not been initialised. " +
                "Ensure NitroPackagesPackage is registered in the host app's getPackages().",
        )

    fun peek(): Context? = appContext
}

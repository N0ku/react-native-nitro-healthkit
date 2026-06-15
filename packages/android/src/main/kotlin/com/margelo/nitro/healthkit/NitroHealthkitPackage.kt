package com.margelo.nitro.healthkit

import android.util.Log
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import io.github.n0ku.nitrohealthkit.util.HealthConnectContextHolder

/**
 * ReactPackage referenced by `packages/react-native.config.js`:
 *
 * ```js
 * android: {
 *   packageImportPath: 'import com.margelo.nitro.healthkit.NitroHealthkitPackage;',
 *   packageInstance: 'new NitroHealthkitPackage()',
 * }
 * ```
 *
 * Two responsibilities:
 *  1. Trigger [NitroHealthkitOnLoad.initializeNative] so `libNitroHealthkit.so` is loaded
 *     once. Its `JNI_OnLoad` registers the HybridObject constructor on the C++ side.
 *  2. Capture the application context for [HealthConnectContextHolder] — Health Connect,
 *     EncryptedSharedPreferences and WorkManager all need it, and the JNI-constructed
 *     module otherwise has no way to obtain it.
 *
 * No Java [NativeModule]s and no [ViewManager]s are registered: the entire JS surface
 * goes through Nitro's hybrid-object registry.
 */
class NitroHealthkitPackage : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        ensureInitialised(reactContext)
        return emptyList()
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        ensureInitialised(reactContext)
        return emptyList()
    }

    private fun ensureInitialised(reactContext: ReactApplicationContext) {
        if (initialised) return
        synchronized(lock) {
            if (initialised) return
            try {
                NitroHealthkitOnLoad.initializeNative()
                HealthConnectContextHolder.attach(reactContext.applicationContext)
                initialised = true
                Log.i(TAG, "NitroHealthkit native library initialised")
            } catch (t: Throwable) {
                // Surfacing the failure as an error log rather than rethrowing — the host app
                // can still boot without the module; calls to HealthKitModule will fail loudly
                // with the underlying cause from the Nitro layer.
                Log.e(TAG, "Failed to initialise NitroHealthkit", t)
            }
        }
    }

    companion object {
        private const val TAG = "NitroHealthkitPackage"
        private val lock = Any()

        @Volatile
        private var initialised = false
    }
}

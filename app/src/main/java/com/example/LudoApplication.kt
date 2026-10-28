package com.example

import android.app.Application
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class LudoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (t: Throwable) {
            Log.w("LudoApplication", "FirebaseApp init in Application: ${t.message}")
        }

        // Initialize Firebase Crashlytics and tag device characteristics for debugging
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.setCustomKey("device_brand", Build.BRAND ?: "unknown")
            crashlytics.setCustomKey("device_manufacturer", Build.MANUFACTURER ?: "unknown")
            crashlytics.setCustomKey("device_model", Build.MODEL ?: "unknown")
            crashlytics.setCustomKey("android_sdk", Build.VERSION.SDK_INT)
            crashlytics.setCustomKey("android_release", Build.VERSION.RELEASE ?: "unknown")
            crashlytics.log("LudoApplication initialized on ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
        } catch (t: Throwable) {
            Log.w("LudoApplication", "Crashlytics setup: ${t.message}")
        }

        // Catch uncaught exceptions to report to Crashlytics before delegating
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("LudoApplication", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                FirebaseCrashlytics.getInstance().apply {
                    setCustomKey("crashing_thread", thread.name)
                    recordException(throwable)
                    sendUnsentReports()
                }
            } catch (ignored: Throwable) {
                // Ignore any reporting failures
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

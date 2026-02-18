# Preserve source line info for production crash analysis.
-keepattributes SourceFile,LineNumberTable

# Koin modules are loaded through Kotlin metadata and runtime reflection.
-keep class org.koin.** { *; }
-keep class kotlin.Metadata { *; }

# Keep Android entry points and WorkManager workers.
-keep class * extends android.app.Service { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

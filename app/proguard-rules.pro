# WorkManager rules
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.ExtractionWorker { *; }
-keep class androidx.work.impl.foreground.SystemForegroundService { *; }

# ViewModel constructor rules
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

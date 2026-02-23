# DeepFake Shield ProGuard Rules - COMPREHENSIVE

# ==================== HILT / DAGGER ====================
# Keep all Hilt generated code and entry points
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.InstallIn class *
-keep @dagger.Module class *
-keep @dagger.hilt.EntryPoint class *
-keepclassmembers class * {
    @dagger.hilt.EntryPoint *;
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# Keep Hilt entry points defined inside classes (inner interfaces with @EntryPoint)
-keep class com.deepfakeshield.service.CallScreeningService$CallScreeningEntryPoint { *; }
-keep class com.deepfakeshield.service.DeepfakeNotificationListenerService$NotificationListenerEntryPoint { *; }

# ==================== ROOM / DATABASE ====================
-keep class com.deepfakeshield.data.entity.** { *; }
-keep class com.deepfakeshield.data.dao.** { *; }
-keep class com.deepfakeshield.data.database.** { *; }
-keep class com.deepfakeshield.data.di.** { *; }

# Keep Room TypeConverters
-keep class com.deepfakeshield.data.database.Converters { *; }

# Keep enums used by Room TypeConverters
-keep enum com.deepfakeshield.core.model.RiskSeverity { *; }
-keep enum com.deepfakeshield.core.model.ThreatType { *; }
-keep enum com.deepfakeshield.core.model.ThreatSource { *; }

# ==================== MODELS ====================
-keep class com.deepfakeshield.core.model.** { *; }
-keep class com.deepfakeshield.core.intelligence.** { *; }

# ==================== ENGINES ====================
-keep class com.deepfakeshield.core.engine.** { *; }
-keep class com.deepfakeshield.ml.engine.** { *; }
-keep class com.deepfakeshield.ml.heuristics.** { *; }

# ==================== DATA LAYER ====================
-keep class com.deepfakeshield.data.repository.** { *; }
-keep class com.deepfakeshield.data.preferences.** { *; }

# ==================== SERVICES / RECEIVERS / WORKERS ====================
-keep class com.deepfakeshield.service.** { *; }
-keep class com.deepfakeshield.receiver.** { *; }
-keep class com.deepfakeshield.widget.** { *; }
-keep class com.deepfakeshield.worker.** { *; }

# Keep WorkManager workers
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ==================== ACTIVITIES ====================
-keep class com.deepfakeshield.MainActivity { *; }
-keep class com.deepfakeshield.DeepfakeShieldApplication { *; }
-keep class com.deepfakeshield.BiometricHelper { *; }

# ==================== NAVIGATION ====================
# Keep Navigation Compose destinations
-keep class com.deepfakeshield.Screen { *; }
-keep class com.deepfakeshield.Screen$* { *; }

# ==================== FEATURE SCREENS ====================
# Keep all Composable screen functions and their containing classes
-keep class com.deepfakeshield.feature.home.** { *; }
-keep class com.deepfakeshield.feature.shield.** { *; }
-keep class com.deepfakeshield.feature.alerts.** { *; }
-keep class com.deepfakeshield.feature.vault.** { *; }
-keep class com.deepfakeshield.feature.settings.** { *; }
-keep class com.deepfakeshield.feature.education.** { *; }
-keep class com.deepfakeshield.feature.diagnostics.** { *; }
-keep class com.deepfakeshield.feature.callprotection.** { *; }
-keep class com.deepfakeshield.feature.analytics.** { *; }
-keep class com.deepfakeshield.feature.onboarding.** { *; }

# ==================== UI COMPONENTS ====================
-keep class com.deepfakeshield.core.ui.** { *; }

# ==================== TENSORFLOW LITE ====================
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.**

# ==================== ML KIT ====================
-keep class com.google.mlkit.** { *; }
-keep class com.google.mlkit.vision.face.** { *; }
-keep class com.google.mlkit.vision.facemesh.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }

# ==================== MEDIAPIPE ====================
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ==================== ML FACE ANALYZERS ====================
-keep class com.deepfakeshield.ml.face.** { *; }
-keep class com.deepfakeshield.ml.deepfake.** { *; }

# ==================== JAVAX LANG MODEL (MediaPipe/AutoValue) ====================
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8

# ==================== FIREBASE ====================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ==================== COMPOSE ====================
-dontwarn androidx.compose.**

# ==================== KOTLIN ====================
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.Metadata *;
}

# Keep data classes (needed for copy(), toString(), etc.)
-keepclassmembers class * {
    public <init>(...);
}

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==================== DATASTORE ====================
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ==================== SERIALIZATION ====================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ==================== ENUMS ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== RELEASE OPTIMIZATION ====================
# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ==================== TOR (Guardian Project) ====================
-keep class org.torproject.jni.** { *; }
-keep class net.freehaven.tor.control.** { *; }
-keepclassmembers class org.torproject.jni.TorService {
    long torConfiguration;
    int torControlFd;
}

# ==================== VPN SERVICE ====================
-keep class com.deepfakeshield.service.TorVpnService { *; }
-keep class com.deepfakeshield.service.TorVpnConsentActivity { *; }
-keep class com.deepfakeshield.service.DeepfakeTorService { *; }
-keep class com.deepfakeshield.service.EmbeddedTorManager { *; }

-optimizationpasses 5
-allowaccessmodification

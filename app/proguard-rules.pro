# Hilt / Dagger - keep generated classes from R8 stripping
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dev.abhi.zmt.Hilt_* { *; }
-keep class dev.abhi.zmt.*_GeneratedInjector { *; }
-keep class dev.abhi.zmt.*_HiltComponents { *; }

# xmlutil's serialization ServiceLoader hook isn't used since we only depend on xmlutil-core.
-dontwarn nl.adaptivity.xmlutil.util.SerializationProvider
-dontwarn nl.adaptivity.xmlutil.util.DefaultSerializationProvider

# Keep TDLib classes for JNI
-keep class org.drinkless.tdlib.** { *; }
-keep class org.drinkless.tdlib.Client { *; }
-keep class org.drinkless.tdlib.TdApi$** { *; }
-keepclassmembers class org.drinkless.tdlib.** {
    native <methods>;
    public *;
}

# Keep TDLib consumer rules
-keep class org.drinkless.tdlib.Client$ResultHandler { *; }
-keep class org.drinkless.tdlib.Client$ExceptionHandler { *; }
-keep class org.drinkless.tdlib.Client$LogMessageHandler { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclassmembers class **$$serializer {
    *** INSTANCE;
}
-keepclassmembers class ** {
    @kotlinx.serialization.Serial <fields>;
}
-keep,includedescriptorclasses class dev.abhi.zmt.**$$serializer { *; }

# Keep Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Chaquopy / Python
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# yt-dlp
-keep class org.bouncycastle.jsse.** { *; }
-dontwarn org.bouncycastle.jsse.**
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-keep class org.openjsse.** { *; }
-dontwarn org.openjsse.**

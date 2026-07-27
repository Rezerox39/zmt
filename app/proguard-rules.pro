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

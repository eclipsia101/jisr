-keep class com.dokar.quickjs.** { *; }
-dontwarn com.dokar.quickjs.**

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static boolean isLoggable(...);
}
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

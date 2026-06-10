# HoldOff ProGuard rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep Capacitor classes
-keep class com.getcapacitor.** { *; }
-keep class androidx.** { *; }
-keep class android.webkit.** { *; }

# Keep app classes
-keep class app.holdoff.polsia.** { *; }

-dontwarn org.apache.http.**
-dontwarn com.google.android.gms.**
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# R8 rule to keep alive wallpaper after app compress 😙
-keepclassmembers class **.R$drawable {
    public static <fields>;
}

# ==========================================
# CAVE ART XPOSED MODULE RULES
# ==========================================

# 1. Keep the main Xposed Module so it isn't stripped as "dead code".
# The Xposed framework looks for this exact name.
-keep class com.android.CaveArt.xposed.CaveArtXposedModule {
    public <init>(...);
}

# 2. Generic rule to protect any class extending LibXposed's module interface
-keep class * extends io.github.libxposed.api.XposedModule {
    public <init>(...);
}

# 3. Keep your custom injected Views. 
# SystemUI's ConstraintLayout relies on these properties, and renaming them 
# via minification will cause SystemUI to silently fail to display them.
-keep class com.android.CaveArt.xposed.VectorTextClock {
    public <init>(...);
    *;
}

-keep class com.android.CaveArt.xposed.IndependentDateView {
    public <init>(...);
    *;
}

-keep class com.android.CaveArt.xposed.SystemUIHider {
    *;
}

-keep class com.android.CaveArt.xposed.OverlapPreventionHelper {
    *;
}

# 4. Keep AdaptiveClockHelper since it interacts deeply with the injected views
-keep class com.android.CaveArt.AdaptiveClockHelper {
    *;
}
-keep class com.android.CaveArt.AutoFitResult {
    *;
}
-keep class com.android.CaveArt.ClockPaths {
    *;
}

# ==========================================
# SAFETY NET FOR KOTLINX SERIALIZATION
# ==========================================
# Prevents R8 from scrambling your JSON config structures (LiveWallpaperConfig) 
# and Update parsing structures (AppUpdateInfo), which breaks animations.
-keep @kotlinx.serialization.Serializable class * {
    *;
}

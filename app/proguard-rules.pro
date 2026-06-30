# Keep JavaScript Interfaces
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebKit and WebView APIs
-keep class android.webkit.** { *; }

# Keep core AndroidX lifecycle / splashscreen dependencies
-keep class androidx.core.splashscreen.** { *; }

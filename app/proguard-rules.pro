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

# Keep line numbers in release stack traces, and rename the source file to hide the original
# .kt name. Without these, a crash report from a beta tester is a list of obfuscated frames with
# no line numbers, which is unusable — and the app has no crash reporter to fall back on.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose/debug log calls from release builds.
# Log.d and Log.v often contain sensitive data (USSD responses, UPI amounts).
# -assumenosideeffects tells R8 the calls have no observable side-effects and
# removes them entirely, including string concatenation arguments.
#
# Log.i/w/e are deliberately NOT stripped: they carry the diagnostics that make a release-build
# bug report actionable. Call sites are instead responsible for keeping bank response text,
# amounts and PINs out of those messages (see UssdManager.onUssdFinalResponse).
-assumenosideeffects class android.util.Log {
    public static int v(java.lang.String, java.lang.String);
    public static int v(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int d(java.lang.String, java.lang.String);
    public static int d(java.lang.String, java.lang.String, java.lang.Throwable);
}

# SQLCipher loads its native library through JNI, so the classes R8 cannot see referenced from
# Java must survive shrinking and renaming.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# ML Kit publishes its scanner through the Firebase component registry, and the registrars are
# found reflectively by name: MlKitInitProvider hands ComponentDiscovery the
# "com.google.firebase.components:<class>" <meta-data> entries under
# MlKitComponentDiscoveryService, and each one is built with
# Class.forName(name).getDeclaredConstructor().newInstance().
#
# The rule firebase-components ships is "-keep class * implements ComponentRegistrar" with no
# member spec, so it pins the class name and lets R8 shrink the members. It did: usage.txt showed
# the no-arg constructor gone from BarcodeRegistrar, VisionCommonRegistrar and
# CommonComponentRegistrar. ComponentDiscovery catches the resulting NoSuchMethodException and
# carries on with an empty registry, so nothing fails at startup -- it fails much later, when
# BarcodeScanning.getClient() looks up a component that was never registered, gets null back
# (AbstractComponentContainer.get returns null rather than throwing) and dereferences it. That
# was "Camera unavailable (NullPointerException)", release builds only.
#
# Three classes match, so keeping their members costs nothing measurable. Everything the
# components themselves need is reachable from getComponents() once it survives.
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
    public java.util.List getComponents();
}

# Proguard rules for OmniProxy
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class io.github.aacreat.omniproxy.core.jni.** { *; }
-keep class io.github.aacreat.omniproxy.core.model.** { *; }

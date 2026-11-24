# JNI PTY
-keep class com.wusper.debarm.core.Pty {
    *;
}
-keepclassmembers class com.wusper.debarm.core.Pty {
    native <methods>;
}

# Terminal JavascriptInterface (called from xterm.js)
-keepclassmembers class com.wusper.debarm.terminal.TerminalBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.wusper.debarm.terminal.TerminalBridge { *; }

# noVNC JavascriptInterface (called from injected desktop skin)
-keepclassmembers class com.wusper.debarm.terminal.NovncBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.wusper.debarm.terminal.NovncBridge { *; }

# Deb parser + extract (reflection-free, but keep in case R8 gets aggressive)
-keep class com.wusper.debarm.core.DebParser { *; }
-keep class com.wusper.debarm.core.DebControl { *; }

# Commons compress uses service loaders / optional codecs
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn io.airlift.compress.**
-keep class org.tukaani.xz.** { *; }
-keep class io.airlift.compress.zstd.** { *; }

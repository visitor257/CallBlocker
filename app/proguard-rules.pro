# CallBlocker ProGuard Rules
-keepattributes *Annotation*
-keep class com.callblocker.app.** { *; }
-keep class * extends android.telecom.CallScreeningService { *; }

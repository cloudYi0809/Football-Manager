# 默认 ProGuard 规则
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin
-keepattributes Signature, InnerClasses, EnclosingMethod
-dontwarn kotlin.**

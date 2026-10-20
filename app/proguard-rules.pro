# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.swan.notes.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.swan.notes.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.swan.notes.data.**$$serializer { *; }

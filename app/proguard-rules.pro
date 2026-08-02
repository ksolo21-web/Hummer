# Kotlin serialization models are referenced through generated serializers.
-keepattributes *Annotation*
-keepclassmembers class com.kreativstudio.app.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.kreativstudio.app.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

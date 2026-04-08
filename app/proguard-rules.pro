# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

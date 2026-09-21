# Firestore documents are mapped by hand (DocumentSnapshot.getString(...)), so no
# reflective model binding is required. These keeps are defensive: they protect the
# model classes and enum names in case toObject() is introduced later.
-keepnames class com.siren.mobile.model.** { *; }
-keepclassmembers enum com.siren.mobile.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Firebase / Play services
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

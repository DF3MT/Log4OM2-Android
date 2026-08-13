-keep class com.log4om.android.data.model.** { *; }

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# EncryptedSharedPreferences / Tink
-dontwarn com.google.crypto.tink.**

-keep class com.mysql.** { *; }
-keep class com.mysql.cj.** { *; }
-dontwarn com.mysql.**
-dontwarn javax.naming.**
-dontwarn javax.transaction.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.**

-keep class com.log4om.android.data.model.** { *; }

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

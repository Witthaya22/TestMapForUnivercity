# Keep line numbers so release crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# --- MapLibre Native -------------------------------------------------------------------
# The renderer calls back into Java from C++ by exact class/member name, so anything the
# JNI layer touches must survive shrinking and obfuscation.
-keep class org.maplibre.android.** { *; }
-keep interface org.maplibre.android.** { *; }
-keep class org.maplibre.geojson.** { *; }
-keep class org.maplibre.turf.** { *; }
-dontwarn org.maplibre.**

# --- Gson ------------------------------------------------------------------------------
# Model classes are populated reflectively from field names.
-keep class com.google.gson.** { *; }
-keep class th.ac.kmutnb.prachin.map.data.geojson.model.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.Unsafe

# --- Room ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- NanoHTTPD -------------------------------------------------------------------------
-dontwarn org.nanohttpd.**
-keep class org.nanohttpd.** { *; }

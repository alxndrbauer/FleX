# Add project specific ProGuard rules here.

# Gson – Reflection-basierte Serialisierung
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-dontwarn sun.misc.**

# Room Entities (als JSON in Backups verwendet)
-keep class com.flex.data.local.entity.** { <fields>; }

# Backup-Datenklassen
-keep class com.flex.data.backup.** { <fields>; }

# DTOs ohne @SerializedName
-keep class com.flex.data.holidays.NagerHolidayDto { <fields>; }
-keep class com.flex.data.update.UpdateInfo { <fields>; }

# Enum-Werte (WorkLocation, DayType etc.)
-keepclassmembers enum * { *; }

# Glance Widgets & Actions (Reflection / BroadcastReceiver / ActionCallback)
-keep class com.flex.widget.** { *; }

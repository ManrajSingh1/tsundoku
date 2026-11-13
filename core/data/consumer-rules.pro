# PDFium is driven through JNI, which looks classes and fields up by name.
-keep class com.shockwave.pdfium.** { *; }
-keep class com.github.barteksc.pdfviewer.** { *; }

# WorkManager keeps its own bookkeeping in a Room 2 database and creates it by reflection.
# The rule Room 2 ships keeps that class but not its constructor, which the stricter
# shrinking in Android Gradle plugin 9 then removes: the app crashed at launch without this.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Native methods are resolved by their Java/Kotlin class and method names.
-keep class app.gbsat.emulator.NativeBridge { *; }

# Preserve source line numbers in release crash reports without keeping original filenames.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

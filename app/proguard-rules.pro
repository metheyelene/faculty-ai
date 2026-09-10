# Acadora (Faculty AI) — R8 / ProGuard rules for release builds.
# R8 handles Compose, Room (via KSP consumer rules) and Kotlin coroutines out of
# the box. Only add rules here for things that rely on reflection or lookups by
# name that R8 cannot see statically.

# Keep line numbers in stack traces (mapped through mapping.txt on deobfuscation)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotation metadata used by Room / Compose runtime reflection
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Room entities are reflectively instantiated by the generated code.
# Keep their no-arg constructors and fields.
-keepclassmembers class com.bits.facultyai.data.local.** {
    <init>();
    <fields>;
}

# If a crash happens, keep the mapping file (app/build/outputs/mapping/release/)
# and use ReTrace to deobfuscate stack traces.
-printconfiguration app/build/outputs/mapping/release/configuration.txt

// CampusUTE — Android root build.
// Toolchain note (ADR plan W4): Gradle wrapper 8.14.x; run on JDK 17-24 via
// JAVA_HOME. Never let the daemon fall back to JDK 26 from PATH.
buildscript {
    dependencies {
        // Hilt's aggregate task runs on the plugin classpath with an old
        // javapoet lacking ClassName.canonicalName(); preload the fixed one.
        classpath("com.squareup:javapoet:1.13.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// CampusUTE — Android root build.
// Toolchain note (ADR plan W4): Gradle wrapper 8.14.x; run on JDK 17-24 via
// JAVA_HOME. Never let the daemon fall back to JDK 26 from PATH.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

# CampusUTE Android

Kotlin + Jetpack Compose app (offline-first). See root README for the platform
overview and the plan for module layout:

- Phase 0: single `:app` module — toolchain proof only.
- Phase 1+: `:core:{designsystem,network,database,datastore,common,sync}` +
  `:feature:*` multi-module layout with a shared version catalog
  (`gradle/libs.versions.toml`).

Build:

```bash
# Requires JDK 17-24 (Gradle 8.14 does NOT run on JDK 26)
./gradlew :app:assembleDebug
```

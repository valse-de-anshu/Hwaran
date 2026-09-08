# Development & Contribution Guide

> **"How do I build, test, and work on Hwaran?"**

---

## 1. Prerequisites & Environment

- **JDK**: Java 17+ (Default environment uses Android Studio JBR: `/opt/android-studio/jbr`).
- **Android SDK**: `compileSdk = 37`, `minSdk = 26`, `targetSdk = 35`.
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`) and version catalogs (`libs.versions.toml`).

Before running builds, always set the correct Java Home:

```bash
export JAVA_HOME=/opt/android-studio/jbr
```

---

## 2. Common Gradle Commands

```bash
# Compile Kotlin sources and check for errors
./gradlew compileDebugKotlin

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Clean build cache
./gradlew clean
```

---

## 3. Git Workflow & Branch Strategy

### `main`
- **Stable / Production**.
- Only verified, compiling, runtime-tested code is merged here.

### `nightingale`
- **Bleeding-edge active development**.
- Experimental features, ongoing refactoring, and work-in-progress.

### Promotion Flow

```text
feature-branch / nightingale ──► Verify (assemble + test) ──► Merge to main ──► Final Verification ──► Push
```

- Preserve commit history.
- Use Conventional Commits (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`).
- **NEVER** run `git reset --hard`, `git push --force`, or destructive rebases without explicit user approval.

---

## 4. Coding Guardrails

1. **Compose = UI Only**: Composable functions should never query the database, parse files, or initiate heavy network/disk tasks directly.
2. **State in ViewModels**: ViewModels hold `StateFlow` instances representing single source of truth for the screen.
3. **Keep I/O Off Main Thread**: Always wrap database, SAF file scanning, and `BitmapFactory` operations in `withContext(Dispatchers.IO)`.
4. **Preserve User Data**:
   - Never write a destructive Room migration.
   - Never delete original user files unless explicitly requested by the user.
5. **Smallest Correct Change**: When refactoring or fixing bugs, make targeted surgical changes rather than broad, speculative rewrites.

---

## 5. Verification Checklist

Before declaring any task or feature complete:

```bash
export JAVA_HOME=/opt/android-studio/jbr
./gradlew assembleDebug
./gradlew testDebugUnitTest
git status
git diff --stat
```

Ensure 0 compilation errors and clean git staging.

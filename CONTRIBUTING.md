# Contributing to Hwaran (화란)

Thank you for your interest in contributing to **Hwaran**! We welcome contributions from developers, designers, and power users who love handcrafted, privacy-first local media software.

---

## 📜 Code of Conduct

- **Be Respectful**: Treat everyone with respect and empathy.
- **Privacy-First**: Never introduce telemetry, analytics, online trackers, or unapproved third-party network dependencies into Hwaran.
- **User Data Integrity**: Never write destructive database migrations or silent deletion code for user files.

---

## 🛠️ Getting Started

### 1. Prerequisites

- **Java Development Kit (JDK)**: JDK 17 or JDK 21 (Android Studio JBR is recommended: `/opt/android-studio/jbr`).
- **Android SDK**: `compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`.
- **IDE**: Android Studio Ladybug / Meerkat or IntelliJ IDEA with Android plugin.

### 2. Forking and Cloning

```bash
# Clone the repository
git clone https://github.com/your-username/hwaran.git
cd hwaran

# Set JAVA_HOME environment variable
export JAVA_HOME=/opt/android-studio/jbr  # or path to your JDK 17+
```

### 3. Verification Commands

Before creating a branch or opening a pull request, ensure the project builds and tests pass cleanly:

```bash
# Build the debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

---

## 🌿 Git Workflow & Branch Strategy

Hwaran uses a dual-branch trunk model:

- **`main`**: Production-ready, verified, and stable releases.
- **`nightingale`**: Bleeding-edge active development, experimental features, and ongoing refactoring.

### Pull Request Process

1. Create a feature branch off `nightingale` (or `main` for critical hotfixes):
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Make small, focused, and testable changes.
3. Follow the **Conventional Commits** specification:
   - `feat:` for new features (e.g. `feat: add bookmark support in novel reader`)
   - `fix:` for bug fixes (e.g. `fix: enable previous button in shuffle mode`)
   - `docs:` for documentation updates
   - `refactor:` for code refactoring without feature changes
   - `chore:` for build scripts, version bumps, or dependency updates
4. Verify your build locally (`./gradlew testDebugUnitTest && ./gradlew assembleDebug`).
5. Submit your Pull Request targeting `nightingale`.

---

## 📐 Architecture & Coding Guidelines

Hwaran follows **Feature-based Clean Architecture** with **Unidirectional Data Flow (UDF)**.

1. **Compose is UI Only**: Composable functions must never perform database queries, file parsing, or disk operations directly.
2. **State in ViewModels**: ViewModels hold single-source-of-truth `StateFlow<T>` states observed by UI components.
3. **Dispatchers.IO for I/O**: Heavy file reading, PDF parsing, Room queries, and bitmap generation must run on `Dispatchers.IO`.
4. **SAF / DocumentFile**: Use Android Storage Access Framework properly and respect Scoped Storage permissions.
5. **No Regressions**: Preserve existing features, user workspaces, bookmarks, and playback positions.

---

## 🐛 Reporting Bugs & Requesting Features

- **Bug Reports**: Include your Android version, device model, file format/sample causing the issue, and steps to reproduce.
- **Feature Requests**: Describe the user scenario and how the feature enhances offline media consumption.

---

## 📄 License

By contributing to Hwaran, you agree that your contributions will be licensed under the project's [Apache-2.0 License](LICENSE).

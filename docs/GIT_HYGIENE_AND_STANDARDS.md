# Git Hygiene, Workflow & Engineering Standards

**Document:** Engineering & Git Standards  
**Project:** Hwaran Media Manager  

---

## 1. Git Workflow & Branching Strategy

To prevent repository regression and the accumulation of unverified code, all development must transition from unmanaged direct commits on `master` to a structured feature branch workflow.

```
master (Stable Production Releases)
  │
  ├── develop (Active Integration Branch)
  │     ├── feature/data-layer-normalization
  │     ├── feature/unified-media-importer
  │     ├── feature/decompose-home-screen
  │     └── fix/exoplayer-lifecycle-leak
```

### Mandates:
1. **Never Commit Directly to `master`:** `master` represents releasable, verified states.
2. **Feature Branch Naming:**
   - `feature/<feature-name>` (e.g. `feature/media-import-engine`)
   - `fix/<bug-name>` (e.g. `fix/exoplayer-anr`)
   - `refactor/<module-name>` (e.g. `refactor/description-screen`)
3. **Commit Size:** Commits must be atomic and focused. Avoid massive 10,000-line omnibus commits containing mixed refactors, UI updates, and test scripts.

---

## 2. Commit Message Conventions (Conventional Commits)

Commit messages must follow the [Conventional Commits](https://www.conventionalcommits.org/) standard:

```
<type>(<scope>): <short summary>

[optional body explaining context and rationale]

[optional footer(s)]
```

### Allowed Types:
- `feat`: A new user-facing feature.
- `fix`: A bug fix.
- `refactor`: Code changes that neither fix a bug nor add a feature.
- `perf`: A code change that improves performance.
- `arch`: Architectural restructuring (e.g. package reorganizations, DI introduction).
- `docs`: Documentation updates.
- `chore`: Build script, dependencies, or tool updates.

### Examples:
- `refactor(home): extract LibraryContent and dialogs from HomeScreen`
- `fix(audio): detach listener in MusicViewModel onCleared to prevent memory leak`
- `arch(database): add foreign key constraints and indexes to ChapterEntity`
- `perf(saf): use direct DocumentsContract cursor instead of DocumentFile.listFiles`

---

## 3. Project Hygiene & File Management Rules

1. **No Scratch or Temporary Files in Git:**  
   Never commit `.orig`, `.tmp`, `patch.kt`, `test_*.kt`, or experimental Python scripts to the repository. Scratch experiments should reside in local `.gitignore`d directories or temporary locations.
2. **Preserved Backup Assets:**  
   Experimental features slated for future integration (such as `backup/payment_gateway/`) must be documented and maintained in designated staging directories, not scattered across root folders.
3. **Pre-Commit Checklist:**
   Before staging and committing changes:
   ```bash
   git status                     # Verify only intentional files are changed
   git diff --stat                # Check modification scale
   export JAVA_HOME=/opt/android-studio/jbr && ./gradlew assembleDebug  # Verification build
   ```

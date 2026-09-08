# Hwaran

## Project Identity

* Hwaran is a Kotlin Android app for managing and consuming local media.
* Package: `com.ballade.hwaran`
* Supports manga/webtoon, books/PDFs,video, music/audio, playlists, history, metadata, and locked content.
* UI: Jetpack Compose + Material 3.
* Database: Room.
* Preferences: DataStore.
* Playback: AndroidX Media3 / ExoPlayer.
* Storage: SAF / DocumentFile / DocumentsContract.
* Images: Coil.
* Build: Gradle Kotlin DSL + KSP.

## Project Direction

Hwaran is an existing app being improved incrementally.

Target direction:

```text
Feature-based architecture
+ UDF
+ Clean separation
+ DI
+ normalized Room database
+ centralized media importing
+ proper Media3 playback lifecycle
```

* Do not rewrite the project just to achieve this architecture.
* Preserve working features and user data.

## Coding Rules

* Compose = UI only.
* ViewModels = UI state/events.
* Repository/use-case layer = data and filesystem operations.
* Keep heavy I/O, scanning, database work, and bitmap processing off the main thread.
* Reuse existing systems instead of creating duplicates.
* Keep media-specific logic isolated.
* Use lifecycle-aware coroutine scopes.
* Inspect existing code before refactoring.
* Make the smallest correct change.

## Database / Storage

* Inspect existing entities and migrations before changing the database.
* Never silently delete or transform user data.
* Never use destructive migrations as a shortcut.
* Use proper relationships, foreign keys, and indexes.
* Use SAF/DocumentFile/DocumentsContract appropriately.
* Never delete the user's original files unless explicitly required.

## Git Branches

### `main`

* Stable / production.
* Only tested and verified code.

### `nightingale`

* Bleeding-edge development.
* Experimental features, refactors, unfinished work, and potentially broken changes.

Promotion:

```text
nightingale → verify → merge → main → verify → push
```

* Preserve commit history.
* Merge normally.
* Do not manually copy/recreate commits.
* Do not squash or rebase unless explicitly requested.
* Keep `nightingale` after merging.

## Git Safety

Before Git operations:

```bash
git status
git branch -avv
git log --oneline --graph --decorate --all
```

To compare branches:

```bash
git log --oneline --left-right main...nightingale
```

To see recent changes:

```bash
git log --oneline -20
git diff
git diff --stat
```

NEVER without explicit approval:

```text
git restore
git reset
git rebase
git push --force
branch deletion
history rewriting
destructive cleanup of uncommitted work
```

* Never overwrite uncommitted user work.
* Never assume branch state.
* If Git state is unexpected or conflicts appear, stop and report before modifying it.

Keep commits focused and free of unrelated changes.

## Verification

Before declaring work complete:

```bash
export JAVA_HOME=/opt/android-studio/jbr
./gradlew assembleDebug
./gradlew testDebugUnitTest
git status
git diff --stat
```

For database, ViewModel, importer, navigation, or Media3 changes, perform runtime verification when practical.

Do not claim success from compilation alone when runtime behavior matters.

## Never Commit

* `.env` / secrets / API keys / passwords / tokens
* private user data
* scratch or temporary files
* build outputs
* unnecessary large binaries

Check the current `.gitignore` before staging unfamiliar files.

## Agent Priority

1. Preserve user work and data.
2. Preserve working functionality.
3. Follow existing project architecture.
4. Make the smallest correct change.
5. Verify before declaring success.
6. Keep `main` stable and `nightingale` experimental.
7. Ask the user if the project, code, or intended behavior is unclear.
8. IF confused immediately ask user question to clarify what they means , never assume ..

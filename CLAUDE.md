# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

Lumiform Android Code Challenge: a native Kotlin Android app that fetches a hierarchical form
definition (JSON) and renders it as a structured, readable screen, with offline support backed
by a relational database.

- Original brief: `Android Code Challenge(Initial task).pdf` (repo root)
- Implementation plan & open questions: `docs/IMPLEMENTATION_PLAN.md`
- Data endpoint:
  `https://gist.githubusercontent.com/aruana-lumiform/383e1291df3e0cc1d49aeb14d45f5b94/raw/lumiform-android-test.json`

The reviewers evaluate **code quality, architecture, testing strategy, and the ability to justify
design choices** — favour clarity and well-reasoned structure over cleverness or feature count.

## Requirements checklist (from the brief)

- [ ] Fetch the JSON from the endpoint (simulated API request)
- [ ] Render the hierarchy clearly: Page → Section (nested, any depth) → Question
- [ ] Font size reflects hierarchy: Page largest; Section medium, decreasing with nesting depth;
      Question smallest
- [ ] `text` question: show `content` directly
- [ ] `image` question: load `src` at reduced size; tap opens a new screen with full-size image + `title`
- [ ] `choice` question: render `response_set.responses` as selectable options; honour
      `multiple_selection` (single-select replaces, multi-select toggles)
- [ ] Offline support: previously fetched data is shown without network, persisted in a
      **relational DB** (Room/SQLite)
- [ ] Bonus: graceful network-failure handling with fallback for poor connections
- [ ] Public GitHub repo, README with clear setup instructions, well-organised commit history

## JSON shape (as observed)

Top level is an array of pages. Every node has numeric `id` and string `type`.

| type      | fields                                                                 |
|-----------|------------------------------------------------------------------------|
| `page`    | `title`, `items[]` (sections or questions)                             |
| `section` | `title`, `items[]` (sections or questions, recursive)                  |
| `text`    | `content`                                                              |
| `image`   | `src` (URL), `title`                                                   |
| `choice`  | `content`, `response_set { id, multiple_selection, responses[] }`      |
| response  | `id`, `label`, `score` (optional — may be absent **or** `null`)         |

Parsing must be defensive: unknown `type`s must not crash the app; optional/nullable fields
must be modelled as such.

## Tech stack

- Kotlin, Jetpack Compose (Material 3), single-activity, Navigation Compose, Hilt
- Architecture: MVVM + Clean-ish layering (`data` / `domain` / `ui`), unidirectional data flow
  (`StateFlow<UiState>` from ViewModels)
- Coroutines + Flow
- Networking: Retrofit + OkHttp + kotlinx.serialization (polymorphic on `type`)
- Persistence: Room (normalised tables — the brief explicitly asks for a relational DB;
  do **not** store the raw JSON blob as the source of truth)
- Images: Coil (with disk cache so images also work offline once loaded)
- DI: Hilt
- Testing: JUnit 5 (Jupiter) by default; JUnit 4 only where Android tooling requires it (Robolectric,
  Compose UI, instrumented), run through the JUnit 5 Vintage engine. kotlinx-coroutines-test, Turbine, MockK, MockWebServer, Room in-memory DB
  (Robolectric or instrumented), Compose UI tests
- Build: Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)

- Background sync: WorkManager (`HiltWorkerFactory`), periodic + on-reconnect
- Security: certificate pinning via Network Security Config (CA-level SPKI pins + backup + expiration)
- minSdk 26; light/dark theme following system; adaptive layout via `WindowSizeClass`

Confirmed product decisions: selections persisted in Room (SSOT); single-select can be cleared by
re-tapping; unknown item types render an "update the app" card (never crash); all pages in one
LazyColumn; fetch on launch + pull-to-refresh + retry. Still-open items are listed in
`docs/IMPLEMENTATION_PLAN.md` → "Pending decisions".

## Architecture rules

- **Single source of truth = database.** UI observes Room via Flow; network refresh writes to
  the DB. This gives offline support and the "fallback for poor connections" for free.
- Layer boundaries:
  - `data/remote` — Retrofit API, `FormRemoteDataSource`, network DTOs (serialization annotations live only here)
  - `data/local` — Room entities, DAOs, database, `FormLocalDataSource`
  - `data/repository` — `OfflineFirstFormRepository`: maps DTO → entity → domain, owns sync/merge logic
  - `domain` — pure Kotlin models (sealed `FormItem` hierarchy), `FormRepository` interface,
    use cases (ViewModels call use cases, never repositories directly); no Android imports
  - `ui` — Compose screens, ViewModels, UI state; renders a flattened list of tree nodes with depth
- Mappers are pure functions and unit-tested.
- No Android framework types in `domain`. No DTOs or entities leaking into `ui`.
- Prefer a `LazyColumn` over a flattened tree (node + depth) rather than recursive nested
  composables, so long forms stay performant.
- Hierarchy typography lives in one place (a small `HierarchyTypography`/depth→style mapping),
  not scattered `fontSize` literals.

## Conventions

- Kotlin official code style; no wildcard imports.
- Keep comments sparse; explain *why*, not *what*.
- Strings in `strings.xml`; no hardcoded user-facing text in composables.
- Every user-visible state is explicit: Loading, Content (+ "showing cached data" / stale
  indicator), Empty, Error (with retry).

## Commits

- Small, focused commits that tell the story of the build (setup → models → network → DB →
  repository → UI → tests → README). Conventional-commit style prefixes (`feat:`, `fix:`,
  `test:`, `chore:`, `docs:`, `refactor:`).
- Never commit secrets, `local.properties`, or build outputs.

## Modules

- `:app` — Compose UI, ViewModels, navigation, Hilt entry points (package `com.lumiform.formviewer`)
- `:core:data` — Room (and later Retrofit), data sources, repository impls (package `…core.data`)
- `:core:domain` — pure Kotlin/JVM; added once network + DB exist (planned)

## Commands

No `JAVA_HOME` setup needed: `gradle/gradle-daemon-jvm.properties` makes Gradle run the build on
JDK 21 and download it automatically if it is missing.

```bash
./gradlew :app:assembleDebug                 # build
./gradlew installDebug                       # install on device/emulator
./gradlew testDebugUnitTest                  # JVM + Robolectric tests (all modules)
./gradlew spotlessApply                      # format (ktlint)
./gradlew spotlessCheck lint                 # static analysis
```

Room schemas are exported to `core/data/schemas/` and must be committed; bump the DB version and
add a migration for any schema change.

## Environment

- macOS, Android SDK at `~/Library/Android/sdk`; compileSdk/targetSdk 37, minSdk 26
- AGP 9 (built-in Kotlin — do not apply `org.jetbrains.kotlin.android`), Kotlin 2.4, KSP
- Robolectric tests need the `--add-exports/--add-opens` JVM args set in `core/data/build.gradle.kts`
- Use the Gradle wrapper (`./gradlew`), not the system `gradle`.

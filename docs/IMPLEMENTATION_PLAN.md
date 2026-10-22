# Implementation Plan — Lumiform Android Code Challenge

Status: **v6. Milestones 1–8 (setup, network, domain, database, repository, background sync, form screen, choice questions) are implemented; 7–8 await review.**

### Decisions recorded after v2

- Score: modelled as a sealed type in the domain (e.g. `Score.Points(n)` / `Score.NotScored`) so it
  can gain meaning later; stored as `score INTEGER NULL`.
- Item ids: hidden by default; a Settings screen has a switch to show them on demand.
- Images: lazy loading + prefetch on unmetered networks. The image disk cache will live in
  `filesDir` rather than `cacheDir`, because the system may clear `cacheDir` under storage pressure.
- Image viewer: as proposed in Q7.
- Multi → single-select change on sync: **all** answers of that question are cleared.
- Modules: three (`:app`, `:core:domain`, `:core:data`). Domain is built after network + DB.
- WorkManager periodic sync every 30 min.
- CI, Macrobenchmark and similar tooling are deferred to the end.
- Still open: Q16 (b) answer-upload option (1) vs (2); PDF in the public repo (Q19).

Legend inside section 1: your answers are kept verbatim; my replies are marked **→ Claude:**.

---

## 0. Pending decisions (summary)

| #  | Topic | My recommendation |
|----|-------|-------------------|
| 4  | Show item ids? | Hide by default; show them only in `debug` builds behind a toggle |
| 6  | Pre-download images? | Prefetch images into Coil's disk cache during the background sync, on unmetered networks only |
| 13 | Module split | `:app` + `:core:domain` (pure Kotlin) + `:core:data` |
| 16 | Merge strategy / answers upload | Diff-based upsert + cascade reconcile now. Model answers with a `sync_state` column, put the upload behind an interface with a fake implementation (there's no real endpoint) |
| 17 | Certificate pinning | Network Security Config `<pin-set>` with backup pins and an expiration date |
| 18 | Testing scope | Full list in section 1, Q18. Includes Roborazzi screenshot tests across screen sizes |
| 7  | Image viewer | A real navigation destination that *looks* like a pop-up (shared-element transition) |
| A2 | "Repositories for network and DB" | Name them `FormRemoteDataSource` / `FormLocalDataSource` behind one `FormRepository` |

---

## 1. Open questions

### Product / behaviour

1. **Persist choice selections?** The brief only says "respect `multiple_selection` when handling
   taps". Should selections survive app restart / be stored in the DB, or is in-memory
   (survives rotation via ViewModel/SavedState) enough?
   Selections should survive app restart, and be stored in DB. We should have room as a single source of truth which should be updated on automatic, and manual sync.

   **→ Claude:** Agreed. Concretely:
   - *Automatic sync* happens in three cases: on app start, through a periodic WorkManager job,
     and when connectivity comes back after being offline.
   - *Manual sync* happens through pull-to-refresh and the Retry button.
   - Every path goes through the same `SyncFormUseCase` → `FormRepository.sync()`, so behaviour is
     identical whichever way a sync starts. The UI only ever reads Room.
   - A tap on a selection writes straight to Room. The UI updates from the Room Flow, not from
     local ViewModel state.

2. **Can a single-select choice be de-selected** by tapping the selected option again, or must it
   behave strictly like a radio button?
   Tap again to clear — the mock shows "none selected" as a valid state

   **→ Claude:** 👍 That's the decision.

3. **Show `score`?** The example screenshot shows score badges (`1`, `2`) on responses. Display
   them when present?
   Seems like score it's like a value for a response, so sometimes some answers might hold this value

   **→ Claude:** Agreed. In Lumiform-style inspection forms, `score` is the points an answer
   contributes to the audit result, and "Not applicable" has `null` (it isn't scored). Plan:
   - Show a small badge on the chip when `score != null`. Show nothing when it's absent or `null`.
   - Stored as `score INTEGER NULL`. Absent and `null` are treated the same.
   - *Optional extra, not in the brief:* show the selected score under each choice question,
     e.g. "Score: 2". Cheap to do, but it's scope creep, so tell me if you want it. My default is
     to leave it out.
We can store score in sealed class/interface, so later it may come to use

4. **Show item ids** (`#7`, `#10` badges like in the mock)?
I don't think item id for each json object is good idea, if you can find who are doing something like that too, I want to know why before I will decide.

   **→ Claude:** I agree that end users shouldn't see them. Here's who shows ids and why:
   - **The brief's own mock does.** It looks like a developer/debug rendering of the JSON. The
     ids let a *reviewer* check at a glance that every node landed in the right place in the
     hierarchy, e.g. "Subsection 1.1 #7 is under Chapter 1 #5". That's the reason they're there.
   - **JSON tree viewers and admin/form-builder tools** (CMS editors, Postman-style viewers,
     back-office tools) show ids so people can refer to a specific node in a bug report or
     support ticket.
   - **End-user form apps don't** show internal ids. Inspection and form apps like Lumiform,
     SafetyCulture and Google Forms show question *numbering* (1, 1.1, 1.2), if anything, and
     never database keys. Ids mean nothing to a user and leak implementation details.

   Even when they're hidden, the ids are essential internally. They are the LazyColumn item
   keys (stable scrolling and animations), Room primary keys, the key that links a selection to
   its response, the navigation argument for the image screen, and what lets a sync be *merged*
   rather than wiped (see Q16).

   Hide them by default, add settings button, to show ids on demand, it'll be radio switch.

   **Your call.**

5. **Refresh UX**: auto-fetch on launch only, or also pull-to-refresh / retry button?
   Yes, all of them

   **→ Claude:** 👍 All three. A refresh that fails while cached data is on screen shows a
   snackbar and keeps the content visible. A failure with an empty DB shows a full-screen error
   with Retry.

6. **Offline images**: is it enough that images previously loaded are served from Coil's disk
   cache, or must images be explicitly pre-downloaded when the JSON is synced?
   Coil disk cache + a placeholder/error state
   What's are pros and cons for predownload? Answer before I'll decide

   **→ Claude:**

   | | Pre-download on sync | Lazy (Coil cache on first view) |
   |---|---|---|
   | Offline guarantee | ✅ Every image works offline after one sync, even ones never scrolled to. This matters for inspection apps, where people sync on Wi-Fi and then work in basements or warehouses with no signal | ❌ Only images the user has already seen work offline |
   | Data usage | ❌ Downloads images the user may never open. Bad on metered or roaming connections | ✅ Only what's viewed |
   | Storage | ❌ Grows with the size of the form. Needs a size limit and eviction policy | ✅ Bounded by Coil's LRU cache |
   | Sync speed on poor connection | ❌ A heavier sync. Image downloads compete with the JSON request | ✅ JSON sync stays tiny and fast |
   | Reliability | ⚠️ If images go into Coil's LRU cache, they *can still be evicted*. A hard guarantee needs our own file storage, a `local_path` column and cleanup of images no longer in the form, which is noticeably more code | ✅ Simple |
   | Complexity / testing | ❌ Download state, partial failures, cleanup | ✅ Almost none |

   *Recommendation (middle ground):* since we're adding WorkManager anyway (Q17), the sync worker
   can **prefetch** image URLs into Coil's disk cache with `ImageLoader.enqueue`. This only runs
   when the network is **unmetered**. That's about 15 lines, gives most of the offline benefit
   without spending mobile data, and needs no download-state tracking. The README will mention
   the eviction caveat and note the "own file storage" approach as a production follow-up.
   **Your call: lazy only, or lazy + unmetered prefetch?**
   Lazy + unmetered prefetch. Also I heard that it's better to store images on disk than in cache, because system can clear cache.

7. **Full-screen image screen**: plain display, or pinch-to-zoom expected?
   When we click, it's pops up, and shows image. Also we can do double tap, pinch to zoom, and pinch out makes it goes away.

   **→ Claude:** The brief says "open a **new screen**", so I'll make it a real Navigation
   destination (`image/{itemId}`). It will still *feel* like a pop-up thanks to a shared-element
   transition: the thumbnail expands into the full-screen image and the background scrim fades in.
   Gestures:
   - Double-tap toggles between 1× and 2.5×, zooming around the tap point.
   - Pinch to zoom (1×–5×) and pan while zoomed.
   - **Pinch-out below 1× dismisses the screen.** While the user pinches below 1×, the image
     shrinks and the scrim fades with it. Releasing below about 0.8× pops back to the list;
     releasing above that snaps back to 1×.
   - Swipe-down to dismiss when not zoomed, plus the system back gesture.
   - The title is shown in an overlay that is hidden while zoomed.

   Yes

8. **Unknown item types** (e.g. a future `"date"` question): skip silently, or render a
   "unsupported item" placeholder?
   We should show "please, update your app to latest version to see this item", and have graceful design for uknown page.

   **→ Claude:** 👍 Plan:
   - The parser maps any unknown `type` to an `Unsupported(id, type)` node instead of failing.
     Its children are still parsed if it has `items`, so a known question nested under an unknown
     container isn't lost.
   - **Unknown question/section** → a subtle inline card: an info icon plus "Please update the
     app to the latest version to see this item".
   - **Unknown top-level type** (where a page is expected) → a page-level card with the same
     message and an "Update" button that opens the Play Store listing. The button is hidden if
     the store isn't available.
   - The raw `type` is stored, so after an app update the next sync renders the item properly.

9. **Pages**: render all pages in one scrolling list (like the mock), or one page per
   tab/pager?
   Single list, as I know lazycolumn does render only visible views at the time so we will not have any issues with the perfomance.

   **→ Claude:** 👍 Correct, LazyColumn composes only visible items plus a small buffer. To make
   that actually pay off, the tree is flattened into rows with stable `key = id` and a
   `contentType` per row kind (so composition slots get reused). Each row is a separate item,
   rather than one giant item per page. Page headers can be `stickyHeader`s, which shows the
   current page while scrolling.

10. **Theme**: the mock is dark. Support both light/dark (system), or dark only?
    Yes, both, folowing system

    **→ Claude:** 👍

### Technical

11. **UI toolkit** → Jetpack Compose. 👍
12. **DI** → Hilt. 👍
13. **Module structure**
Let's built single module for now. But we can separate some data into other module, to show our ability, propose what usually are done for this.

    **→ Claude:** The usual reference is Google's *Now in Android* structure:
    `:app`, `:core:model`, `:core:domain`, `:core:data`, `:core:network`, `:core:database`,
    `:core:designsystem`, `:core:testing`, `:feature:*`, plus `build-logic` convention plugins.
    That's the right target for a real product, but more than ten modules for one screen would
    look like over-engineering to a reviewer.

    *Recommendation: three modules*, which shows the important property (dependency inversion
    enforced by the build) at a small cost:
    ```
    :app          Android app — UI (Compose), ViewModels, navigation, Hilt wiring, WorkManager
    :core:domain  pure Kotlin/JVM (no Android!) — models, repository interfaces, use cases
    :core:data    Android library — Retrofit, Room, data sources, repository impls, mappers
    ```
    Dependencies: `app → domain`, `app → data` (only for DI wiring), `data → domain`.
    `:core:domain` being a JVM module means the compiler *guarantees* that no Android types leak
    into business logic, and its tests run fastest. The README will explain how this grows into
    the Now-in-Android layout. Shared Gradle config stays in the version catalog; convention
    plugins aren't worth it for three modules. I like it

14. **minSdk** → 26. targetSdk/compileSdk = latest stable. 👍
15. **Database schema** → fully normalised. 👍
16. **Sync strategy on refresh**
    Yes. But what if we will be able to send something to server, and change answer options, we should also handle that case. When we already answered on some question, but newer information came. We should be also able to merge data.

    **→ Claude:** Good point, and it changes my earlier proposal. A "delete everything and
    reinsert" sync would cascade-delete the user's answers, so we'll **merge** instead:

    **a) Merging the form definition (now).** In one transaction:
    1. Upsert every incoming item, response set and response by `id`.
    2. Delete items and responses whose ids aren't in the payload. FK `ON DELETE CASCADE` then
       removes the answers that pointed at them. That's correct: the option no longer exists.
    3. Reconcile the answers that are left:
       - The response moved to a different question → drop it. FK to `(response_set_id,
         response_id)` handles this.
       - The question changed from multi-select to single-select and has more than one selection
         → I think here it's better to remove selection if something was changed like this.
       - The question changed type (choice → text) → its response set is gone, so the cascade
         clears the answers.
       - Labels or scores changed → nothing to do. Answers reference ids, so they follow the
         new label automatically.

   

    **b) Two-way sync of answers (designed for, partly built).** The brief's endpoint is read-only,
    so there's no server to send answers to. I'd design the schema to be ready now:
    - `answer(response_id, response_set_id, selected_at, sync_state[SYNCED|PENDING_UPSERT|PENDING_DELETE], server_updated_at)`
    - A local tap writes `PENDING_*` (outbox pattern). An `UploadAnswersWorker` pushes pending
      rows through an `AnswersRemoteDataSource` interface.
    - When server answers arrive:
      - local `PENDING_*` rows win when `selected_at > server_updated_at`;
      - otherwise the server wins (last-writer-wins per question);
      - `SYNCED` rows always take the server value.
    - The merge function is a pure function `merge(local, remote, definition) → result`, fully
      unit-tested with a table of conflict cases.

    For the upload, I'd ship the interface plus a **fake implementation** that pretends to
    succeed, clearly labelled in code and README, rather than invent a server contract. **Your
    call: (1) build all of the above with the fake upload, or (2) build (a) plus only the schema
    and merge function from (b), and describe the uploader in the README?** I lean towards (2).
    It shows the thinking without shipping pretend networking.

17. **Poor-connection fallback (bonus)**
    Background sync via workmanager should bethere too.
    Also we might need certificate pinning mechanism

    **→ Claude:**
    - **WorkManager:** a unique `PeriodicWorkRequest` (every 30m, `NetworkType.CONNECTED`,
      exponential backoff) plus a one-time sync enqueued when the app goes from offline to
      online. Hilt-injected via `HiltWorkerFactory`. 👍
    - **Certificate pinning:** this can be done, with a caveat for the reviewer conversation. The
      endpoint is GitHub's `gist.githubusercontent.com`, and images come from
      `raw.githubusercontent.com`. GitHub and Fastly rotate certificates without telling us, so
      **pinning the leaf certificate would eventually brick the app**. Plan:
      - Use `res/xml/network_security_config.xml` with a `<pin-set expiration="…">` for both
        hosts. It pins the SPKI hashes of the **intermediate/root CA**, not the leaf, plus at
        least one **backup pin**.
      - After the expiration date the OS falls back to normal validation instead of breaking.
      - I prefer Network Security Config over OkHttp's `CertificatePinner` because it's
        declarative and covers *all* traffic (Retrofit **and** Coil) in one place.
      - Debug builds get `<debug-overrides>` so proxies like Charles still work during
        development.
      - A test with MockWebServer + `HeldCertificate` checks that a wrong pin is rejected (via
        OkHttp `CertificatePinner` in a JVM test, since NSC is enforced by the platform).
      - The README explains how to rotate pins.
    - Also: an OkHttp HTTP cache, timeouts (connect 10s, read 20s), and retries with exponential
      backoff and jitter (3 attempts, retrying only IO errors and 5xx/408/429). There's also an
      "Offline – showing data from 14:32" banner and a stale-data indicator.

18. **Testing depth**
    We should cover everything, propose what we can do before

    **→ Claude:** Proposed test pyramid. Everything marked JVM runs in `./gradlew test` without
    an emulator, which is fast and CI-friendly.

    | Layer | What | Tooling | Runs on |
    |---|---|---|---|
    | Parsing | Real JSON fixture; edge cases: missing/null `score`, unknown type (nested and top-level), empty `items`, missing optional fields, malformed JSON | kotlinx.serialization, JUnit | JVM |
    | Mappers / flattening | DTO→domain→entity round trip; tree flatten keeps order and depth | JUnit | JVM |
    | Domain | Selection reducer (single/multi/toggle-off), merge/reconcile function (conflict table), use cases | JUnit, fakes | JVM (pure Kotlin module) |
    | Network | Success, 4xx/5xx, timeout, malformed body, retry/backoff policy, HTTP cache, cert-pin rejection | MockWebServer, `HeldCertificate` | JVM |
    | Database | DAO ordering, upsert/delete-missing merge, cascades, answer persistence, multi→single reconcile | Room in-memory | JVM (Robolectric) |
    | DB migrations | Exported schemas; migration test scaffold for v1→v2 | `MigrationTestHelper` | instrumented |
    | Repository | Offline-first: cache + network success/fail/empty, sync state emission | fakes + in-memory DB | JVM |
    | ViewModel | UI state transitions, refresh errors with/without cache, selection events | Turbine, coroutines-test | JVM |
    | WorkManager | Worker success/retry/failure, unique periodic scheduling and constraints | `work-testing`, `TestListenableWorkerBuilder` | JVM (Robolectric) |
    | Compose UI | Each row type renders; single/multi taps; unsupported-item card; loading/empty/error/offline states; navigation to image viewer and back | `compose-ui-test` + Hilt test modules | JVM (Robolectric) |
    | Screenshots | Light/dark × font scale 1.0/1.5/2.0 × devices (small phone 360dp, Pixel-size phone, foldable, tablet, landscape) | **Roborazzi** | JVM |
    | Accessibility | Touch targets, contrast, content descriptions, selection semantics | ATF checks via Roborazzi/Espresso | JVM |
    | End-to-end | Launch → content → network off → relaunch → cached content and answers still there | Hilt + MockWebServer | instrumented |
    | Static analysis | Lint, ktlint (via spotless); detekt deferred | Gradle | CI |
    | Coverage | Report plus a soft threshold on `domain`/`data` | Kover | CI |

    Let's for now leave information below, such as CI, and Macrobenchmark, we'll leave it for the end.
    **CI (GitHub Actions):** a PR job runs spotless, lint, all JVM tests, a Roborazzi
    verify and a Kover report. A separate job runs the instrumented tests on an emulator
    (`reactivecircus/android-emulator-runner`); it's slower, so it runs on `main` only.

    Out of scope unless you want it: Macrobenchmark and Baseline Profiles (scroll jank), and
    Maestro flows.


19. **GitHub**
    It's already attached to repo

    **→ Claude:** Remote confirmed: `git@github.com:Beretta299/LumiformTestAssignment.git`. Note:
    the PDF is currently **untracked**, not committed. I'll commit it with the docs unless you'd
    rather keep the company's brief out of a public repo.

### Additional comments from you (outside the numbered questions)

**A1. Ids "just act like a number for the item"** → answered in Q4. They're the stable identity
of each node, and every key, merge and navigation path depends on them.

**A2. "We should also have usecases. Repositories for network and db layers."**

**→ Claude:** 👍 Use cases, yes. On "repositories for network and DB": Google's architecture guide
(and most reviewers) call the per-source classes **data sources** and keep a single
**repository** that coordinates them. That repository is where offline-first logic lives. I'd
follow that naming so the structure reads as expected:

```
:core:domain
  model/           Page, Section, TextQuestion, ImageQuestion, ChoiceQuestion, Unsupported, …
  repository/      FormRepository (interface)
  usecase/         ObserveFormUseCase, SyncFormUseCase, ToggleResponseUseCase,
                   GetImageQuestionUseCase
:core:data
  remote/          FormApi (Retrofit), FormRemoteDataSource, DTOs
  local/           FormDatabase, DAOs, entities, FormLocalDataSource
  repository/      OfflineFirstFormRepository (implements FormRepository)
  mapper/          DTO ↔ domain ↔ entity
```

`ToggleResponseUseCase` holds the single/multi-select rule, so that business rule lives in the
domain layer and not in the UI. Use cases that would only forward a call are kept anyway, for a
consistent ViewModel → use case → repository flow. The README will note this trade-off.

**A3. Screen sizes:** "We need to optimize it onto different screen sizes (most popular ones)"

**→ Claude:**
- Use `WindowSizeClass`.
  - **Compact** (phones, 360–430dp): full width, 16dp gutters, indentation capped at 3 levels,
    and long chip rows wrap with `FlowRow`.
  - **Medium/Expanded** (foldables, tablets, landscape): content centred with a max width of
    about 720dp, so lines don't run too long, and larger image thumbnails.
- Typography uses `sp`, so it respects the user's font scale. It's tested at 2.0× (Q18).
- Edge-to-edge with proper insets, and display-cutout aware.
- Verified with Roborazzi screenshots on these profiles: small phone (360×640dp), Pixel 8
  (412×915), foldable inner screen (673×841), tablet (1280×800), and phone landscape.

---

## 2. Architecture overview

```
:app ─ Compose UI ──observes──▶ ViewModel (StateFlow<FormUiState>)
                                   │ calls
                                   ▼
:core:domain              Use cases ──▶ FormRepository (interface)
                                              ▲ implements
:core:data                 OfflineFirstFormRepository
                       ┌──────────────┴───────────────┐
             FormLocalDataSource (Room, SSOT)   FormRemoteDataSource (Retrofit)
                       ▲                               │
                       └──── merge in one transaction ◀┘

Sync triggers: app start · pull-to-refresh · Retry · connectivity regained · WorkManager periodic
```

### Domain model

```kotlin
sealed interface FormItem { val id: Long }
data class Page(id, title, children: List<FormItem>)
data class Section(id, title, children: List<FormItem>)
sealed interface Question : FormItem
data class TextQuestion(id, content)
data class ImageQuestion(id, src, title)
data class ChoiceQuestion(id, content, responseSet: ResponseSet, selectedResponseIds: Set<Long>)
data class ResponseSet(id, multipleSelection: Boolean, responses: List<Response>)
data class Response(id, label, score: Score)        // Score.Points(value) | Score.NotScored
data class Unsupported(id, rawType: String, children: List<FormItem>) : FormContainer
// Page, Section and Unsupported share `FormContainer { children }`; display text is nullable.
```

### Database schema (Room)

| table            | columns |
|------------------|---------|
| `form_item`      | `id` PK, `parent_id` FK→form_item ON DELETE CASCADE (null for top level), `sort_index`, `type` (raw string), `title`, `content`, `image_src` |
| `response_set`   | `id` PK, `item_id` FK→form_item CASCADE (unique), `multiple_selection` |
| `response`       | `id` PK, `response_set_id` FK CASCADE, `sort_index`, `label`, `score` NULL |
| `answer`         | `response_id` PK FK→response CASCADE, `response_set_id`, `selected_at`, `sync_state`, `server_updated_at` NULL |
| `sync_metadata`  | `key` PK, `last_synced_at`, `last_error` NULL |

Indices on every FK column. Schemas exported to `core/data/schemas/` for migration tests.

### Typography mapping

| node                 | style (M3) |
|----------------------|------------|
| Page                 | `headlineMedium` (largest) |
| Section depth 0      | `titleLarge` |
| Section depth 1      | `titleMedium` |
| Section depth ≥ 2    | `titleSmall` (floor, never smaller than question text) |
| Question / response  | `bodyMedium` (smallest) |

---

## 3. Milestones (each ≈ one or a few commits)

Order changed: domain now comes after network and database.

1. ✅ **Project setup:** Gradle KTS, version catalog, `:app` + `:core:data`, Compose, Hilt,
   spotless (ktlint), `.gitignore`, JUnit 5 platform (+ Vintage for Robolectric), empty
   `MainActivity` (`setContent {}`). Theme, colours and screens come with the UI milestone; detekt
   is deferred.
   (CI deferred to milestone 11; `:core:domain` is created in milestone 3.)
2. ✅ **Network:** DTOs on kotlinx's built-in sealed polymorphism (`@SerialName` per type, so a new
   type is one new class) with an `Unsupported` default deserializer, Retrofit, OkHttp
   (timeouts, cache), retry with backoff, network security config with pins.
   kotlinx-serialization stays on 1.11.0: with 1.12.0, Android Studio (bundled Kotlin 2.3.20) flags the
   generated serializers as `InternalSerializationApi` opt-in errors, although the build is clean. Errors surface as a sealed
   `FormFetchException`: `Connectivity` and transient `Http` codes are retried; `MalformedResponse`
   (incl. an empty 2xx), client `Http` codes and `Insecure` (certificate or pin rejected, so possibly
   intercepted) are not. A `callTimeout` of 15 s bounds a refresh at ≈ 47 s in the worst case
   (3 attempts + backoff) while cached data stays on screen.
   `ConnectionSecurityInterceptor` (on the shared client, so images are covered too; it implements the
   domain's `ConnectionSecurityRepository`) exposes `isCompromised`: set on any certificate rejection, cleared by the next response that
   arrives over a verified TLS connection. The UI milestone adds a non-dismissable "connection not
   secure" blocker with Retry on top of it; that blocker is also how to demo pinning with Charles
   (a release build, or `overridePins="false"` in the debug overrides).
   *Tests:* JVM tests for fixture parsing, retry policy, MockWebServer (incl. HTTP cache) and
   certificate rejection over real TLS (okhttp-tls), plus an NSC config guard test in `:app`.
   Deviations from the plan:
   - **Retries use coroutine `delay` in `RetryPolicy`, not an OkHttp interceptor.** The wait is
     cancellable, testable with virtual time, and doesn't block OkHttp threads.
   - **Pins live in the Network Security Config, enforced by the platform**, so a JVM test guards
     the NSC itself (cleartext off, both hosts pinned, expiration and a backup pin on every
     pin-set). OkHttp's `CertificatePinner` appears only in tests, to simulate a pin rejection for
     the monitor and the `Insecure` mapping.
     GitHub's content hosts now use Let's Encrypt, so the pins are ISRG Root X1 / Root YR (in the
     live chain), with ISRG Root X2, DigiCert and USERTrust roots as backups.
   - **The request sends `Cache-Control: max-age=0`, not `no-cache`.** OkHttp skips the conditional
     (ETag) request on `no-cache` and always downloads the full body; `max-age=0` revalidates and
     gets a 304 for an unchanged form. Covered by a MockWebServer test.
   - **A known item type missing required structure (ids, a choice's response set, a response's
     label) rejects the whole payload.** The DB merge deletes items missing from the snapshot, so a
     partial parse would wipe the user's answers; failing keeps the cached form intact. Unknown
     types still fall back to `Unsupported`.
3. ✅ **Domain:** `:core:domain` module, models (incl. sealed `Score`), repository interface, use
   cases, selection rule. *Tests:* 15 pure JVM tests (selection rule table, every use case over a fake).
   Decisions:
   - **Pure Kotlin/JVM module** (`org.jetbrains.kotlin.jvm`), so the build itself rejects Android
     imports. Use cases are `@Inject` classes via `javax.inject`, the only non-Kotlin dependency.
   - **The selection rule is a pure function** (`toggleSelection`): multi-select toggles; single-select
     replaces, and re-tapping the selected response clears it (also when an inconsistent stored state
     has several).
   - **`FormRepository.updateSelection(responseSetId, transform)`** takes the rule as a function the
     repository applies inside a DB transaction to the *stored* response set and selection, so the
     rule still lives in the domain but never acts on a stale screen: two quick taps, or a tap racing
     a sync. The stored `multipleSelection` wins over the shown one, a tap on a response the sync
     removed is ignored, a removed set is a no-op, and ids outside the set are never written (an
     answer for a deleted response would violate its foreign key and crash).
   - **`SyncError` is an enum** (Offline, Server, InvalidData, Insecure): the UI only needs the kind;
     HTTP codes and causes stay in the data layer. `sync()` returns a `SyncResult` and never throws
     for I/O, so every trigger handles failures the same way.
   - **Display text is nullable** in the domain, because the payload doesn't guarantee it; the UI
     decides how to render a missing title.
   - **Time is `kotlin.time.Instant`/`Clock`** (stable since Kotlin 2.3), so tests inject a fake clock.
   - **`ConnectionSecurityRepository`** is a domain interface; the network layer's interceptor
     implements it, replacing the former `ConnectionSecurityMonitor`.
4. ✅ **Database:** entities, DAOs, transactional merge sync, answers, schema export.
   *Tests:* 21 Robolectric DAO tests.
5. ✅ **Repository + data sources:** offline-first sync, sync state, last-synced time.
   *Tests:* 14 repository tests (fake remote + in-memory Room via Robolectric + Turbine), 19 mapper
   tests (pure JVM), 1 extra DAO test.
   Decisions:
   - **The form is read with one `@Transaction` Room query over `@Relation`s** (`StoredFormItem` →
     response set → responses + answers), not by combining four table Flows: combined Flows re-query
     independently after a sync and can briefly emit new items with old responses. Answers waiting
     for a server delete are filtered in the mapper, matching `AnswerDao.observeAnswers`.
   - **Mappers are pure functions:** the DTO tree is flattened declaratively (a sequence of
     item + parent + position), and the rows are rebuilt into the domain tree sorted by position,
     so they don't depend on query order. Unknown types and a choice without a response set become
     `Unsupported`; `score` `Int?` becomes `Score`.
   - **A successful sync merges the form and records `lastSyncedAt` in one transaction.** A failed
     one records only the mapped `SyncError` and keeps the cached form and the last success time;
     the next success clears the error. The error is stored by a stable string, like `AnswerSyncState`;
     an unknown stored value is dropped rather than crashing.
   - **Selection writes are a diff:** only added responses are inserted and removed ones deleted, so a
     kept answer's `selected_at` and outbox state survive. Removal is a hard delete, since there is no
     upload yet (Q16 option 2).
   - **The repository and data sources are stateless and unscoped**; Room serialises the writes.
     Two overlapping syncs simply merge twice, which is idempotent.
   - `SyncMetadataDao` gained a suspend `get`, so a failure can keep the last success time inside
     the transaction without collecting a Flow there.
6. ✅ **Background sync:** HiltWorker, periodic and on-reconnect scheduling, image prefetch.
   *Tests:* 18 tests: scheduler, both workers (work-testing + Robolectric), the real Hilt application
   (schedules on start; Hilt builds every worker), the prefetch use case and the Coil image
   repository (MockWebServer, Robolectric native graphics to decode).
   Decisions:
   - **WorkManager is initialised on demand with `HiltWorkerFactory`**: the app implements
     `Configuration.Provider`, and the manifest removes the default `WorkManagerInitializer`.
     The Application injects `SyncScheduler` as `dagger.Lazy`: creating it creates WorkManager, which
     reads the injected factory, so it must not depend on field injection order.
   - **`SyncScheduler` (in `:app`) holds the whole policy.** On every app start it enqueues:
     - a unique periodic sync every 30 min on any network. `UPDATE`, not `KEEP`: it keeps the schedule
       across restarts but lets a new app version change the interval or constraints of installed
       work. Its first run is one interval later, since the one-time sync below covers the launch;
     - a unique one-time sync on any network (`KEEP`). WorkManager holds it until a network is
       available, so an app started offline syncs as soon as the connection is back, with no
       `ConnectivityManager` callback of our own. A connection lost and regained while the app runs
       is covered by the periodic sync and pull-to-refresh. The form screen should rely on this work
       for the launch sync and call `SyncFormUseCase` directly only for pull-to-refresh and Retry.
   - **`SyncWorker` maps `SyncResult`:** success → `success()` and schedules the image prefetch;
     `Offline`/`Server` → `retry()` with exponential backoff; `InvalidData`/`Insecure` → `failure()`,
     since retrying can't fix them (the periodic run still tries again later).
   - **Image prefetch is its own unique one-time work on `UNMETERED`**, enqueued after each successful
     background sync (`KEEP`: a pending prefetch reads the form when it runs). It goes through a
     domain use case (`PrefetchFormImagesUseCase` → `ImageRepository`), so the worker knows nothing
     of Coil. Up to 3 attempts, so an image that is gone from the server isn't retried forever.
   - **One Coil `ImageLoader` singleton in `:core:data`** reuses the app's OkHttp client, so pinning
     and the security interceptor cover images (its HTTP cache is dropped; Coil keeps its own). Its
     disk cache is `filesDir/image_cache` (50 MB), which the system never clears. Prefetching
     decodes at 1 px with the memory cache off: the disk cache keeps the downloaded bytes anyway.
     Cached images aren't revalidated with the server (Coil 3's default), on purpose for offline
     use; the trade-off is that an image replaced at the same URL stays stale until evicted.
   - Retries use WorkManager's default backoff (exponential from 30 s); nothing needed tuning.
   - *For milestone 7:* thumbnails must load through this Hilt `ImageLoader` (register it as Coil's
     singleton via `SingletonImageLoader.Factory`, or inject it); Coil's default loader would cache
     in `cacheDir` and bypass the shared client's security interceptor.
   - Versions: WorkManager 2.12.0, androidx.hilt 1.4.0, Coil 3.6.3.
7. ✅ **Form screen:** ViewModel, flattened LazyColumn, hierarchy typography, timeline rail, states,
   pull-to-refresh, offline status, unsupported-item cards, adaptive layout.
   *Tests:* 13 ViewModel tests, 4 row/summary tests, 4 screen-state tests, 11 Compose UI tests (Robolectric),
   2 connectivity tests, 1 use case test.
   Decisions:
   - **One `FormUiState`** with a sealed `FormState` (Loading, Empty, Error, Content) plus flags for
     refreshing, offline, an insecure connection and a one-off refresh failure. A stored form always wins:
     a failed sync is a full-screen error only while nothing is stored, otherwise a snackbar.
   - **The launch sync is left to the background work.** The screen calls `SyncFormUseCase` only for
     pull-to-refresh and Retry, plus one case: a first launch offline with nothing stored, where the work
     would wait for a network and leave the screen loading instead of offering Retry.
   - **Connectivity is a domain `ConnectivityRepository`**, backed by the default-network callback. Online
     means *validated*, as JobScheduler requires for WorkManager's `CONNECTED`; with plain `INTERNET` a
     captive-portal network kept the launch work waiting while the screen thought it was online. It drives
     the header's offline status ("Offline · saved 5 minutes ago") and the first-launch rule.
   - **Rows are flattened in `:app`** (`toRows()`, pure) on `Dispatchers.Default` via an injected
     dispatcher, in a flow shared by the screen state and the launch check. Pages don't add depth; sections
     and unknown containers do. Rows carry ids as LazyColumn keys and a `contentType` per kind.
   - **Indentation:** nested rows indent 16 dp per level beside the rail, capped at 3 levels on compact widths
     and 6 from the medium width class, where content is also centred at 720 dp.
   - **State hoisting:** `FormScreen` is stateless apart from purely visual animation state. Scroll position,
     header collapse, pull-to-refresh, snackbars, the played entrance and the brief "back online" notice live in
     a `FormScreenState` holder (`rememberFormScreenState()`, saved across rotation); the store link comes in as
     an `onUpdateApp` callback.
   - **Insecure connection** is a full-screen, non-dismissable blocker over everything, with Retry.
   - **Thumbnails go through the Hilt `ImageLoader`**: the application implements
     `SingletonImageLoader.Factory`. Placeholder, error and tap-to-open come with milestone 9.
   - A platform `NoActionBar` window theme (light/night) removes the default action bar and the start-up
     flash; colours come from the Compose theme.
   - Espresso is pinned to 3.7.0: Compose's ui-test pulls 3.5, which calls an `InputManager` method newer
     SDKs removed, so every Robolectric click failed.
   **UI direction "Field" (chosen after two rounds of mockups; stock Material looked like a component demo).**
   - Own design language on top of Material 3 Expressive: an ink-on-paper palette with one signal colour
     (`FormColors`, mapped onto a Material scheme only for the stock components still used), bundled variable
     fonts (Space Grotesk for headings and numbers, Inter for text; SIL OFL, licences in `assets/licenses`).
     References: Typeform (one calm focus), Jetsnack (own colour system in Compose), segmented controls with a
     sliding thumb, Lumiform/SafetyCulture numbering and scores, Linear/Things palettes.
   - Material3 is pinned to `1.5.0-beta02` for `MaterialExpressiveTheme`, `MotionScheme.expressive()` (every spring)
     and `LoadingIndicator`; `1.4.0` keeps them internal. Accepted for a test assignment.
   - **Header:** an ink card with answered count, a rolling score and one progress segment per choice question,
     plus sync status (offline with a pulsing dot, "back online", last update) and refresh. A custom nested-scroll
     connection collapses it into a slim bar with the current page's title; it sits between the list and
     pull-to-refresh, so a pull at the top expands the header before refreshing. Only the visible layout of the
     two is composed for touch and accessibility.
   - **Rows:** outline numbers per page ("2", "2.1"); each page child's subtree hangs on a timeline rail whose node
     fills with a spring once its choice question is answered. A group's root section is its heading, off the rail.
   - Rows on screen at first load rise in with a stagger (read once per row, saved across rotation);
     `animateItem()` animates rows a sync adds or removes.
8. ✅ **Choice questions:** sliding selector and check tiles with points, single/multi via use case, persisted.
   *Tests:* UI taps (selection shown, tap reports question + response), ViewModel → use case → repository.
   - Single select is a track with one thumb that springs to the chosen answer (measured option bounds, drawn
     behind the options); short answers (≤ 4, ≤ 10 characters, like Yes / No / N/A) sit side by side. Multi select
     is a grid of tiles that pop a check. Points show as "+2" and light up once they count. Taps tick haptically.
   - Images are full width with the title as the question line above.
   - The selection comes only from Room; a tap goes to `ToggleResponseUseCase`.
   - Options have the radio-button role inside a `selectableGroup`, tiles the checkbox role.
   - Points show only when `score` is set, with a "Score N" content description.
9. **Image viewer:** thumbnail with placeholder/error, shared-element nav, zoom/pan, double-tap,
   pinch-out and swipe-down dismiss. *Tests:* nav and gesture UI tests.
10. **Screenshots and accessibility:** Roborazzi matrix, ATF checks, fixes.
11. **CI finalisation + README:** setup, architecture diagram, decisions and trade-offs
    (including ids, prefetch, pinning, modules), testing strategy, screenshots, future work.

---

## 4. Risks / notes

- `score` can be absent or explicitly `null`. Modelled as `Int? = null`.
- Image `src` URLs are external (raw.githubusercontent.com). On load failure show an error
  fallback; show a placeholder while loading or when `src` is missing.
- Ids are assumed unique across all items, while response ids are a separate id space. This
  holds for the sample. If the assumption breaks, the PKs need to become composite. Worth a note
  in the README.
  Seems like ids are just acts like number for the item. After I'll get answer for why it's here, I'll answere there. Id's will be shown only on request, so reviewer can observe it. 
  **→ Claude:** see Q4 / A1.
- Certificate pins on third-party (GitHub) hosts can break when they rotate. That's mitigated
  with CA-level pins, backup pins and `expiration`.
- Deeply nested sections: indentation is capped. Layout is tuned for the device profiles listed
  in A3.

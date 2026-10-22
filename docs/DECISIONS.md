# Design decisions

The choices behind the app, with the reason for each and what it costs. The README has the overview; this is the
detail.

## Product

**Answers are stored in the database and survive a restart.** A tap writes to Room, and the screen shows what Room
returns, never a copy held in the ViewModel. So a restart, a rotation or a sync can't show a stale selection.

**Single select can be cleared** by tapping the selected answer again. "No answer" is a valid state in an inspection
form.

**Scores show as "+2" badges** when a response has one. A missing and a `null` score both mean "not scored", modelled
as a sealed `Score` (`Points` / `NotScored`) so scoring rules can grow later.

**Item ids are hidden by default.** They mean nothing to someone filling in a form; outline numbers (`2.1`) give a
readable reference instead. A "Show item ids" setting shows them, for checking the rendering against the JSON. The
setting lives in Preferences DataStore, not Room: it isn't form data and shouldn't share the form's migrations.

**Unknown item types never crash the app.** They become an "update the app" card, and known questions nested inside
them still render. Their raw type is stored, so an updated app renders them after the next sync. A top-level unknown
item also gets an "Update" button that opens the store listing.

**All pages in one list.** A `LazyColumn` only composes what is on screen, so one list costs nothing extra and keeps
scrolling simple.

## Architecture

**Three modules: `:app`, `:core:domain`, `:core:data`.** The domain is a plain Kotlin/JVM module, so the build itself
rejects Android types in models, repository interfaces and use cases. Now in Android's dozen modules would be
over-engineering for one screen; this split shows the same dependency rule at a fraction of the cost.

**One repository, two data sources.** `OfflineFirstFormRepository` coordinates `FormRemoteDataSource` (Retrofit) and
`FormLocalDataSource` (Room), following Google's architecture guide naming.

**ViewModels only call use cases**, even ones that just forward a call, so every screen follows the same path. The
selection rule (`toggleSelection`) is a pure domain function.

**The tree is flattened into rows** (item + depth) off the main thread and shown in one `LazyColumn` with stable keys
and content types, instead of nested composables.

## Data and sync

**Room is the single source of truth.** Every sync writes to it and the UI only observes it, which gives offline
support and the poor-connection fallback for free.

**Normalised tables:** `form_item` (a self-referencing tree), `response_set`, `response`, `answer`, `sync_metadata`.
The brief asks for a relational database, and foreign keys keep answers consistent with the form.

**A sync merges instead of replacing.** Deleting and reinserting the form would cascade-delete every answer. The merge
upserts by id and sweeps rows missing from the payload in one transaction. A removed response takes its answer with it;
an answer whose response moved to another question is dropped; a question that switched from multi to single select
loses all its answers.

**A known item missing required fields rejects the whole payload.** A partial parse followed by a merge would delete
items and answers; failing keeps the cached form intact.

**The form is read with one `@Transaction` query** over Room relations, so a screen can never see a sync half-applied.

**Selection changes are applied inside a transaction to the stored response set**, not to what the screen showed.
Two quick taps, or a tap racing a sync, can't write an answer for a response that no longer exists.

**Answers carry an upload state** (`sync_state`) as an outbox, ready for an endpoint that sends answers back. The
brief's endpoint is read-only, so nothing is uploaded yet.

**Background sync with WorkManager:** a one-time sync on every start, held until a network is available, plus a
periodic sync every 30 minutes. Pull-to-refresh and Retry call the same use case.

## Network and security

**Retries with exponential backoff and jitter** for transient failures only (no connection, 408, 429, 5xx), using a
coroutine `delay` so the wait is cancellable and testable with virtual time.

**`Cache-Control: max-age=0`** makes OkHttp revalidate with the ETag and get a 304 for an unchanged form; `no-cache`
would download the full body every time.

**Certificate pinning in the Network Security Config**, on CA keys with backup pins and an expiry date. It covers
Retrofit and Coil in one place, survives GitHub rotating its leaf certificates, and falls back to normal validation
after expiry instead of breaking old installs. A rejected certificate shows a blocking "connection not secure" screen.

## Images

**Thumbnails are decoded at the size they are drawn**; the viewer loads the original for zooming and shows the cached
thumbnail while it loads.

**Lazy loading plus a prefetch on unmetered networks** after each background sync, so images work offline without
spending mobile data. The disk cache lives in `filesDir`, which the system doesn't clear. It is still an LRU cache, so a
hard offline guarantee would need our own file storage.

**The image viewer is a real navigation destination** that feels like a pop-up: a
shared-element transition, double-tap and pinch zoom, and pinch-out or swipe to close. The gesture rules are pure
functions, tested without a device.

## UI

**An own visual language on Material 3 Expressive:** an ink-on-paper palette with one accent colour and bundled fonts
(Space Grotesk, Inter). Stock Material looked like a component demo. Material 3 is on a beta for the Expressive APIs.

**Font size follows the hierarchy** in one place (`HierarchyTypography`): page largest, sections shrinking with depth,
questions smallest.

**Adaptive layout** from the window size class: phones get full width and capped indentation; foldables and tablets
centre the form at 720dp with larger thumbnails.

## Testing

**Everything runs on the JVM**, Robolectric included, so CI needs no emulator: parsing, network (MockWebServer and real
TLS for pinning), DAOs, repository, mappers, ViewModels, workers, Compose UI, screenshots (Roborazzi) and accessibility
checks (ATF).

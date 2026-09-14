# ACADORA — Faculty AI
## Complete Project Report: History, Features, Models & Roadmap

> Personal academic assistant for faculty — built Android-first with Kotlin + Jetpack Compose,
> local-first architecture (Room), optional cloud sync (Firebase), and an on-device AI assistant.
> Current release: **v1.6.0** (versionCode 15) · 83 Kotlin source files · 89 unit tests green.

---

## 1. HOW THE PROJECT STARTED

The project began as **"Faculty AI — personal academic assistant"** (commit `8e597a7 — Initial
release`), a single-user Android app where a faculty member manages their own teaching life:
timetable, attendance, students, notes, tasks, and an AI assistant.

The first releases focused on making the core loops real:

| Phase | Milestone | What landed |
|---|---|---|
| v1.0 | `8e597a7` | Initial release: timetable, attendance, students, notes, AI assistant |
| v1.1 | `d5f2bc1`, `f38159c` | **Release readiness**: R8 minification, signed release builds, home-screen **widgets**, calendar view, Play Store assets + privacy policy |
| v1.1 | `308a73b` | Personalization, year/section rosters, **Excel import**, CI with signed APK + test annotations |
| v1.2 | `2a21657`, `39a5871` | **Design overhaul part 1**: "Liquid Glass" translucent design layer over the Kinetic Typography system; real `RenderEffect` blur in the dock, extended glass system |

From v1.3 onward the project went through a series of production-hardening passes —
every version below was built, installed on a physical device, and verified before release:

| Version | Milestone | Highlights |
|---|---|---|
| v1.3.0 | `82ff568` | Stabilization baseline |
| v1.3.4 | `7ca3703` | **Events with budgets** (expenses + collections + financial summary), file uploads (note attachments, event photos, receipts), ViewModel factory fixes, fluid **glass splash screen** |
| v1.4.0 | `aefd078` | **Obsidian Purple transformation** — full re-theme from the yellow accent to the premium purple identity (tokens, glass, splash, icon) |
| — | `8b8afcd` | Money math and date filters hardened behind unit tests |
| v1.5.1 | `1b67f05` | **Monthly attendance system** with Excel (.xlsx) import/export and EXCUSED status |
| v1.5.2 | `55664db` | Timetable year-mismatch fix, Excel/deep-link input hardening |
| — | `b1bdc40` | **Firebase auth layer**: email/password, email verification, Google Sign-In, guest mode |
| — | `ad42a54` | UX polish: event editor discard guard, note title placeholder fix |
| v1.6.0 | `40cb1f8`, `934c105`, `139594e` | **Firestore account sync** (timetable, students, attendance follow the account across devices) + CI/config fixes + lint-found bug fixes |

Release engineering ran in parallel from early on: tag-driven signed-release CI
(`d3c1d3d`), Renovate dependency automation, SHA-pinned GitHub Actions, and
checksums published with every GitHub Release.

---

## 2. MODELS & TECHNOLOGY USED

### 2.1 Language & UI framework
- **Kotlin 2.0.20** — 100% of the app code
- **Jetpack Compose** (BOM 2024.09.03) + **Material 3** — entire UI is declarative Compose;
  there are no XML layouts
- **Navigation Compose** for routing; **Lifecycle ViewModel + runtime-compose** for state
- A **custom design system** built on top of M3 — not a template:
  - *Kinetic Typography* (Space Grotesk-driven editorial type hierarchy)
  - *Liquid Glass* — layered translucent surfaces with `RenderEffect` backdrop blur,
    thin structural borders, soft inner highlights
  - Centralized tokens: color/theme tokens, typography tokens, glass tokens, motion tokens,
    shared components (`Glass.kt`, `Components.kt`) — screens never hardcode styling
- **Obsidian Purple identity** (current): `#0B0A10` background, `#7C5CFF` primary,
  `#A78BFA` highlight, `#F5F3FA` text — full light + dark themes, system/light/dark modes

### 2.2 Data & persistence ("backend" models)
- **Room 2.6.1** — the local database is the source of truth. 17 entities:
  `FacultyProfileEntity`, `ClassSlotEntity`, `TimetableVersionEntity`, `AcademicEventEntity`,
  `AttendanceRecordEntity`, `AttendanceEntryEntity`, `StudentEntity`, `NoteEntity`,
  `TaskEntity`, `NoteAttachmentEntity`, `MemoryEntity`, `EventEntity`, `EventPhotoEntity`,
  `EventExpenseEntity`, `EventCollectionEntity`, `PendingDeletionEntity`, `SyncMetaEntity`
- **DataStore Preferences** — settings, theme mode, onboarding state
- **Guarded schema migrations** (v1→v9) that are idempotent and preserve real user data
- **Files & media**: app-private storage for note attachments, event photos, and receipts,
  shared through `FileProvider` (no unrestricted device access)

### 2.3 Authentication & cloud
- **Firebase Authentication** (BOM 33.5.1): email/password, email verification,
  **Google Sign-In** via AndroidX **Credentials 1.3.0** + play-services-auth —
  native account chooser, minimum scopes only
- **Cloud Firestore** — account-scoped sync (see §4.3), UID-scoped security rules
  (`firestore.rules`), tombstone-based deletes, last-write-wins conflict resolution
- Account isolation: sign-out/account-switch wipes local data in one transaction;
  reset tombstones the cloud before clearing

### 2.4 AI assistant — what "model" actually powers it
The AI assistant is **fully on-device and deterministic** — it is *not* a cloud LLM call,
and no external ML model ships in the APK:

- `domain/FacultyAssistant` — a local rule/retrieval engine that answers real questions
  ("What is my next class?", "Find my notes about DSP") by querying the user's actual
  Room database, returning answers with `AnswerSource` citations
- `MemoryEntity` — the assistant learns persistent preferences and offers them back
  ("confirm/dismiss memory") instead of silently storing anything
- Phases: `IDLE → THINKING → ERROR`, with suggestion chips grounded in real data

This design means the AI works **offline**, has **zero API cost**, and can never leak
faculty data to a third-party model. (A cloud-LLM tier is on the roadmap — §7.)

### 2.5 Documents & imports
- **Apache POI** (on-device, no server) — reads/writes `.xlsx` for:
  student roster import, monthly-attendance import (wide & long layouts, header
  auto-detection + manual column mapping, preview-before-commit validation), export
- Custom parsers under `domain/` with dedicated unit-test suites

### 2.6 System integration
- **WorkManager / AlarmManager + BroadcastReceiver** (`notifications/`) — reliable local
  reminders and class notifications; permission requested only when needed
- **Glance-style home-screen widgets** (`widget/`) — today's schedule at a glance
- **Haptics** — API-guarded vibration effects with pre-29 fallback

### 2.7 Quality tooling
- **JUnit** unit tests (89 green) covering: sync policy math (LWW verdicts, watermark
  monotonicity, tombstone survival), event money/date math, monthly attendance parsing,
  Excel student parsing, auth-gate logic
- **Android Lint** — 0 errors at final state (two real bugs found & fixed by lint:
  non-functional haptics, frozen onboarding fields)
- **R8 minification** + signed release builds; **GitHub Actions** CI: tag guard
  (tag ↔ versionName), tests, signed APK, GitHub Release + checksums;
  Renovate for dependency updates; all actions SHA-pinned

---

## 3. FEATURES IMPLEMENTED (by module)

**Auth & account**
- Email/password sign-up & sign-in, email verification enforced, forgot/reset password,
  Google Sign-In (native chooser), guest mode, sign-out, account switching with data isolation

**Onboarding & profile**
- Guided onboarding (name, preferred name, designation, department, subjects), editable profile

**Dashboard & schedule**
- Personalized dashboard from real data, timetable with academic-year-aware slots and
  timetable versions, calendar view of everything, home-screen widget

**Attendance**
- Per-session attendance (present/absent/late/excused), attendance history,
  **Monthly Attendance**: filter by year/section/subject/month, per-student summary
  (working days, present, absent, %), class statistics (average/highest/lowest/shortage),
  day-by-day calendar detail, **Excel import with preview/validation/unmatched handling**,
  export

**Students**
- Roster import from Excel, year/section rosters, student profiles linked to attendance history

**Academics**
- Assignments, exams, marks — integrated with the academic calendar

**Notes**
- Rich note editor (title guard, autosave), file attachments (PDF/DOCX/PPTX/TXT/images)
  with SAF picking, preview, rename/remove, upload state machine (uploading/processing/
  saved/synced/failed), search, open/download

**Events & finances**
- Event workspace: name, date (day auto-derived), start/end time, venue, description,
  organizer, department, category, participants, notes
- Photo gallery: multi-select + camera, preview, remove, cover photo, fullscreen viewer,
  compression, per-event organization
- **Expense tracker**: expenses (title, category, amount, date, paid by, method, vendor,
  receipt image/PDF, notes) and collections (source, amount, date, method, purpose,
  reference) with custom categories
- **Financial summary**: collected / spent / balance computed live with exact integer
  money math, transaction counts, confirmation before deleting financial records

**AI assistant**
- Grounded Q&A over the user's real data with source citations, memory/preference
  learning with explicit confirm, thinking/error states, suggestion chips

**Tasks, reminders & notifications**
- Tasks with due dates, local notification scheduling, class-start reminders, timezone-aware

**Documents, research & more**
- Document vault, research section, settings (theme system/light/dark, data reset with
  cloud tombstoning, **SYNC NOW** + sync status), deep links (hardened input validation)

**Cross-cutting**
- Full light/dark/system theming, responsive layouts with WindowInsets handling,
  offline-first behavior everywhere, empty states with no demo/mock data

---

## 4. ARCHITECTURE

### 4.1 Layered structure
```
ui/                 Compose screens + ViewModels (one folder per feature)
  ├─ theme/         design tokens: colors, type, glass, motion
  ├─ components/    shared glass system, buttons, fields, haptics
  └─ <feature>/     screen + ViewModel per feature
domain/             pure logic: FacultyAssistant, parsers, formatting (unit-testable)
data/
  ├─ local/         Room: Entities, DAOs, database, migrations, SyncDao
  ├─ auth/          FirebaseAuthSource, AuthRepository (single owner of auth state)
  ├─ sync/          SyncPolicy (pure math) + SyncEngine (only Firestore caller)
  ├─ attachments/   media engine: SAF copy, MIME detection, compression
  └─ prefs/         DataStore settings
notifications/      reminders & scheduled notifications
widget/             home-screen widgets
```

### 4.2 Data flow
```
Compose screen ──collect──▶ ViewModel(StateFlow) ──▶ DAO/Repository ──▶ Room
                                                                     │ (invalidation)
                                                          SyncEngine (debounced push)
                                                                     ▼
                                            Firestore  users/{uid}/{slots,students,sessions}
```

### 4.3 Sync design (v1.6.0)
- Room stays the source of truth; Firestore is the account's cloud home
- Every synced row carries a `uuid` business key (local autoincrement IDs are
  meaningless across devices); references travel as uuids
- Deletes travel as **tombstones** (`users/{uid}/tombstones`) — every device learns of
  a delete without guessing from absence
- Conflicts: **last-write-wins on `updatedAt`**; watermarks only advance over rows
  actually written (clock skew can never swallow future writes)
- Triggers: sign-in (identity backfill + convergence), any Room write (2s debounce),
  remote tombstones (snapshot listener), manual SYNC NOW
- Rules: one island per UID, payload validation, bounded lists, append-only tombstones

---

## 5. TESTING & RELEASE PROCESS

- Every release: unit tests → Android lint → R8 signed build → install on physical device
  → manual feature walkthrough → tag `vX.Y.Z` → CI builds & publishes the signed APK
  to GitHub Releases with checksums
- CI guard verifies the git tag matches `versionName` before building
- Bugs found by the verification passes are fixed and the release is re-cut
  (e.g., v1.6.0 was rebuilt twice to ship the google-services config fix and lint fixes)

---

## 6. PROBLEMS FACED DURING DEVELOPMENT — AND HOW WE OVERCAME THEM

Every problem below was hit during real development or testing, its root cause was identified,
and its fix shipped in a release. They are grouped by the kind of problem.

### 6.1 Crashes & stability

1. **v1.3.0 crashed on launch in the field ("Acadora keeps stopping").** Opening the theme
   picker or the profile section killed the app. *Root cause:* several ViewModels were
   constructed by screens without correct factory wiring, throwing at inflation.
   *Fix:* ViewModel factory fixes shipped in v1.3.4 — plus a process change: **no version
   ships without a physical-device walkthrough.** Every release since has been installed on
   a real phone and exercised before tagging.
2. **Notes section crashed on tapping any note.** *Root cause:* a two-sided defect — the
   notes ViewModel autosaved a literal "Untitled note" row on first keystroke, and the
   editor re-seeded its title field from that stored default (`remember(note)`), compounding
   on every edit. *Fix:* a shared `DEFAULT_TITLE` constant the editor treats as empty when
   seeding; fallbacks now apply only at display sites and are never materialized into the
   database.
3. **The AI chat lost its send button.** The composer degraded to a text button behind the
   keyboard. *Fix:* a dedicated 44dp glass send button, IME send action, duplicate-submit
   protection, and an inset-aware composer that stays above the keyboard at every screen size.
4. **Silent data loss in the event editor.** Backing out of a half-filled form discarded
   everything without asking. *Fix:* a DISCARD / KEEP EDITING confirmation guard.

### 6.2 Data integrity

5. **Floating-point money.** Event budgets risked ₹0.01 drift from float math.
   *Fix:* exact integer arithmetic for all money, verified by dedicated unit tests.
6. **Attendance recorded under the wrong academic year.** Monthly queries key on
   `record.year`/`section`, but one fallback write path didn't repair the slot's year.
   *Fix:* year repair at save time, plus the v1.5.2 hardening pass.
7. **Cross-device identity: local autoincrement IDs are meaningless across devices**
   (device A's slot 5 ≠ device B's). *Fix:* every synced row carries a `uuid` business key;
   references travel as uuids; migration v8→v9 adds the columns guarded and idempotently,
   preserving all existing data.
8. **Deletes must survive offline and multi-device.** Guessing deletions from absence is
   unsafe when one device is offline. *Fix:* tombstones — a local `pending_deletion` table
   drained to `users/{uid}/tombstones` in Firestore; last-write-wins on `updatedAt`;
   watermarks only advance over rows actually written, so clock skew can never silently
   swallow future local writes.
9. **Room `@Query` cannot INSERT.** The first sync-engine draft used invalid SQL upserts.
   *Fix:* Room 2.6.1 `@Upsert` with correct REPLACE semantics.
10. **Excel files are untrusted input.** Malformed, oversized, or ambiguous sheets could
    corrupt rosters and attendance. *Fix:* preview-before-commit with unmatched/invalid/
    duplicate highlighting and explicit confirmation; a hardening pass added file-size
    limits and deep-link input validation.

### 6.3 Release engineering

11. **The published release APK shipped without Firebase config.** Two stacked causes: the
    google-services plugin's conditional apply checked the project root instead of `app/`
    (so it silently never applied locally), and `GOOGLE_SERVICES_JSON` was never set as a
    repo secret (so every CI APK would be config-less too). *Caught by byte-verifying the
    published artifact* instead of trusting a green workflow. *Fix:* `pluginManager.apply`
    on the module path + the repo secret + stale release deleted and tag re-pointed;
    artifact re-verified by checksum.
12. **Tag/version drift risk.** *Fix:* a CI guard refuses to build unless the git tag
    matches `versionName`.
13. **GitHub Release collision on workflow re-run.** *Fix:* the stale release is deleted
    and the job re-run; the release is treated as rebuildable from the tag.

### 6.4 Caught by static analysis

14. **Haptics had never worked at all.** `createPredefined()` was passed
    `HapticFeedbackConstants.CONFIRM` — the wrong constant family (it needs
    `VibrationEffect.EFFECT_*`) — inside a `runCatching` guard that hid the failure, and
    the `VIBRATE` permission was missing entirely. *Caught by Android lint;* fixed with the
    correct constants, an API-29 guard with a pre-29 fallback, and the permission.
15. **Five onboarding fields were frozen while typing.** They read `StateFlow.value` once
    in composition — a read without collection never recomposes. *Caught by lint;*
    converted to collected Compose state.

### 6.5 Environment & platform constraints

16. **Firebase provider toggles aren't settable via the admin REST surface** for standard
    projects — email/password enablement resisted full CLI automation. *Resolution:* every
    scriptable step was scripted (project, app registration, SHA-1s, config download,
    repo secrets); only two console clicks remain, documented in `docs/firebase-setup.md`.
17. **No local emulator capacity.** The dev machine hit disk exhaustion downloading a
    system image, and the physical phone dropped off USB mid-verification more than once.
    *Resolution:* pivot to the strongest available verification — full lint, the 89-test
    suite, R8 build, and artifact byte-verification — with the on-device end-to-end run
    tracked as the explicit remaining step.
18. **Dependency automation (Renovate) crashed CI repeatedly** during setup (platform,
    permissions, Node version). *Resolution:* iterated the config over several commits
    until stable; dependency updates now run weekly and grouped.

> **The meta-lesson:** every crash class above was eliminated by a *process* change, not
> just a patch — physical-device gating for releases, lint as a required gate, artifact
> byte-verification, and tag↔version guards.

---

## 7. ROADMAP

### 7.1 Frontend (UI/UX)
| Horizon | Items |
|---|---|
| Next (short) | Tablet/landscape two-pane layouts (list-detail) per screen; large-font & TalkBack audit; per-screen motion polish; interactive onboarding previews |
| Mid | Compose widgets v2 (attendance snapshot, next-class countdown); notification inbox screen; richer event gallery (captions editing, album organization); dark/light theme custom accent picker |
| Long | Multi-language UI (i18n strings extraction); Wear OS companion (next class, tasks); watch-face complication |

### 7.2 Backend / data
| Horizon | Items |
|---|---|
| Next (short) | Deploy & verify Firestore rules in production; two-device sync E2E on real accounts; App Check (Play Integrity) enforcement; encrypted export/backup of all user data |
| Mid | Cloud Functions for sensitive operations (class summaries, digest emails); FCM server-triggered notifications; conflict-resolution UI (show both versions when LWW loses data); Firestore offline tuning |
| Long | Optional end-to-end encryption of synced data (client-side keys); multi-faculty shared workspaces (sections taught by multiple faculty) with role-based rules |

### 7.3 AI assistant
| Horizon | Items |
|---|---|
| Next (short) | More grounded intents (attendance analytics questions, finance summaries per event); answer streaming UI |
| Mid | **Optional cloud-LLM tier** via an authenticated backend proxy (never an API key in the APK), strictly scoped to the user's own data with explicit consent; on-device small model (MediaPipe/MLC) as a private alternative |
| Long | Proactive assistant: daily briefing ("2 shortage alerts, internal marks due Friday"), smart timetable conflict detection, attendance-at-risk predictions from local history |

### 7.4 Product / distribution
| Horizon | Items |
|---|---|
| Next (short) | Google Play internal testing → production track; crash reporting (Firebase Crashlytics); analytics (privacy-preserving) |
| Mid | Public Play Store launch; landing page + docs site; feedback channel in-app |
| Long | iOS counterpart (Kotlin Multiplatform — business logic in `domain/` and `data/` is already platform-neutral); desktop companion for bulk data entry |

---

## 8. REACHING FULL PRODUCTION — THE FEATURE GAP LIST

Section 7 evolves the product; this is the focused list of what stands between v1.6.0 and a
fully production-grade app for real users.

### 8.1 Observability — know it broke before users say so
- **Firebase Crashlytics** — uncaught-crash and ANR reporting with release attribution.
  The highest-value single addition; today, field crashes are invisible.
- **Firebase Performance Monitoring** — app-start, screen, and sync-latency traces.
- **Remote Config** — feature flags and kill switches so a bad release degrades gracefully
  without an emergency rebuild.

### 8.2 Security & compliance — the trust layer
- **Deploy & verify `firestore.rules` in production** (console step pending) + rules unit
  tests against the emulator.
- **Firebase App Check with Play Integrity** — block non-app clients from Firestore.
- **Cloud Functions** for sensitive aggregate operations, keeping trust server-side.
- **Keystore-backed encryption** for the most sensitive local fields; SQLCipher evaluation
  for the full database.
- **Account deletion flow** — a Play Store policy requirement; must wipe cloud data via
  the existing tombstone mechanism.
- Optional **end-to-end encryption** of synced payloads (client-held keys).

### 8.3 Testing at production grade
- **Instrumented Compose UI tests** for the critical flows: auth, attendance marking,
  Excel import, sync convergence.
- **Firestore-emulator integration tests** for the sync engine — the policy math is unit
  tested, the engine's I/O is not yet.
- **Two-device E2E automation** — the definitive sync proof on real accounts.
- **Staged rollout** on the Play testing track with defined halt criteria.

### 8.4 Backend completeness
- **FCM + Cloud Functions** for server-triggered notifications (daily digests,
  cross-device reminder delivery).
- **Scheduled Firestore backups** and quota/billing alerting.
- **Minimum-supported-version handshake** — a forced-upgrade path so old clients cannot
  corrupt newer sync schemas.

### 8.5 Product & distribution
- **Play Console pipeline**: internal → closed → production tracks with Play App Signing;
  Data Safety form aligned with the privacy policy.
- **In-app feedback & bug report** (attaches diagnostics with explicit consent).
- **i18n string extraction** and the first non-English locale.
- **Tablet/landscape two-pane layouts** — the largest remaining UI gap.
- Optional **authenticated cloud-LLM proxy** for the AI tier — never an API key in the
  APK, scoped strictly to the user's own data, behind explicit consent.

---

## 9. PRESENTATION TALKING POINTS

1. **Local-first, cloud-optional.** The app is fully usable offline; Firebase sync is an
   enhancement, not a dependency. Architecture made this possible from day one.
2. **A design system, not screens.** Kinetic Typography + Liquid Glass tokens mean every
   screen is consistent by construction — a full re-theme (yellow → Obsidian Purple) was a
   token change, not a rewrite.
3. **The AI is honest.** It answers only from the user's real data with cited sources,
   works offline, costs nothing, and cannot leak data — a deliberate engineering choice.
4. **Real financial integrity.** Event money math uses exact integer arithmetic, is unit
   tested, and never silently deletes financial history.
5. **Production discipline.** 89 unit tests, lint-clean, R8, signed releases through CI
   with checksums, guarded migrations that never lose user data, and every version
   verified on a physical device before shipping.

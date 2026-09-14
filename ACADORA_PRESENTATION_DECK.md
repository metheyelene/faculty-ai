# ACADORA — Faculty AI
## Final Presentation — Slide-by-Slide Outline with Speaker Notes

> **Format:** 17 slides ≈ 15–20 minutes (60–75 seconds per slide).
> Every slide's content is drawn from `ACADORA_PROJECT_REPORT.md` — nothing here is invented.
> Slide 6 uses `ACADORA_ARCHITECTURE_DIAGRAM.md` (see note on that slide).

---

### Slide 1 — Title
**On the slide**
- **ACADORA — Faculty AI**
- *A local-first academic assistant for faculty*
- Kotlin · Jetpack Compose · Room · Firebase — **v1.6.0, shipped and signed**

**Speaker notes**
> Open with one sentence: "Acadora is a production Android app that gives faculty one private
> place for their entire teaching life — and it's finished, not a prototype." Point out it's
> real: signed releases, CI, a GitHub release page anyone can download from.

---

### Slide 2 — The Problem
**On the slide**
- Faculty life is fragmented: one app for timetable, spreadsheets for attendance,
  the gallery for event photos, chat threads for tasks
- Generic assistants don't know *your* classes, students, or deadlines
- Attendance percentages, event budgets, "what's my next class?" — all manual today

**Speaker notes**
> Ground it in a real day: marking attendance on paper, computing monthly percentages by
> hand, tracking a college fest's collections in a notebook. The gap isn't another to-do app —
> it's software that actually holds the faculty member's own data and can answer questions
> from it.

---

### Slide 3 — The Solution
**On the slide**
- One app: **timetable · attendance · students · notes · events & budgets · tasks · AI**
- **Local-first** — fully usable offline; cloud sync is optional
- **Private** — data stays on device unless the user signs in; account-scoped cloud
- **Honest AI** — answers only from the user's real data, with cited sources

**Speaker notes**
> Emphasize the differentiators: offline-first is an architectural decision, not a feature
> bolt-on; privacy shaped the AI design (next slides); and everything shown runs on real
> user data — there is no demo content anywhere in the app.

---

### Slide 4 — The Journey: v1.0 → v1.6
**On the slide**
- **v1.0–v1.1** — core loops real; release readiness (R8, signing, widgets, Excel import, CI)
- **v1.2** — "Liquid Glass" design system over Kinetic Typography
- **v1.3–v1.4** — events with budgets, file uploads, splash; **Obsidian Purple re-theme**
- **v1.5** — monthly attendance + Excel import/export
- **Auth → v1.6.0** — email + Google sign-in; **Firestore account sync**

**Speaker notes**
> 19 commits, each version built and walked through on a physical phone before tagging.
> Tease slide 12: the process got this strict *because* v1.3.0 crashed in the field —
> that failure bought the discipline that every later release shipped clean.

---

### Slide 5 — Technology & Models
**On the slide**
- **Kotlin 2.0.20** + **Jetpack Compose / Material 3** — zero XML layouts
- **Room** — 17 entities, guarded migrations v1→v9 · **DataStore** for settings
- **Firebase Auth** (email, verification, Google via Credentials API) + **Firestore**
- **Apache POI on-device** — .xlsx import/export, no server
- **AI = local rule/retrieval engine over Room** — no cloud LLM, no API keys in the APK

**Speaker notes**
> Answer the "what models did you use" question head-on and honestly: there is no external
> ML model. The assistant is a deterministic engine (`FacultyAssistant`) that queries the
> user's actual database and cites its sources — which is why it works offline, costs
> nothing, and cannot leak data. A cloud-LLM tier is roadmap, behind an authenticated proxy.

---

### Slide 6 — Architecture
**On the slide**
- *(insert the diagram — render/export from `ACADORA_ARCHITECTURE_DIAGRAM.md`)*
- **UI** — Compose screens + ViewModels · **Domain** — pure, unit-testable logic
- **Data** — Room is the single source of truth · **Sync** — the only Firestore caller
- Dependencies point one way: UI → Domain/Data; Sync observes Room

**Speaker notes**
> Walk the main flow left to right: a tap becomes a ViewModel call, a DAO write, a Room
> row; invalidation flows back as state. Then the sync lane: Room invalidation triggers a
> debounced push, and a Firestore snapshot listener pulls remote changes through the same
> policy. The one sentence to land: "Room is the source of truth — that's why offline-first
> was free and sync is an enhancement, not a dependency."

---

### Slide 7 — Design System
**On the slide**
- **Kinetic Typography** (Space Grotesk editorial hierarchy) + **Liquid Glass**
  (translucent layers, RenderEffect blur, thin borders)
- Fully centralized tokens: color, type, glass, motion — screens can't hardcode style
- Proof: v1.4's yellow → **Obsidian Purple** re-theme was a *token* change, not a rewrite

**Speaker notes**
> The re-theme is the strongest evidence the system works: an entire visual identity change
> across ~30 screens shipped as one focused release because every screen reads the same
> tokens. Light and dark themes both derive from the same source.

---

### Slide 8 — The AI Assistant
**On the slide**
- Ask real questions: *"What is my next class?" · "Find my notes about DSP"*
- Pipeline: intent → **query the user's actual Room data** → answer + **source chips**
- Learns preferences as **memories — confirmed explicitly**, never silently stored
- States: IDLE → THINKING → ERROR; suggestion chips grounded in real data

**Speaker notes**
> Three deliberate trade-offs: privacy (nothing leaves the device), cost (zero API spend),
> and availability (works in a basement classroom with no signal). When someone asks
> "why not ChatGPT?" — the answer is that a faculty member's timetable and student data
> should never be an API payload; the roadmap adds an optional cloud tier *behind an
> authenticated proxy* with explicit consent.

---

### Slide 9 — Attendance Deep-Dive
**On the slide**
- Session marking: Present / Absent / Late / **Excused** · full history
- **Monthly Attendance**: filter by year · section · subject · month
  - per-student table: working days, present, absent, **auto %**
  - class stats: average · highest · lowest · **shortage list**
  - day-by-day calendar per student
- **Excel import**: header auto-detect + manual mapping → **preview → validate → confirm**

**Speaker notes**
> The import flow is the trust story: the sheet is parsed on-device (wide and long layouts),
> unmatched and invalid rows are highlighted, and nothing is written until the faculty
> member reviews the summary and confirms. Existing records are never silently overwritten —
> conflicts are surfaced.

---

### Slide 10 — Events & Finances
**On the slide**
- Event workspace: date (**day auto-derived**), venue, times, organizer, category, photos
- Gallery: multi-select + camera, compression, cover photo, fullscreen viewer
- **Expenses & collections** with custom categories and receipt attachments
- Live summary: **collected − spent = balance** — exact integer money math, unit-tested

**Speaker notes**
> Financial integrity is a stated requirement, not an accident: all money is integer
> arithmetic (no floating-point drift), totals update live as transactions change, and
> deleting financial history always asks for confirmation. Receipts attach to expenses and
> open via FileProvider — never broad storage access.

---

### Slide 11 — Sync Across Devices
**On the slide**
- Room stays source of truth; Firestore is the account's cloud home
- **uuid** business keys — local autoincrement IDs mean nothing across devices
- Deletes travel as **tombstones** — offline devices still learn of deletions
- **Last-write-wins on `updatedAt`**; watermarks advance only over rows actually written
- Sign-out / account-switch **wipes local data in one transaction** — no cross-user leaks

**Speaker notes**
> Name the hard problems: identity (solved with uuids), deletion (solved with tombstones
> because guessing from absence is unsafe when a device is offline), and clock skew (solved
> by watermarks that only advance over confirmed writes). Security rules give each account
> a private island — one UID, one namespace, validated payloads.

---

### Slide 12 — Problems We Faced — Stability
**On the slide**
- **v1.3.0 crashed in the field** (theme picker, profile) → root cause: ViewModel factories
  → fixed, and a process change: **no release ships without a device walkthrough**
- **Notes crashed on open** → autosave materialized "Untitled note", editor re-seeded it
  → fallbacks now live only at display, never in the database
- **AI send button vanished** → rebuilt composer: 44dp target, IME action, above keyboard
- **Event editor silently discarded input** → DISCARD / KEEP EDITING guard

**Speaker notes**
> The pattern to highlight: each fix was preceded by finding the *root cause*, and the worst
> failure (the field crash) produced a permanent process change. This is where the project's
> quality discipline came from — it was earned.

---

### Slide 13 — Problems We Faced — Integrity & Release
**On the slide**
- **Floating-point money** → exact integer math + unit tests
- **Attendance under the wrong academic year** → year repaired at save time
- **Release APK shipped without Firebase config** — two stacked causes (plugin path check +
  missing CI secret) → *caught by byte-verifying the artifact*, fixed, release re-cut
- **Lint caught silent bugs**: haptics that never fired (wrong constant family, missing
  permission) and five frozen onboarding fields

**Speaker notes**
> The release-APK story is the best one: CI was green, yet the published binary was broken —
> discovered by comparing bytes of the artifact, not by trusting the pipeline. Since then:
> lint is a gate, artifacts are verified, and the tag must match the version before CI builds.

---

### Slide 14 — Quality & Release Process
**On the slide**
- **89 unit tests**: sync policy (LWW, watermarks, tombstones), money & date math,
  attendance + Excel parsing, auth-gate logic
- **Android lint: 0 errors** · **R8 minified signed builds**
- **CI**: tag ↔ versionName guard → tests → signed APK → GitHub Release **with checksums**
- Renovate dependency automation; every release device-verified

**Speaker notes**
> Frame it as a pipeline a team could inherit: a tag triggers a guarded build, the artifact
> lands on the releases page with a checksum, and the release is fully rebuildable from
> source. Nothing about shipping depends on one person's machine.

---

### Slide 15 — Roadmap
**On the slide**
- **Frontend**: tablet two-pane layouts · accessibility audit · widgets v2 · i18n
- **Backend**: rules deployed & verified · App Check · Cloud Functions + FCM · E2E encryption option
- **AI**: more grounded intents · optional cloud-LLM tier via authenticated proxy · proactive daily briefing
- **Product**: Play Store track · iOS via Kotlin Multiplatform (domain/data already platform-neutral)

**Speaker notes**
> Point out the deliberate shape: near-term items remove risk (rules, App Check, Crashlytics),
> mid-term items add reach (FCM, cloud tier), long-term items open platforms (Wear, iOS).
> The KMP claim is credible precisely because business logic already lives in `domain/` and
> `data/`, not in the UI.

---

### Slide 16 — Path to Full Production
**On the slide**
1. **Crashlytics + Performance** — today, field crashes are invisible
2. **Deploy Firestore rules + App Check (Play Integrity)** — lock the backend
3. **Instrumented UI tests + Firestore-emulator sync tests** — automate the critical flows
4. **FCM + Cloud Functions** — server-triggered digests and cross-device reminders
5. **Play pipeline** — staged rollout, Data Safety form, **account-deletion flow** (policy requirement)

**Speaker notes**
> This is the ordered punch list between v1.6.0 and a store-grade product, ranked by risk
> reduction per unit of effort. Crashlytics is first because it converts unknown field
> failures into actionable reports; the account-deletion flow is non-negotiable for Play policy.

---

### Slide 17 — Closing
**On the slide**
- **Local-first, cloud-optional** — architecture as a product decision
- **A design system, not screens** — a full re-theme was a token change
- **The AI is honest** — grounded, cited, offline, private
- **Production discipline** — tested, linted, signed, verified, shipped
- *Demo cue: dashboard → mark attendance → ask the AI → open an event budget*

**Speaker notes**
> Close on the thesis: Acadora proves a solo-scale project can hold production standards —
> real releases, real tests, real user data, no demo content. Then run the 60-second demo
> cue if time allows, ending on the AI answering a question about data the audience just
> watched you create. Repo + `v1.6.0` release page on screen as Q&A starts.

---

## Presenter checklist
- [ ] Export Slide 6 diagram: open `ACADORA_ARCHITECTURE_DIAGRAM.md` on GitHub (renders
      automatically) or paste its Mermaid into **mermaid.live** → export PNG at 2× for slides
- [ ] Have `Acadora-v1.6.0.apk` installed on a demo device (github.com/metheyelene/faculty-ai/releases)
- [ ] Seed demo data on-device beforehand (one class, five students, one event) — the app
      shows *real* data only, so an empty install shows empty states
- [ ] Backup plan without a device: screenshots of dashboard, monthly attendance table,
      event finances, and the AI answering a question

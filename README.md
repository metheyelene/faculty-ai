# Faculty AI — Personal Academic Assistant

A premium, kinetic-typography personal academic assistant for ECE faculty, built with Kotlin + Jetpack Compose. The app is designed around one principle: **it knows how you work** — everything personal on screen comes from your own profile, timetable, tasks, notes and saved memories. Nothing is faked.

**Status: builds clean, produces an installable APK at `app/build/outputs/apk/debug/app-debug.apk`.**

---

## ✨ What's Inside

| Area | Feature |
|---|---|
| **Personal identity** | Onboarding collects name/preferred name/designation/department; every screen greets and addresses the faculty personally |
| **Home dashboard** | Time-aware greeting, animated "TODAY" counters (classes / tasks / due this week), live **NEXT CLASS** card with "STARTS IN N MIN" countdown, contextual brief line, personalized shortcut grid, real upcoming + recent notes |
| **MY TIMETABLE** | Mon–Sat day selector, editorial class cards (current class highlighted in accent yellow, past classes muted), expandable actions (mark attendance / delete), conflict-checked add-class dialog |
| **Attendance** | One-tap flow from timetable or attendance screen → roster with P/A/L chips, ALL PRESENT / ALL ABSENT bulk actions, live present/absent counters, saved sessions with history |
| **Students** | Debounced search over 12 sample students, per-student detail with attendance % from real sessions |
| **MY NOTES** | Folder chips (Lectures / Meetings / Research / Lesson Plans / Personal / Ideas), autosave editor with "SAVED / SAVING..." status, SAVE TO MEMORY action |
| **MY TASKS** | **Natural-language quick add** ("remind me tomorrow 9am submit internal marks" → task + due date + reminder notification), complete/delete, due-grouping |
| **MY ASSISTANT** | Data-grounded chat: next class, day schedule, open tasks, note lookup, memory recall, lesson-plan and question drafts — every answer cites its source; honest "couldn't find" when data doesn't exist |
| **MY MEMORY** | View / edit / delete / clear-all saved memories; privacy toggles (assistant memory, AI access to notes, personalized notifications) |
| **MY SPACE** | Personal hub: profile, memory, notes, tasks, timetable, attendance, students, settings |
| **Profile** | Identity page with edit mode (name, preferred name, designation, department) |
| **Settings** | SYSTEM / LIGHT / DARK theme, greeting style, reset demo data |
| **Reminders** | Exact alarm notifications via AlarmManager, persisted in Room, scheduled on task creation |

Design language: **Kinetic Typography** — Space Grotesk, oversized uppercase display type, single acid-yellow accent `#DFE104`, sharp 0dp geometry, structural 1–2dp borders instead of shadows, flat surfaces, animated stat counters, press-scale micro-interactions, and a dark/light theme driven by centralized tokens.

---

## 🎬 90-Second Demo Script

1. **Launch → Onboarding** — "Let's set up your personal assistant." Enter name, designation, department → Continue.
2. **Subjects step** — type "DSP, Communication Systems, VLSI" → this becomes a *teaching memory*.
3. **Home** — point out the personal greeting, the animated counters, the NEXT CLASS card with live countdown ("STARTS IN 42 MIN" style), the contextual brief line.
4. **MY TIMETABLE** — tap a day, expand a class, hit **ATTENDANCE**.
5. **Attendance** — flip a few students to ABSENT with one tap, show live counters, **SAVE**.
6. **MY TASKS** — type *"remind me tomorrow 9am submit internal marks"* → ADD → it parses the date/time and schedules a real notification.
7. **MY ASSISTANT** — ask "What is my next class?" → cited answer from the real timetable. Ask "Find my notes about DSP" → pulls the actual saved note. Ask "Create a lesson plan" → draft for the next class's subject.
8. **MY MEMORY** — show the saved memories, edit one live, delete another, show the privacy toggles.
9. **Settings** — flip DARK ↔ LIGHT, entire app re-themes instantly. Show greeting style change.

---

## 🔨 Building & Running

Requirements: JDK 17+, Android SDK 35.

```bash
# Standard Gradle wrapper (after running `gradle wrapper --gradle-version 8.14.3` once)
./gradlew :app:assembleDebug

# Install on a connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

This repository bootstraps from a local Gradle 8.14.3 distribution in `.android-gradle-cache/` (gitignored) so `./gradlew` works immediately on the author's machine. `gradle.properties` pins `org.gradle.java.home` to the local Homebrew OpenJDK 21 — adjust for your machine.

The app seeds realistic sample data (11 timetable slots across 3 subjects, 12 students, 5 notes, 4 tasks, 1 memory) on first launch, so the demo works offline with zero backend configuration.

---

## 🏗 Architecture

```
app/src/main/java/com/bits/facultyai/
├── data/
│   ├── local/          # Room: entities, DAO, DB, sample data
│   ├── prefs/          # DataStore settings (theme, privacy toggles)
│   └── Seeder.kt       # First-run sample content
├── domain/             # Pure Kotlin: TimeUtils, ContextEngine, FacultyAssistant, NaturalDateParser
├── reminders/          # AlarmManager scheduler, receivers, notification channel
├── ui/
│   ├── theme/          # Kinetic design tokens: Color, Type, Tokens, Theme
│   ├── components/     # Reusable Kinetic component library
│   ├── navigation/     # NavHost, kinetic bottom navigation
│   ├── onboarding/     # Personal-assistant setup flow
│   ├── home/  timetable/  attendance/  students/
│   ├── notes/  tasks/  assistant/  memory/  more/
├── FacultyAIApp.kt
└── MainActivity.kt
```

- **MVVM** with one ViewModel per feature; `StateFlow` everywhere; Room flows auto-update the UI.
- **Domain layer is pure Kotlin** — `FacultyAssistant` and `ContextEngine` are unit-testable with no Android dependencies.
- **Personalization engine**: `ContextEngine` computes real day context (classes, next class, deadlines) from DB; `FacultyAssistant` answers questions strictly from authorized local data with source citations.

---

## 🚀 Post-Competition Roadmap

The architecture is deliberately ready for the full enterprise spec:

- **Firebase sync** — swap the seeder for Firestore repositories; collection structure mirrors the entities. The assistant's data-access interface stays identical; only the data source changes.
- **Secure AI backend** — the assistant's `respond()` is the seam for a Cloud Function proxy: client sends the same context (user, timetable, tasks) to an authenticated function that calls an LLM; keys never ship in the APK.
- **Roles & permissions** — entities already carry ownership semantics; add role fields + Firestore rules for HOD/admin/coordinator flows.
- **Audit trail** — `MemoryEntity.source` already records provenance; extend the pattern to attendance edits.
- **Rich-text editor** — the toolbar UI is in place; wire formatting spans in the editor.

---

*Built for the ECE Department, Baba Institute of Technology and Sciences.*

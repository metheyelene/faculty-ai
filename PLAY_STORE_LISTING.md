# Play Store Listing — Acadora (Faculty AI)

Everything below is ready to paste into the Google Play Console. Replace
bracketed placeholders before submitting.

## App identity

| Field | Value |
|---|---|
| App name | Acadora — Faculty Assistant (≤30 chars) |
| Package | `com.bits.facultyai` |
| Category | Education |
| Tags | Scheduling, Productivity, Academia |
| Upload artifact | `app-release.aab` (built via `./gradlew :app:bundleRelease`) |

## Store listing text

**Short description (≤80 chars):**

> Timetable, attendance, tasks & reminders — the assistant that knows your day.

**Full description:**

> Acadora is the personal academic assistant built for faculty life.
>
> It knows how you work: everything personal on screen comes from your own
> profile, timetable, tasks, notes and saved memories — nothing is faked.
>
> ● MY TIMETABLE — a clean Mon–Sat schedule with the current class highlighted
>   and conflict-checked entries.
> ● NEXT CLASS — a live "starts in N min" countdown on your home screen and
>   home-screen widgets.
> ● ATTENDANCE — one-tap roster marking with bulk actions, live counters and
>   saved session history.
> ● MY TASKS — natural-language quick add: type "remind me tomorrow 9am submit
>   internal marks" and Acadora parses the date, time and reminder for you.
> ● MY ASSISTANT — a data-grounded chat that answers strictly from your own
>   timetable, tasks and notes, with citations for every answer.
> ● MY NOTES — foldered, autosaving notes; push any note into long-term memory.
> ● MY MEMORY — view, edit and curate what the assistant remembers, with
>   privacy toggles you control.
> ● REMINDERS — exact-time notifications, rescheduled automatically after
>   reboot.
>
> Works fully offline. No account required. Your data never leaves your phone.

## Graphics checklist

- [ ] App icon 512×512 PNG (32-bit, no transparency) — export from
      `ic_launcher_foreground` over the `#DFE104` accent background
- [ ] Feature graphic 1024×500 (kinetic typography: "ACADORA — KNOWS YOUR DAY")
- [ ] 2–8 phone screenshots (Home, Timetable, Attendance, Tasks quick-add,
      Assistant, Memory) — capture from a device/emulator at 1080×1920+
- [ ] (Optional) 7" and 10" tablet screenshots

## Privacy policy

Required by Play even for offline apps. Host the page (GitHub Pages works) and
link it in the store listing. Suggested content points:

- All data (timetable, tasks, notes, memories, attendance) is stored locally on
  the device in a private app database; no account is created; nothing is
  transmitted to servers.
- Notifications use the device's local alarm/notification system.
- The timetable-photo import runs an on-device text recognition model; the
  photo is processed locally and never uploaded.
- Data is removed by uninstalling the app or via Reset demo data in Settings.

## Data safety form (Play Console)

Declare based on what the app actually does:

| Question | Answer |
|---|---|
| Does your app collect or share user data? | **No** (all data stays on-device) |
| Is data encrypted in transit? | N/A (no transmission) |
| Can users request data deletion? | Yes — uninstall / in-app reset |

If a future version adds any network analytics or AI backend, this form must be
updated — the README roadmap flags those seams.

## Content rating & target audience

- Content rating questionnaire: Education/utility app, no user-generated
  content shared between users, no ads → typically **Everyone / 3+**.
- Target audience: adults (faculty). Not designed for children.
- Whether the app is a news app: No. COVID-19 app: No. Government app: No.

## Pre-submission checklist

- [x] R8 minification + resource shrinking enabled
- [x] Signed release AAB (`app-release.aab`) built with the release keystore
- [x] Keystore + passwords gitignored, backed up privately
- [x] `mapping.txt` archived (upload to Play Console → deobfuscation files)
- [x] versionCode incremented (2)
- [ ] Store listing text + graphics uploaded
- [ ] Privacy policy URL live
- [ ] Internal testing track pass on a real device (onboarding → attendance →
      reminder notification → widgets)
- [ ] App content declarations (data safety, ads, content rating) submitted

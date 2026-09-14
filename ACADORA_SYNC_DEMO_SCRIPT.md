# ACADORA — Live Two-Device Sync Demo (for judges)

**Length:** ~3 minutes · **Devices:** 2 Android phones (or 1 phone + the emulator on the laptop) · **Needs:** Wi-Fi, the same Acadora account on both devices

---

## Before the judges arrive (5-minute pre-flight)

1. Both devices run **Acadora v1.6.2 or newer** (`Acadora-v1.6.2.apk` from the GitHub release). v1.6.2 fixed a Settings crash present in v1.6.1 — do not demo on older builds.
2. **Phone A** is signed in (email or Google) and already has real data: a timetable, at least one student roster, and a couple of attendance sessions. Load `Settings → DEMO DATA` here if you want a rich view.
3. **Phone B** is signed in **with the same account** and is nearly empty — its emptiness is the point. Verify once beforehand that Phone B actually pulls A's data at sign-in; do that dry run the day before, not live.
4. Both phones on the same Wi-Fi, screens unlocked, Do Not Disturb on.
5. Know your fallback: if the venue Wi-Fi dies, sync pauses and both phones keep working offline — say that out loud, it's a feature.

---

## The script

### Beat 1 — Set the stakes (20 s, talk over Phone A)

> "Acadora is local-first. Everything a faculty member does — every class slot, every attendance mark — is written to the phone first, so the app never hangs on a network. The question every judge should ask a local-first app is: *what happens on the second device?* Watch."

### Beat 2 — Prove the two phones are strangers right now (20 s)

- Open **Phone B → HOME**. Note out loud what's missing: "No timetable, no students — this account is signed in on a second phone and hasn't pulled anything yet."
- If B already shows A's data (pre-synced), instead create a **new task on B** ("Buy lab supplies") — you'll delete it on A later to show deletions travel too.

### Beat 3 — Make a change on Phone A (30 s, drive it live)

1. Phone A → **MY TIMETABLE → ADD CLASS**.
2. Add: *Computer Networks*, Wednesday, 2:00–3:00 PM, Room LH-2, Section ECE-A.
3. Save. Point at the slot appearing instantly on A: "Saved locally in under a second — the app is already usable. The cloud copy is chasing it."

### Beat 4 — The reveal on Phone B (30 s)

1. Phone B → pull-to-refresh or tap **Settings → SYNC NOW** (or just wait ~5 seconds — pushes are debounced, pulls arrive via a live listener).
2. **MY TIMETABLE** on B now shows *Computer Networks* — same day, same room.
> "Same account, second device, no cable, no manual export. That sync survived the demo build being offline a minute ago — it queues and catches up."

### Beat 5 — One deeper cut (30 s, choose ONE)

- **Attendance round-trip:** On A, mark attendance for any class (a few present, one absent). On B, open **ATTENDANCE → recent sessions** — the same session, same per-student marks.
- **Deletion travels:** On **A**, delete the task you created on B in the pre-flight (or any demo task). On B it disappears too — deletes propagate as tombstones, so nothing resurrects.

### Beat 6 — Close on the architecture (20 s)

> "Under the hood: Room is the source of truth, Firestore mirrors each account under its UID with last-write-wins on a per-row timestamp, and every record carries a UUID so device-local IDs never matter. One island per user — Account A can never see Account B. The rules enforcing that are deployed to Firebase right now."

---

## If something goes wrong live

| Symptom | Say / do |
|---|---|
| B doesn't show the new slot within ~10 s | Tap **Settings → SYNC NOW** on B — "forcing a sync cycle" — then continue |
| Wi-Fi is down entirely | "Both phones are offline — watch them keep working." Do Beat 3 on A; reconnect at Beat 4 |
| Sync shows an error row in Settings | Read it out loud: "the sync engine reports failures instead of swallowing them," retry once, move on to the deeper cut |
| Phone B battery dies | Finish on A; show the Firestore console (firebase console → Firestore data) as the second "device" — the new slot's document is right there |

## One-slide fallback (if zero devices)

Screenshot pair: A's timetable *before* adding the class, and B's timetable *after* — plus the Firestore console showing the same document. Practice this swap once.

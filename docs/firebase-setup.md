# Firebase setup — Acadora

Email + password auth and Google Sign-In are wired in code. This file lists
the one-time console steps and how the config reaches builds.

## Project facts

| | |
|---|---|
| Firebase project | `acadora-app` (display name "Acadora") |
| Android package | `com.bits.facultyai` |
| CLI account | see `firebase login` (never commit tokens) |

## One-time console steps (CLI can't do these)

1. **Activate the Firebase project** — open
   <https://console.firebase.google.com>, sign in, and click through the
   welcome/terms screen once.
2. **Add an Android app** to project `acadora-app` with package
   `com.bits.facultyai`. Register BOTH SHA-1 fingerprints when asked:
   - Release: `54:80:60:11:E8:07:A5:F0:9A:26:8B:E8:43:E8:56:C1:51:DA:60:43`
   - Debug: `E2:37:D2:FC:8C:92:5D:9B:85:71:D4:F5:33:A7:A2:BE:34:CA:82:B0`
3. **Download `google-services.json`** and place it at `app/google-services.json`
   (already gitignored). For CI, also paste the file contents into a new
   repository secret named `GOOGLE_SERVICES_JSON`.
4. **Enable sign-in providers**: Firebase console → Authentication →
   Sign-in method → enable **Email/Password** and **Google**.
5. **Create the Firestore database**: Firebase console → Firestore Database →
   Create database → production mode → pick a region. Then deploy the
   committed rules from the repo root:

   ```bash
   firebase deploy --only firestore:rules
   ```

   `firestore.rules` gives every account a private island at `users/{uid}/…` —
   no document is world-readable, and a tampered client cannot read another
   user's data.

## How sync works

- **Room stays the source of truth.** The cloud mirrors three aggregates:
  `users/{uid}/slots`, `users/{uid}/students`, and `users/{uid}/sessions`
  (each session embeds its attendance entries). Deletions travel as tombstones
  under `users/{uid}/tombstones`.
- **Conflict rule: last-write-wins** on each row's `updatedAt`. A row edited
  after a delete was requested survives the tombstone (and re-pushes).
- **Second device** pulls the account's data at sign-in; local writes push
  automatically (debounced). Sign-out and account switches wipe local data so
  accounts never mix on one install.
- **Requires the providers step**: if Email/Password isn't enabled, sign-in
  can't happen — and sync only runs for signed-in accounts.

## Why both SHA-1s

Google Sign-In verifies the app to Google using the signing certificate's
SHA-1. Release installs verify against the release key; Android Studio
installs against the debug key. Missing either one breaks sign-in on that
build type with `ApiException: 10 (DEVELOPER_ERROR)`.

## How builds consume the config

- `app/build.gradle.kts` applies the `google-services` plugin **only when
  `app/google-services.json` exists**, so the repo builds green before setup
  and CI stays green without the secret.
- Both GitHub Actions workflows restore `app/google-services.json` from the
  `GOOGLE_SERVICES_JSON` secret when present.
- With no config, the app runs in **guest-only mode**: the login screen shows
  a notice and guest entry; nothing crashes.

## Architecture map

```
data/auth/AuthModels.kt        AuthUser, AuthIntent, AuthError (UI-safe vocabulary)
data/auth/AuthValidator.kt     pure form rules (unit-tested)
data/auth/FirebaseAuthSource.kt FirebaseAuth wrapper + error mapping
data/auth/AuthRepository.kt    single owner of auth state; Credential Manager Google flow
ui/auth/AuthViewModel.kt       form state, submit flows, verify-then-sign-out sign-up
ui/auth/AuthGate.kt            pure launch decision (login vs app vs loading)
ui/auth/LoginScreen.kt         Obsidian Purple glass login screen
MainActivity.kt                Crossfade gate: splash-hold -> login -> app
```

On sign-up the account is created, a verification email is sent, and the
session is signed out — the user must verify before first sign-in.

Sign-out (Settings → ACCOUNT → SIGN OUT) clears Credential Manager state and
erases account-owned local data on the device so a different account never
sees the previous one's data.

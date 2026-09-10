# Play Console Walkthrough — Internal Testing Setup

Step-by-step guide to get `app-release.aab` in front of testers today, and the
exact answers for every declaration Play asks. Budget ~45 minutes.

> You need: a **Google Play Console developer account** ($25 one-time,
> play.google.com/console). Identity verification can take 1–3 days — start it
> first if you haven't.

## 1. Create the app

1. Play Console → **All apps → Create app**.
2. Name: `Acadora — Faculty Assistant` · Default language: English (US)
3. Type: **App** · Free/paid: **Free**
4. Accept declarations (developer program policies + US export law) → **Create app**.

## 2. Complete App content (the declarations)

Play Console → **Policy → App content**. Every item must be green before
review. The answers below match what the app actually does:

| Section | Answers |
|---|---|
| **Privacy policy** | Paste the URL of your hosted `docs/privacy-policy.html` (see §5 below) |
| **Ads** | **No, my app contains no ads** |
| **In-app purchases** | No (nothing to declare) |
| **Content ratings** | Questionnaire: Education category; no violence, no gambling, no user-generated sharing, not aimed at children → expected rating **Everyone** |
| **Target audience** | Age groups: **18–44** (and optionally 45+). NOT designed for children → confirm "does not target children" |
| **Data safety** | **Does your app collect or share any of the required user data types? → No.** Nothing is collected, shared, or transmitted (all data stays on-device; this matches docs/privacy-policy.html). |
| **App access** | **All functionality is available without special access** (no login, no restrictions) |
| **Government apps** | No |
| **Financial features** | None |
| **Health apps** | None |
| **News apps** | No |
| **COVID-19** | No |
| **Data deletion** | Users can delete data by uninstalling; declare URL: same privacy policy page |
| **Account deletion** | N/A — no accounts exist |

## 3. Upload the AAB to internal testing

1. Play Console → **Testing → Internal testing**.
2. **Create new release** — if asked about App signing, choose
   **"Let Google create and manage my signing key"** (recommended; your local
   keystore stays the upload key).
3. **App bundles**: upload `app/build/outputs/bundle/release/app-release.aab`.
4. **Deobfuscation files**: upload
   `app/build/outputs/mapping/release/mapping.txt` (also archived in the
   v1.1.0 GitHub release, gzipped).
5. Release name: `1.1.0 (3)` · Release notes: "First internal build — all features enabled."
6. **Save → Review release → Start rollout to Internal testing.**

## 4. Add testers

1. Internal testing → **Testers** tab → **Create email list** (e.g. `faculty-beta`).
2. Add tester Gmail addresses (yours first).
3. **Copy the opt-in link** and open it on your Android phone → tap
   **Become a tester** → install from the Play Store link.
4. First rollout to a new app requires the app content section complete + a
   short automated review — usually minutes to a few hours.

## 5. Host the privacy policy (free, 2 minutes)

GitHub Pages from this repo (already enabled if you ran this once):

```bash
gh api --method POST repos/metheyelene/faculty-ai/pages \
  --input - <<< '{"source":{"branch":"main","path":"/docs"}}'
```

Then `Settings → Pages` shows the URL, typically:

```
https://metheyelene.github.io/faculty-ai/privacy-policy.html
```

Before sharing it, open `docs/privacy-policy.html` and replace
`__CONTACT_EMAIL__` with a real contact address.

## 6. Internal test-pass checklist (on a real device)

- [ ] Onboarding completes; name/designation flow into greeting everywhere
- [ ] Home: next-class countdown matches timetable; widgets on home screen update
- [ ] Timetable: add a class, see conflict check, take attendance
- [ ] Tasks: NL quick-add "remind me tomorrow 9am ..." → notification fires
- [ ] Assistant: answers cite timetable/tasks/notes sources
- [ ] Memory: save/edit/delete; privacy toggles hold
- [ ] Photo import: timetable image → OCR slots (offline, airplane mode)
- [ ] Reboot device → reminders still fire (BootReceiver reschedules)
- [ ] Settings: SYSTEM/LIGHT/DARK re-themes instantly

## 7. Ship to production (later)

When internal testing is clean: **Testing → Closed testing** (wider audience)
→ then **Production**. The store listing text lives in
`PLAY_STORE_LISTING.md`; graphics are in `docs/store/`.

## Store graphics (already generated)

| Asset | File | Spec |
|---|---|---|
| App icon 512×512 | `docs/store/icon-512.png` | ✅ 512×512 |
| Feature graphic | `docs/store/feature-graphic.png` | ✅ 1024×500 |
| Phone screenshots ×5 | `docs/store/phone-0*.png` | ✅ 1080×2340 (16:9-ish min 2, max 8) |

> Note: the five phone screenshots are **high-fidelity design mockups**
> rendered from the app's real design system, not live captures. Recommended:
> replace them with real device screenshots before production (the checklist
> in §6 gives you every screen). To re-render or edit the mockups:
> `python3 docs/render_store_assets.py` after editing templates in
> `docs/store-templates/`.

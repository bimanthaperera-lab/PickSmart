# Cloud backup — how it works and the owner's setup

Optional backup for signed-in riders (CLAUDE.md section 14). Built on branch `v2`, 2026-10-26.

## What happens

- **Where:** Cloud Firestore. `users/{uid}` holds the settings that follow the rider (mobile number, active vehicle); `users/{uid}/{kind}/{syncId}` holds one document per record, for `vehicles`, `trips`, `other_income`, `fuel_logs` and `expenses`.
- **Ids:** every record has a `sync_id` (database version 5). Room ids differ per phone, so records and their vehicle links travel by sync id.
- **Upload:** every save marks the row `sync_pending`. About 20 seconds after a change, pending rows and deletes are uploaded in batches, then marked backed up, unless they were edited again meanwhile.
- **Download:** on sign-in, app start, coming back online after a failure, and Back up now. Only documents written since the last download are read (a `serverUpdatedAt` server timestamp on each document).
- **Merge:** last write wins by each record's `updatedAt`; a tie keeps the phone's copy. Deletes are kept as tombstones (`sync_tombstones`), so a stale copy can't bring a record back. Vehicles are archived, never deleted. The phone's data is never replaced by an empty or older cloud copy.
- **New phone:** "I already have an account" restores the backup before setup. If it brings back a vehicle, the vehicle step is skipped. Offline, the rider can retry or set up a vehicle; the backup joins later.
- **Another rider on the same phone:** everything on the phone is uploaded to the account that signed in, and their own backup is merged in.
- **Delete account:** deletes the cloud data first, then the account. Offline, nothing is deleted.
- **Status:** the Account tab shows a Cloud backup card when signed in: last backup time, waiting for internet, changes not backed up yet, and Back up now.

## Owner setup (before testing on a phone)

1. Firebase console → Build → **Firestore Database** → Create database. Pick a location close to Sri Lanka (for example `asia-south1`, Mumbai); it can't be changed later. Start in **production mode**.
2. Firestore → **Rules**: paste the contents of `firestore.rules` from the repository root and Publish. Without them, production mode refuses every request (the app shows "The backup server didn't accept your sign-in").
3. Nothing else is needed on the Spark (free) plan. The app already has the `firebase-firestore` library and uses the existing `google-services.json`.
4. Test on a real phone: sign in, add a trip, wait about 20 seconds (or tap Back up now), then check Firestore → Data → `users/<uid>/trips`. Then sign in on a second phone or the emulator with "I already have an account" and check that the vehicle and trips come back.

## App Check (Play Integrity)

Firestore should accept requests only from the real Picksmart app, so a modified or repackaged APK can't use backup (CLAUDE.md section 10).

- **In the app (done):** release builds use the Play Integrity provider; debug builds use the debug provider (`app/src/release` and `app/src/debug`, `AppCheckProvider.kt`). It is installed before the first Firebase request (`AppCheck.install` in `AppContainer`). Tokens are fetched only when Firestore needs one, with no background refresh.
- **When the check fails:** only backup stops. The Account tab's Cloud backup card says "Backup is paused because this copy of Picksmart couldn't be verified. Install Picksmart from Google Play to back up. Your records are safe on this phone." A restore on a new phone says the same and offers Set up a vehicle now. Firestore answers a failed App Check the same way as a rules refusal, so on a refusal the app asks App Check for a token: no token means "couldn't be verified", a token means the rules or the sign-in refused.

**Owner setup, in this order** (enforcing first would stop every rider's backup):

1. Firebase console → **App Check** → Apps → the Android app → register **Play Integrity**. It asks for the app's SHA-256 signing fingerprint. For an app uploaded to Play, use the **app signing key** fingerprint from Play Console → Setup → App signing, not only the upload key.
2. Play Console → Release → App integrity → link the same Google Cloud project for the Play Integrity API.
3. For debug builds on the emulator or your phone: run the app, find `Firebase App Check debug token` in logcat, and add it under App Check → Apps → ⋮ → **Manage debug tokens**. The token is a secret: don't commit or share it, and delete it when you're done.
4. Watch App Check → **APIs → Cloud Firestore** for a few days after a release: it shows verified and unverified requests before anything is blocked.
5. When nearly all requests are verified, click **Enforce** for Cloud Firestore. Don't enforce **Authentication** for now: sign-in would then fail on an unverified copy, and only backup should stop.

A release APK installed by hand (not from Play) fails Play Integrity, so once enforcement is on its backup stops with the message above. That is intended. Test release builds through a Play internal testing track.

## Known limits

- Two phones editing the same record within the same few seconds: the later upload wins even if its edit is older. Rare for one rider; a check before each write would cost an extra read per record.
- A document written by a newer app version that this version can't read (for example a new vehicle type) is skipped, not half-applied.
- App Check guards backup only once the owner turns on enforcement (above). Until then, the security rules are the only guard.
- Profile photo, gender, date of birth, theme and language are not backed up.

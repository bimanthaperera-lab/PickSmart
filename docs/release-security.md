# Release security checklist

From the security review of 2026-10-30. The app side is done; what is left happens in the Firebase
console, Google Cloud, Play Console and GitHub, and only the owner can do it. Tick each item before
the first upload to Play.

## Must do before release

- [ ] **Publish the latest `firestore.rules`.** Firebase console → Firestore → Rules: paste the file
      from the repository root and Publish. Without the latest rules, backing up places, gender,
      date of birth and the photo is refused.
- [ ] **Enforce App Check for Firestore.** The rules limit what each document holds but not how many
      documents a signed-in account writes, so until App Check is enforced, anyone who takes the
      Firebase values out of the APK can make an account and fill its space, which costs money.
      Follow `docs/cloud-backup.md`, "App Check": register Play Integrity with the **app signing
      key** SHA-256 from Play Console, watch App Check → APIs → Cloud Firestore for a few days on an
      internal test track, then **Enforce** for Cloud Firestore (not Authentication).
- [ ] **Delete the App Check debug tokens** (App Check → Apps → ⋮ → Manage debug tokens). Anyone
      holding one passes App Check. Make new ones only while testing and delete them after.
- [ ] **Budget alerts** (Google Cloud → Billing → Budgets) at 50%, 80% and 100%, once the project is
      on Blaze. Alerts only notify; App Check and the rules are what stop abuse.
- [ ] **Web page for account deletion.** Play requires a link outside the app. A page (for example on
      GitHub Pages next to the fuel price file) with:
      - the app name, Picksmart, and the developer name as on Play;
      - the steps: open Picksmart → Account → your profile → Delete account;
      - for riders who no longer have the app: an email address to ask for deletion, and that you
        reply within 30 days;
      - what is deleted (the account, the cloud backup: vehicles, trips, fuel log, expenses, income,
        profile details and photo) and that nothing is kept.
      Put the link in Play Console → App content → Data safety, and the email in the privacy
      policy's Contact line.
- [ ] **Upload key and Play App Signing.** Make the upload key once, keep it and its passwords
      somewhere safe outside the repository (a password manager, plus an offline copy):

      ```bash
      keytool -genkeypair -v -keystore picksmart-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
      ```

      Then create `keystore.properties` in the repository root (git-ignored):

      ```text
      storeFile=picksmart-upload.jks
      storePassword=...
      keyAlias=upload
      keyPassword=...
      ```

      `./gradlew bundleRelease` then signs with it (`app/build.gradle.kts`, `signingConfigs`).
      Enrol in Play App Signing when you upload, and add both the upload key and the app signing key
      SHA-1 and SHA-256 to the Firebase project (Google sign-in needs them).
- [ ] **Upload the R8 mapping file** with every release (`app/build/outputs/mapping/release/mapping.txt`;
      Play Console takes it with the bundle). Never commit it.
- [ ] **Offer reader on Play.** Play Console → App content → Accessibility API: the declaration and
      a video of the disclosure and the badge (CLAUDE.md section 9b). If Play rejects it, the reader
      comes out of the release build.

## Console settings

- [ ] **Restrict the Android API key** (Google Cloud → APIs & Services → Credentials → the "Android
      key" Firebase made): Application restrictions → Android apps → `lk.picksmart` with the upload
      key and app signing key SHA-1. API restrictions → only Identity Toolkit API, Token Service API,
      Cloud Firestore API and Firebase App Check API. Test sign-in and backup on the Play internal
      track after the change.
- [ ] **Email enumeration protection** (Firebase → Authentication → Settings → User actions): on,
      so sign-in and "Forgot password?" don't tell an attacker which emails have accounts. The app
      already shows a wrong email and a wrong password as one message. Register still says when an
      email is in use; Firebase can't hide that.
- [ ] **Watch sign-ups** (Authentication → Users, and Usage) for the first weeks. App Check isn't
      enforced for Authentication on purpose (only backup should stop for an unverified copy), so
      sign-up abuse would show here first.
- [ ] **GitHub 2FA** on the account that owns the fuel price repository. The app refuses a price
      that moved more than 40% or lies outside Rs. 50–2,000, but within that someone with the account
      could push a wrong price, which riders with automatic updates would take.

## Later

- **Entitlements after Delete account.** `entitlements/{uid}` can't be deleted by the app (only
  the server writes it), so it stays after an account is deleted. When the purchase-verification
  Cloud Function is built (Phase 8d), add a function on user deletion that removes it.
- **Data safety form.** The release manifest also carries `USE_BIOMETRIC`, `USE_FINGERPRINT` and
  `READ_GSERVICES` from Google's sign-in libraries. They need no declaration of their own, but
  don't be surprised to see them in Play Console.

## Checked and fine (2026-10-30)

Release APK: no debug probe or sample offer screen, no cleartext traffic, not debuggable, the offer
reader lists only `com.ubercab.driver` and `com.pickme.driver.byod` with `canPerformGestures=false`;
lint 0 errors. Code: the "Save this trip?" PendingIntent is immutable and only the non-exported
alias can deliver an offer, whose numbers are validated; CSV export escapes formulas and shares only
the cache `exports/` folder; fuel price and profile photo downloads are HTTPS only with size limits;
the Firestore rules allow each rider only their own data and never the Premium entitlement;
`google-services.json`, `local.properties` and keystores are git-ignored and absent from history;
R8 is on. The Delete account dialog ignores touches through another app's overlay.

Accepted, low: the Room database isn't encrypted beyond Android's own file encryption; the
debug-only probe logs amounts (never give anyone a debug APK).

# Picksmart — Build Plan

Claude updates this file at the end of every phase. The rules for *how* to build are in `CLAUDE.md`; this file tracks *what* to build next.

## Status

- **Current phase:** 8c (first part) — sign-in and the Account tab are built, ahead of 6c by the owner's choice; cloud backup (the rest of 8c) comes later. Next is Phase 6c — Multiple vehicles, then Phase 7
- **Next step:** Owner: try sign-up, sign-in (email and Google), password reset and the Profile screen on a phone, and publish the fuel price file (`docs/fuel-price-file.md`). Claude: start Phase 6c with the Vehicles list on the Account tab, the active-vehicle switch and the 1 / 3 limit (now that sign-in exists), then Phase 7.
- **Last updated:** 2026-10-23 (first-launch welcome with Get started / I already have an account, "how it works" pages, sample values removed from onboarding; Login and Register match the mockups)

## Open questions for the owner

Claude uses the default shown until the owner answers, and moves answered items into `docs/decisions.md`.

| Question | Default until answered |
| --- | --- |
| Final package name and application ID | `lk.picksmart` |
| Support cars (Uber car drivers) from the start? | Yes, as vehicle type `CAR` |
| Enable Android Auto Backup? | Yes, with explicit include/exclude rules |
| When to add Sinhala and Tamil translations? | After the MVP; strings are ready from day one |
| Include ads in the first release? | Yes, final phase only, behind `AdsProvider` |
| Default theme | Follow system setting |
| Monetization model | Feature gating (never ride caps); Free / Signed in / Premium |
| Premium price | Rs. 490/month, Rs. 4,900/year (placeholder, confirm) |
| Where to host the fuel price file | A JSON file in a public GitHub repository |
| Who updates the fuel price file, and how fast | The owner, within a day of each CPC announcement |

---

## Phase 0 — Inspect and scaffold

- [x] Inspect the repository, Gradle setup, AGP/Kotlin versions and SDK assumptions
- [x] If code exists: summarize its structure and propose the safest next step, then stop for review (no code existed)
- [x] If empty: scaffold the smallest Kotlin + Compose + Material 3 app that builds
- [x] Add `.gitignore` (build outputs, `local.properties`, keystores)
- [x] Create `docs/decisions.md`
- [x] `./gradlew assembleDebug` passes (2026-10-22; launched on the Pixel_8_Pro emulator in light and dark mode)

**Done when:** the app builds and launches to a blank themed screen.

## Phase 1 — ProfitCalculator

- [x] Pure Kotlin `ProfitCalculator` in `domain/calculator`
- [x] Typed validation errors (no exceptions into the UI, no NaN/Infinity)
- [x] Both fare modes and both commission types
- [x] Result bands, including the optional target
- [x] Golden tests G1–G7 from `CLAUDE.md` section 6, plus negative, zero-efficiency, over-100% and large-value tests (25 tests, all passing on 2026-10-22)
- [x] Rounding policy written in KDoc

**Done when:** `./gradlew testDebugUnitTest` passes with every golden test.

## Phase 2 — Design system and app shell

- [x] Theme: colours (including profit, loss and below-target), typography, spacing, shapes; dark, light and system modes
- [x] Reusable components: buttons, number input fields, cards, result band chip, empty/loading/error states
- [x] Currency and number formatter in `util/` (`Rs. 1,250.00`)
- [x] Type-safe navigation with the five bottom tabs
- [x] `AppContainer` for manual DI
- [x] All strings in `strings.xml`
- [x] Checked at small screen size and 200% font scale (360 × 640 dp emulator, 2026-10-22)

**Done when:** every tab opens a real (if simple) screen, and back navigation works.

## Phase 3 — Persistence foundation and onboarding

- [x] Room database v1 with `exportSchema = true`, schema committed
- [x] `Vehicle` entity, DAO and repository
- [x] DataStore: default vehicle, onboarding flag, theme (last-used Check a Ride inputs moved to Phase 4, where their shape is decided)
- [x] First-launch onboarding: vehicle type, fuel type, fuel price, efficiency, maintenance reserve, optional target per km
- [x] "Use sample values" option, clearly labelled as samples
- [x] One-time safety notice
- [x] Settings screen to edit the vehicle

**Done when:** a new user finishes setup and the values survive an app restart.

## Phase 4 — Check a Ride

- [x] Form pre-filled from the default vehicle and last-used values
- [x] Save last-used Check a Ride inputs in DataStore (moved from Phase 3)
- [x] Show "Sample" next to rates while the vehicle still uses sample values
- [x] Fare field focused with a numeric keyboard
- [x] Fare mode selector; commission field hidden in payout mode
- [x] Return distance visibly optional
- [x] Live recalculation and validation messages
- [x] Result card: total km, fuel, reserve, commission, net profit, per km, band with text label
- [x] Per-trip overrides of vehicle rates
- [x] "Results are estimates" line and safety line
- [x] Checked on the emulator (2026-10-22): G2 and G3 on screen, remembered inputs after restart, overrides leave the vehicle unchanged, 360 × 640 dp at 100% and 200% font, light and dark. Not checked: the full on-screen keyboard (the emulator shows only Gboard's floating toolbar while a host keyboard is attached).

**Done when:** a typical check needs three field edits or fewer, and the result is visible without scrolling on a small screen.

## Phase 5 — Trips, history and other income

- [x] `Trip` entity with the rate snapshot; Room migration v1→v2 with a migration test
- [x] Save a checked offer as `OFFER_CHECK` ("Save offer" / "I took it" on the Check a Ride result, plus the platform chips from the mockup)
- [x] Mark an offer as completed (rider confirms actual values) or cancelled (with optional cancellation fee)
- [x] Add a completed trip directly, without checking it first
- [x] History list: date, platform, fare, costs, net profit; filter by status
- [x] Edit and delete with confirmation
- [x] `OtherIncome` entity and simple entry screen (incentives, tips, bonuses)
- [x] Empty state for new users
- [x] Test proving offers are excluded from all totals (`TripProfitTest`, `HistoryGroupingTest`)
- [x] Checked on the emulator (2026-10-22): update from the Phase 4 build kept the vehicle, theme and remembered inputs with no re-onboarding; saved an offer and a taken trip; marked the offer cancelled with a fee; deleted a trip after confirming; added a trip and an income entry; data survived a force-stop; 360 × 640 dp at 200% font.

**Done when:** trips and income survive restarts, and no offer is ever counted as income.

## Phase 6 — Fuel log and expenses

- [x] `FuelLog` entity, entry screen and list (with full-tank flag)
- [x] `Expense` entity, entry screen and list (no fuel category)
- [x] Actual km/L calculated from full-tank fills, with an optional suggestion to update the vehicle setting
- [x] Migrations and migration tests for each schema change (database v3, `MIGRATION_2_3`, `MigrationTest`)
- [x] Checked on the emulator (2026-10-22): update from the Phase 5 build kept the vehicle, trip and income with no re-onboarding; added fill-ups by amount and by litres; two full tanks with a partial fill between gave 30.0 km/L, "Use 30.0 km/L" changed the vehicle and left the past trip's profit unchanged; deleted fill-ups after confirming; added a repair (with odometer) and parking (no odometer field); data survived a force-stop; 360 × 640 dp at 200% font.

**Done when:** fuel and expenses are recorded in exactly one place each.

## Phase 6b — Fuel price updates

- [ ] Publish the first fuel price file (owner). The `BuildConfig.FUEL_PRICE_URL` field exists and reads the Gradle property `picksmart.fuelPriceUrl`; steps in `docs/fuel-price-file.md`
- [x] Vehicle fields: `fuelType`, `fuelPriceSource`, `fuelPriceEffectiveDate`, `autoUpdateFuelPrice` (included in database v1 in Phase 3, so no migration is needed)
- [x] `FuelPriceSource` in `data/remote`: fetch, validate, cache the last valid list in DataStore
- [x] 12-hour throttle; silent failure; offline uses the last valid list
- [x] Fuel price screen: fuel type, current price with its source and date, auto-update switch
- [x] "New price" prompt with both prices and the change in fuel cost per km (also a notice on Home and the price on Settings)
- [x] Hand-edited price switches the source to manual and turns auto-update off
- [x] "May be out of date" label after 45 days
- [x] Tests from `CLAUDE.md` section 8a (`FuelPriceParserTest`, `FuelPriceSourceTest`, `PublishedFuelPriceTest`, plus `FuelCostTest`, `FuelPriceFormStateTest` and new `UserPreferencesRepositoryTest` cases)
- [x] Privacy policy and Data safety answers drafted for the download (`docs/privacy.md`, owner to review)
- [x] Checked on the emulator (2026-10-23) against a local test file: update from the Phase 6 build kept every record and setting; Home notice → prompt with both prices and per-km cost; Update set "CPC price from 15 Oct"; with auto-update on, a newer list was applied with a one-time Home notice; a typed price turned auto-update off, and the next list was only offered; Keep hid it; past trips' profits unchanged throughout; 360 × 640 dp at 200% font. Not checked: a real HTTPS host (no file is published yet).

**Done when:** a new published price reaches the rider as a prompt (or automatically, if they chose that), never overwrites a price they set by hand, and never changes a past trip.

## Phase 6c — Multiple vehicles

- [ ] Pure rule for the vehicle limit (`canAddVehicle`: active count vs. limit; 1 signed out, 3 signed in, until `PlanProvider` exists), with tests including archived vehicles not counting and a rider over the limit after signing out
- [ ] Vehicles list in Settings (moves to the Account tab with 8c): each vehicle's type and rates, the active one marked, Add, Edit, Set active, Archive (with confirmation)
- [ ] Archiving the active vehicle asks which becomes active; the last active vehicle can't be archived
- [ ] Vehicle chip at the top of Check a Ride to switch the active vehicle in one tap; switching re-fills the rates and keeps the typed fare and distances
- [ ] Add / Edit trip, fuel log and expense forms pick a vehicle, defaulting to the active one
- [ ] History rows show the vehicle name when the rider has more than one
- [ ] Adding past the limit opens the upgrade screen: signed out, "Sign in to add up to 3 vehicles" with Sign up / Sign in; signed in, "More vehicles with Premium" ("coming soon" until Phase 8d)
- [x] Onboarding: remove "Skip and use sample values"; fields start empty; existing sample vehicles keep their label and notice until saved (no re-onboarding) (done 2026-10-23 with the new welcome and intro)
- [ ] Onboarding: pre-fill a saved CPC price for the chosen fuel type, with its source shown
- [ ] Fuel prices: prompt and Fuel price screen follow the active vehicle; automatic updates apply to every vehicle with auto-update on
- [ ] No schema change expected (vehicles, `vehicleId` and `archived` exist since v1); if one is needed it ships a `Migration` and test
- [ ] Checked on the emulator: update from the 6b build keeps the vehicle and records (including a sample vehicle, not re-onboarded); fresh install has no sample option; signed out a 2nd vehicle opens the sign-in screen; switch, archive, past trips unchanged; 360 dp at 200% font

**Done when:** a new rider starts with their own numbers (no samples), a signed-out rider has 1 vehicle and a signed-in rider up to 3, a rider can switch the active one in one tap from Check a Ride, and every saved trip keeps the vehicle and rates it was saved with.

## Phase 7 — Dashboard, reports and export

- [ ] Accounting use case implementing both views from `CLAUDE.md` section 7, with tests including a no-double-counting test
- [x] Home: today's net profit, gross income, recorded expenses, completed trips, total km, and a large Check a Ride button (brought forward on 2026-10-22; day figures from `summarizeDay`, tested in `DaySummaryTest`)
- [ ] Reports: daily, weekly (Mon–Sun) and monthly, with an Estimated / Cash toggle
- [ ] Simple Canvas charts
- [ ] Clear notice when data is incomplete
- [ ] CSV export of trips, income, fuel and expenses through the share sheet

**Done when:** dashboard and reports agree with each other and with hand-calculated test data.

## Phase 8 — Screenshot import and OCR

- [ ] Import through the Photo Picker and from the share sheet
- [ ] ML Kit Latin recognition, unbundled model, with fallback when unavailable
- [ ] Parser for fare and distance candidates, tolerant of format variations
- [ ] Review and edit screen before any calculation or save
- [ ] Manual entry fallback on every failure path, including Sinhala/Tamil screenshots
- [ ] Bitmaps released; nothing logged or kept
- [ ] Parser tests with text fixtures and malformed values

**Done when:** every OCR path ends in either a confirmed, edited value or manual entry.

## Phase 8b — Notifications and glance surfaces

- [ ] One `NotificationChannel` per type; `POST_NOTIFICATIONS` requested in context
- [ ] Shift reminder: start/stop from Home, ongoing silent notification with the magic number and 5/10/15 km fares, recomputed when inputs change
- [ ] Magic number in a tested pure function (percentage and fixed commission)
- [ ] Fuel price changed, daily summary (rider-set time), optional unfinished-offer reminder
- [ ] Daily summary and fuel price check scheduled through WorkManager
- [ ] Quick Settings tile opening a mini fare check
- [ ] Floating bubble (off by default, advanced) behind `SYSTEM_ALERT_WINDOW`, requested only on enable, with the tap-block warning; app fully usable without it
- [ ] Bubble obscured-touch safeguards: two states with one visible at a time, auto-collapse on a request, snap out of the bottom-centre region, long-press to hide
- [ ] No reading of any other app on any surface (N5)
- [ ] `Alert` entity + in-app alerts inbox (bell icon with unread count in Home header); posting a notification also writes an inbox row; 30-day retention; reads offline
- [ ] Migration and migration test for the `Alert` table

**Done when:** a rider sees their minimum fare per km at a glance while riding, and can check a fare from the tile, with no notification sound while riding and no access to the ride app.

## Phase 8c — Optional account and cloud backup

- [x] `AccountProvider` interface with Firebase and no-op implementations; app builds and runs with `google-services.json` absent (checked 2026-10-23)
- [ ] `SyncProvider` interface with a no-op implementation (with sync)
- [x] Rename the Settings tab to **Account**: profile card at the top, the existing settings unchanged below it (CLAUDE.md section 14, "Account tab")
- [x] Signed-out profile card shows no profile details, just the account benefit line and Sign up / Sign in buttons
- [x] Auth screens: Login (email/password, Forgot password, Google) and Register (name, email, password, optional mobile number, Google), each with a prominent "Skip — use without an account"; restyled to the mockups without the Phone button
- [x] First-launch welcome: Get started (three "how it works" pages, then vehicle setup) and I already have an account (Login / Register, then vehicle setup)
- [x] Profile screen: photo (Google photo or initials, cached), name, email with verified state, optional mobile number marked "Not verified", sign-in method, Edit, Sign out
- [x] Owner: Firebase project, Android app `lk.picksmart`, SHA-1/SHA-256 fingerprints, `google-services.json` (git-ignored), Email/Password and Google providers enabled
- [x] Firebase Auth: email/password (with verification email and reset) and Google; no phone OTP
- [ ] Owner: test sign-up, sign-in, Google, reset and Profile on a real phone (Claude can't create accounts or sign in)
- [ ] Firebase budget alerts at 50/80/100% (when moving to Blaze)
- [x] Create an account later from the Account tab without losing local data (nothing local is touched; backup comes with sync)
- [ ] Firestore per-user storage with security rules isolating each rider; offline persistence on
- [ ] Firebase App Check with the Play Integrity provider on Firestore; a failed check stops only backup, with a plain message, never local use
- [ ] Sync: local stays authoritative, last-write-wins by `updatedAt`, delete tombstones; never replaces local with empty cloud
- [x] Sign-out keeps local data; "Delete account" action (deletes the sign-in account; cloud data joins it with sync)
- [ ] Play Data safety form and privacy policy updated; marketing promise reworded

**Later (not this phase):** Facebook login (Meta developer app, Facebook SDK, Meta app review).

**Done when:** a rider can use the app fully without an account, optionally sign in with email or Google to back up, reinstall on another phone and restore their history, and sign out without losing anything on-device.

## Phase 8d — Plans, profile and Premium

- [ ] `PlanProvider` with feature flags for the three tiers; features check flags, not tier names directly
- [ ] Profile screen additions (the screen itself comes in 8c): tier badge, rides-per-day chart, lifetime stats
- [ ] Premium screen listing the three tiers; Google Play Billing integration
- [ ] Feature gates wired: reports range, export, vehicle limit (1 / 3 / unlimited), OCR, widgets, tax summaries, ads
- [ ] Server-side purchase verification: a Cloud Function checks each Play purchase with the Google Play Developer API, acknowledges it and stores the entitlement under the user id (needs the Blaze plan, with budget alerts)
- [ ] App Check (Play Integrity) on that function; the app trusts only the server-verified entitlement, never a local flag alone
- [ ] Entitlement cached locally so Premium works offline once verified; re-check when online
- [ ] No ride or ride-check caps anywhere (core rule); downgrade never deletes data

**Done when:** every tier can track unlimited rides, Premium unlocks only the extra features, a paid rider keeps working offline, and a lapsed rider keeps all their data with just the gated features off.

## Phase 9 — Release readiness and ads

- [ ] `AdsProvider` interface, no-op implementation, AdMob implementation with test IDs
- [ ] Ads kept away from Check a Ride and data entry; no interstitials during ride checks
- [ ] Privacy policy and Data safety answers that match the actual app
- [ ] Check the current Play target API and policy requirements
- [ ] Protection check: release build has R8 minify and resource shrinking on; the R8 mapping file is uploaded to Play Console; no keys, credentials or service-account files in the APK or repository
- [ ] Owner: register the "Picksmart" name and logo as a trademark in Sri Lanka (NIPO), and confirm the app's source repository is private
- [ ] App icon, store screenshots, store description
- [ ] Release checklist run on a low-end device: offline use, small screen, large fonts, back navigation, ad failure

**Done when:** every item in the MVP acceptance list below is verified.

---

## MVP acceptance list

- [ ] Project builds; all unit and migration tests pass
- [ ] Golden calculator tests pass exactly
- [ ] Invalid input cannot crash the app
- [ ] Offers are never counted as income
- [ ] Estimated and Cash views never count an amount twice
- [ ] All records survive restarts and app updates (old-version database opens and keeps every row)
- [ ] An app update never re-onboards an existing rider or clears their settings
- [ ] App is fully useful with no internet connection, including the alerts inbox
- [ ] Alerts appear both in the shade and in the in-app inbox, and open the right screen
- [ ] App works fully with no account; skip is offered everywhere and is not a dead end
- [ ] Signing in backs up data; reinstalling and signing in restores it; signing out keeps local data
- [ ] No tier caps the number of rides checked or recorded; premium gates features only
- [ ] A lapsed Premium rider keeps all data; only gated features turn off
- [ ] OCR always falls back to manual entry, and extracted values need confirmation
- [ ] No reading, screen-capture, overlays or automation of other apps, on any surface
- [ ] Shift reminder shows the magic number and stays silent while riding
- [ ] Notification permission asked in context, not on first launch
- [ ] Ad failure never blocks the app
- [ ] Small screens, 200% font scale and back navigation work
- [ ] No fake buttons or TODO placeholders in core screens

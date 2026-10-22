# Picksmart — Build Plan

Claude updates this file at the end of every phase. The rules for *how* to build are in `CLAUDE.md`; this file tracks *what* to build next.

## Status

- **Current phase:** 4 — Check a Ride
- **Next step:** Build the Check a Ride form pre-filled from the default vehicle and last-used inputs (saved in DataStore), with live `ProfitCalculator` results.
- **Last updated:** 2026-10-22 (Phase 3 done)

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
| Which sign-in methods to ship | Email/password, Google, and phone OTP, with account optional |
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

- [ ] Form pre-filled from the default vehicle and last-used values
- [ ] Save last-used Check a Ride inputs in DataStore (moved from Phase 3)
- [ ] Show "Sample" next to rates while the vehicle still uses sample values
- [ ] Fare field focused with a numeric keyboard
- [ ] Fare mode selector; commission field hidden in payout mode
- [ ] Return distance visibly optional
- [ ] Live recalculation and validation messages
- [ ] Result card: total km, fuel, reserve, commission, net profit, per km, band with text label
- [ ] Per-trip overrides of vehicle rates
- [ ] "Results are estimates" line and safety line

**Done when:** a typical check needs three field edits or fewer, and the result is visible without scrolling on a small screen.

## Phase 5 — Trips, history and other income

- [ ] `Trip` entity with the rate snapshot; Room migration v1→v2 with a migration test
- [ ] Save a checked offer as `OFFER_CHECK`
- [ ] Mark an offer as completed (rider confirms actual values) or cancelled (with optional cancellation fee)
- [ ] Add a completed trip directly, without checking it first
- [ ] History list: date, platform, fare, costs, net profit; filter by status
- [ ] Edit and delete with confirmation
- [ ] `OtherIncome` entity and simple entry screen (incentives, tips, bonuses)
- [ ] Empty state for new users
- [ ] Test proving offers are excluded from all totals

**Done when:** trips and income survive restarts, and no offer is ever counted as income.

## Phase 6 — Fuel log and expenses

- [ ] `FuelLog` entity, entry screen and list (with full-tank flag)
- [ ] `Expense` entity, entry screen and list (no fuel category)
- [ ] Actual km/L calculated from full-tank fills, with an optional suggestion to update the vehicle setting
- [ ] Migrations and migration tests for each schema change

**Done when:** fuel and expenses are recorded in exactly one place each.

## Phase 6b — Fuel price updates

- [ ] Publish the first fuel price file (owner) and add its URL as a `BuildConfig` field
- [x] Vehicle fields: `fuelType`, `fuelPriceSource`, `fuelPriceEffectiveDate`, `autoUpdateFuelPrice` (included in database v1 in Phase 3, so no migration is needed)
- [ ] `FuelPriceSource` in `data/remote`: fetch, validate, cache the last valid list in DataStore
- [ ] 12-hour throttle; silent failure; offline uses the last valid list
- [ ] Fuel price screen: fuel type, current price with its source and date, auto-update switch
- [ ] "New price" prompt with both prices and the change in fuel cost per km
- [ ] Hand-edited price switches the source to manual and turns auto-update off
- [ ] "May be out of date" label after 45 days
- [ ] Tests from `CLAUDE.md` section 8a
- [ ] Privacy policy and Data safety answers updated for the download

**Done when:** a new published price reaches the rider as a prompt (or automatically, if they chose that), never overwrites a price they set by hand, and never changes a past trip.

## Phase 7 — Dashboard, reports and export

- [ ] Accounting use case implementing both views from `CLAUDE.md` section 7, with tests including a no-double-counting test
- [ ] Home: today's net profit, gross income, recorded expenses, completed trips, total km, and a large Check a Ride button
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

- [ ] `AccountProvider` / `SyncProvider` interfaces with a no-op implementation; app builds and runs with Firebase absent
- [ ] Auth screens: Register, Login, phone OTP verify, each with a prominent "Skip — use without an account"
- [ ] Firebase Auth: email/password, Google, phone OTP (billing + SHA-256 set up)
- [ ] SMS cost controls: region policy locked to +94, verification rate-limiting, email/Google shown before phone; Firebase budget alerts at 50/80/100%
- [ ] Settings → Account & cloud backup entry; create an account later without losing local data
- [ ] Firestore per-user storage with security rules isolating each rider; offline persistence on
- [ ] Sync: local stays authoritative, last-write-wins by `updatedAt`, delete tombstones; never replaces local with empty cloud
- [ ] Sign-out keeps local data; "Delete account and cloud data" action
- [ ] Play Data safety form and privacy policy updated; marketing promise reworded

**Done when:** a rider can use the app fully without an account, optionally sign in to back up, reinstall on another phone and restore their history, and sign out without losing anything on-device.

## Phase 8d — Plans, profile and Premium

- [ ] `PlanProvider` with feature flags for the three tiers; features check flags, not tier names directly
- [ ] Profile screen: tier badge, rides-per-day chart, lifetime stats, account and sign-out
- [ ] Premium screen listing the three tiers; Google Play Billing integration
- [ ] Feature gates wired: reports range, export, multiple vehicles, OCR, widgets, tax summaries, ads
- [ ] Entitlement cached locally so Premium works offline once verified; re-check when online
- [ ] No ride or ride-check caps anywhere (core rule); downgrade never deletes data

**Done when:** every tier can track unlimited rides, Premium unlocks only the extra features, a paid rider keeps working offline, and a lapsed rider keeps all their data with just the gated features off.

## Phase 9 — Release readiness and ads

- [ ] `AdsProvider` interface, no-op implementation, AdMob implementation with test IDs
- [ ] Ads kept away from Check a Ride and data entry; no interstitials during ride checks
- [ ] Privacy policy and Data safety answers that match the actual app
- [ ] Check the current Play target API and policy requirements
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

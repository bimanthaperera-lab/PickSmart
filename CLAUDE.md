# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Picksmart — project instructions.** This file holds rules that are always true for this project. The roadmap and current status live in `PLAN.md`. Decisions made during development are recorded in `docs/decisions.md`.

**At the start of every session:** read `PLAN.md` to find the current phase and next step, and skim `docs/decisions.md` (create it if missing).
**At the end of every phase:** update the checkboxes and status in `PLAN.md`, add any new decisions to `docs/decisions.md`, and give the phase report described in section 13.

---

## 0. Non-negotiables

These rules override everything else in this file. Other sections refer back to them by number.

- **N1. Offers are never income.** A checked offer (`OFFER_CHECK`) must never appear in any earnings total.
- **N2. Nothing is counted twice.** Commission, fuel and maintenance follow the accounting rules in section 7 exactly.
- **N3. Calculations are pure and tested.** All formulas live in `domain/calculator`, never in Composables or ViewModels, and are covered by the golden tests in section 6.
- **N4. Local-first; the account is optional.** The app is fully usable with no account and no backend, and that is the default. The agreed network features are: the read-only fuel price download (section 8a, no user data), and an optional account with cloud backup (section 14, only after the rider signs in and consents). No analytics, location, ads (until the final phase) or other uploads. A rider who skips the account keeps everything on-device, as before.
- **N5. Driver apps are never controlled; reading is opt-in and limited.** Picksmart never acts inside another app: no automated tap, gesture, typing or scrolling, no MediaProjection or screen capture, and no overlay on top of its controls. The one thing allowed is the **offer reader** (section 9b): an AccessibilityService, off by default and turned on by the rider after a plain disclosure, that reads only the text of a ride offer in the listed ride apps, keeps it in memory to calculate the result, and never logs, stores or uploads that text. The one exception is the offer's pickup and "Where to" places, which may fill Add trip and are saved only if the rider keeps them and taps Save (owner decision 2026-10-29). Owner decision 2026-10-26; the risks the owner accepted are in section 9b.
- **N6. OCR and screen-read output is untrusted.** Values from a screenshot or from the offer reader are never saved without the rider reviewing and confirming them. The offer reader may show a live estimate in the floating badge, labelled as an estimate; saving that offer still goes through a form the rider checks (Add trip).
- **N7. Records are never destroyed silently.** No destructive Room migrations, and every delete needs confirmation. A rider's data survives every app update: each schema change ships a tested `Migration`, `fallbackToDestructiveMigration` is never used in any build type, and an update never resets, clears or re-onboards a rider who already has data.
- **N8. Results are verified, not claimed.** Never say a build or test passed unless you ran it and saw it pass.
- **N9. Offline-first.** Every core screen (Check a Ride, History, Reports, fuel log, expenses, income, alerts inbox) works with no internet, using data on the device. The online features are the fuel price download (section 8a), which degrades to the last saved price, and account sign-in and sync (section 14), which is optional and runs in the background; neither ever blocks a core screen. Sign-in and verification themselves need internet and say so.

## 1. Project overview

**Picksmart** is an Android-first, Sri Lanka-focused profit tracker for Uber and PickMe drivers, courier and delivery riders, and people delivering goods for their own shop or small business.

Core promise: **Know your estimated real profit before you ride.**

The app estimates whether a ride offer is worth taking, then tracks actual daily, weekly and monthly profit after commission, fuel, maintenance and other costs. It is a lightweight utility. Priorities, in order: correct numbers, speed, clarity, offline use, privacy, and smooth performance on low-to-mid-range Android phones.

## 2. How to work in this repository

Act as a careful senior Android engineer with good product judgement.

- Inspect the existing code before changing anything. Preserve working features and follow the existing architecture unless there is a strong, stated reason to change it.
- Work in small, reviewable steps. Never generate the whole app in one pass.
- Before a major step, say in two or three sentences what you plan to do, then do it.
- Run the relevant commands from section 4 after meaningful changes and fix anything you broke.
- Do not leave core screens as placeholders, fake buttons or TODO-only stubs.
- Use the defaults in this file and in `docs/decisions.md`. Ask the owner only when a missing decision truly blocks progress; otherwise pick a sensible default, record it in `docs/decisions.md`, and continue.
- Keep dependencies minimal. Check existing Gradle, AGP and Kotlin versions before changing them.
- Never invent real-world fares, fuel prices or commission rates, or claim platform API access. Use user-editable values. The app offers no sample values to riders (onboarding asks for the rider's own numbers); sample data in previews and tests must be clearly labelled as sample data.

### Git

- Commit after each working step, using a short imperative message (for example `Add ProfitCalculator with golden tests`).
- Never commit `local.properties`, keystores, signing configs, API keys, `build/` outputs, or real screenshots containing personal data.

## 3. Technology stack and fixed decisions

| Area | Decision |
| --- | --- |
| Language | Kotlin |
| JDK | 17 |
| UI | Jetpack Compose + Material 3, edge-to-edge |
| Architecture | MVVM: UI → ViewModel → use case / repository → data source |
| Dependency injection | Manual DI through a single `AppContainer` (no Hilt in the MVP) |
| Navigation | Navigation Compose with type-safe routes |
| Database | Room with `exportSchema = true`; schemas committed under `app/schemas/` |
| Preferences | DataStore (Preferences) |
| Dates | `java.time`, zone fixed to `Asia/Colombo` |
| Charts | Native Compose `Canvas` (no chart library unless a clear need appears) |
| Image import | Android Photo Picker and share intents |
| OCR | ML Kit Text Recognition v2, Latin script, unbundled model via Google Play services |
| Background work | WorkManager for the daily summary and the fuel price check only. No continuous foreground service in the MVP. |
| Offer reader (optional) | A read-only `AccessibilityService` limited to the ride apps' packages, `canPerformGestures=false`, off by default (section 9b). It draws its result badge as an accessibility overlay. |
| Notifications | Local only, through `NotificationManager`, one channel per type. No push service (no Firebase). |
| Network | A read-only HTTPS GET for the fuel price file (section 8a), and the optional account/sync in section 14. The price file URL is a `BuildConfig` field. |
| Accounts & sync (optional) | Firebase Authentication (email/password and Google; Facebook later; no phone OTP) and Cloud Firestore for per-user backup, behind an interface so the app builds and runs with them absent. See section 14. |
| Ads | Google Mobile Ads SDK (AdMob), only in the final phase, behind an interface |
| Tests | JUnit unit tests for domain logic; Room DAO and migration tests |
| minSdk | 26 (native `java.time`, wide coverage of Sri Lankan devices) |
| compileSdk / targetSdk | Latest stable SDK. Before release, check the current Google Play target API requirement. |
| Default package | `lk.picksmart` — the owner can change this; see `PLAN.md` open questions |

## 4. Commands

```text
Build debug APK:   ./gradlew assembleDebug
Unit tests:        ./gradlew testDebugUnitTest
Device tests:      ./gradlew connectedDebugAndroidTest   (only when a device or emulator is available)
Lint:              ./gradlew lint
Single unit test:  ./gradlew testDebugUnitTest --tests "lk.picksmart.domain.calculator.ProfitCalculatorTest"
Single device test: ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=lk.picksmart.data.local.SchemaTest
```

On this Windows machine (Git Bash):

- `java` is not on `PATH`. Before Gradle, run `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (JDK 25; bytecode targets Java 17, see `docs/decisions.md`).
- The emulator is the AVD `Pixel_8_Pro`. Start it in the background with `"$LOCALAPPDATA/Android/Sdk/emulator/emulator" -avd Pixel_8_Pro -no-snapshot-save -no-audio` before device tests.
- `connectedDebugAndroidTest` uninstalls the app when it finishes; reinstall before testing by hand.
- Git Bash rewrites `/sdcard/...` in `adb shell` arguments. Set `MSYS_NO_PATHCONV=1` for those, and then give local files to adb as Windows paths (`C:/Users/...`).
- `local.properties` must escape the drive colon (`sdk.dir=C\:/Users/USER/AppData/Local/Android/Sdk`), or lint reports `PropertyEscape`.
- Known, accepted lint warnings (generated logo paths, `mipmap-anydpi-v26`, DataStore version) are listed in `docs/decisions.md`. Lint must stay at 0 errors.

If a command cannot run in the current environment (for example, no SDK or no emulator), say so plainly instead of implying it passed.

## 5. Locale and formatting

- Currency: LKR, displayed as `Rs. 1,250.00`. All formatting goes through one formatter in `util/`, with Western digits.
- Units: kilometres, litres, km/L.
- Vehicle types: `MOTORCYCLE`, `THREE_WHEELER`, `CAR`, `OTHER`.
- Fuel types: `PETROL_92`, `PETROL_95`, `AUTO_DIESEL`, `SUPER_DIESEL`, shown as "Petrol 92 Octane", "Petrol 95 Octane", "Auto Diesel" and "Super Diesel".
- Every user-facing string goes in `strings.xml` from day one. No hardcoded text in Composables. English first, then Sinhala (`values-si`) and Tamil (`values-ta`), translated by the owner. A string not translated yet shows in English, so lint reports `MissingTranslation` as a warning, not an error. Strings marked `translatable="false"` never go in the translation files. Apostrophes in translations are escaped (`\'`), and Sinhala plurals show the number in the `one` form, since Sinhala uses `one` for 0 as well as 1. Play bundles keep every language (`bundle.language.enableSplit = false`) because the rider chooses the app language inside Picksmart. Dates and times are formatted with `dateFormatter(pattern, locale)` (`util/DateFormats.kt`), never `DateTimeFormatter.ofPattern` directly, so Sinhala shows calendar month names (ජන … ඔක්, නොවැ, දෙසැ) rather than the traditional ones (වප්, ඉල්) some Android versions give.
- Fuel price, fuel efficiency, commission and maintenance rate are always user-editable. Never hardcode one value as "the" correct one.

### Date boundaries

- A trip belongs to the calendar day (`Asia/Colombo`, midnight to midnight) of its trip date and time.
- Weeks run Monday to Sunday. Months are calendar months.
- Night shifts that cross midnight are split across two days in the MVP. A "shift" view is a possible later feature.

## 6. Profit calculator

The calculator is a pure Kotlin class, `domain/calculator/ProfitCalculator`, with no Android dependencies.

### Inputs

| Field | Meaning |
| --- | --- |
| `fare` | Amount offered or earned (its meaning depends on `fareMode`) |
| `fareMode` | `GROSS_BEFORE_COMMISSION` or `PAYOUT_AFTER_COMMISSION` |
| `commissionType` | `AMOUNT` or `PERCENT` |
| `commissionValue` | Rupee amount, or percentage of `fare` |
| `pickupKm` | Distance driven to reach the pickup |
| `tripKm` | Distance driven during the trip |
| `returnKm` | Optional unpaid or empty return distance (shown as optional in the UI) |
| `fuelEfficiencyKmPerLitre` | Expected km per litre |
| `fuelPricePerLitre` | Rs. per litre |
| `maintenanceReservePerKm` | Estimated maintenance allocation, Rs. per km |
| `otherCosts` | Other costs for this trip |
| `targetProfitPerKm` | Optional rider target, Rs. per km |
| `tip` | A tip shown on a ride offer, paid on top of the fare (0 when none; only the offer reader sets it) |

### Formulas

```text
totalKm            = pickupKm + tripKm + returnKm
fuelCost           = totalKm × fuelPricePerLitre ÷ fuelEfficiencyKmPerLitre
maintenanceReserve = totalKm × maintenanceReservePerKm

commission =
  if fareMode == PAYOUT_AFTER_COMMISSION → 0   (already removed; the commission field is hidden)
  else if commissionType == AMOUNT       → commissionValue
  else                                   → fare × commissionValue ÷ 100

netProfit      = fare − commission − fuelCost − maintenanceReserve − otherCosts + tip
                 (no commission is taken from the tip)
netProfitPerKm = netProfit ÷ totalKm, only when totalKm > 0, otherwise null
```

### Result band

- `LOSS` when `netProfit < 0`.
- When no target is set: `PROFIT` when `netProfit > 0`, `BREAK_EVEN` when `netProfit == 0`.
- When a target is set and `netProfitPerKm` exists: `GOOD` when `netProfitPerKm ≥ targetProfitPerKm`, otherwise `BELOW_TARGET`.
- The UI never relies on colour alone: every band has a text label and an icon.

### Validation

- Distances, fare, commission, prices and costs must be ≥ 0.
- `fuelEfficiencyKmPerLitre` must be > 0.
- A percentage commission must be between 0 and 100.
- Invalid input returns a typed validation error. It never throws into the UI and never produces NaN or Infinity.

### Money and rounding

- Money is stored as `Long` cents and calculated with `BigDecimal`.
- Non-money quantities (km, litres, km/L) are stored as `Double` and converted with `BigDecimal.valueOf()` inside the calculator.
- Intermediate division uses scale 10. Final money values round `HALF_UP` to 2 decimal places (cents).
- Never use `Double` for money totals.

### Golden tests (must pass exactly)

```text
G1  Gross mode, amount commission
    fare 1000, commission AMOUNT 200, pickup 2, trip 8, return 0,
    40 km/L, Rs. 300/L, Rs. 2/km, other 0
    → totalKm 10, fuel 75.00, reserve 20.00, commission 200.00,
      net 705.00, per km 70.50

G2  Same as G1, but commission PERCENT 20
    → commission 200.00, net 705.00

G3  Payout mode
    fare 800, PAYOUT_AFTER_COMMISSION, commission field ignored,
    other inputs as in G1
    → commission 0.00, net 705.00

G4  Rounding
    totalKm 7 (pickup 1, trip 6), 45 km/L, Rs. 297.50/L, reserve 0
    → fuel 46.28   (7 × 297.50 ÷ 45 = 46.2777…)

G5  Zero distance
    fare 500, commission AMOUNT 0, all distances 0
    → fuel 0.00, reserve 0.00, net 500.00, per km null

G6  Loss
    fare 300, commission AMOUNT 60, pickup 5, trip 10, return 5,
    40 km/L, Rs. 300/L, Rs. 2/km
    → fuel 150.00, reserve 40.00, net 50.00, band PROFIT
    With other costs 100 → net −50.00, band LOSS

G7  Target band
    G1 with targetProfitPerKm 80 → band BELOW_TARGET
    G1 with targetProfitPerKm 70 → band GOOD
```

Also test: negative inputs, efficiency of 0, percentage above 100, and very large values (no overflow).

## 7. Accounting rules

Reports show two clearly labelled views. Never mix their components.

### Estimated profit

The sum of calculator results for trips, using each trip's stored input snapshot:

- `COMPLETED` trips: full `netProfit`.
- `CANCELLED` trips: the cancellation fee (if any) minus estimated fuel and reserve for the distance actually driven (usually `pickupKm` only).
- Plus `OtherIncome` entries.
- `OFFER_CHECK` trips: **excluded** (N1).

### Cash profit

What actually moved in and out of the rider's pocket:

- **Income:** completed trip earnings after commission, cancellation fees, and `OtherIncome`.
- **Minus:** `Expense` entries and `FuelLog` totals.
- **Excludes:** the maintenance reserve (it is an estimate, not cash) and estimated trip fuel (actual fuel comes from `FuelLog`).

### Rules

- Fuel is recorded **only** in `FuelLog`. `Expense` has no fuel category.
- Each report states which view it shows. When data is incomplete (for example, trips without distances, or a month with no fuel logs), the report says so.
- These rules are implemented in a single use case in `domain/usecase`, documented in KDoc, and unit-tested, including at least one test proving that no amount is counted twice.

## 8. Data model

Adapt names to project conventions, but keep the meaning. Every money field is `Long` cents.

**Vehicle**
`id`, `name`, `type`, `fuelType`, `fuelPricePerLitreCents`, `fuelPriceSource` (`MANUAL` or `PUBLISHED`), `fuelPriceEffectiveDate` (nullable), `autoUpdateFuelPrice` (default `false`), `fuelEfficiencyKmPerLitre`, `maintenanceReservePerKmCents`, `targetProfitPerKmCents` (nullable), `archived`, `createdAt`, `updatedAt`

**Trip**
`id`, `vehicleId`, `createdAt`, `tripDateTime`, `platform` (`UBER`, `PICKME`, `COURIER`, `OWN_DELIVERY`, `OTHER`), `status` (`OFFER_CHECK`, `COMPLETED`, `CANCELLED`), `fareCents`, `fareMode`, `commissionType`, `commissionValue`, `pickupKm`, `tripKm`, `returnKm`, `otherCostsCents`, `cancellationFeeCents` (nullable), `notes` (nullable), `pickupLocation` (nullable), `dropOffLocation` (nullable; "Where to")

The locations are optional free text (up to 120 characters), typed by the rider on Check a Ride or Add / Edit trip, or filled from a ride offer (section 9b). They never change any calculation and are never a map position.

Plus a **snapshot of the rates used at the time**: `fuelPricePerLitreCentsSnapshot`, `fuelEfficiencySnapshot`, `maintenanceReservePerKmCentsSnapshot`. Changing a vehicle's rates later must never change past trips.

Computed values (fuel cost, net profit and so on) are derived through `ProfitCalculator` and are not stored in the MVP.

**OtherIncome**
`id`, `date`, `type` (`INCENTIVE`, `TIP`, `BONUS`, `OTHER`), `platform` (nullable), `amountCents`, `note` (nullable)

**Expense**
`id`, `vehicleId` (nullable), `date`, `category` (`MAINTENANCE_REPAIR`, `PARKING`, `MOBILE_DATA`, `TOLLS`, `OTHER`), `amountCents`, `note` (nullable), `odometerKm` (nullable)

**FuelLog**
`id`, `vehicleId`, `date`, `litres`, `pricePerLitreCents`, `totalCents`, `odometerKm` (nullable), `fullTank`

Actual efficiency = km between two full-tank fills ÷ litres added after the first fill. When it differs noticeably from the vehicle's setting, the app may suggest an update but never changes the setting automatically.

**Alert** (in-app inbox, section 9a)
`id`, `type` (`FUEL_PRICE`, `DAILY_SUMMARY`, `UNFINISHED_OFFERS`, `EFFICIENCY_SUGGESTION`, `OTHER`), `title`, `body`, `createdAt`, `read`, `deepLink` (nullable)

**DataStore**
`defaultVehicleId`, `onboardingComplete`, `themeMode`, last-used Check a Ride inputs, and the last valid fuel price list (`effectiveFrom`, `fetchedAt`, prices per fuel type).

### Migrations (N7)

- `exportSchema = true`, with schemas committed to the repository.
- Every schema change after version 1 gets an explicit `Migration` and a `MigrationTestHelper` test.
- `fallbackToDestructiveMigration` is never used, in any build type.

### Surviving app updates (N7)

- A new app version must open the existing database and keep every trip, fuel log, expense, income, vehicle, alert and setting. There is a test that opens a database created by the previous schema version and asserts the rows are intact after migration.
- Onboarding runs only when `onboardingComplete` is false; an update never flips it back, so an existing rider is never sent through setup again or given sample values over their own.
- DataStore keys are only added or migrated, never silently dropped. Renaming a key ships a read-old-write-new migration.
- Before a release, the update path is tested by installing the previous build, adding data, then installing the new build over it and checking the data and settings are unchanged.

## 8a. Fuel price updates

The app can offer the current published Sri Lankan pump price, but the rider's own number always wins.

### Source

- The owner hosts one small JSON file (default: a public GitHub repository) and updates it whenever the Ceylon Petroleum Corporation (CPC) announces new retail prices. Do not scrape the CPC website from the app; a site change would break every installed copy.
- File shape:

```json
{
  "schema": 1,
  "source": "Ceylon Petroleum Corporation (CPC) retail prices",
  "effectiveFrom": "2026-10-01",
  "pricesCentsPerLitre": {
    "PETROL_92": 41400,
    "PETROL_95": 47500,
    "AUTO_DIESEL": 39200,
    "SUPER_DIESEL": 52800
  }
}
```

- The app never ships these prices in code. Before the first successful download, the fuel price is whatever the rider entered.

### Fetching

- Fetch only when the device is online: on app start and when the rider opens Check a Ride, at most once every 12 hours, with a 10-second timeout.
- The request sends no user data, identifiers, cookies or analytics. It is a plain HTTPS GET of the file.
- Reject the file (and keep the last valid list) when the schema is unknown, a price is missing, a price is outside Rs. 50–2,000 per litre, or a price changed by more than 40% from the last valid list.
- Failures are silent apart from the "last updated" date. They never block any screen.

### Applying a new price

- `autoUpdateFuelPrice = false` (default): when the published price for the vehicle's fuel type differs from its current price, show a prompt with both prices and the effect on fuel cost per km. "Update" sets the price and `fuelPriceSource = PUBLISHED`; "Keep" leaves it and does not ask again for that `effectiveFrom`.
- `autoUpdateFuelPrice = true`: apply the new price automatically and show a one-time notice.
- If the rider edits the price by hand, set `fuelPriceSource = MANUAL` and turn auto-update off, so a station with a different price (for example, Sinopec) is never overwritten.
- Always show where the price came from: "Rs. 414.00 · CPC price from 1 Oct" or "Rs. 300.00 · set by you".
- When the last valid list is older than 45 days, show "may be out of date" next to the price.
- Past trips keep their rate snapshot (section 8). A new price only affects new checks.

### Tests

Parsing a valid file, each rejection rule, the 12-hour throttle, a manual price never being overwritten when auto-update is off, and past trip results staying unchanged after a price update.

## 9. UI and UX

### Check a Ride speed (top priority)

- Riders often have only seconds to decide. A rider must reach a result after editing **three fields or fewer** in the common case.
- Pre-fill the last-used values and the default vehicle's rates. Focus the fare field with a numeric keyboard. Recalculate live as the rider types. No network calls.
- The result (net profit, per km, band) is visible without scrolling on a small screen.
- Show a short line on the Check a Ride screen: results are estimates, and offers should only be checked when stopped safely.

### Brand

- App name: **Picksmart** (one word, as in the logo). Launcher label: "Picksmart".
- Logo: a yellow location pin formed from a "P" with a forward arrow through it, over the "Picksmart" wordmark ("Pick" off-white, "smart" yellow). The owner supplies the source file; keep it in `design/brand/`.
- Brand yellow `#FBBB23` is the accent colour (primary buttons, active navigation, focus). Background stays near-black.
- The launcher icon is an adaptive icon: the pin mark only (no wordmark) on a transparent foreground layer inside the 66 dp safe zone, with a near-black background layer and a monochrome version for themed icons. The pin's inner white detail needs a visible outline so it reads on the themed (single-colour) icon.
- The mark is yellow, so it disappears on a yellow surface. On any yellow or light background (a primary button, the Quick Settings tile, the collapsed bubble badge), place the mark inside a near-black circle; on the near-black app background, use the mark directly.
- Use the full logo only on the splash screen and onboarding; use the pin mark alone at small sizes.
- **Splash screen** follows the app's theme (light, dark or system). The logo is on screen from the moment the app is tapped, never a blank screen: Android 12+ shows the pin on its own splash, and the app's splash starts with the pin at exactly that size and place (on its near-black circle in light mode), then the pin shrinks and rises while the "Picksmart" wordmark and the tagline "Know your real profit before you ride." fade in. "smart" is brand yellow in both themes; "Pick" is off-white in dark mode and near-black in light mode. After 0.9 s, and once settings have loaded, it fades into the app (0.3 s). It shows once per launch, never on rotation.

### Visual direction

- Calm, modern, high-contrast utility app. Dark theme polished; light and system themes supported.
- Green for profit, red for loss, amber for below target, neutral for information. Always paired with a text label.
- Large numbers with clear "Rs." labels. Short forms, correct keyboard types, the primary action reachable with one hand.
- No heavy gradients, glass effects, decorative animation or filler illustrations.
- Every screen has empty, loading, validation and error states.
- Touch targets of at least 48 dp, content descriptions on icons, and layouts that work at 200% font scale.

### Navigation

Bottom navigation: Home, Check Ride, History, Account. The Account tab replaces the old Settings tab: a profile card at the top, and every setting below it, unchanged (section 14 describes the card and the Profile screen). Reports, the fuel log and expenses are reached from the Account tab (fuel log and expenses also from Home). Android back navigation must work everywhere.

### Onboarding

On first launch, the splash settles into a welcome screen (the logo stays where the splash left it) with **Get started** and **I already have an account**. Get started shows three short "how it works" pages (Next, or Skip to the vehicle), then a short setup asks for vehicle type, fuel type, fuel price, fuel efficiency and maintenance reserve, then a **Permissions** step offers notifications (Allow, the system dialog) and Read ride offers (Turn on, the disclosure, then Android's Accessibility settings), both optional with Continue, then shows the one-line safety notice. I already have an account opens Login / Register, and signing in goes straight to the vehicle setup. There is no "use sample values" or skip option for the vehicle: the rider uses the app after entering their own numbers.

- Fields start empty, with no suggested values. The one exception is the fuel price: if a published CPC list is already saved on the device (section 8a), it is pre-filled for the chosen fuel type with its source shown ("CPC price from 1 Oct"), and the rider can change it.
- Fuel efficiency must be above 0. Maintenance reserve may be 0, with a short hint explaining what it is for. The profit target stays optional.
- Riders who chose sample values in an earlier version keep their vehicle as it is (N7). Its "Sample" label and review notice stay until they save the vehicle form, and they are never sent through onboarding again.

### Vehicles

- A rider can have several vehicles (for example a motorcycle and a three-wheeler). One is the **active vehicle** (`defaultVehicleId`); its rates pre-fill Check a Ride and new trips.
- **Switching:** the Vehicles list on the Account tab (add, edit, set active, archive). Check a Ride and Add trip have no vehicle switch (owner decision 2026-10-29): they use the active vehicle, and Edit trip only names a saved trip's vehicle. Switching never changes a saved trip, because each trip keeps its own `vehicleId` and rate snapshot (section 8).
- **Limit:** 1 active (not archived) vehicle on Free (not signed in), up to 3 when signed in, unlimited on Premium (section 15). Archived vehicles don't count, and their trips stay in History and Reports. Archiving the active vehicle asks which one becomes active; the last active vehicle can't be archived.
- Adding a vehicle past the limit opens an upgrade screen instead of the form: on Free, "Sign in to add up to 3 vehicles" with Sign up and Sign in; when signed in, "More vehicles with Premium".
- A rider who signs out, or whose Premium lapses, keeps every vehicle they have and can still switch between them and record trips; they just can't add another until they are under their limit. The same holds for a rider who already had more vehicles before the limit shipped. The limit is a feature gate, never a reason to block checking or recording rides on any vehicle the rider already has.
- Fuel log and expense forms pick a vehicle, defaulting to the active one. Reports combine all vehicles; a per-vehicle filter can come later.
- Fuel prices: the Home notice, the prompt and the Fuel price screen follow the active vehicle. Automatic updates apply to every vehicle that has auto-update on.

## 9a. Glance surfaces and notifications

The goal is a near-instant read on an offer. Every surface below takes numbers the rider types or confirms, except the opt-in offer reader (section 9b), which fills the badge from the offer's text and labels the result as an estimate.

### Shift reminder

- The rider starts a shift from Home or the tile. While it is on, an ongoing, silent, low-priority notification pins the **minimum fare per km** (the "magic number") plus the fare needed at 5, 10 and 15 km.
- Magic number = `(targetProfitPerKm + fuelCostPerKm + maintenanceReservePerKm) ÷ (1 − commissionFraction)` when commission is a percentage; when commission is a fixed amount it is added after, and the figure is labelled an estimate. It recalculates whenever fuel price, efficiency, reserve, target or commission change.
- No sound or vibration (the rider may be riding). Tapping it opens Check a Ride. It shows on the lock screen.

### Quick Settings tile

- A tile that opens a small fare check (fare + distance → profit, per km, band) without opening the full app. Saving a ride hands off to the app.

### Floating bubble (optional, off by default, advanced)

A draggable overlay that expands into the same small calculator. The rider types the fare and distance; with the offer reader on (section 9b), the badge also shows the latest offer's result on its own. It is off by default and lives behind an "advanced" switch in Notifications & glance, because of the obscured-touch limit below.

**Obscured-touch limit (must be designed around).** When an overlay sits over another app's control, Android marks touches on that control as obscured (`MotionEvent.FLAG_WINDOW_IS_OBSCURED`), and an app that calls `setFilterTouchesWhenObscured(true)` — some banking and ride apps do — will silently ignore the tap. So the bubble can stop a rider accepting a request if it covers the Accept button. This cannot be fully removed while any overlay exists; it can only be avoided by keeping the overlay away from that button. The rules below are mandatory, not cosmetic:

- **Two states, one visible at a time.** Collapsed is a small badge showing the magic number (for example "Rs. 91/km"); expanded is the calculator. Tapping the badge expands it; the expanded "−" collapses it. Never show both at once.
- **Auto-collapse on a request.** When a ride request is likely on screen, collapse to the badge so the calculator never covers the request card. (With the offer reader on, it knows when an offer card is showing; otherwise use a timer.)
- **Safe-corner snap.** The badge defaults to a top corner and snaps back out of the bottom-centre region (the lower ~25% of the screen, where Accept buttons usually sit). The rider can drag it, but it will not rest in that region.
- **Hold to hide.** Long-pressing the badge hides the overlay immediately (a panic gesture); the rider brings it back from the tile or Settings.
- **On-enable warning.** Turning the bubble on requests `SYSTEM_ALERT_WINDOW` with a plain explanation and warns that some ride apps block taps under an overlay, so they should move the badge aside before accepting. The app is fully usable with the bubble off; the shift notification (not an overlay) is the default glance surface and can never block a tap.

### Other notifications

- Fuel price changed (links to section 8a), daily summary at a rider-set time, and an optional reminder to mark offers the rider took as completed trips.
- One `NotificationChannel` per type, so the rider can control each from Android settings.
- On Android 13+, the notification permission is offered on the onboarding Permissions step, with what it is for, never as a bare dialog on launch (owner decision 2026-10-26). A rider who skipped it is asked again in context: when turning on Read ride offers, starting the first shift or enabling a summary.
- Scheduled items run through WorkManager.

### In-app alerts inbox

Android notifications are easy to miss or swipe away, so every alert is also kept inside the app.

- A bell icon in the Home header opens the Alerts inbox and shows an unread count. Each alert has a title, a short body, a time, read/unread state, and where useful a tap target (fuel price → Fuel price screen; summary → Reports; unfinished offers → History).
- Alerts are stored locally in a Room `Alert` table, not just posted to the shade, so they survive a reboot and are readable offline. Keep them 30 days, then delete.
- Posting a system notification and adding an inbox row happen together, so the two never disagree. Opening the inbox or an alert marks it read in both places.
- The inbox never shows anything that needs the network to load; a fuel price alert is written when the price is fetched and then reads offline like the rest.

## 9b. Offer reader (owner decision 2026-10-26)

Until 2026-10-26 auto-reading was excluded. The owner decided to allow **reading** the ride offer, never acting on it, so the badge can show PROFIT or LOSS the moment an offer appears. What it does and the rules it must keep:

- **What it reads.** An `AccessibilityService` (`glance/OfferReaderService`) listens only to the ride apps' packages listed in its config (`com.ubercab.driver` and PickMe Driver, `com.pickme.driver.byod`). PickMe shows a trip as a popup in its own window, which is often not the active window, so the service sets `flagRetrieveInteractiveWindows` and looks at every window (`getWindows()`), top first; of another app's window it checks only the root's package name. When a window changes it walks the listed app's node tree, collects the visible text in memory, and hands it to the pure parser (`domain/usecase/OfferText.kt`), which finds the fare and the pickup and trip distances (`parseOfferText`; the result comes from `checkOffer`). Nothing else is kept.
- **What it never does.** No `performAction`, gestures (`canPerformGestures=false`), typing, scrolling or global actions; no screen capture; no logging, storing or uploading of screen text (not even in debug logs), apart from the pickup and "Where to" places below; no reading of any app outside the list.
- **How the result is shown.** The same calculator and rates as Check a Ride: the active vehicle's rates and the last-used fee settings, so payout-mode riders get payout maths. The badge shows the band label and icon ("PROFIT", "LOSS", "BELOW TARGET"), the net amount and per km, "Estimate · <vehicle>", and "Powered by Picksmart". It sits in a top corner, never over the Accept button, and follows the obscured-touch rules in section 9a. It clears when the offer card closes or after a short time.
- **Opt-in.** Off by default. Turning it on shows a prominent disclosure (what is read, that nothing leaves the phone, the account risk below) before sending the rider to Android's Accessibility settings. It can be turned off there or from the app at any time. The rest of the app works the same with it off.
- **Risks the owner accepted** (2026-10-26, after they were explained):
  - **Rider accounts.** Ride apps can see that an accessibility service is enabled and can't tell reading from clicking. Their driver terms generally forbid third-party tools that interact with or extract data from the app, so a driver could be warned or blocked. The disclosure says so plainly, and Picksmart never promises it is safe.
  - **Play policy.** A non-accessibility use of the AccessibilityService API needs the Play Console declaration, a prominent in-app disclosure and a video of the feature, and can still be rejected or removed. If Play rejects it, the reader is removed from the release build and the manual surfaces in section 9a remain.
  - **It can stop working.** A ride app can mark its views as sensitive to non-accessibility services (`accessibilityDataSensitive`, Android 14+) or change its layout at any time. A screen it can't read shows no badge, never a guess.
  - **Script.** The parser expects Latin digits and "Rs", "LKR" or "km"; an offer shown in other forms is not read.
- **Tips.** An amount after a "+" on the card (PickMe shows the passenger's tip as "+ LKR 200") is the tip, never the fare. The badge adds it to the profit after the commission and says "Includes Rs. … tip". A trip doesn't hold a tip: when the rider saves the offer as a completed trip, a switch (on by default) also saves the tip as a `TIP` in Other income, so it is counted once (N2).
- **Saving an offer.** When a shown offer leaves the screen, a silent notification asks "Took this ride? Save it as a trip" (`ui/notifications/OfferSaveNotification`), because the app can't tell whether the rider accepted it. Tapping it opens Add trip filled with the offer's fare, distances, ride app and the time it was read, marked "Filled from the ride offer"; nothing is saved until the rider taps Save (N6). The pickup and "Where to" places (the line under each distance on the card, `parseOfferText`) fill the form too; the rider can change or clear them before saving (owner decision 2026-10-29). Only the parsed numbers and those two places go into the notification's tap, never other screen text; the notification shows neither the places nor, on the lock screen, the amounts. The badge carries a small "Powered by Picksmart" line.
- **Debug probe.** `docs/screen-read-probe.md` describes a debug-only service (never in release builds) that logs money, distance and time values from the ride app's tree, and only the length of any other text, so the parser can be fitted to the real offer card. Its logs are never committed or shared.
- **Testing.** The parser is a pure function with text fixtures (made-up values, never real screenshots or passenger data). A debug-only sample offer screen lets the service be checked on an emulator without a ride app.

## 10. Privacy, safety and platform compliance

- See N4, N5 and N6.
- Request only the permissions an implemented feature needs. No precise location in the MVP.
- `INTERNET` is used for the fuel price download (section 8a), the optional account and sync (section 14), and, in the final phase, ads. The Data safety form declares the price download sends no personal data, and that the optional account stores an identifier, email, name, the optional mobile number, gender, date of birth and profile photo, and backed-up app data, for backup only.
- The offer reader's `BIND_ACCESSIBILITY_SERVICE` service is enabled only by the rider, after the disclosure in section 9b. The Data safety form and privacy policy say that offer text is read on the device only and never collected or shared, except a pickup and "Where to" place the rider chooses to save with a trip, which is backed up like the rest of the trip.
- `POST_NOTIFICATIONS` (Android 13+) is offered on the onboarding Permissions step with its purpose, and again in context, never as a bare dialog on launch. `SYSTEM_ALERT_WINDOW` (draw over other apps) is requested only if the rider turns on the floating bubble, and the app works fully without it.
- Screenshot import must be started by the user through the Photo Picker or the share sheet.
- Never claim an official Uber or PickMe integration, and never promise the app can stop a driver account from being blocked, nor that the offer reader is safe for a driver account.
- **Backup:** Android Auto Backup is enabled with explicit rules that include the Room databases, DataStore and the space registry (`data_spaces.xml`) and exclude caches. `hasFragileUserData` makes Android 10+ offer "Keep app data" when the app is uninstalled from the phone's settings or launcher (owner decision 2026-10-27). This is disclosed in the privacy policy. CSV export is the user's manual backup.
- Before release, check the current Google Play policies, Data safety requirements and AdMob requirements; they change.

### App and revenue protection

The aim is to make Picksmart hard to copy or crack without giving up offline use, speed or privacy.

- **Logic and data stay on the device.** Do not move the calculator, reports or records to a server to stop copying. The profit formula is simple arithmetic anyone can rebuild, a server cannot stop someone cloning the screens, and it would break N4 and N9, slow Check a Ride, and add backend cost for every install. Only the features in section 14 and below use the server.
- **Code.** Release builds keep R8 on (`isMinifyEnabled` and `isShrinkResources`). Keep `proguard-rules.pro` to the rules reflection or serialization actually needs. Upload each release's R8 mapping file to Play Console so crash reports stay readable; never ship or commit it.
- **No secrets in the APK.** No server keys, admin credentials, service-account JSON or Play Developer API credentials in the app or the repository; anything privileged runs on the server. `google-services.json` only identifies the Firebase project and is not a secret (it stays git-ignored anyway); protection comes from Firestore security rules and App Check.
- **Firebase App Check with the Play Integrity provider** guards Firestore (and Cloud Functions) from the first release with sync, so a modified or repackaged APK can't use cloud backup. It never affects local use: if the check fails, only backup stops, with a plain message.
- **Premium is verified on the server.** A Cloud Function verifies each Play purchase with the Google Play Developer API, acknowledges it, and stores the entitlement in `entitlements/{uid}`, which `firestore.rules` lets the rider read but never write (the `users/{uid}` profile accepts only the fields the app writes, so a rider can't grant themselves Premium); the app trusts that verified entitlement (cached for offline use, section 15), never a local flag alone. Cloud Functions need the Blaze plan (section 16).
- **Brand.** Code and design are protected by copyright automatically. The owner registers the "Picksmart" name and logo as a trademark in Sri Lanka (National Intellectual Property Office), so a copy using the name or logo can be reported and removed from Play; confirm the details with a lawyer. Keep the app's source repository private (the fuel price file lives in its own public repository).

## 11. OCR rules

- ML Kit Text Recognition supports Latin script only. Screenshots in Sinhala or Tamil will not parse; the app must handle this gracefully and fall back to manual entry.
- If the unbundled model is not available on the device, fall back to manual entry with a short explanation.
- Parsing tolerates variations in spacing, thousands separators, "Rs", "Rs.", "LKR" and decimal formats, and does not assume a fixed layout for any platform.
- Results always go to a review and edit screen (N6). Never save OCR values as completed earnings without confirmation.
- Do not log or keep screenshots. Release bitmaps when finished.
- Parser tests use text fixtures or sanitized images only. Never commit screenshots that contain names, phone numbers, plate numbers or addresses.

## 12. Ads rules (final phase only)

- AdMob sits behind an `AdsProvider` interface with a no-op implementation, so the app works fully when ads fail or are disabled.
- Use Google's test ad unit IDs during development. Never click real ads.
- No ads on the Check a Ride screen, near data-entry controls, or anywhere the rider may be making a quick decision. No interstitials during ride checking.
- Adding ads changes the Data safety disclosure; update the privacy policy at the same time.

## 13. Code structure and phase reports

```text
app/src/main/java/<package>/
  AppContainer.kt
  data/
    local/        (dao/, entity/, PicksmartDatabase.kt, migrations/)
    preferences/
    remote/       (FuelPriceSource: fetch, validate, cache)
    account/      (AccountProvider, FirebaseAccountProvider, NoOpAccountProvider, ProfilePhotoCache)
    repository/
  domain/
    model/
    calculator/
    usecase/
  ui/
    navigation/  theme/  components/
    onboarding/  home/  checkride/  history/
    fuel/  fuelprice/  expenses/  income/  reports/  ocr/
    settings/        (the settings list shown under the profile card on the Account tab)
    notifications/   (channels, shift reminder, scheduled WorkManager jobs)
    glance/          (Quick Settings tile service, floating bubble service, offer reader service and its badge)
    account/         (Account tab, Login, Register, Forgot password, Profile screen, form rules, Google picker)
    profile/         (Phase 8d additions to the Profile screen: tier, daily ride counts, lifetime stats)
    plan/            (PlanProvider, feature flags, Play Billing, Premium screen)
  util/
```

Keep business logic out of the UI. Prefer small, focused classes and files.

At the end of each phase, report:

- Files created or changed
- Features completed
- Commands run and their actual results
- Known issues and decisions still needed
- The recommended next step (also written into `PLAN.md`)

## 13a. Architecture as built

How the pieces fit today. Read this before adding a screen, a table or a setting.

- **Wiring.** `PicksmartApplication` creates one `AppContainer` and calls `start()`. The container holds what belongs to the phone (account, fuel prices, photos, `ProfitCalculator`, a UTC `Clock`) and the `DataSpace` on screen, which lazily builds that space's database, `UserPreferencesRepository`, repositories, use cases and backup; `container.trips` and the like read from the space on screen. Each ViewModel has a companion `Factory` (`viewModelFactory { initializer { ... } }`) that takes what it needs from the container via `APPLICATION_KEY`, plus `createSavedStateHandle()` when it reads route arguments.
- **Start-up gate.** `SplashScreen` (`ui/splash`) is drawn on the first frame, before `themeMode` and `onboardingComplete` load; its theme is the phone's setting at that moment, which on Android 12+ is already the app's (`setApplicationNightMode` from the stored theme). When 0.9 s have passed and both values have loaded, an `AnimatedContent` fades it into `PicksmartApp`, or switches instantly to `OnboardingScreen`, whose welcome (`WelcomeScreen`, also in `ui/splash`) is the splash's last frame plus buttons that fade in. `OnboardingScreen` has its own small `NavHost` so the Login, Register and Forgot password screens can open on top of setup. The system splash is removed at once (`setOnExitAnimationListener { it.remove() }`) because the first frame matches it: Android draws the launcher icon at 288 dp without its background on a dark window, and at 240 dp with its near-black circle on a light one, and the splash copies whichever applies. The activity handles `uiMode`, `locale` and `layoutDirection` changes itself instead of restarting, so changing the theme or the app language (Android 13+) redraws in place with no blank frame; screens that show the chosen language read it with `rememberChosenLanguage()`, which follows the configuration. The theme and setup flag come from `AppContainer.startupSettings`, a `StateFlow` that stays loaded, so a screen recreated later (a language change below Android 13) has them on its first frame. Finishing onboarding flips the DataStore flag, which switches the root automatically; nothing ever sets it back.
- **Account.** `AccountProvider` (`data/account`) is the only door to Firebase: `AppContainer.account` is `FirebaseAccountProvider` when the build has `app/google-services.json` (Gradle then applies the google-services plugin and sets `BuildConfig.FIREBASE_ENABLED` and `GOOGLE_WEB_CLIENT_ID`) and Firebase started, else `NoOpAccountProvider`, which reports `isAvailable = false`, and the card says sign-in isn't available. `accountUi()` (`ui/account/AccountUi.kt`) joins the profile, the rider's `PersonalDetails` (mobile number, gender and date of birth, DataStore keys `account_mobile_<uid>`, `account_gender_<uid>`, `account_birth_date_<uid>`) and the photo (`ProfilePhotoCache`, `files/profile_photo/`: the cached Google photo, and a photo the rider chose, which wins) for the Account tab and Profile screen. Form rules (`AuthForms.kt`) are pure and tested; `AuthFormViewModel` runs one request at a time, turns `AuthError` into a message, and handles Google: `requestGoogleIdToken()` shows Credential Manager's "Sign in with Google" over the activity, then Firebase signs in with the token. Login and Register replace each other on the back stack, so back always returns to the tab.
- **Navigation.** Routes are `@Serializable` types in `ui/navigation/Destinations.kt`. The `TopLevelDestination` enum drives the bottom bar, which shows only on those four tabs; switching tabs pops to Home and restores tab state. Detail screens (for example `EditVehicleRoute(vehicleId)`, where `0` means "add") hide the bar and are registered in `PicksmartApp.kt`.
- **Layers.** Room entities in `data/local/entity` carry `toDomain()` / `toEntity()` mappers; repositories return domain models from `domain/model`. Business rules are pure functions in `domain/usecase` (for example `applyVehicleEdit`, which enforces the manual fuel price rule from section 8a, and `countedProfitCents` in `TripProfit.kt`, the one place that decides what a trip adds to profit, offers never) so they are tested on the JVM; use-case classes there only orchestrate repositories (`CompleteOnboarding`).
- **Money in code.** `ProfitInput.commissionHundredths` stores the commission as one `Long` in hundredths of its unit (cents for `AMOUNT`, hundredths of a percent for `PERCENT`). Text in forms is parsed with `util/Parsing.kt` and cleaned while typing by `sanitizeDecimalInput` (used inside `NumberField`). Display goes through `rememberMoneyFormatter()`, which reads the translatable `currency_prefix` string.
- **Trip forms.** `TripFormState` wraps `CheckRideFormState`, so Check a Ride and Add / Edit trip share one set of parsing and validation rules; field helpers such as `FieldRow` and `FeeUnitToggle` are `internal` in `ui/checkride` for that reason.
- **Forms.** Form state keeps raw text; a pure `validate()` returns either a typed input or per-field errors (see `ui/vehicle/VehicleFormState.kt`). Onboarding and Edit vehicle share `VehicleFormFields`.
- **Record screens.** Other income, Fuel log and Expenses share `DetailHeader`, `MonthTotalCard`, `RecordRow` (amount moves under the details above 130% font) and `RecordsGroup` (the Home and Settings → Records rows) from `ui/components`. Required amounts in their forms use `FieldError` with `positiveCentsError` / `positiveDecimalError`.
- **Theme.** Material roles plus `PicksmartTheme.status` for profit, loss and below-target colours. In light mode `primary` is a dark amber for text, focus and selection, while `primaryContainer` stays brand yellow for filled buttons; use `PrimaryButton` rather than raw `Button`. Use `BrandMark` for the pin: it adds the near-black circle on light backgrounds.
- **Keyboard.** `NavHost` consumes the Scaffold's inner padding, so a screen's `imePadding()` adds only the part of the keyboard below the bottom bar. Check a Ride uses this to pin its result above the keyboard.
- **Large fonts.** Screens scroll (`TabScreen`), bottom-bar labels auto-shrink to one line, and `ChoiceGrid` drops to one column when a label would wrap mid-word. Check new screens on a 360 dp-wide emulator at 200% font.
- **Fuel prices.** `AppContainer.refreshFuelPrices()` (app start, Check a Ride) runs `FuelPriceSource.refreshIfDue()` (`data/remote`: fetch, `parseFuelPriceFile`, cache in DataStore) on an application scope, then `FuelPriceUpdates.applyAutomatic()`. The rules are pure functions in `domain/usecase/PublishedFuelPrice.kt` (`publishedPriceAction`, `applyPublishedPrice`); `FuelPriceUpdates.state` feeds Home, Settings and the Fuel price screen. The file URL is the Gradle property `picksmart.fuelPriceUrl` (see `docs/fuel-price-file.md`).
- **Cloud backup.** `SyncProvider` (`data/sync`) is `CloudSyncProvider` when the account is available, else `NoOpSyncProvider`; one per space, and it only runs while the account that owns the space is signed in (`AppContainer.start()`), so one account's records are never uploaded to another's. `CloudSync` does one run: download what changed since the last run, merge each record with `mergeAction` (`domain/usecase/SyncMerge.kt`, last write wins by `updatedAt`), then upload every row with `sync_pending` and every pending tombstone. `CloudRecords` maps entities to and from Firestore documents (vehicle links by sync id); `FirestoreCloudStore` is the only Firestore code. Every record has a `syncId` (domain models generate one in their constructor, so an edit must `copy()`, never rebuild, the record). Deletes go through the DAO's `deleteAndRemember`, which leaves a tombstone. A new table that should be backed up needs `sync_id`/`sync_pending` columns, a `SyncKind`, queries in `SyncDao`, a mapping in `CloudRecords` and a case in `CloudSync`; see `docs/cloud-backup.md`. `firestore.rules` accepts only the fields `CloudRecords` writes, so a new table or field also goes into the rules, published before that app version ships (one refused record stops a rider's whole backup).
- **Database changes.** Bump the version in `PicksmartDatabase`, add the `Migration` to `ALL_MIGRATIONS` in `data/local/migrations/Migrations.kt`, commit the new schema JSON, and add a `MigrationTestHelper` test to `MigrationTest` (device test), copying the `CREATE` SQL from the exported JSON. The Room Gradle plugin supplies `app/schemas` to device tests.
- **Generated assets.** `ic_launcher_foreground`, `ic_launcher_monochrome`, `picksmart_mark`, `picksmart_logo` and the split wordmark `picksmart_word_pick` / `picksmart_word_smart` (tinted per theme by `Wordmark`) are traced from `design/brand/picksmart-logo.jpeg` by the scripts in `design/brand/` (see its README). Regenerate rather than hand-edit. Other icons are 24-unit vectors with a 2-unit round stroke in `res/drawable/ic_*.xml`, mostly hand-written; the vehicle types, gauge, pencil, shield and the filled bottom-bar icons come from `design/icons/make_icons.py` (see its README). The selected bottom-bar tab shows the filled version (`TopLevelDestination.selectedIcon`). There is no icon library.

## 14. Optional account and cloud backup

An account exists for one reason: so a rider who loses or changes their phone does not lose their income history. It is never required.

### Principles

- **Optional and late.** The app works fully with no account. Register, Login and the welcome screen all offer "Skip — use without an account" with equal weight, and an account can be created later from the profile card on the Account tab. Skipping is not a dead end.
- **Local is the source of truth.** The device database stays authoritative. Sync copies local data up and merges remote data down; it never replaces local data with an empty cloud state. A signed-in rider still works offline, and changes sync when back online.
- **Nothing extra is collected.** Only what the rider already entered (trips, fuel, expenses, income, vehicles, settings), the auth identifier, the email, name and photo link from the sign-in provider, and the optional personal details the rider adds: mobile number, gender, date of birth and a profile photo they choose. Those details are backed up with the account so they come back on a new phone (owner decision 2026-10-27); removing one on the Profile screen removes it from the backup too. No location, contacts or device fingerprinting.
- **Sign-out keeps local data.** Signing out never wipes the on-device records: the account's records stay on the phone and come back when it signs in again. While signed out, the app shows the records made without an account (empty, so setup starts again, when there are none), so nothing added while signed out goes into an account (owner decision 2026-10-27).
- **One phone, several accounts, no mixing** (owner decision 2026-10-27). Each account keeps its own records in its own space (`domain/usecase/DataSpaces.kt`): a Room database (`picksmart.db` for the phone's first space, `picksmart-<id>.db` for others) and its own per-rider settings (setup, active vehicle, last Check a Ride inputs; DataStore keys ending `@<id>`). Signing in switches to that account's space with no question asked. Records made without an account go into an account only if the rider says so: when an account signs in for the first time on this phone while such records are on screen, a dialog asks (`asksToMoveRecords`, `MoveRecordsDialog`). A new account gets "Move to my account" or "Keep separate"; one that existed before gets "Add to my account" (its backup then merges into them) or "Keep separate". Moved records belong to the account and are no longer no-account records. Kept ones stay apart, and the account gets its own space that its backup fills (it uses the no-account space only while that is still empty); with no backup it only sets up a vehicle. An account that already has a space on this phone goes back to it without a question. The registry of which account owns which space is `data_spaces.xml`. Signing in again with the same account, even after the app's data was cleared or it was reinstalled, brings its records back from the cloud.

### Mechanics

- Firebase Authentication with two sign-in methods: email/password (with email verification and "Forgot password?" reset by email) and Google (needs the app's SHA-1 and SHA-256 fingerprints in the Firebase project). Sign-in and verification need internet and say so.
- Facebook login is planned for a later phase; it needs a Meta developer app, the Facebook SDK and Meta's app review for the email permission. Add it behind the same `AccountProvider`.
- Phone OTP is not used (owner decision, 2026-10-23). If it is ever added, the SMS cost rules in section 16 apply first.
- Per-user data lives under the user's own id in Firestore, isolated by security rules so one rider can never read another's data. Firestore offline persistence is on, so a signed-in rider still reads and writes offline.
- Merge rule: last-write-wins per record using `updatedAt`, with deletes as tombstones so a delete on one device is not undone by a stale copy on another.
- No duplicate vehicles on restore (owner decision 2026-10-27). On an account's first download into a space, a backed-up vehicle that is the same as one made on this phone and never backed up (`matchVehicle`: the same plate; otherwise the same name and type, unless the plates differ) updates that vehicle instead of being added: it takes the backup's sync id, so the backup's trips find it, and its values follow last write wins.
- All of this sits behind an `AccountProvider` / `SyncProvider` interface with a no-op implementation, so the app compiles and runs with Firebase disabled or unreachable.

### Account tab

The fifth bottom-bar tab is **Account** (it replaces Settings).

- **Profile card** at the top.
  - Signed out: no profile details at all. The card shows a plain avatar, one line on what an account gives ("Back up your data and add up to 3 vehicles"), and two buttons, **Sign up** (opens Register) and **Sign in** (opens Login). The Profile screen can't be opened while signed out.
  - Signed in: the profile photo (or initials), name and email; tapping the card opens the Profile screen.
- **Settings below the card**: every row the old Settings tab had (vehicle, fuel price, records, theme and so on), unchanged, and fully working whether or not the rider is signed in.
- The card reads the cached profile, so the tab opens instantly and offline (N9).

### Login and Register

- **Login:** email, password, "Forgot password?", "Continue with Google", a link to Register, and "Skip — use without an account".
- **Register:** name, email, password, confirm password, optional mobile number, "Continue with Google", a link to Login, and the same Skip. Creating an account sends a verification email.
- Both are reached from the profile card, and from a welcome screen if one is added; Skip returns the rider to where they were. Errors (wrong password, email in use, no internet) show as plain text under the form, never as a crash or a silent failure.
- When an account signs in for the first time on a phone that has records made without an account, the app asks once whether to move them to it (owner decisions 2026-10-27). Move: they become the account's records and are backed up, merged with the account's backup if it has one (local is the source of truth). Keep separate: the account starts with only its own backup, and they come back when the rider signs out (section 14, "One phone, several accounts").
- **Restoring blocks setup** (owner decision 2026-10-27). After an existing account signs in to an empty space (a reinstall, or a phone it hasn't used), setup shows "Restoring your data, please wait…" and asks for nothing until the backup is down, so nothing the rider enters is merged with it later. Problems are retried on their own. **Skip and start fresh** is always there: the rider stays signed in, setup goes on to the vehicle, backup is off for that account on this phone (`SyncStatus.Paused`, `backup_paused` setting), and what the restore had downloaded is dropped (only rows exactly as the backup has them, `SyncDao.discardRestored`). The Account tab's backup card says so and offers **Turn on backup**, which brings the backup back and merges it in. Back does the same as Skip. A new account has no backup, so a failed restore never stops it. When the restore finishes and the account had nothing backed up, setup says "No backup found" and **Next** goes on to the vehicle, with backup left on (owner request 2026-10-27); a new account goes straight to the vehicle without that message.

### Profile screen

- Shows the profile photo, name, email (with "Verified" or "Not verified" and a resend link), mobile number, gender and date of birth. The sign-in method is not shown (owner decision 2026-10-26).
- **Photo:** the rider can choose their own photo with the Photo Picker (Change photo, or tap the avatar) and remove it again; otherwise the Google account photo for Google sign-in, else initials. A chosen photo is cropped to a 512 px square JPEG, kept on the device, and backed up as one Firestore document (`users/{uid}/profile/photo`, not Cloud Storage, so it needs no Blaze plan); it comes back on a new phone that has no photo of its own, stays on sign-out, and is deleted with the account. If it can't load, initials show.
- **Gender and date of birth:** optional, kept on the device and in the rider's own Firestore profile, restored on a new phone, kept on sign-out and removed with the account. Gender is Male, Female, Other or Prefer not to say; the date of birth is between 1 Jan 1900 and today.
- **Mobile number:** optional, typed by the rider on Register or Profile, labelled "Not verified" because there is no OTP. It is stored on the device and in the rider's own Firestore profile, and is never used to send SMS or shared.
- Actions: Edit (name, mobile number), Sign out (keeps local data), and Delete account (owner decision 2026-10-26). Delete account shows a warning listing what goes (the account, the cloud backup, and everything on this phone), asks the rider to confirm it's them (their password, or Google's account picker), then deletes the cloud backup, the account, and that account's records, settings and photos on the phone (other accounts' spaces, the theme, the app language and the published fuel price list stay), and returns to the records made without an account, or to the welcome screen when there are none. The emptied space has no owner afterwards, so a new account made there never shows the old records. If any step fails (offline, wrong password), the steps after it don't run and the phone's records stay. Sign-out still keeps everything.

### Compliance

- The Play Data safety form must now declare the account identifier, email, name, the optional mobile number, gender, date of birth and profile photo, and the backed-up app data, how they are used (backup/sync only), and that they are encrypted in transit. Update it in the same release.
- The privacy policy explains the optional account, what is stored, and how to delete it. Provide an in-app Delete account action that removes the Firestore data, the auth user and the rider's data on the phone (see "Profile screen").
- The marketing promise changes from "your data stays on your phone" to "works offline; your data stays on your phone unless you choose to back it up". Update the store listing and any posts.

## 15. Plans and monetization

Three tiers: Free (no account), Signed in (free account), and Premium (paid). A `PlanProvider` exposes the current tier and feature flags; features check the flag, so changing what a tier includes is one place.

**Core rule: never cap the number of rides a rider can check or record.** Capping rides or ride-checks breaks the app's one promise — an accurate profit total — and gives a rider wrong information about their own money (a day of 30 rides would stop recording after the cap and under-report earnings). Gating is by feature, never by volume of the rider's own data. If the owner still wants hard ride caps, raise the harm before implementing and get an explicit decision; it is off by default.

Recommended feature gating (owner can adjust the split, not the no-cap rule):

| Feature | Free | Signed in | Premium |
| --- | --- | --- | --- |
| Ride check + full tracking | ✓ unlimited | ✓ unlimited | ✓ unlimited |
| Reports range | current week | full history | full history |
| Cloud backup & multi-device | — | ✓ | ✓ |
| CSV / Excel export | — | ✓ | ✓ |
| Vehicles (active, not archived) | 1 | 3 | unlimited |
| Screenshot OCR auto-fill | — | — | ✓ |
| Shift widget & Quick Settings tile | — | — | ✓ |
| Tax & monthly summaries | — | — | ✓ |
| Ads | shown | shown | removed |

- Signed in is free and unlocks backup, export and full history, so there is a real reason to make an account beyond backup.
- Premium is billed through Google Play Billing (not a custom payment flow). Prices shown in the UI (Rs. 490/month, Rs. 4,900/year) are placeholders to confirm.
- Entitlement is cached locally so Premium features work offline once verified; the app re-checks when online. Losing network never silently downgrades a paid rider mid-session.
- Downgrading never deletes data: a lapsed Premium rider keeps all their trips and still sees them; only the gated features turn off.
- Signing out or a lapsed Premium never removes a vehicle: the rider keeps all of them, can still switch and record trips on each, and only can't add another until under the limit (section 9, "Vehicles").
- Until `PlanProvider` exists (Phase 8d), the vehicle limit comes from one small function of "signed in or not" (1 or 3) that `PlanProvider` will replace, and the Premium screen says "coming soon".

## 16. Cost and scaling

Figures below are approximate (prices checked Oct 2026, USD ≈ LKR 300) and must be re-confirmed on Google's official pricing pages before relying on them. They exist so cost-driving decisions are made deliberately.

### What costs money, what doesn't

- Free, no-account users cost essentially nothing: no backend calls. The fuel price file is hosted free (GitHub), OCR runs on-device, and push notifications (Cloud Messaging) are free.
- Cost scales with **signed-in active users and sync operations**, not total installs. Because the app is offline-first (local DB is the source of truth), Firestore is touched only on sync, not on every screen.
- Firestore free tier: 50,000 reads/day, 20,000 writes/day, 1 GiB storage. Above that: reads ~$0.03/100k, writes ~$0.09/100k, storage ~$0.15/GB/month. Auth: 50,000 MAU free, then ~$0.0055/MAU.

### Rough monthly infrastructure cost (signed-in daily-active users)

| Signed-in DAU | Firestore | Auth MAU | Monthly ≈ |
| --- | --- | --- | --- |
| 1,000 | ~$0.3 | free | ~$0–1 (LKR 0–300) |
| 10,000 | ~$10 | free | ~$10–15 (LKR 3k–4.5k) |
| 100,000 | ~$115 | ~$275 | ~$390–450 (LKR 120k–135k) |

Assumptions: ~30 writes + 30 reads per signed-in active user per day, ~0.5 MB stored per user. Keep sync batched and incremental to stay near these.

### Phone OTP is the bill-shock risk

Phone OTP is not shipped (section 14). These rules apply only if it is added later.

- SMS is billed per message (first 10/day free; by country roughly US $0.01, UK $0.04, India $0.07; Sri Lanka not confirmed, assume $0.05–0.08). It is a near one-time cost per phone signup, not recurring.
- SMS pumping / toll fraud (an attacker triggering mass OTPs) is the real danger. Mandatory mitigations: restrict SMS region policy to +94 only, rate-limit verification requests per device and number, and present email/Google sign-in before phone so SMS is used only when needed.

### Plan upgrade (Spark → Blaze)

- Start on Spark (free, no card). Move to Blaze (pay-as-you-go) when Cloud Functions (Premium purchase verification, Phase 8d), Cloud Storage, phone auth (not planned), or usage above the Firestore daily quota is needed. Email/password and Google sign-in work on Spark.
- Blaze still includes all free quotas; it bills only above them, so enabling Blaze is not itself a charge.
- Set budget alerts at 50% / 80% / 100%. Alerts notify only; they do not stop reads or downloads, so also cap runaway paths in code (rate limits, pagination, no per-screen Firestore reads).

### Sustainability check

- Even a 2% Premium conversion at 100k users (~2,000 subscribers × Rs. 490 × 0.85 after the Play cut ≈ LKR 830k/month) far exceeds the ~LKR 130k infrastructure cost at that scale. The model is viable as long as sync stays efficient and SMS fraud is controlled.

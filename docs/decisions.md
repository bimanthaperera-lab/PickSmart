# Picksmart — Decisions

Decisions made during development, newest last. Rules that are always true live in `CLAUDE.md`; the roadmap lives in `PLAN.md`.

Each entry: date, decision, reason, and who decided (owner or default).

---

## 2026-10-22 — Defaults in effect until the owner answers

The open questions in `PLAN.md` use their listed defaults until the owner answers. Answered items move here.

## 2026-10-22 — Phase 0 scaffold

- **Toolchain versions.** AGP 9.4.1, Kotlin 2.4.21 (AGP's built-in Kotlin plus the Compose compiler plugin), Gradle 9.8.2, Compose BOM 2026.09.00, compileSdk and targetSdk 37, minSdk 26. These match the owner's other Android project, which already builds on this machine with these versions cached. *Default.*
- **JDK.** Gradle runs on Android Studio's bundled JBR (JDK 25), since no separate JDK 17 is installed. Bytecode targets Java 17 (`sourceCompatibility` and `targetCompatibility` 17), which is how the "JDK 17" rule in CLAUDE.md is met. Set `JAVA_HOME` to `C:\Program Files\Android\Android Studio\jbr` before running Gradle from a terminal. *Default.*
- **Package.** `lk.picksmart` for both the namespace and the application ID, until the owner decides. *Default.*
- **Repository.** GitHub `bimanthaperera-lab/PickSmart`, branch `main`. *Owner.*
- **Auto Backup.** On, with explicit rules (`backup_rules.xml` and `data_extraction_rules.xml`) that include only the Room database and the `datastore/` files. Caches are never included. *Default (PLAN.md open question).*
- **Launcher icon.** A plain yellow pin on a near-black background is a placeholder until the owner's logo source is added to `design/brand/`. The real adaptive icon follows the rules in CLAUDE.md section 9. *Default. Superseded on 2026-10-22, below.*
- **Secrets.** `google-services.json` is in `.gitignore` along with keystores and `local.properties`. *Default.*
- **Known issue for Phase 2.** Brand yellow text on the light background has low contrast. The light theme needs a darker accent for text, with yellow reserved for fills. *Default.*

## 2026-10-22 — Brand logo in the app

- **Logo source.** The owner's `picksmart-logo.jpeg` lives in `design/brand/`. It's a raster image on a dark gradient, so the mark and wordmark are traced into vector drawables (`design/brand/trace_logo.py` and `make_drawables.py`, explained in `design/brand/README.md`). If the owner later supplies an SVG, regenerate from that instead. *Owner asked; tracing method is the default.*
- **Launcher icon.** The adaptive icon uses the traced pin mark, 58 of 108 units tall, centred inside the 66 dp safe zone, on a near-black background. The monochrome (themed) icon uses the same paths in one colour; the dark gaps in the mark keep the white P readable. *Default.*
- **Brand colours in the logo.** The traced logo uses the flat brand colours (yellow `#FBBB23`, off-white `#F5F5F2`) instead of the slight gradient in the JPEG. *Default.*
- **Start screen.** It always uses the dark theme and light system bar icons, because the full logo is only legible on a dark surface. The window background is near-black in both modes, so there's no light flash at launch. Phase 2 revisits this when the app shell arrives. *Default.*

## 2026-10-22 — Phase 1 ProfitCalculator

- **Commission as one whole number.** `ProfitInput.commissionHundredths` holds the commission in hundredths of its unit: cents for `AMOUNT` (Rs. 200 = 20000) and hundredths of a percent for `PERCENT` (20% = 2000, 12.5% = 1250). Both types stay an exact `Long` with no `Double`, and percentages are precise to 0.01%. The `Trip` entity will store the same value. *Default.*
- **Net profit from rounded parts.** Fuel, reserve, commission and per-km values are each rounded HALF_UP to cents. Net profit is computed from those rounded parts, so the breakdown on screen always adds up to the net shown. *Default.*
- **Band uses the rounded per-km value.** The target comparison uses the same rounded profit per km the rider sees, and meeting the target exactly counts as `GOOD`. A loss is always `LOSS`, even with a target set. With a target but zero distance, the band falls back to `PROFIT` or `BREAK_EVEN`. *Default.*
- **Validation.** Every problem is returned at once as typed `ValidationError`s, each tied to an `InputField`, so the UI can mark each field. NaN or infinite numbers are rejected. Commission isn't validated in payout mode, because that field is hidden. Results too large for a `Long` return `ResultTooLarge` instead of overflowing or throwing. *Default.*
- **Gradle daemon JVM.** Android Studio generated `gradle/gradle-daemon-jvm.properties`, which pins the daemon to JDK 25. It's committed, consistent with the JDK entry above. *Default.*

## 2026-10-22 — Phase 2 design system and app shell

- **Colours.** The dark theme follows the mockups: near-black `#0D1112` background, `#1A1F1F` cards, green profit, red loss, amber below-target. In the light theme, yellow fails contrast as text on white, so Material `primary` (text, focus, selection) is a dark amber `#7A5000` and `primaryContainer` stays brand yellow for filled primary buttons. Profit, loss and below-target colours live in `PicksmartTheme.status`, because Material has no roles for them. *Default.*
- **Icons.** No icon library. The 11 outline icons are hand-drawn 24-unit vector drawables, matching the mockups' line style, so there's no extra dependency. *Default.*
- **Money format.** `MoneyFormatter` puts a non-breaking space between "Rs." and the number so they never wrap apart, always uses Western digits and comma grouping (`Locale.US` symbols), and takes the prefix from `strings.xml` (`currency_prefix`) so it can be translated. Negative amounts are `-Rs. 50.00`, and signed profit is `+Rs. 705.00`. *Default.*
- **Number input.** `sanitizeDecimalInput` keeps only digits and one decimal point. A dot counts as the decimal point only after a digit (or at the very start) and before a digit (or at the end), so pasting "Rs. 1,000.00" gives `1000.00`, not `0.10`. *Default.*
- **Theme preference brought forward.** The Settings theme choice (System / Light / Dark) is saved now in DataStore (`user_preferences`, key `theme_mode`) through `UserPreferencesRepository`, so Settings has a real control rather than a placeholder. Unknown stored values fall back to System. *Default.*
- **No theme flash.** `MainActivity` waits for the stored theme before drawing, and the window background (off-white in light mode, near-black in night mode) shows for that moment. *Default.*
- **Navigation.** Navigation Compose with `@Serializable` routes. Switching tabs pops back to Home and restores each tab's state, so Back from any tab returns to Home and Back from Home leaves the app (checked on the emulator). *Default.*
- **Bottom-bar labels at large font sizes.** Five tabs on a 360 dp phone leave about 72 dp each. At 200% font, "Check Ride" can't fit, so the tab labels shrink to fit one line (8 dp minimum) instead of being cut off. Screen readers always get the full text. All other text scales normally. *Default.*
- **Tab content before data exists.** Home, Check Ride, History and Reports show honest empty states until their phases land (4, 5 and 7). Check Ride says it's coming soon; it gets its form in Phase 4. *Default.*
- **Lint warnings accepted.** `VectorPath`/`VectorRaster` on the traced logo (an accurate trace needs long paths, and it's drawn once and cached), `ObsoleteSdkInt` on `mipmap-anydpi-v26` (AAPT fails to find the adaptive icon without the `-v26` suffix), `UnusedResources` on `picksmart_logo` (onboarding uses it in Phase 3), and `GradleDependency` for DataStore 1.3.0 (staying on the cached 1.2.1). Lint reports 0 errors. *Default.*

## 2026-10-22 — Phase 3 persistence and onboarding

- **Vehicle table v1 has every Vehicle field from CLAUDE.md section 8**, including `fuel_type`, `fuel_price_source`, `fuel_price_effective_date` and `auto_update_fuel_price`. PLAN.md had planned to add those in Phase 6b with a migration; putting them in v1 (before any release) avoids that migration. Columns are snake_case, enums are stored by name, dates as ISO `yyyy-MM-dd` text, and timestamps as UTC epoch millis. *Default.*
- **`is_sample` column.** A vehicle created with "Skip and use sample values" is flagged in its own row, so the "Sample" label follows the vehicle (and survives backup and restore) until the rider saves the form. Any save from Edit vehicle clears it. *Default.*
- **Sample values** are the CLAUDE.md G1 test numbers: Motorcycle, Petrol 92, Rs. 300.00/L, 40 km/L, Rs. 2.00/km reserve, no target. They are not real prices and are always labelled "Sample". *Default.*
- **Onboarding** is a welcome screen, then two steps (vehicle, then the safety notice). Nothing is saved until the rider accepts the notice; then the vehicle is inserted and `default_vehicle_id` and `onboarding_complete` are written in one DataStore edit. If the app dies first, setup simply runs again. The flag is never cleared by code. Fuel type is asked during setup (needed later to match published prices). A blank vehicle name defaults to the vehicle type ("Three-wheeler"). The mockup's "I already have an account" link waits for Phase 8c. *Default.*
- **Fuel price rules on edit** (CLAUDE.md section 8a): changing the fuel price or the fuel type by hand sets the source to `MANUAL`, clears the effective date and turns auto-update off. Editing other fields keeps a published price as it is. *Default.*
- **Edit vehicle** is one route, `EditVehicleRoute(vehicleId)`, where `0` means "add". The first vehicle created becomes the default. The mockup's separate "Fuel price & type" screen (Phase 6b), the "Default vehicle" switch and the archive action (multiple vehicles, Phase 8d) are left out for now; fuel type and price are edited inline. *Default.*
- **Last-used Check a Ride inputs** move to Phase 4, where the form decides exactly what is remembered. *Default.*
- **Schema safety net.** `SchemaTest` (device test) creates a database from the committed `1.json`, writes a row with raw SQL, and opens it with the app's real builder and `ALL_MIGRATIONS`. An entity change without a version bump and migration fails that test. The Room Gradle plugin supplies the schema files to device tests. *Default.*
- **Large fonts.** `ChoiceGrid` measures its labels and drops from two columns to one when a label can't fit a tile on one line, instead of breaking words. *Default.*
- **Welcome screen bars.** The welcome screen is always dark (the full logo needs it), so it forces light status and navigation bar icons and restores the theme's setting when it leaves. *Default.*

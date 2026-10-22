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

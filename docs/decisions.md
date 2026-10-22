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
- **Launcher icon.** A plain yellow pin on a near-black background is a placeholder until the owner's logo source is added to `design/brand/`. The real adaptive icon follows the rules in CLAUDE.md section 9. *Default.*
- **Secrets.** `google-services.json` is in `.gitignore` along with keystores and `local.properties`. *Default.*
- **Known issue for Phase 2.** Brand yellow text on the light background has low contrast. The light theme needs a darker accent for text, with yellow reserved for fills. *Default.*

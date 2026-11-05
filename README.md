<p align="center">
  <img src="design/brand/picksmart-logo.jpeg" alt="Picksmart logo" width="220">
</p>

<h3 align="center">Know your real profit before you ride.</h3>

<p align="center">
  An Android profit tracker for Uber and PickMe drivers, couriers and delivery riders in Sri Lanka.
</p>

---

## What it does

Picksmart tells a rider, in a few seconds, whether a ride offer is worth taking, then tracks what they actually earned after commission, fuel, maintenance and other costs.

- **Check a Ride.** Type the fare and the distances and see the net profit, profit per km and a clear band (Profit, Loss, Below target, Good) as you type. The last-used values and the active vehicle's rates are filled in, so most checks need three fields or fewer.
- **Trips, history and income.** Save completed and cancelled trips, with optional pickup and "Where to" places. Add tips, incentives and bonuses as other income.
- **Fuel log and expenses.** Fuel is recorded once, in the fuel log. Expenses cover repairs, parking, data, tolls and more.
- **Reports.** Day, week and month, with two separate views: *Estimated profit* (from each trip's calculation) and *Cash profit* (what actually moved in and out of the rider's pocket). Nothing is counted twice, and checked offers are never counted as income.
- **Several vehicles.** For example a motorcycle and a three-wheeler. Each trip keeps the rates it was saved with, so changing a fuel price never changes past results.
- **Published fuel prices.** The app can offer the current CPC pump price, but the price the rider sets by hand always wins.
- **Floating bubble (optional).** A small badge that stays out of the way of the ride app's Accept button.
- **Offer reader (optional, off by default).** Reads the text of a ride offer in Uber Driver or PickMe Driver on the phone and shows an estimated result in the badge. It never taps, types or captures the screen, and the offer text never leaves the phone. See the risks in `CLAUDE.md` section 9b before turning it on.
- **Alerts inbox.** Fuel price changes and other alerts are kept in the app, readable offline.
- **Optional account and cloud backup.** Sign in with email or Google to back up records and restore them on a new phone. The app works fully without an account.
- **English, Sinhala and Tamil.** Light, dark and system themes.

Results are estimates. Check offers only when stopped safely.

## Screens

> These are the design mockups the app was built from (October 2026), with made-up sample values. The built app follows them closely but differs in places: for example, the fifth tab is now **Account** instead of Settings.

<table>
  <tr>
    <td align="center"><img src="Picksmart%20%E2%80%94%20App%20Screens-png/Home%20%E2%80%94%20today.png" alt="Home" width="230"><br><sub>Home</sub></td>
    <td align="center"><img src="Picksmart%20%E2%80%94%20App%20Screens-png/Check%20a%20Ride%20%E2%80%94%20good%20offer.png" alt="Check a Ride, good offer" width="230"><br><sub>Check a Ride: a good offer</sub></td>
    <td align="center"><img src="Picksmart%20%E2%80%94%20App%20Screens-png/Check%20a%20Ride%20%E2%80%94%20loss.png" alt="Check a Ride, loss" width="230"><br><sub>Check a Ride: a loss</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="Picksmart%20%E2%80%94%20App%20Screens-png/History.png" alt="History" width="230"><br><sub>History</sub></td>
    <td align="center"><img src="Picksmart%20%E2%80%94%20App%20Screens-png/Reports%20%E2%80%94%20week.png" alt="Weekly report" width="230"><br><sub>Reports: week</sub></td>
    <td align="center"><img src="Picksmart%20%E2%80%94%20App%20Screens-png/Floating%20bubble%20over%20another%20app.png" alt="Floating bubble" width="230"><br><sub>Floating bubble</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="Picksmart%20%E2%80%94%20App%20Screens-png/Fuel%20log.png" alt="Fuel log" width="230"><br><sub>Fuel log</sub></td>
    <td align="center"><img src="Picksmart%20%E2%80%94%20App%20Screens-png/Fuel%20price%20%E2%80%94%20new%20CPC%20price.png" alt="New CPC fuel price" width="230"><br><sub>Fuel price: new CPC price</sub></td>
    <td align="center"><img src="Picksmart%20%E2%80%94%20App%20Screens-png/Alerts%20%E2%80%94%20in-app%20inbox.png" alt="Alerts inbox" width="230"><br><sub>Alerts inbox</sub></td>
  </tr>
</table>

More mockups are in [`Picksmart — App Screens-png/`](Picksmart%20%E2%80%94%20App%20Screens-png/). Some of them (OCR import, Quick Settings tile, shift reminder, Premium, phone OTP) show features that are planned or dropped, not built.

## How the profit is worked out

```text
totalKm     = pickupKm + tripKm + returnKm
fuelCost    = totalKm × fuel price per litre ÷ km per litre
maintenance = totalKm × maintenance reserve per km
commission  = 0 when the fare is the payout after commission,
              otherwise a fixed amount or a percentage of the fare
netProfit   = fare − commission − fuelCost − maintenance − other costs
per km      = netProfit ÷ totalKm (only when totalKm > 0)
```

Money is kept as whole cents and calculated with `BigDecimal`, rounded half-up to two decimals. The calculator is a pure Kotlin class (`domain/calculator/ProfitCalculator`) covered by golden tests.

## Principles

- **Correct numbers first.** Offers are never income, and nothing is counted twice.
- **Offline-first.** Every core screen works with no internet.
- **Local-first and private.** No analytics, no location, no ads (until the final phase). Records stay on the phone unless the rider chooses to back them up.
- **The rider's own numbers.** Fuel price, fuel efficiency, commission and maintenance are always editable. The app never invents fares, prices or rates.
- **Records are never lost.** Every database change ships a tested migration, and every delete asks first.

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin, JDK 17 |
| UI | Jetpack Compose, Material 3, edge-to-edge |
| Architecture | MVVM with manual DI through one `AppContainer` |
| Navigation | Navigation Compose with type-safe routes |
| Storage | Room (schemas in `app/schemas/`), DataStore |
| Dates | `java.time`, zone `Asia/Colombo` |
| Charts | Compose `Canvas` |
| Background work | WorkManager |
| Account and backup | Firebase Authentication and Cloud Firestore, optional, with App Check |
| SDK | minSdk 26, targetSdk 37 |

## Building

You need Android Studio (or JDK 17+ and the Android SDK).

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew lint
```

Device tests need an emulator or a phone:

```bash
./gradlew connectedDebugAndroidTest
```

Sign-in and cloud backup are switched on only when `app/google-services.json` is present (it is git-ignored). Without it, the app builds and runs fully with no account. The fuel price file URL comes from the Gradle property `picksmart.fuelPriceUrl`; see [`docs/fuel-price-file.md`](docs/fuel-price-file.md).

## Project layout

```text
app/src/main/java/lk/picksmart/
  AppContainer.kt     manual dependency injection
  data/               Room, DataStore, fuel price download, account, sync
  domain/             calculator, models and pure use cases
  glance/             floating bubble, offer reader and its badge
  ui/                 Compose screens, navigation and theme
  util/               money and date formatting, parsing
design/               logo, icons, fonts and sounds, with the scripts that generate them
docs/                 decisions, cloud backup, privacy and release notes
web/                  privacy policy page
```

## Documentation

- [`PLAN.md`](PLAN.md): the roadmap and current status
- [`CLAUDE.md`](CLAUDE.md): the full product and engineering rules
- [`docs/decisions.md`](docs/decisions.md): decisions made along the way
- [`docs/cloud-backup.md`](docs/cloud-backup.md): how backup and restore work
- [`docs/privacy.md`](docs/privacy.md) and [`web/privacy-policy.html`](web/privacy-policy.html): privacy

## Disclaimer

Picksmart is not affiliated with, endorsed by or integrated with Uber or PickMe. All results are estimates. The optional offer reader may be against a ride app's driver terms; use it at your own risk.

## License

© 2026 Bimantha Perera. All rights reserved.

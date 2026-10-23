# Publishing the fuel price file

The app offers riders the current CPC pump price from one small JSON file that the owner hosts and updates (CLAUDE.md section 8a). The app never ships prices in code, and never scrapes the CPC website.

## 1. Create the file

Put a file such as `fuel-prices.json` in a public GitHub repository. Prices are **whole cents per litre** (Rs. 414.00 = `41400`), and the date is when the prices took effect:

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

The numbers above are example values, not real prices. Copy the real ones from the CPC announcement.

## 2. Point the app at it

Use the file's raw HTTPS address, for example `https://raw.githubusercontent.com/<owner>/<repo>/main/fuel-prices.json`, and set it in `gradle.properties` (or pass `-Ppicksmart.fuelPriceUrl=...` when building):

```properties
picksmart.fuelPriceUrl=https://raw.githubusercontent.com/<owner>/<repo>/main/fuel-prices.json
```

It becomes `BuildConfig.FUEL_PRICE_URL`. With no URL, the app doesn't download anything and the Fuel price screen says published prices aren't available.

## 3. When CPC announces new prices

Within a day: update all four prices and `effectiveFrom`, then commit. Phones pick it up within 12 hours of their last check, next time the app starts or Check a Ride opens.

## What the app rejects (and keeps the last good list instead)

- `schema` other than `1`
- any of the four prices missing, or not a whole number of cents
- a price below Rs. 50 or above Rs. 2,000 per litre
- a price that moved more than 40% from the last list the phone accepted
- an `effectiveFrom` earlier than the last list the phone accepted

Extra fields and extra fuel types are ignored, so they can be added without breaking installed apps. A typo that trips a rule is safe: phones keep the previous prices until the file is fixed.

## Testing on the emulator

Debug builds allow plain HTTP to `127.0.0.1` and `10.0.2.2` only (`app/src/debug/res/xml/network_security_config.xml`). Serve a test file from the computer and forward the port:

```bash
python -m http.server 8765
```

```bash
adb reverse tcp:8765 tcp:8765
```

```bash
./gradlew installDebug -Ppicksmart.fuelPriceUrl=http://127.0.0.1:8765/fuel-prices.json
```

`10.0.2.2` can time out when the emulator sends app traffic over its Wi-Fi network, so prefer `127.0.0.1` with `adb reverse`.

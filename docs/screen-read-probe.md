# Screen-read probe (debug only)

A test to see whether a ride app's offer card exposes the fare and distances to an AccessibilityService. It is not a feature, and it is not in release builds. Background and the decision it feeds: `docs/decisions.md`, 2026-10-24.

The probe only reads and logs. It never taps or acts in any app and draws nothing on screen.

## Run it

1. Install the debug build on a phone that has the Uber Driver app: `./gradlew installDebug`.
2. On the phone: Settings → Accessibility → Installed apps (or Downloaded apps) → **Picksmart screen-read probe (debug)** → turn it on. Android 13+ may first need Settings → Apps → Picksmart → ⋮ → **Allow restricted settings**, because the debug build isn't from Play.
3. Start the log on the computer:

   ```bash
   adb logcat -s PicksmartProbe
   ```

4. Open Uber Driver and wait for a ride request (or open any screen showing fares, such as earnings).
5. Turn the probe off afterwards in Accessibility settings.

## Read the result

Each dump ends with a line like `==== end: 212 nodes, 48 with text, 6 money/km/min matches, 18 ms ====`.

- **Readable:** the offer card shows lines such as `text="LKR 850.00"` and `text="4.2 km"`. Note which node holds each value (class and `id=`) and the time in ms.
- **Not readable:** the card has few nodes, no matches, or the log says `no active window root`. The card is drawn as an image or the window is protected, and this approach won't work for that app.

Text that isn't money, a distance or a time is printed only as its length, so passenger names and addresses don't reach the log. Still, don't commit or share raw logs.

## PickMe

PickMe Driver (Sri Lanka) is `com.pickme.driver.byod` on Google Play (publisher Digital Mobility Solutions Lanka), and the probe already lists it. PickMe shows a trip as a popup, so the probe dumps every window of the app, each headed `---- window type=… layer=… active=… focused=… ----`. Note which window holds the fare and distances: if it is `active=false` or `system/overlay`, only the window list (`flagRetrieveInteractiveWindows`) can reach it, which is what the offer reader now uses. If `dumpsys window | grep mCurrentFocus` shows a different package on a real phone, change it in both config files and in `OfferReaderService`.

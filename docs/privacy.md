# Picksmart — Privacy policy draft and Play Data safety answers

Draft for the owner to review before release. It must match what the app actually does; update it in the same release as any change to network use, accounts or ads (CLAUDE.md sections 10, 12 and 14).

Last updated: 2026-10-23 (Phase 6b, fuel price download).

## What the app does today

- Everything the rider enters (vehicle, trips, offers, fuel log, expenses, other income, settings) is stored only on the phone, in the app's own database and settings files.
- Android Auto Backup is on. If the rider has Google backup turned on, Android may copy the app's database and settings to the rider's own Google account, as it does for other apps. Caches are never included. Picksmart's developer can't see these backups.
- The only network request is the fuel price download described below. There is no account, no analytics, no advertising, no crash reporting and no location access.

## Fuel price download

- When the phone is online, the app downloads one small public file with the current Ceylon Petroleum Corporation (CPC) pump prices, at most once every 12 hours (on app start and when Check a Ride opens).
- The request is a plain HTTPS GET of that file. It sends no personal data, no account or device identifier, no cookies and no analytics. The app replaces Android's default User-Agent (which names the phone model and Android build) with a fixed `Picksmart`.
- Like any web request, the server that hosts the file (GitHub, by default) receives the phone's IP address as part of the connection. Picksmart doesn't receive or keep it.
- The prices are only used to offer the rider an updated fuel price. The rider's own price is never replaced unless they tap "Update" or turned on "Update automatically".

## Privacy policy text (draft)

> **Picksmart privacy policy**
>
> Picksmart works on your phone. The trips, fuel, expenses, income and settings you enter stay on your phone. We don't run servers that receive them, and we don't use analytics, advertising or location.
>
> **Fuel prices.** When you're online, Picksmart downloads a public list of CPC fuel prices, at most twice a day. This download doesn't include any information about you or your phone. The website that hosts the list sees your IP address, as with any website; Picksmart doesn't collect it.
>
> **Backups.** If Google backup is on for your phone, Android may back up Picksmart's data to your own Google account. You control this in your phone's settings.
>
> **Deleting your data.** Uninstalling Picksmart, or clearing its storage in Android settings, removes all of its data from your phone.
>
> **Contact.** [owner to add a contact email]

## Play Data safety answers (current build)

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | No |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (no user data is collected). The fuel price download uses HTTPS. |
| Do you provide a way for users to request that their data is deleted? | Not applicable (no data leaves the phone). Data is removed by uninstalling or clearing storage. |

Notes for the form:

- Google's definition of "collect" is data sent off the device. The fuel price request sends no user data, so it isn't collection. The IP address seen by the file host is part of a normal network connection, not data the app sends.
- Re-answer the form when the optional account and cloud backup (Phase 8c) or ads (Phase 9) ship. Both add declarations.

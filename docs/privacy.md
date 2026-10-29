# Picksmart — Privacy policy draft and Play Data safety answers

Draft for the owner to review before release. It must match what the app actually does; update it in the same release as any change to network use, accounts or ads (CLAUDE.md sections 10, 12 and 14).

Last updated: 2026-10-27 (gender, date of birth and the chosen profile photo are now backed up with the account, on branch `v2`).

## What the app does today

- Everything the rider enters (vehicles, trips, offers, fuel log, expenses, other income, settings) is stored on the phone, in the app's own database and settings files. The app works fully without an account or internet.
- **Optional account (Firebase Authentication).** A rider may create an account with email and password, or sign in with Google. Firebase then holds the account identifier, email address, name and, for Google sign-in, the profile photo link. Email verification and password reset emails are sent by Firebase.
- **Cloud backup (Cloud Firestore), only while signed in.** The rider's records are copied to their own space in Firestore (`users/{uid}`) so they can sign in on another phone and get them back: vehicles, trips and checked offers, other income, fuel log, expenses, which vehicle is active, and the optional personal details the rider added: mobile number, gender, date of birth and the profile photo they chose (a 512 px JPEG). Removing a detail or the photo in the app removes it from the backup too. Firestore security rules let only that rider read or write it. A deleted record keeps only its id and time in the cloud, so other phones delete it too. Data is encrypted in transit (HTTPS).
- **Not backed up:** app settings such as theme and language, and the text the offer reader sees on screen (never stored anywhere, except the pickup and "Where to" places when the rider keeps them in Add trip and saves the trip; those are part of the trip).
- **Signing out** stops backup and keeps everything on the phone. **Delete account** (Profile) warns first and asks the rider to confirm it's them, then deletes the cloud backup, the account, and every record and setting on the phone (the theme, app language and published fuel prices stay).
- Android Auto Backup is on. If the rider has Google backup turned on, Android may copy the app's database and settings to the rider's own Google account, as it does for other apps. Caches are never included. Picksmart's developer can't see these backups.
- **CSV export (Reports).** The rider can export their records as CSV files and choose, in Android's share sheet, which app receives them (email, Drive, WhatsApp and so on). Picksmart sends them nowhere itself. The files are written to the app's cache, which is never backed up, and each export deletes the previous one. Under Play's Data safety rules, data the user chooses to send with the share sheet is not "shared" by the app.
- Network use: the fuel price download described below, and, for a rider who signs in, Firebase Authentication and Firestore. There is no analytics, no advertising, no crash reporting and no location access.

## Fuel price download

- When the phone is online, the app downloads one small public file with the current Ceylon Petroleum Corporation (CPC) pump prices, at most once every 12 hours (on app start and when Check a Ride opens).
- The request is a plain HTTPS GET of that file. It sends no personal data, no account or device identifier, no cookies and no analytics. The app replaces Android's default User-Agent (which names the phone model and Android build) with a fixed `Picksmart`.
- Like any web request, the server that hosts the file (GitHub, by default) receives the phone's IP address as part of the connection. Picksmart doesn't receive or keep it.
- The prices are only used to offer the rider an updated fuel price. The rider's own price is never replaced unless they tap "Update" or turned on "Update automatically".

## Privacy policy text (draft)

> **Picksmart privacy policy**
>
> Picksmart works on your phone and without internet. The trips (including any pickup and "Where to" places you add), fuel, expenses, income and settings you enter stay on your phone unless you choose to back them up. We don't use analytics, advertising or location.
>
> **Account and backup (optional).** You can use Picksmart without an account. If you create one (with email or Google), we use Google Firebase to keep your account (your email, name and an account ID) and, while you're signed in, a backup of your vehicles, trips, offers you checked, fuel log, expenses, other income, which vehicle you use, and the details you chose to add to your profile (mobile number, gender, date of birth and profile photo). Only you can access your backup; it's used only so you can restore your records and profile on another phone. If you remove a detail or your photo, it's removed from the backup too. Signing out stops the backup and keeps your records on your phone.
>
> **Deleting your account.** In Picksmart, open Account, then your profile, then Delete account. This deletes your cloud backup, your account, and your trips, records and settings on this phone. It can't be undone; export your records as CSV first if you want a copy.
>
> **Fuel prices.** When you're online, Picksmart downloads a public list of CPC fuel prices, at most twice a day. This download doesn't include any information about you or your phone. The website that hosts the list sees your IP address, as with any website; Picksmart doesn't collect it.
>
> **Reading ride offers (optional).** If you turn on Read ride offers, Picksmart reads the text of a ride offer on your screen to estimate its profit. It's read on your phone only and is never stored or sent anywhere.
>
> **Exporting.** You can export your records as CSV files from Reports. You choose where they go; Picksmart doesn't send them anywhere itself.
>
> **Backups by Android.** If Google backup is on for your phone, Android may back up Picksmart's data to your own Google account. You control this in your phone's settings.
>
> **Deleting your data.** Uninstalling Picksmart, or clearing its storage in Android settings, removes all of its data from your phone. To remove your cloud backup, delete your account in the app first.
>
> **Contact.** [owner to add a contact email]

## Play Data safety answers (build with account and backup)

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | Yes, collects (only for a rider who creates an account). Not shared. |
| Personal info | Name, Email address, User IDs, Phone number (optional), Other info: gender and date of birth (optional). Collected, optional, for Account management and App functionality. |
| Photos and videos | Photos: the profile photo the rider chose (optional). Collected, optional, for Account management (restored on a new phone). |
| Financial info | Other financial info (the rider's earnings and costs: trips, income, expenses, fuel). Collected, optional, for App functionality (backup). |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS / TLS). |
| Do you provide a way for users to request that their data is deleted? | Yes: in-app "Delete account" deletes the account, the cloud backup and the data on the phone. Also provide a web link or email for deletion requests, which Play requires. |

Notes for the form:

- Google's definition of "collect" is data sent off the device. A rider without an account sends no user data; the fuel price request isn't collection.
- Data processed only on the device (offer reader text) isn't collected. A trip's optional pickup and "Where to" places are rider-entered text saved with the trip and backed up with it; they are not device location. *Owner: check whether Play wants them declared under Location (approximate) or as App activity / Other user-generated content.* The profile photo, gender and date of birth are collected only for a rider with an account who adds them (backed up since 2026-10-27).
- Play requires a deletion route outside the app as well (a web page or email). The owner needs to add one before upload.
- Re-answer the form when ads (Phase 9) ship.

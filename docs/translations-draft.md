# Sinhala and Tamil translations: drafts and what is still missing

Drafted by Claude on 2026-10-26 for the strings missing from `values-si` and `values-ta`. **Added to the app on 2026-10-26 at the owner's request:** "Both languages", "Tamil only" and the cut-short sentence below. Claude is not a native speaker, so if a line sounds wrong in the app, correct it in `values-si/strings.xml` or `values-ta/strings.xml` (or tell Claude). Still open: the optional shorter Tamil title, and the English-only strings at the end, which have no translation yet.

The drafts reuse the words already in your translations: offer = දීමනාව / சலுகை, trip = ගමන / சவாரி, fare = ගාස්තුව / கட்டணம், save = සුරකින්න / சேமி, estimate = ඇස්තමේන්තු / மதிப்பீடு. "On", "Off" and "Settings" stay in English, as you did elsewhere. `%1$s` and `%2$s` are filled in by the app: keep them exactly as written.

## Both languages (added)

| Key | English | Sinhala | Tamil |
| --- | --- | --- | --- |
| `action_remove` | Remove | ඉවත් කරන්න | அகற்று |
| `onboarding_permissions_title` | Permissions | අවසර | அனுமதிகள் |
| `onboarding_permissions_body` | Both are optional. Picksmart works without them, and you can change them later in Android settings or on the Account tab. | දෙකම අනිවාර්ය නැත. ඒවා නැතිවත් Picksmart ක්‍රියා කරයි. පසුව Android settings හෝ ගිණුම ටැබයෙන් ඒවා වෙනස් කළ හැක. | இரண்டும் கட்டாயமில்லை. அவை இல்லாமலும் Picksmart வேலை செய்யும். பின்னர் Android settings அல்லது கணக்கு தாவலில் அவற்றை மாற்றலாம். |
| `permission_notifications_title` | Notifications | දැනුම්දීම් | அறிவிப்புகள் |
| `permission_notifications_body` | Lets Picksmart ask whether to save a ride offer as a trip, and send reminders you turn on. | ගමන් දීමනාවක් ගමනක් ලෙස සුරකින්නද කියා අසන්නත්, ඔබ On කරන මතක් කිරීම් යවන්නත් Picksmart හට ඉඩ දෙයි. | சவாரி சலுகையைச் சவாரியாகச் சேமிக்கவா என்று கேட்கவும், நீங்கள் ஆன் செய்யும் நினைவூட்டல்களை அனுப்பவும் Picksmart-க்கு அனுமதி அளிக்கிறது. |
| `permission_reader_body` | Shows the estimated profit or loss on Uber Driver offers. Off unless you turn it on. | Uber Driver දීමනා වල ඇස්තමේන්තුගත ලාභය හෝ පාඩුව පෙන්වයි. ඔබ On කරන තුරු Off වේ. | Uber Driver சலுகைகளில் மதிப்பிடப்பட்ட இலாபம் அல்லது நட்டத்தைக் காட்டுகிறது. நீங்கள் ஆன் செய்யும் வரை Off ஆக இருக்கும். |
| `permission_allow` | Allow | ඉඩ දෙන්න | அனுமதி |
| `permission_allowed` | Allowed | ඉඩ දී ඇත | அனுமதிக்கப்பட்டது |
| `permission_turn_on` | Turn on | On කරන්න | ஆன் செய் |
| `permission_on` | On | On | On |
| `permission_open_settings` | Open settings | Settings විවෘත කරන්න | Settings-ஐத் திற |
| `offer_badge_powered` | Powered by Picksmart | Picksmart මඟින් | Picksmart மூலம் |
| `offer_save_channel_name` | Save offers as trips | දීමනා ගමන් ලෙස සුරැකීම | சலுகைகளைச் சவாரிகளாகச் சேமித்தல் |
| `offer_save_channel_description` | After the offer reader shows a ride offer, asks whether to save it as a trip. | දීමනා කියවනය ගමන් දීමනාවක් පෙන්වූ පසු, එය ගමනක් ලෙස සුරකින්නද කියා අසයි. | சலுகை வாசிப்பான் ஒரு சவாரி சலுகையைக் காட்டிய பிறகு, அதைச் சவாரியாகச் சேமிக்கவா என்று கேட்கிறது. |
| `offer_save_title` | Took this ride? Save it as a trip | මෙම ගමන ගත්තාද? එය ගමනක් ලෙස සුරකින්න | இந்தச் சவாரியை எடுத்தீர்களா? அதைச் சவாரியாகச் சேமிக்கவும் |
| `offer_save_body` | %1$s · %2$s km · tap to check and save | %1$s · කි.මී %2$s · පරීක්ෂා කර සුරැකීමට තට්ටු කරන්න | %1$s · %2$s கி.மீ · சரிபார்த்துச் சேமிக்கத் தட்டவும் |
| `offer_save_public_title` | Save your last ride offer as a trip | ඔබගේ අවසන් ගමන් දීමනාව ගමනක් ලෙස සුරකින්න | உங்கள் கடைசி சவாரி சலுகையைச் சவாரியாகச் சேமிக்கவும் |
| `trip_from_offer_title` | Filled from the ride offer | ගමන් දීමනාවෙන් පුරවා ඇත | சவாரி சலுகையிலிருந்து நிரப்பப்பட்டது |
| `trip_from_offer_notice` | These numbers were read from the screen. Check the fare and distances, and add any return trip or other costs, before you save. | මෙම අගයන් තිරයෙන් කියවා ගත් ඒවාය. සුරැකීමට පෙර ගාස්තුව සහ දුර පරීක්ෂා කර, ආපසු ගමනක් හෝ වෙනත් වියදම් ඇත්නම් එක් කරන්න. | இந்த எண்கள் திரையிலிருந்து படிக்கப்பட்டவை. சேமிப்பதற்கு முன் கட்டணம் மற்றும் தூரங்களைச் சரிபார்த்து, திரும்பும் பயணம் அல்லது பிற செலவுகள் இருந்தால் சேர்க்கவும். |
| `profile_change_photo` | Change photo | ඡායාරූපය වෙනස් කරන්න | புகைப்படத்தை மாற்று |
| `profile_change_photo_description` | Change profile photo | පැතිකඩ ඡායාරූපය වෙනස් කරන්න | சுயவிவரப் புகைப்படத்தை மாற்று |
| `profile_remove_photo` | Remove photo | ඡායාරූපය ඉවත් කරන්න | புகைப்படத்தை அகற்று |
| `profile_photo_failed` | Couldn't use that photo. Try another one. | එම ඡායාරූපය භාවිත කළ නොහැකි විය. වෙනත් එකක් උත්සාහ කරන්න. | அந்தப் புகைப்படத்தைப் பயன்படுத்த முடியவில்லை. வேறொன்றை முயற்சிக்கவும். |
| `profile_photo_note` | Your photo, gender and date of birth stay on this phone. They aren't uploaded or shared. | ඔබගේ ඡායාරූපය, ස්ත්‍රී පුරුෂ භාවය සහ උපන් දිනය මෙම දුරකථනයේ පමණක් රැඳේ. ඒවා උඩුගත කිරීම හෝ බෙදාගැනීම සිදු නොවේ. | உங்கள் புகைப்படம், பாலினம் மற்றும் பிறந்த தேதி இந்தத் தொலைபேசியில் மட்டுமே இருக்கும். அவை பதிவேற்றப்படுவதோ பகிரப்படுவதோ இல்லை. |
| `gender_male` | Male | පුරුෂ | ஆண் |
| `gender_female` | Female | ස්ත්‍රී | பெண் |
| `gender_other` | Other | වෙනත් | மற்றவை |
| `gender_prefer_not_to_say` | Prefer not to say | නොකියන්න කැමතියි | சொல்ல விரும்பவில்லை |
| `profile_photo_unsupported` | Choose a JPG, PNG, WebP or HEIC photo. | JPG, PNG, WebP හෝ HEIC ඡායාරූපයක් තෝරන්න. | JPG, PNG, WebP அல்லது HEIC புகைப்படத்தைத் தேர்ந்தெடுக்கவும். |
| `profile_photo_too_large` | That photo is over 20 MB. Choose a smaller one. | එම ඡායාරූපය MB 20 ට වඩා විශාලයි. කුඩා එකක් තෝරන්න. | அந்தப் புகைப்படம் 20 MB-ஐ விட பெரியது. சிறியதைத் தேர்ந்தெடுக்கவும். |

## Tamil only (added)

Your Sinhala file keeps `km/L`, `L` and `/km` as they are, so the Tamil drafts do the same. The two date patterns use the same day-month-year order as your other Tamil dates.

| Key | English | Tamil |
| --- | --- | --- |
| `unit_per_litre` | per litre | லிட்டருக்கு |
| `unit_km_per_litre` | km/L | km/L |
| `unit_litre_short` | L | L |
| `unit_per_km` | /km | /km |
| `pattern_day_of_month` | d | d |
| `pattern_month` | MMM yyyy | MMM yyyy |

## A sentence that was cut short in both languages (fixed)

`reports_incomplete_distance` (Reports, when some trips have no distance). Your translations end after "no fuel or maintenance reserve" and leave out the last part. These drafts add it back with `%2$s`:

- **English, one:** %1$d trip in this period has no distance, so it has no fuel or maintenance reserve, and per km uses the %2$s km that were recorded.
- **English, other:** %1$d trips in this period have no distance, so they have no fuel or maintenance reserve, and per km uses the %2$s km that were recorded.
- **Sinhala, one and other:** මෙම කාල සීමාව තුළ ගමන් %1$d කට දුරක් නොමැත, එබැවින් එයට ඉන්ධන හෝ නඩත්තු වෙන් කිරීමක් නොමැත. කි.මී එකකට ලාභය ගණනය කරන්නේ සටහන් කළ කි.මී %2$s මතය.
- **Tamil, one:** இந்தக் காலகட்டத்தில் %1$d சவாரியில் தூரம் இல்லை, எனவே அதற்கு எரிபொருள் அல்லது பராமரிப்பு ஒதுக்கீடு இல்லை. கி.மீ-க்கான இலாபம் பதிவுசெய்யப்பட்ட %2$s கி.மீ-ஐக் கொண்டு கணக்கிடப்படுகிறது.
- **Tamil, other:** இந்தக் காலகட்டத்தில் %1$d சவாரிகளில் தூரம் இல்லை, எனவே அவற்றுக்கு எரிபொருள் அல்லது பராமரிப்பு ஒதுக்கீடு இல்லை. கி.மீ-க்கான இலாபம் பதிவுசெய்யப்பட்ட %2$s கி.மீ-ஐக் கொண்டு கணக்கிடப்படுகிறது.

## Optional, not changed: a shorter Tamil title for Check a Ride

`check_ride_title` is "சவாரியைச் சரிபார்க்கவும்", which takes two lines and pushes the form down, so on a small phone the result may not fit without scrolling. The tab label already uses the shorter **சவாரியைச் சரிபார்**; using that for the title too would keep it on one line. Your choice.

## New for cloud backup (2026-10-26, branch v2) — English only, to translate

| Key | English |
| --- | --- |
| `backup_title` | Cloud backup |
| `backup_running` | Backing up… |
| `backup_never` | Not backed up yet |
| `backup_done` | Last backed up %1$s |
| `backup_offline` | Waiting for internet. Your records are safe on this phone. |
| `backup_refused` | The backup server didn't accept your sign-in. Sign out and in again. |
| `backup_unverified` | Backup is paused because this copy of Picksmart couldn't be verified. Install Picksmart from Google Play to back up. Your records are safe on this phone. |
| `backup_failed` | Backup didn't finish. Try again. |
| `backup_pending` (plural) | %d change(s) waiting to back up |
| `backup_now` | Back up now |
| `pattern_backup_time` | d MMM, h:mm a (a date pattern, like `pattern_date_field`) |
| `profile_delete_cloud_failed` | Couldn't delete your cloud backup. Check your internet connection and try again. |
| `onboarding_restore_title` | Restoring your data |
| `onboarding_restore_body` | Getting your vehicles, trips and records from your backup. This needs internet. |
| `onboarding_restore_offline` | Couldn't reach your backup. Check your internet connection and try again. |
| `onboarding_restore_unverified` | Couldn't restore your backup because this copy of Picksmart couldn't be verified. Install Picksmart from Google Play, or set up a vehicle now. |
| `onboarding_restore_failed` | Couldn't restore your backup. Try again, or set up a vehicle now and your backup joins this phone later. |
| `onboarding_restore_skip` | Set up a vehicle now |

## Delete account (2026-10-26) — translated by Claude, in the app

The owner asked for these to be translated (from screenshots of the delete warning and the Account tab note), so Claude's Sinhala and Tamil are in the app, together with `profile_delete_confirm` (සියල්ල මකන්න / அனைத்தையும் நீக்கு), `profile_delete_cloud_failed` and three new messages for why the cloud backup couldn't be deleted (`profile_delete_cloud_refused`, `profile_delete_cloud_unverified`, `profile_delete_cloud_error`). `profile_delete_item` ("• %1$s") is not translatable. Correct any line that reads wrong. The English was:

| Key | English |
| --- | --- |
| `profile_delete_body` | This can't be undone. It permanently deletes: |
| `profile_delete_item` | • %1$s |
| `profile_delete_item_account` | Your Picksmart account |
| `profile_delete_item_backup` | Your cloud backup |
| `profile_delete_item_phone` | Everything on this phone: vehicles, trips, fuel logs, expenses, income, profile details and settings |
| `profile_delete_export_hint` | Want a copy first? Cancel and export your records as CSV. Picksmart then starts again from the welcome screen. This needs internet. |
| `profile_delete_password_label` | Your password |
| `profile_delete_password_hint` | Enter your password to confirm it's you. |
| `profile_delete_google_hint` | You'll choose your Google account to confirm it's you. |
| `profile_deleting` | Deleting… |
| `profile_delete_account_failed` | Your cloud backup was deleted, but your account wasn't. Your records are still on this phone. Check your internet connection and try again. |
| `profile_records_stay` | Signing out keeps your trips and records on this phone. Deleting your account removes them from this phone and from the cloud. |
| `auth_error_different_account` | That's a different Google account. Choose the account you're signed in with. |


**English changed, so this Sinhala and Tamil is out of date:**

- `onboarding_safety_on_device`: now "Your records stay on this phone and work without internet. When you're signed in, they're also backed up to your account."

# GBSat — Privacy Policy

_Last updated: November 2, 2026_

GBSat is a Sega Saturn emulator for Android. This policy explains what data the app handles. GBSat has no ads, analytics or tracking. If you choose to buy or restore Gold, the app sends a Google Play purchase token, app and product IDs, and Android ID to the GameBeacon license service to verify and record the license. Google Play handles payment; the developer does not receive your payment credentials, Google account password, or account email.

## Data stored on your device

GBSat keeps the following only on your device (in the app's storage or in a folder you choose):

- Settings, save states, saved games, cheats, play time per game, downloaded cover images, and the local ownership state for an optional Google Play purchase
- Your BIOS file and the location of your games folder (your games are read from your storage; they are never uploaded)
- Crash reports, shown to you on the next launch (never sent anywhere)
- If you use RetroAchievements: your username, profile picture and the login token issued by RetroAchievements. The
  session token is kept in Android's no-backup storage and is not copied by Android backup or device transfer.

Uninstalling the app or clearing its data removes all of it (except files in a folder you chose yourself).

## Network connections

GBSat connects to the internet only for these optional features:

| Service | When | What is sent |
| --- | --- | --- |
| **RetroAchievements** (retroachievements.org, media.retroachievements.org) | Only if you enable RetroAchievements and log in | Your username and password at login (password sent only to RetroAchievements, never stored by GBSat), then the login token, a hash identifying the game disc, and your achievement unlocks |
| **Cover art** (thumbnails.libretro.com) | When the library shows a game without a cover | The game's title, as part of the image address |
| **Google Play Billing and GameBeacon license service** (`gblicence.lovable.app`) | Only if you choose to buy or restore Gold themes | Google Play processes payment. To verify the license, GBSat sends the Play purchase token, app/product IDs, and Android ID to the GameBeacon server over HTTPS. The server records the license; it does not receive your payment credentials or Google account password |

These services have their own privacy policies:
[RetroAchievements](https://retroachievements.org) (see its site footer) and [libretro](https://www.libretro.com/index.php/privacy-policy/).
Like any internet request, they can see your IP address.

## Optional theme purchase

GBSat offers one optional, non-consumable purchase through Google Play. It unlocks the **Grid** and **All in One**
library themes and supports the developer. Custom cover images, the default **Showcase** theme, automatically sourced
cover art, emulation, and RetroAchievements remain available without this purchase. Google Play handles payment. For
verification and restoration, GBSat sends the purchase token, app/product IDs, and Android ID to the GameBeacon license
service over HTTPS, which records the license. GBSat also caches the resulting ownership state locally and excludes that
cache from Android backup. Purchases can be restored on devices using the same Google Play account. We do not receive
payment credentials, your Google account password, or your account email through this verification.

## Permissions

- **Internet**: RetroAchievements and cover art, as described above.
- **Storage / file access**: to read the disc images and BIOS file you select.

## Children

GBSat is not directed at children and does not knowingly collect data from anyone.

## Changes

Changes to this policy are published in this file, in the GBSat source repository.

## Contact

Questions: open an issue at <https://github.com/mindspotgb/GBSat/issues>.





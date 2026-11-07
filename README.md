# GazAI

Android fuel tracker for cars and motorcycles, inspired by Fuelio-style apps (fuel.io is closed source, so this is a from-scratch build).
Open the app, photograph the pump, done: a **free OpenRouter model** reads the total cost and liters and logs the fill-up.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

## Features
- Opens straight to the camera for your default vehicle; AI (plus on-device text recognition) reads the pump and adds the fill-up, with Undo
- Cars and motorcycles with make, model, year, fuel type, tank size, plate and a photo; edit or delete any time
- Stats: km/L, L/100km or mpg for the last fill-up vs. your average and the previous one, best/worst, trend chart, monthly spend, cost per km
- Service log with last maintenance and next-due reminders
- Fix or delete any fill-up from the Log
- Type a note ("filled the bike 8.2 L for 15.40, 12340 km") or share a receipt photo/text from any app
- Metric/imperial, fill-ups stored on the phone (Room), your own free OpenRouter key

## Setup
1. Install the APK from the latest [release](https://github.com/mcastae813/GazAI/releases).
2. Get a free key at https://openrouter.ai/keys and paste it in on the first screen (or Settings).
3. If photos never get read, use **Settings > Test AI connection**. If it says free models are blocked, allow free model endpoints at https://openrouter.ai/settings/privacy
   (see Privacy below for what that means).

Leave the model fields blank: GazAI lists OpenRouter models priced at 0 and falls back through them if one is busy. You can pin a model id (e.g. one ending in `:free`).

## Privacy
- Your fill-ups, vehicles, photos and settings are stored only on the phone (and in your Android backup, if it's on).
- Each pump photo, receipt or note that GazAI reads is sent to OpenRouter, which passes it to a free model provider,
  together with your vehicles' names, year, make and model (never the plate).
- Free model providers may log what they receive or use it for training. Allowing free model endpoints in your
  OpenRouter privacy settings means accepting that. Type the numbers in by hand for anything you'd rather not send.
- The OpenRouter key is kept out of Android's cloud backup.

## Release signing
Releases must always be signed with the same key, otherwise Android refuses to install updates.
The Release workflow reads it from two repository secrets (Settings > Secrets and variables > Actions):
- `SIGNING_KEYSTORE_B64`: the keystore file, base64-encoded (`base64 -w0 gazai-release.jks`)
- `SIGNING_PASSWORD`: its password (key alias `gazai`)

Keep a private backup of the keystore and password. To release:
1. In `app/build.gradle.kts`, set `versionName` to `x.y.z` and raise `versionCode`.
2. In CHANGELOG.md, rename `## Unreleased` to `## x.y.z - <date>`.
3. Run Actions > Release with version `vx.y.z`. It stops if the version, `versionCode` or changelog section doesn't match.

## Build
Open in Android Studio, or `./gradlew testDebugUnitTest assembleDebug`. CI runs the tests and builds a debug APK on every push (Actions > Build APK).
Don't install a debug APK over a release one: they are signed with different keys.

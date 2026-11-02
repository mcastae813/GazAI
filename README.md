# GazAI

Android fuel tracker for cars and motorcycles, inspired by Fuelio-style apps (fuel.io is closed source, so this is a from-scratch build).
Instead of typing every fill-up, you describe it or snap the receipt and a **free OpenRouter model** fills the log for you.

## Features
- Multiple vehicles (car, motorcycle)
- Type a note ("filled the bike 8.2 L for 15.40 at Shell, 12340 km") and/or attach a receipt/pump photo; AI extracts date, odometer, fuel, cost, station and auto-adds the entry
- Share a receipt photo or text from any app straight to GazAI
- Consumption (L/100km or mpg), spend, average price per vehicle
- Metric/imperial, local-only data (Room), your own OpenRouter key

## Setup
1. Get a free key at https://openrouter.ai/keys and paste it in Settings.
2. Leave the model fields blank: GazAI lists OpenRouter models priced at 0 and falls back through them if one is busy. You can pin a model id (e.g. one ending in `:free`).

## Build
Open in Android Studio, or `./gradlew assembleDebug`. CI builds the APK on every push (Actions > Build APK > artifact).

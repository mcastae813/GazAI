# Changelog

All notable changes to GazAI. Versions follow the GitHub release tags.

## 0.3.0 - 2026-11-04

> **One-time reinstall:** 0.1 and 0.2 were signed with a throwaway key, so Android refuses to install 0.3
> over them ("App not installed" / "package conflicts"). Uninstall the old version once, then install 0.3.
> From 0.3 on, every release uses the same permanent key and installs as a normal update.

### Fixed
- Pump photos not being read:
  - a model that answered without any numbers counted as a success, so no other model was tried; it now moves on to the next free model
  - "reasoning" free models ran out of room before answering (answer limit raised from 500 to 1500 tokens, and their reasoning text is checked too)
  - if Android closed GazAI while the camera was open, the photo was dropped and the camera opened again; the photo is now kept and read
  - numbers like `1,234.56`, `1.234,56` and `$ 45.20` now parse
- Updates failing to install: releases are now signed with one permanent key
- Deleting a vehicle no longer happens on a single tap; it asks first and says how many fill-ups go with it

### Added
- On-device text recognition (ML Kit) reads the pump digits and is sent along with the photo; if no free photo model works, the recognized text is read by a free text model instead
- Clear error messages that say what each model did (rate limited, blocked by OpenRouter privacy settings, couldn't find the numbers...)
- "Test AI connection" button in Settings
- Shows which model read each photo
- Vehicle details: make, model, year, fuel type, tank size and plate, with an Edit button in the Garage
- First-run setup asks for your vehicle details and the OpenRouter key
- Fill-ups bigger than the vehicle's tank are not auto-saved; you're asked to check the numbers
- Automated tests for database upgrades, number parsing and fuel-efficiency math

## 0.2.0 - 2026-11-04

### Added
- App opens straight to the camera for the default vehicle; the AI reads total cost and liters from the pump photo and adds the fill-up, with Undo
- Odometer prompt after an auto-added fill-up
- Stats tab: km/L, L/100km or mpg for the last fill-up compared with the average and the previous one, best/worst, trend chart, monthly spend, cost per km
- Service log with last-maintenance info and next-due reminders
- Vehicle photos and a default vehicle

## 0.1.0 - 2026-11-02

### Added
- First release: cars and motorcycles, fuel log, consumption stats, AI fill-up entry from a note or receipt photo using free OpenRouter models, share-to-GazAI

# Detour

A personal Android app that steps in when Instagram opens and gives you somewhere better to go: a finite daily route of words, slang and terms, India and world news, and one good video.

## How it fits together

```
GitHub Action (06:00 IST) ──► pipeline/build_day.py ──► content/latest.json ──► Detour app (fetches ~06:05)
```

- **pipeline/**: standard-library Python. Fetches news RSS, YouTube channel feeds (long-form only) and podcast feeds, asks Gemini for words, loop cards and news bullets, validates, and writes `content/`. Without `GEMINI_API_KEY` it falls back to `pipeline/seed/` and raw headlines.
- **app/**: Kotlin + Jetpack Compose. Everything is stored on the phone (Room). No accounts, no backend.

## Run the pipeline locally

```bash
python pipeline/build_day.py --dry-run          # print today's content, write nothing
GEMINI_API_KEY=... python pipeline/build_day.py  # write content/latest.json
```

Edit `pipeline/sources.json` to change news feeds, YouTube channels (by channel ID) or podcasts.

## Build the app

1. Open this folder in Android Studio and let Gradle sync.
2. Plug in the phone with USB debugging on, then press Run.

Or from a terminal: `./gradlew installDebug`.

The app reads content from `BuildConfig.CONTENT_URL` (set in `app/build.gradle.kts`). Until the repo exists it uses the bundled `app/src/main/assets/sample_latest.json`.

## Turning on the Instagram pause

In the app: **You → Set up the Instagram pause**. On Android 13+, a sideloaded app's accessibility toggle is greyed out until you open **App info → ⋮ → Allow restricted settings**.

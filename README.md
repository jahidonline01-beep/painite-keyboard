# Painite Keyboard

A premium Android IME (Input Method Editor) keyboard app with neon/glass design.

## Features

- **10 Premium Themes** — Neon Dark, Neon Pink, Glass Ocean, Purple Galaxy, Gold Luxury, Red Fire, Cyber Green, Ice White, Sunset Vibes, Painite
- **Dual Language** — English & Bangla keyboard layouts, switch with BN/EN button
- **Rowbar** — Scrollable toolbar with drag-to-reorder buttons
- **Voice Typing** — Speak text in English or Bangla
- **Clipboard Manager** — Unlimited clipboard history, pin/unpin, delete
- **Translate** — Built-in Google Translate (EN ↔ BN)
- **Emoji Panel** — Full emoji picker with categories
- **Number Row** — Toggleable 1–0 row above letters
- **Symbol Keyboard** — Two pages of symbols, special characters, math, arrows
- **Settings** — Theme picker, toggle number row, reset rowbar, clear clipboard

## Project Structure

```
painite-keyboard/
├── app/
│   └── src/main/
│       ├── java/com/painite/keyboard/
│       │   ├── PainiteApp.kt              # Application class
│       │   ├── ime/
│       │   │   └── PainiteIME.kt          # Main IME service
│       │   ├── data/
│       │   │   ├── AppDatabase.kt         # Room database
│       │   │   ├── ClipboardItem.kt       # Clipboard entity
│       │   │   ├── ClipboardDao.kt        # Clipboard DAO
│       │   │   ├── ClipboardRepository.kt
│       │   │   └── SettingsRepository.kt  # DataStore settings
│       │   ├── ui/
│       │   │   ├── keyboard/
│       │   │   │   ├── KeyboardScreen.kt  # Main keyboard composable
│       │   │   │   ├── KeyboardViewModel.kt
│       │   │   │   └── components/
│       │   │   │       ├── KeyButton.kt
│       │   │   │       ├── LetterKeys.kt
│       │   │   │       ├── BanglaKeys.kt
│       │   │   │       ├── NumberRow.kt
│       │   │   │       ├── SymbolKeys.kt
│       │   │   │       ├── BottomRow.kt
│       │   │   │       ├── RowBarSection.kt
│       │   │   │       └── EmojiPanel.kt
│       │   │   ├── clipboard/
│       │   │   │   └── ClipboardPanel.kt
│       │   │   ├── translate/
│       │   │   │   └── TranslatePanel.kt
│       │   │   ├── settings/
│       │   │   │   ├── SettingsActivity.kt
│       │   │   │   └── QuickSettingsPanel.kt
│       │   │   └── setup/
│       │   │       └── SetupActivity.kt
│       │   ├── utils/
│       │   │   ├── VoiceTypingManager.kt
│       │   │   └── HapticManager.kt
│       │   └── ui/theme/
│       │       └── KeyboardTheme.kt       # All 10 themes
│       ├── res/
│       └── AndroidManifest.xml
├── .github/
│   └── workflows/
│       └── build.yml                      # GitHub Actions APK builder
├── build.gradle
├── settings.gradle
├── gradlew
└── gradlew.bat
```

## Build Instructions

### Option 1 — GitHub Actions (Recommended, no setup needed)

1. Push this project to a GitHub repository
2. Go to **Actions** tab → **Build Painite APK** → **Run workflow**
3. After it finishes, download the APK from the **Artifacts** section

### Option 2 — Android Studio locally

1. Open `painite-keyboard/` folder in Android Studio
2. Let Gradle sync finish
3. Run **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. APK will be at `app/build/outputs/apk/debug/app-debug.apk`

### Option 3 — Command line

```bash
cd painite-keyboard
chmod +x gradlew
./gradlew assembleDebug
```

## Install & Activate

After installing the APK on your Android phone:

1. Open the **Painite** app
2. Tap **"Enable Keyboard"** → Turn on Painite in Settings
3. Tap **"Choose Keyboard"** → Select Painite as active input method
4. Open any app and tap a text field — Painite keyboard appears

## Requirements

- Android 8.0+ (API 26+)
- Microphone permission for Voice Typing
- Internet permission for Translate feature

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Database**: Room (clipboard storage)
- **Settings**: DataStore Preferences
- **Build**: Gradle 8.4 + AGP 8.2
- **Min SDK**: 26 | Target SDK: 34

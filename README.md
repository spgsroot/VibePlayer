# VibePlayer

<div align="center">

**VibePlayer** is a modern Android video player with support for syncing with adult devices via **Buttplug API**.

[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09.00-blue.svg)](https://developer.android.com/jetpack/compose)
[![Buttplug](https://img.shields.io/badge/Buttplug-API-orange.svg)](https://buttplug.io/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![justforfunnoreally.dev badge](https://img.shields.io/badge/justforfunnoreally-dev-9ff)](https://justforfunnoreally.dev)

[🇷🇺 Russian version](README_RU.md)

</div>

---

## 📱 Screenshots

<div align="center">

|                       Main Screen                        | Gallery |
|:--------------------------------------------------------:|:---:|
| <img src="repo/main.png" width="200" alt="Main Screen"/> | <img src="repo/gallery.png" width="200" alt="Gallery"/> |

| Add Video | Settings |
|:---:|:---:|
| <img src="repo/AddVideo.png" width="200" alt="Add Video"/> | <img src="repo/settings.png" width="200" alt="Settings"/> |

</div>

---

## ✨ Features

- **🎬 Local video playback** — support for various video formats via ExoPlayer
- **🌐 WebView playback** — open a website in-app and use page audio for haptic sync
- **📁 Gallery** — video library management with rename and cover options
- **🔗 Buttplug API integration** — direct device sync via Buttplug.io
- **📡 Bluetooth connection** — device support through Intiface Central
- **🎚️ Unified DSP settings** — shared smoothing, power boost, and threshold for Player and WebView sources
- **⏱️ Auto-switch timer** — automatic video switching by timer
- **🎛️ Playback speed** — adjustable from 0.5x to 2.0x
- **🔒 Password protection** — app lock with password
- **🌐 Multi-language** — Russian/English with auto-detection
- **📥 Video download** — URL download directly to app
- **🎨 Material Design 3** — modern UI built with Jetpack Compose
- **💾 Encrypted database** — SQLCipher for data protection

---

## 🔌 Buttplug API Integration

VibePlayer uses **Buttplug API** to sync with compatible devices:

### Supported Devices

- **Lovense** — Max, Nora, Lush, Calor, Ferri, Solace, and more
- **WeVibe** — Pivot, Connect, Verge, Moxie, Jive series
- **Kiiroo** — Pearl, Keon, Onyx, Pulse
- **Satisfyer** — Bluetooth-enabled models
- **Magic Motion** — Compatible devices
- **Other** — Any device supported by Buttplug.io

### How It Works

```
┌─────────────────┐     Bluetooth      ┌─────────────────┐
│   VibePlayer    │ ◄────────────────► │    Device       │
│   (Android)     │                    │  (Lovense, etc.)│
└────────┬────────┘                    └─────────────────┘
         │
         │ Buttplug Protocol
         ▼
┌─────────────────┐
│ Intiface Central│
│   (Server)      │
└─────────────────┘
```

1. **Intiface Central** runs on PC or mobile device
2. VibePlayer connects to Intiface and discovers supported actuators
3. Local player audio or WebView page audio is analyzed in real time
4. Intensity is mapped through unified DSP settings and sent through **Buttplug Protocol**

### Buttplug Commands Used

| Command | Description |
|---------|-------------|
| `DeviceScan` | Scan for nearby devices |
| `DeviceConnect` | Connect to selected device |
| `StopAllDevices` | Stop all device activity |
| `ScalarCmd` / actuator commands | Control vibration, rotation, oscillation, position and other supported actuators |
| `BatteryLevelCmd` | Check device battery level |

### Benefits of Buttplug API

- ✅ **Single protocol** for all supported devices
- ✅ **Open specification** — fully documented
- ✅ **Active community** — regular updates and support
- ✅ **Cross-platform** — works on Android, iOS, PC

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 2.0.21 |
| **UI Framework** | Jetpack Compose, Material Design 3 |
| **Architecture** | MVVM, Clean Architecture |
| **Dependency Injection** | Hilt |
| **Database** | Room + SQLCipher |
| **Async Operations** | Coroutines, Flow |
| **Navigation** | Navigation Compose |
| **Networking** | OkHttp |
| **Media Player** | ExoPlayer (Media3) |
| **Image Loading** | Coil |
| **Device Protocol** | Buttplug Android Library |

---

## 📋 Requirements

- **Android 8.0 (API 26)** or higher
- **Android 13 (API 33)** recommended for full localization support
- **Android 10 (API 29)** or higher for WebView audio capture haptics

### For Buttplug Sync

- **Intiface Central** (PC or mobile app)
- **Compatible Bluetooth device**
- **Bluetooth permission** granted

### For WebView Audio Haptics

- Android audio output capture consent via MediaProjection
- `RECORD_AUDIO` permission
- A visible foreground capture notification while WebView audio analysis is active
- Note: while WebView is open, Android audio output capture may include other media playing on the device; audio is analyzed locally and converted to haptic intensity.

---

## 🚀 Installation

### From APK

1. **Download APK** from [Releases](https://github.com/spgsroot/VibePlayer/releases)
2. **Enable installation from unknown sources** in device settings
3. **Install APK** and launch the app
4. **For Buttplug:** install [Intiface Central](https://intiface.com/central/)

### From Google Play

> Coming soon...

---

## 🔧 Build from Source

```bash
# Clone the repository
git clone https://github.com/spgsroot/VibePlayer.git
cd VibePlayer

# Open in Android Studio or build via command line
./gradlew assembleDebug

# APK will be created in app/build/outputs/apk/debug/
```

### Build Requirements

- Android Studio Hedgehog or newer
- JDK 11 or higher
- Android SDK 36

---

## 📖 Usage Guide

### Quick Start with Buttplug

#### 1. Setup Intiface Central

- Download and install [Intiface Central](https://intiface.com/central/)
- Launch the application on your PC
- Enable Bluetooth server in settings

#### 2. Connect Your Device

- Open VibePlayer on your Android device
- Navigate to **Settings → Device Connection**
- Tap **Start Scan**
- Select your device from the discovered list

#### 3. Start Playback

- Add a video from gallery or paste URL
- Tap to start playback
- Device will sync automatically with video

#### 4. Configure Sync Settings

- Open **Settings** menu
- Configure **auto-switch timer** for automatic playback
- Adjust **playback speed** (0.5x - 2.0x)
- Tune shared **DSP settings** for all haptic sources:
  - **Smoothing** — response stability
  - **Threshold** — silence/noise cutoff
  - **Power Boost** — final intensity multiplier
- Set your preferred **language**

### WebView Haptics

1. Open **Add Video → Open WebView**.
2. Enter a website URL.
3. Grant Android audio capture consent.
4. Play media on the page; VibePlayer analyzes WebView audio and drives the selected Buttplug device through the same DSP settings as the local player.

When local player playback is started again, WebView audio capture is stopped so the two sources do not fight for device control.

### Adding Videos

| Method | Description |
|--------|-------------|
| **Gallery** | Import videos from device storage |
| **URL** | Paste direct video link |
| **Batch Import** | Import multiple URLs at once |
| **WebView** | Open a website and use page audio for haptics |

---

## 🌐 Languages

The app supports two languages with automatic detection:

| Language | Option |
|----------|--------|
| **System Default** | Follows device language |
| **Русский** | Force Russian interface |
| **English** | Force English interface |

Change language in **Settings → Language**.

---

## 🔒 Security

- **🔐 App Password** — PIN code protection against unauthorized access
- **🔒 SQLCipher** — 256-bit AES database encryption
- **🛡️ Secure Storage** — Android Keystore for sensitive data
- **📱 Runtime Permissions** — Bluetooth and WebView audio capture permissions are requested only when needed
- **🎧 Local audio analysis** — audio samples are analyzed on-device for intensity and are not stored by VibePlayer

---

## 📁 Project Structure

```
VibePlayer/
├── app/
│   ├── src/main/
│   │   ├── java/ru/spgsroot/vibeplayer/
│   │   │   ├── data/
│   │   │   │   ├── db/              # Room database + SQLCipher
│   │   │   │   ├── repository/      # Data repositories
│   │   │   │   ├── downloader/      # Video download service
│   │   │   │   └── storage/         # File storage management
│   │   │   ├── device/
│   │   │   │   └── buttplug/        # Buttplug API integration
│   │   │   ├── domain/
│   │   │   │   ├── dsp/             # Audio analysis, haptic mapping and runtime DSP config
│   │   │   │   └── model/           # Business logic models
│   │   │   ├── playback/
│   │   │   │   ├── player/          # ExoPlayer wrapper and DSP audio processor
│   │   │   │   └── service/         # Foreground playback and WebView audio capture services
│   │   │   ├── ui/
│   │   │   │   ├── player/          # Video player screen
│   │   │   │   ├── webview/         # In-app WebView playback screen
│   │   │   │   ├── gallery/         # Gallery screen
│   │   │   │   ├── settings/        # Settings screen
│   │   │   │   ├── auth/            # Authentication screen
│   │   │   │   ├── dialog/          # Dialog components
│   │   │   │   └── onboarding/      # Onboarding flow
│   │   │   ├── di/                  # Hilt dependency injection
│   │   │   ├── locale/              # Localization manager
│   │   │   └── security/            # Auth & encryption
│   │   └── res/
│   │       ├── values/              # Russian strings
│   │       └── values-en/           # English strings
│   └── build.gradle.kts
└── build.gradle.kts
```

---

## 🔗 Useful Links

### Buttplug Resources

- [**Buttplug.io Official**](https://buttplug.io/) — Official documentation
- [**Intiface Central**](https://intiface.com/central/) — Connection server
- [**Device List**](https://buttplug.io/docs/devices/) — Supported devices
- [**API Reference**](https://buttplug.io/docs/) — API documentation
- [**Discord Community**](https://discord.gg/9jRg3qf) — Get help and chat

### Development

- [**Jetpack Compose**](https://developer.android.com/jetpack/compose)
- [**Hilt**](https://developer.android.com/training/dependency-injection/hilt-android)
- [**Room Database**](https://developer.android.com/training/data-storage/room)

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

1. **Report bugs** — Open an issue with detailed description
2. **Suggest features** — Share your ideas for improvements
3. **Improve translations** — Help localize the app
4. **Add device support** — Contribute Buttplug device configurations
5. **Submit PRs** — Send pull requests for fixes and features

### Development Setup

```bash
# Fork and clone
git clone https://github.com/spgsroot/VibePlayer.git
cd VibePlayer

# Create a branch
git checkout -b feature/your-feature

# Make changes and commit
git commit -m "Add: your feature description"

# Push and create PR
git push origin feature/your-feature
```

---

## 📄 License

**MIT License** — free to use, modify and distribute.

```
Copyright (c) 2024 VibePlayer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

## 💖 Support

If you like this project and want to support its development, you can donate via TON:

**TON:** `UQCGFymEHFNq1IcIhXBWJJe7Ha7Cx7RU6apvotRs5DcEEAaG`

Every contribution helps keep this project alive and growing! ❤️

---

## 📞 Contact

- **GitHub:** [@spgsroot](https://github.com/spgsroot)
- **Email:** aqu.de@yandex.ru

---

<div align="center">

**VibePlayer** © 2026

Powered by **[Buttplug.io](https://buttplug.io/)**

Made with ❤️ using Kotlin & Jetpack Compose

</div>

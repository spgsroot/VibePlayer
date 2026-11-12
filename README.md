# VibePlayer

Android video player that maps playback audio to Buttplug actuators, plus a reader for nhentai. UI is Jetpack Compose. Version in this tree: `2.4.0` (`versionCode` 4). [Русская версия](README_RU.md).

The player shot is a local sample clip. The gallery shows clips imported into the app. Catalog covers and titles are blurred. The reader shot is the screen after a failed open and does not contain page images.

| Onboarding | PIN | Player |
|:---:|:---:|:---:|
| <img src="repo/screens/onboarding.png" width="180" alt="Onboarding"/> | <img src="repo/screens/auth.png" width="180" alt="PIN pad"/> | <img src="repo/screens/player.png" width="180" alt="Player with a local clip"/> |

| Settings | Device | Add video |
|:---:|:---:|:---:|
| <img src="repo/screens/settings.png" width="180" alt="Settings"/> | <img src="repo/screens/device.png" width="180" alt="Intiface dialog"/> | <img src="repo/screens/add-video.png" width="180" alt="Add video"/> |

| Gallery | Catalog | Reader |
|:---:|:---:|:---:|
| <img src="repo/screens/gallery.png" width="180" alt="Gallery with imported clips"/> | <img src="repo/screens/catalog.png" width="180" alt="Catalog search with covers blurred"/> | <img src="repo/screens/reader.png" width="180" alt="Reader with no pages loaded"/> |

## What it does

- Plays local video with Media3 ExoPlayer. The gallery can rename an item, replace its cover, and delete one or many items.
- Downloads a video from an `http` or `https` URL into the app library.
- Opens an HTTPS page in a WebView and uses that page's audio for haptics. The window is marked `FLAG_SECURE`. Non-HTTPS pages are rejected.
- Connects to Intiface Central over a WebSocket. The default address is `ws://127.0.0.1:12345`. The app does not scan Bluetooth itself; Intiface does that.
- Maps audio amplitude through one DSP path: bass range, smoothing, threshold, and power boost. The same settings apply to the player and the WebView.
- Sends commands only to actuators that can follow an audio level, such as vibration, oscillation, rotation, and position.
- Switches videos on a timer and changes playback speed.
- Locks the app with a 4-digit PIN. Repeated failures increase the wait. The database is SQLCipher.
- Speaks Russian and English. The interface is dark.
- Searches nhentai in a catalog that filters by tag, date, and page count, sorts by date or popularity, and opens a native Compose reader. Saves a gallery on the device and marks favorites. Page images are requested from `i1.nhentai.net` and `t1.nhentai.net` without rewriting the path returned by the API. Turning a page sends a short pulse. Page-turn strength is configured in the reader settings sheet, not with a slider on the page.

The nhentai key is compiled into the client and sent only in the `Authorization` header. It is in the public source and can be read from the APK. Minify is off in the release build type.

## Requirements

- minSdk 26, targetSdk 36
- JDK 17 and an Android SDK for a local build
- Intiface Central, if a device should move

## Build

```bash
./gradlew :app:assembleDebug
```

`assembleRelease` produces an unsigned APK. The GitHub Actions workflow `.github/workflows/release.yml` signs the release with repository secrets. It runs on a `v*` tag or when started by hand. This checkout has no `LICENSE` file.

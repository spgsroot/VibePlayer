# Peachy

Private player and gallery for adult content: videos, image galleries and hentai manga in one app. It syncs playback with devices through the Buttplug protocol (Intiface Central). Native Android, Jetpack Compose. Version in this tree: `3.0.0` (`versionCode` 5). [Русская версия](README_RU.md).

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

| Manga drawer | Tag browser |
|:---:|:---:|
| <img src="repo/screens/drawer.png" width="180" alt="Manga navigation drawer"/> | <img src="repo/screens/tags.png" width="180" alt="Tag browser"/> |

## What it does

- Plays local video with Media3 ExoPlayer. The gallery can rename an item, replace its cover, and delete one or many items.
- Downloads a video from an `http` or `https` URL into the app library.
- Imports videos from file-host links: an album or a single file on Bunkr, Cyberdrop, Turbo, Pixeldrain or Filester. An album link brings every video in it; images and archives are skipped. Only public links work: password albums, GoFile and FileDitch are not supported. Turbo's CDN could not be reached from the test machine, so Turbo downloads are untested.
- Exports selected gallery videos to a folder you choose. The app gets access to that folder only, and a failed copy is removed instead of left half-written.
- Compresses selected gallery videos with Media3 Transformer: four presets (visually lossless, compact, smaller, smallest) or custom codec, bitrate, height, frame rate and audio. HEVC is used only where the phone's encoder supports the output size; otherwise that video gets H.264 at a higher bitrate. The dialog shows a size estimate. The original is replaced only when the new file is smaller and passes a check (size, duration, audio); otherwise it stays. Cancelling leaves originals untouched.
- Opens an HTTPS page in a WebView and uses that page's audio for haptics. The window is marked `FLAG_SECURE`. Non-HTTPS pages are rejected.
- Has a built-in browser with tabs, bookmarks, history and an ad and tracker blocker. The blocker uses EasyList, EasyPrivacy and an adult list bundled in the app; "Update filter lists" in the menu fetches fresh copies. Blocking can be turned off per site. The browser lists the video files a page loads and saves a chosen one to the library; HLS and DASH streams cannot be saved. Pages open over HTTPS only.
- Keeps a private note on a video, a saved gallery or a browser page. Notes live in the encrypted database and are never sent anywhere.
- Exports the whole library to one password-protected `.peachy` file and imports it on another phone (Settings, Backup): videos with covers, saved galleries and pages, read and search history, tags and blacklist, notes, bookmarks and browser history. AES-256-GCM, key from PBKDF2-SHA256; the file is written and read in blocks, so a large library does not need RAM. Import only adds: videos already present are skipped, newer notes win, nothing is deleted. The PIN and device settings are not included. A forgotten password cannot be recovered.
- Connects to Intiface Central over a WebSocket. The default address is `ws://127.0.0.1:12345`. The app does not scan Bluetooth itself; Intiface does that.
- Maps audio amplitude through one DSP path: bass range, smoothing, threshold, and power boost. The same settings apply to the player and the WebView.
- Sends commands only to actuators that can follow an audio level, such as vibration, oscillation, rotation, and position.
- Switches videos on a timer and changes playback speed.
- Locks the app with a 4-digit PIN. Repeated failures increase the wait. The database is SQLCipher.
- Speaks Russian and English. The interface is dark.
- Browses nhentai in a drawer-based hub: Catalog, Downloads, Favorites, History, Tags, Characters, and Random gallery. The catalog is a two-column cover grid with tag chips, filters by tag (include or exclude), date, and page count, and sorting by date or popularity. The tag browser lists tags, artists, characters, parodies, groups, categories, and languages by popularity; tags can be marked favorite, put into named collections kept on the device, and sent to the catalog as a search. A gallery page shows titles, page count, favorite count, upload date, tags grouped by type, related galleries, and read-only comments loaded on demand. Posting comments needs an nhentai account and is not available. Galleries open in a native Compose reader and can be saved on the device and marked favorite. Page images are requested from `i1.nhentai.net` and `t1.nhentai.net` without rewriting the path returned by the API. Turning a page sends a short pulse. Page-turn strength is configured in the reader settings sheet, not with a slider on the page.
- Recommends galleries in a For you tab, computed on the device. It learns from the galleries you open, read, save, and favorite, from your favorite tags and tag collections, and from "Not interested" taps, with older signals fading over time. Candidates come from nhentai searches by your top tags and from the related lists of galleries you liked; each pick shows the tags that earned it a place. Until there is some taste to go on, the tab shows what is popular. The upload date appears on a card only for galleries whose page the app has already opened, because search results do not carry it. History uses the same large cards as the catalog. A gallery page shows a grid of page previews under the tags, and a tap opens the reader at that page.

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

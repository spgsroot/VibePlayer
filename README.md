# Peachy

Private player and gallery for adult content: videos, image galleries and hentai manga in one app. It syncs playback with devices through the Buttplug protocol (Intiface Central). Native Android, Jetpack Compose. Version in this tree: `3.1.0` (`versionCode` 6). [Русская версия](README_RU.md).

Screenshots use sample clips. Catalog covers and titles are pixelated, and the reader shot shows its settings dialog over a pixelated page.

## Overview

| Area | What you get |
|---|---|
| Player and devices | Local video on Media3 ExoPlayer, with playback speed and a timer that switches videos. Audio amplitude goes through one DSP path (bass range, smoothing, threshold, power boost) and drives vibration, oscillation, rotation or position on devices connected through Intiface Central. |
| Library | A gallery of your videos: rename, change the cover, delete, export to a folder, compress with HEVC or H.264. Add videos from the phone, from a URL, from file-host links (Bunkr, Cyberdrop, Turbo, Pixeldrain, Filester) or from the built-in browser. Private notes on videos. |
| Browser | Tabs, bookmarks, history and an ad and tracker blocker. It lists the video files a page loads and saves a chosen one to the library; HLS and DASH streams cannot be saved. A page's audio can drive haptics. |
| Manga | nhentai in a native app: a catalog with tag filters, a tag browser with favorites and collections, a For you tab computed on the device, history, saved galleries, and a native reader with zoom, a thumbnail strip and saved pages. Tag names are shown in Russian when the interface is Russian. |
| Privacy and backup | A 4-digit PIN with a growing wait after failures, an SQLCipher database, app windows marked `FLAG_SECURE`. The whole library exports to one password-protected `.peachy` file and imports on another phone. |
| Languages | Russian and English, dark interface. |

## Screenshots

### Player and library

| Onboarding | PIN | Player |
|:---:|:---:|:---:|
| <img src="repo/screens/onboarding.png" width="180" alt="Onboarding"/> | <img src="repo/screens/auth.png" width="180" alt="PIN pad"/> | <img src="repo/screens/player.png" width="180" alt="Player with a local clip"/> |

| Settings | Device | Add video |
|:---:|:---:|:---:|
| <img src="repo/screens/settings.png" width="180" alt="Settings"/> | <img src="repo/screens/device.png" width="180" alt="Intiface dialog"/> | <img src="repo/screens/add-video.png" width="180" alt="Add video: gallery, URL, list, browser"/> |

| Gallery | Compress | Note |
|:---:|:---:|:---:|
| <img src="repo/screens/gallery.png" width="180" alt="Gallery with two sample clips"/> | <img src="repo/screens/compress.png" width="180" alt="Compression presets and custom settings"/> | <img src="repo/screens/note.png" width="180" alt="Private note on a clip"/> |

### Browser and backup

| Start page | Page videos | Backup |
|:---:|:---:|:---:|
| <img src="repo/screens/browser-start.png" width="180" alt="Browser start page with bookmarks and history"/> | <img src="repo/screens/browser-videos.png" width="180" alt="Video files found on a page, with a save button"/> | <img src="repo/screens/backup.png" width="180" alt="Export library dialog with a password"/> |

### Manga

| Catalog | Menu | Tags | Reader |
|:---:|:---:|:---:|:---:|
| <img src="repo/screens/catalog.png" width="180" alt="Catalog filtered by one tag, covers pixelated"/> | <img src="repo/screens/drawer.png" width="180" alt="Manga navigation drawer"/> | <img src="repo/screens/tags.png" width="180" alt="Tag browser"/> | <img src="repo/screens/reader.png" width="180" alt="Reader settings dialog over a pixelated page"/> |

## Details

- Plays local video with Media3 ExoPlayer. The gallery can rename an item, replace its cover, and delete one or many items. The button in the gallery header switches the view between large portrait covers, two per row, one per row, and a list; the choice is remembered.
- Downloads a video from an `http` or `https` URL into the app library.
- Imports videos from file-host links: an album or a single file on Bunkr, Cyberdrop, Turbo, Pixeldrain or Filester, or a findr.to album page (imported from the album it points to). An album link brings every video in it; images and archives are skipped. Only public links work: password albums, GoFile and FileDitch are not supported. Turbo's CDN could not be reached from the test machine, so Turbo downloads are untested.
- Exports selected gallery videos to a folder you choose. The app gets access to that folder only, and a failed copy is removed instead of left half-written.
- Compresses selected gallery videos with Media3 Transformer: four presets (visually lossless, compact, smaller, smallest) or custom codec, bitrate, height, frame rate and audio. HEVC is used only where the phone's encoder supports the output size; otherwise that video gets H.264 at a higher bitrate. The dialog shows a size estimate. The original is replaced only when the new file is smaller and passes a check (size, duration, audio); otherwise it stays. Cancelling leaves originals untouched.
- Opens an HTTPS page in a WebView and uses that page's audio for haptics. The window is marked `FLAG_SECURE`. Non-HTTPS pages are rejected.
- Has a built-in browser with tabs, bookmarks, history and an ad and tracker blocker. The blocker uses EasyList, EasyPrivacy and an adult list bundled in the app; "Update filter lists" in the menu fetches fresh copies. Blocking can be turned off per site. The browser lists the video files a page loads and saves a chosen one to the library; HLS and DASH streams cannot be saved. Pages open over HTTPS only.
- Translates a manga page in the native reader with a vision-language model of your choice, over an OpenAI-compatible `chat/completions` API (the default points at OpenCode Go with `glm-5.3-flash`; one tap fills in the address and a cheap model for OpenCode Go, OpenAI, Google Gemini, OpenRouter or Mistral; set the address, model and key in Settings, "Page translation", or from the panel). A button in the reader header sends the current page; the result shows in a panel over the bottom of the page: each line with its translation, original and sound-effect markers. The mode is set in the reader settings: on request, on every page turn, or the whole gallery with progress and Cancel. The language is the app language (the device language if it is set to System). Translations are cached per gallery, page and language in the encrypted database and travel in the `.peachy` file, so a page is paid for once. A 401, 403 or 429 stops a batch instead of retrying.
- Sends page images to the provider address you entered, and nowhere else, only after you accept a prompt that names the host. Changing the host asks again. Plain `http` is accepted only for localhost and private networks. The key is kept encrypted on the phone (`EncryptedSharedPreferences`), is never written to the database, the `.peachy` file or a backup, and is not logged. Adult content is sent as it is: a provider may refuse some pages, and the panel says so.
- Keeps a private note on a video, a saved gallery or a browser page. Notes live in the encrypted database and are never sent anywhere.
- Exports the whole library to one password-protected `.peachy` file and imports it on another phone (Settings, Backup): videos with covers, saved galleries and pages, read and search history, tags and blacklist, notes, bookmarks, browser history and cached page translations. AES-256-GCM, key from PBKDF2-SHA256; the file is written and read in blocks, so a large library does not need RAM. Import only adds: videos already present are skipped, newer notes and translations win, nothing is deleted. The PIN, device settings and the translation provider key are not included. A forgotten password cannot be recovered.
- Connects to Intiface Central over a WebSocket. The default address is `ws://127.0.0.1:12345`. The app does not scan Bluetooth itself; Intiface does that.
- Maps audio amplitude through one DSP path: bass range, smoothing, threshold, and power boost. The same settings apply to the player and the WebView.
- Sends commands only to actuators that can follow an audio level, such as vibration, oscillation, rotation, and position.
- Switches videos on a timer and changes playback speed.
- Locks the app with a 4-digit PIN. Repeated failures increase the wait. The database is SQLCipher.
- Speaks Russian and English. The interface is dark.
- Browses nhentai in a drawer-based hub: Catalog, Downloads, Favorites, History, Tags, Characters, and Random gallery. The catalog is a two-column cover grid with tag chips, filters by tag (include or exclude), date, and page count, and sorting by date or popularity. The tag browser lists tags, artists, characters, parodies, groups, categories, and languages by popularity; tags can be marked favorite, put into named collections kept on the device, and sent to the catalog as a search. A gallery page shows titles, page count, favorite count, upload date, tags grouped by type, related galleries, and read-only comments loaded on demand. Posting comments needs an nhentai account and is not available. Galleries open in a native Compose reader and can be saved on the device and marked favorite. Page images are requested from `i1.nhentai.net` and `t1.nhentai.net` without rewriting the path returned by the API. Turning a page sends a short pulse. Page-turn strength is configured in the reader settings sheet, not with a slider on the page.
- In the catalog the Filters button sits in the title bar and shows how many tags are active. Tapping a tag on a card cycles it through include, exclude, and removed, the same order as the chips under the search field; a long press marks the tag as a favorite and highlights it on every card. The next page is appended while you scroll toward the end of the list, and a failed page shows a Retry button instead of reloading by itself.
- With the Russian interface, tags, categories, and gallery languages are shown in Russian: a built-in dictionary covers the tags used by at least 500 galleries, and any other name stays in English. Search, filters, favorites, and tag collections always use the English names the API uses, so switching the language changes only what is drawn.
- Recommends galleries in a For you tab, computed on the device. It learns from the galleries you open, read, save, and favorite, from your favorite tags and tag collections, and from "Not interested" taps, with older signals fading over time. Candidates come from nhentai searches by your top tags and from the related lists of galleries you liked; each pick shows the tags that earned it a place. Until there is some taste to go on, the tab shows what is popular. The upload date appears on a card only for galleries whose page the app has already opened, because search results do not carry it. History uses the same large cards as the catalog. A gallery page shows a grid of page previews under the tags, and a tap opens the reader at that page.
- The native reader turns pages by tapping the left or right third of the screen (or the top and bottom third in vertical paged mode; the continuous strip keeps left and right); the middle shows the title bar, a strip of page thumbnails, and the settings sheet. Settings: reading direction (vertical or horizontal), image fit (whole page or full width), continuous scroll, tap navigation with a left/right swap for one-handed use, zoom buttons, and hiding the tap hints that show when the reader opens. Pinch, double tap and the zoom buttons enlarge a page up to 6x, and the next three pages load in advance. The reader resumes at the last page you read. The bookmark button in the title bar keeps a single page; the Saved pages tab lists those pages, opens the reader on one, and exports or deletes a selection. Exported pages go to a folder you choose, and the app gets access to that folder only.

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

# Native manga reader

The `manga/{id}` route uses Compose and Coil. Downloaded pages take precedence over remote URLs resolved by `NhentaiCdn.page`. Saved library metadata remains usable if the gallery request fails.

## Catalog filters

Language chips include English, Japanese, Chinese, and Translated. Each cycles through include, exclude, and off using the corresponding `language:` tag.

Tag blacklisting applies only to search cards, not the saved library. Search summaries carry tag IDs only. Fetching gallery details teaches the current catalog ViewModel the lowercase name of each returned tag ID; cards with those IDs can then be hidden by an exact, case-insensitive blocked name. Substrings do not match. Results are refiltered when metadata arrives or the blacklist changes, including restoring cards when a blocked name is removed.

## Controls

- Swipe horizontally in paged mode or scroll vertically in strip mode.
- Tap the left or right third to move backward or forward. The mirror setting swaps those two tap zones.
- Tap the center third to show or hide the title, page count, Back, favorite, settings, and page thumbnails. Chrome starts hidden.
- Pinch the current page to zoom from 1× to 5×; drag while zoomed to pan. Double-tap to reset zoom and pan.
- Tap a thumbnail to jump to its page.
- Reader settings select paged/strip mode, contain/fill-width fit, mirrored taps, and page-turn intensity. The sheet scrolls on short viewports, keeping the intensity slider and Save action reachable above the navigation bar.

Contain fits each page inside the viewport. Fill-width preserves the image aspect ratio; tall pages can be scrolled vertically in paged mode and extend naturally in strip mode. Short strip pages occupy at least one viewport so a page jump does not immediately select a neighboring page. Paged mode composes one neighboring page on each side.

## Persistence and haptics

Reader settings use SharedPreferences named `manga_reader`:

| Key | Values | Default |
| --- | --- | --- |
| `mode` | `paged`, `strip` | `paged` |
| `fit` | `contain`, `width` | `contain` |
| `mirror` | boolean | `false` |
| `intensity` | float | `PageTurnHaptic.DEFAULT_INTENSITY` |

The intensity slider calls `MangaViewModel.setIntensity`. Page turns retain the existing pulse duration.

Opening loads the saved zero-based history page through `startPage`. Invalid or absent positions start at zero. `PageTurnGate` suppresses the initial placement and repeated idle reports. Paged mode supplies its settled page; strip mode supplies the page whose center is nearest the viewport center together with the scroll-idle flag. A gate-approved change triggers one pulse and upserts reading history.

Leaving the reader reads the current pager position or live strip layout, even during a fling, and upserts history without emitting a pulse. Writes are ordered and survive cancellation of the navigation-scoped ViewModel. History stores gallery ID, title, thumbnail path, zero-based page, page count, and read timestamp; library rows are not used to store reading progress.

## Translation data and requests

`ReaderSettings` persists four translation choices alongside the existing translation mode: `engine` (`model` by default, or `mlkit`), `sourceLanguage` (`auto` by default), `targetLanguage` (`app` by default), and `overlay` (`false` by default). `app` resolves to the app/device language using the existing Russian/English fallback; an explicit target ISO code is passed through, including codes outside the UI list. The overlay flag is stored here; drawing and settings controls are separate from the translation pipeline.

`PageTranslator` receives a `TranslateRequest(engine, source, target)` with the resolved target. The Model engine sends the page image to the configured provider. The MlKit engine consumes `PageOcr` blocks locally and sends only numbered OCR text to that same provider for translation; a page with no OCR blocks returns an empty translation without an HTTP request. Both network paths require the existing provider consent, reject redirects, and use the same HTTP error mapping.

Translated lines can carry an optional `Box` around their original text. Stored JSON uses `[left, top, right, bottom]` fractions in `0..1`. Model replies may instead use the requested `0..1000` coordinates: parsing scales the whole box when any coordinate exceeds one, clamps and orders its edges, and discards malformed or degenerate boxes without dropping the translated line. OCR translations retain each referenced block's original text and box; invalid indices and repeated references are skipped.

The database and vault continue to treat the `lang` field as an opaque cache key. `TranslationKey.of(engine, source, target)` returns exactly `target` for Model + `auto`, preserving existing caches and backups. Other choices use `target|engine.wire|source`, for example `de|mlkit|ja`. Changing translation choices switches the reader's displayed state and batch to that key; cached entries under other keys remain available.


## On-device page text recognition

`PageOcr` is bound through Hilt to the singleton `MlKitPageOcr`. It uses ML Kit's bundled Latin,
Japanese, Chinese and Korean text-recognition models (16.0.1); recognition needs no model download
and does not send page images to a server. `ja` and `auto` select Japanese (also reads Latin),
`zh` selects Chinese, `ko` selects Korean, and other source codes select Latin. Each recognizer
is created lazily and retained for the application's lifetime. Decoding and result processing run
on `Dispatchers.Default`; invalid image bytes throw `IOException`.
`auto` uses the contract's default left-to-right ordering and space-separated lines; selecting the
Japanese recognizer does not change the requested source language's ordering.

Each ML Kit text block remains one output block. Lines below 0.3 confidence, blank lines and
symbol-only noise are removed. Japanese/Chinese lines join without spaces; Latin/Korean lines
join with spaces. Boxes are clipped to the page and expressed as fractions of its dimensions.
Reading order uses overlapping vertical extents to form rows, top-to-bottom; Japanese/Chinese
rows read columns right-to-left and each column top-to-bottom, while Latin/Korean rows read
left-to-right. The exact overlap rule and its limitations are documented in `OcrGrouping.kt`.
Cancellation stops waiting immediately; because ML Kit cannot cancel its task, the task retains
the decoded bitmap until recognition completes and then recycles it.

Dependency references: [Android setup](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
and [release notes](https://developers.google.com/ml-kit/release-notes). ML Kit requires API 23;
the app retains minSdk 26. In the measured universal debug APK, ML Kit adds one
`libmlkit_google_ocr_pipeline.so` per ABI. Its arm64-v8a and x86_64 ELF load segments are 16 KB
aligned; its armeabi-v7a and x86 segments are 4 KB aligned. APK ZIP alignment passes
`zipalign -c -P 16 4`. SQLCipher's existing 4 KB ELF alignment is unchanged; these OCR dependencies
add no further 64-bit ELF alignment issue. The 16.0.1 release notes announce 16 KB page-size support.

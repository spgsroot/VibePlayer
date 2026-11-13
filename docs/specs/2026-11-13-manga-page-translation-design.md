# Manga page translation: design

Date: 2026-11-13. Branch for the work: `page-translation`.

## Intent

The owner reads manga in the native reader (`ui/manga/MangaReader.kt`) and often cannot read the page's language. They want the page read and translated by an LLM they pay for themselves (for example an OpenCode Go subscription), without leaving the reader. The app is a private, offline-leaning reader (README: notes and history "are never sent anywhere"); this feature is the first place page images leave the phone, so it is opt-in, explicit, and never bundled with a key.

## Goals

- A translation panel under the page shows the page's lines in reading order: the original text, its translation, and the kind of line (speech, thought, narration, sound effect, sign).
- One vision-model request per page. No on-device OCR.
- The user decides when translation runs. Three modes, one setting in the reader settings dialog: **Manual** (a button translates the current page), **On page turn** (the page that becomes current is translated), **Whole gallery** (all pages not yet translated are queued).
- Translations are cached per gallery, page and language, so reopening a page costs nothing and works offline.
- The provider is configurable: base URL, model, API key. Defaults point at OpenCode Go. The user supplies the key.
- The target language is the app language (Russian or English; "System" resolves to the device language, Russian if it is Russian, English otherwise).

## Non-goals

- Drawing the translation over speech bubbles (the owner chose a panel; no coordinates are requested or stored).
- Bundling an API key, a proxy, or any server of ours.
- Token or cost accounting in the UI, streaming responses, tool calls, or a chat with the model.
- Translating anything but manga pages (no video, no WebView, no comments).

## Anti-goals (a failure even if it works)

- Pages sent anywhere before the user has read and accepted a one-time consent text naming the provider URL.
- A retry loop that burns the subscription: on an auth, rate-limit or quota error the batch stops and says so.
- The key written to the database, the `.peachy` vault, logs, saved UI state, or backups.
- Translating the same page twice at once (turn mode and the batch racing).

## Constraints

- Android app, Kotlin, Jetpack Compose, Hilt, Room (SQLCipher), OkHttp 4.12, `org.json`. No new dependency except `mockwebserver` for tests (already in the version catalog).
- Room is at version 10; the new table is migration 10 to 11 with an exported schema `11.json`.
- Vault `FORMAT` stays `1`: new data is an optional field that older exports lack and that `VaultData.fromJson` treats as empty.
- Provider settings use `EncryptedSharedPreferences` like `KeystoreManager`, in their own file. They are never exported.
- OpenCode Go is built for coding agents and watches traffic. A manga reader is not one; the client identifies itself with its own User-Agent and, for `opencode.ai` only, a stable `x-opencode-session` per gallery. The provider may still restrict the key; the design assumes the user can point the same screen at another OpenAI-compatible service.

## Approaches considered

1. **One vision call per page, JSON list of lines (chosen).** Cheapest and simplest; the panel needs no coordinates. Risks: refusals on adult content, malformed JSON.
2. On-device OCR (ML Kit) plus a text-only LLM. Rejected: vertical Japanese is recognised poorly, it adds a dependency, and the panel does not need boxes.
3. Overlay translation on the bubbles. Rejected by the owner's choice of a panel; LLM bounding boxes are also unreliable.

## Design

### Request

`POST {baseUrl}/chat/completions`, headers `Authorization: Bearer <key>`, `User-Agent: Peachy/<versionName> (+https://github.com/spgsroot/VibePlayer)`, plus `x-opencode-session: peachy-<galleryId>` when the host is `opencode.ai`. Body: `model`, `temperature: 0.2`, `max_tokens: 4096`, `response_format: {"type":"json_object"}`, one system message and one user message holding the page as an `image_url` data URI (`data:image/jpeg;base64,...`).

The system prompt (owner-visible text lives in one file, `translate/PagePrompt.kt`) tells the model: it is a manga translator; read the page in reading order (right-to-left, top-to-bottom for Japanese, Chinese and Korean-webtoon-style vertical pages as laid out); translate into the given target language; answer with one JSON object and nothing else:

```json
{"sourceLanguage":"ja","lines":[{"kind":"speech","text":"original","translation":"translated"}]}
```

`kind` is one of `speech`, `thought`, `narration`, `sfx`, `sign`. A page with no text returns `"lines":[]`. The prompt states that the content is fiction for adults and must be transcribed faithfully, not summarised or softened.

### Page image

Bytes come from the saved copy (`MangaFiles` / `LibraryGalleryRepository.localPage`) when present, otherwise from `NhentaiCdn.page(path)` over OkHttp. The bitmap is decoded with `inSampleSize`, scaled so its long side is at most 1600 px, and re-encoded as JPEG quality 80.

### Response handling

The reply text is parsed tolerantly: strip a Markdown fence, take the outermost `{...}`, read `lines`. Unknown `kind` becomes `speech`. A reply that is not valid after one extra attempt (a single re-request, not a loop) is a `BadResponse` failure for that page. An HTTP 401/403 is `Auth`, 429 is `RateLimit` (honouring `Retry-After` for the message only), other 4xx/5xx is `Server(code)`, a transport error is `Network`, a 200 with no usable content or a refusal text is `Refused`.

### Cache

Room table `page_translations(galleryId INTEGER, page INTEGER, lang TEXT, model TEXT, json TEXT, createdAt INTEGER, PRIMARY KEY(galleryId, page, lang))`. `json` holds the parsed lines, not the raw reply. A "Translate again" action on the panel replaces the row. Deleting a gallery from the library removes its rows.

### Triggering

`ReaderSettings` gets `translateMode: TranslateMode` (`Manual`, `OnTurn`, `Whole`), stored with the other reader keys, default `Manual`. A `TranslationViewModel` (Hilt, scoped to the reader screen) owns per-page state (`Idle`, `Loading`, `Done(lines)`, `Failed(error)`) and the batch.

- On a cache hit the page is `Done` at once in every mode.
- `OnTurn`: when `visiblePage` settles on a page with no cached translation, translate it. Pages passed by quickly in the continuous strip are not queued: the request starts only once the page has been centred and idle (the same gate that triggers the page-turn pulse).
- `Whole`: queue every uncached page of the gallery, two requests at a time, with a progress line in the panel (`12 / 80`) and a Cancel button. It runs only while the reader is open. It stops on `Auth` and `RateLimit` (message tells which); a `BadResponse` or `Refused` page is marked failed and the batch moves on.
- A per-key (gallery, page, language) mutex makes a page that is both on screen and queued translate once.

### Panel and settings

A translate button joins the reader's header actions. It opens a bottom panel (collapsed to one line showing state, expandable to about half the screen, scrollable). Each line shows `translation` large, `text` small and dimmed, sfx dimmed and italic. The panel's menu has "Translate again" and "Provider settings".

The reader settings dialog gets a "Translation" row group: the mode, and a link to provider settings. Provider settings are one dialog (base URL, model, key hidden with a show toggle, a "Test" button that sends a tiny text-only request). The same dialog opens from the app Settings drawer.

### Consent and privacy

Before the first request a dialog says: the page image is sent to `<host>`, which may keep it under its own terms; nothing else is sent; the key stays on this phone. Accepting stores a flag in the provider prefs. The README privacy lines are updated.

### Provider rules

`baseUrl` must be `https`, except plain `http` is accepted for localhost, `127.*`, `10.*`, `192.168.*` and `172.16-31.*` (a model on the user's own network; this also lets the emulator reach a mock at `10.0.2.2`). Defaults: `https://opencode.ai/inference/go/v1` (`GET /models` on it was confirmed; `/chat/completions` is confirmed by the live check below); the default model is decided by the same check.

### Vault

`VaultData` gets `translations: List<VaultTranslation>` (galleryId, page, lang, model, json, createdAt), written always, read as empty when the key is absent. `VaultMerge` keeps the newer `createdAt` for the same key and never deletes. Provider settings and the key are not part of the vault.

### Live check (first task, gated on the owner)

Before building on assumptions, a throwaway script sends three sample pages to `mimo-v2.6-flash`, `glm-5.3-flash` and `deepseek-v4.1-flash` through the Go endpoint, using a key the owner provides at run time in the environment variable `PEACHY_LLM_KEY` (never written to a file). It records: whether `image_url` with a data URI is accepted on `chat/completions`, whether `response_format` is honoured, whether the model refuses adult pages, latency, and `usage`. The cheapest model that returns valid JSON for all three pages becomes the default. Note: DeepSeek V4.1 Flash needs the workspace region set to Global; if it is the only one that works, the consent text and README say so.

## What is left to the builder

Exact Compose layout and spacing, string wording beyond what is named here, the panel's collapse animation, file and class names beyond the ones given, test fixtures, and the choice between `kotlinx.serialization` and `org.json` for the line JSON (the codebase uses `org.json`).

## Live check result (done 2026-11-13)

Run against `https://opencode.ai/inference/go/v1/chat/completions` with three pages of two Japanese galleries (page long side 1600 px, JPEG 80), key from the environment, not stored anywhere.

- `image_url` with a data URI, `response_format: json_object`, and a custom User-Agent with `x-opencode-session` are accepted. No refusals on adult pages from any of the three models.
- `glm-5.3-flash`: valid JSON on 3 of 3 pages, 2-10 s, 2.2k prompt and 0.02-0.5k completion tokens. **Chosen default model.**
- `mimo-v2.6-flash`: 1 of 3 pages ran to the `max_tokens` limit (4096) in 103 s and the JSON was cut off. A cut-off reply is therefore a normal `BadResponse`, not a bug.
- `deepseek-v4.1-flash`: valid JSON, but 7-38 s and 0.5-3.9k completion tokens per page; too slow for page turning.
- Two and four parallel requests to `glm-5.3-flash` all succeeded, no 429. Default batch parallelism stays 2.

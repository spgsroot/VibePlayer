# Manga Page Translation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The native manga reader translates the current page (or the whole gallery) through a user-configured OpenAI-compatible vision model and shows original text and translation in a panel under the page.

**Architecture:** A new package `ru.spgsroot.peachy.translate` holds the pure parts (reply parser, provider rules, image scaling, chat client, runner with cache and batch). A Room table caches results per gallery, page and language. The reader gets a `TranslationViewModel`, a bottom panel and dialogs; provider settings live in `EncryptedSharedPreferences`, never in the DB or the vault.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room (SQLCipher, now version 10), OkHttp 4.12, `org.json`, JUnit 4, MockWebServer, kotlinx-coroutines-test.

**Spec:** `docs/specs/2026-11-13-manga-page-translation-design.md` (read it; it also holds the live-check result that fixed the default model).

## Global Constraints

- Package root `ru.spgsroot.peachy`; sources under `app/src/main/java/ru/spgsroot/peachy/`, tests under `app/src/test/java/ru/spgsroot/peachy/`. New logic package: `translate`.
- Branch `page-translation` from the current `gallery-layout` head. Commit per task. Never commit a key, never print one in logs.
- Defaults: base URL `https://opencode.ai/inference/go/v1`, model `glm-5.3-flash`, `temperature` 0.2, `max_tokens` 4096, `response_format` `{"type":"json_object"}`, page image long side at most 1600 px, JPEG quality 80, batch parallelism 2.
- `baseUrl` must be `https`; plain `http` only for `localhost`, `127.*`, `10.*`, `192.168.*`, `172.16.*` to `172.31.*`.
- `x-opencode-session: peachy-<galleryId>` is sent only when the host is `opencode.ai` or ends with `.opencode.ai`. User-Agent: `Peachy/<versionName> (+https://github.com/spgsroot/VibePlayer)`.
- Line kinds: `speech`, `thought`, `narration`, `sfx`, `sign`; unknown becomes `speech`. Target language is `ru` or `en` (app language; `system` follows the device, Russian only if the device is Russian).
- Room: DB version 10 to 11, migration `MIGRATION_10_11`, exported schema `app/schemas/ru.spgsroot.peachy.data.db.AppDatabase/11.json`. Vault `FORMAT` stays `1`; the new `translations` array is optional on read.
- Strings in both `values/` (Russian) and `values-en/`, in a new `translate.xml` per locale. README.md and README_RU.md updated (Task 11).
- Build and test (Windows): `cmd.exe /c "set JAVA_HOME=C:\Users\spgs\.jdks\jbr-21.0.11&& set ANDROID_HOME=C:\Users\spgs\AppData\Local\Android\Sdk&& set PATH=%JAVA_HOME%\bin;%PATH%&& gradlew.bat :app:testDebugUnitTest --offline -q"`; add `--tests ru.spgsroot.peachy.translate.*` to narrow. Results: `app/build/test-results/testDebugUnitTest/*.xml`.

## Review Focus

- A reply wrapped in prose or a Markdown fence, cut off mid-JSON, or with `"lines": []`: fence and prose are tolerated, a cut-off reply is `BadResponse` (one re-request, no loop), empty lines is a valid "no text" page. (Task 2, 5)
- Provider URL typed with trailing slash, uppercase scheme, a user-info part (`https://user@host`) or a look-alike private host (`10.evil.com`): only exact rules above pass. (Task 3)
- Page image that is already small, or a tall webtoon strip: never upscaled, aspect kept, long side at most 1600. (Task 4)
- Same page asked twice while the first request runs, then cancelled by leaving the reader: one request, no orphan writes. (Task 7)
- A vault made before this feature, or one with a translation for a gallery the phone does not have: imports without error. (Task 10)

---

### Task 1: Reply model and tolerant parser

**Files:**
- Create: `translate/PageTranslation.kt`
- Test: `translate/PageTranslationTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class LineKind { Speech, Thought, Narration, Sfx, Sign }
  data class TranslatedLine(val kind: LineKind, val text: String, val translation: String)
  data class PageTranslation(val sourceLanguage: String, val lines: List<TranslatedLine>) {
      fun toJson(): String
      companion object { fun parse(raw: String): PageTranslation? }
  }
  ```
  `parse` takes a model reply or a stored `toJson()` string; `null` means unusable.

- [ ] **Step 1: Write the failing tests** in `PageTranslationTest`: `plainJsonParses` (two lines, kinds and strings preserved), `markdownFenceIsStripped` (reply wrapped in a json fence), `proseAroundObjectIsIgnored` (text before and after the outermost `{...}`), `unknownKindBecomesSpeech`, `lineWithoutTranslationIsDropped` (blank `translation`; an `sfx` with blank `text` but a translation is kept), `emptyLinesIsValid` (`"lines":[]` gives an empty list, not null), `cutOffReplyIsNull` (`{"sourceLanguage":"ja","lines":[{"kind":"speech","te`), `garbageIsNull`, `roundTrip` (`parse(t.toJson()) == t`).
- [ ] **Step 2: Run** `--tests ru.spgsroot.peachy.translate.PageTranslationTest`. Expected: FAIL, unresolved `PageTranslation`.
- [ ] **Step 3: Implement** the types above with `org.json`. Take the substring from the first `{` to the last `}`; missing `sourceLanguage` becomes `""`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git add app/src/main/java/ru/spgsroot/peachy/translate app/src/test/java/ru/spgsroot/peachy/translate && git commit -m "Parse page translations from model replies"`

### Task 2: Provider rules and secure store

**Review:** per-task — security (API key storage, URL rules)

**Files:**
- Create: `translate/ProviderSettings.kt`
- Test: `translate/ProviderUrlTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  const val DEFAULT_BASE_URL = "https://opencode.ai/inference/go/v1"
  const val DEFAULT_MODEL = "glm-5.3-flash"
  data class ProviderSettings(val baseUrl: String = DEFAULT_BASE_URL, val model: String = DEFAULT_MODEL,
                              val apiKey: String = "", val consented: Boolean = false) {
      val isConfigured: Boolean  // key and model not blank and ProviderUrl.isAllowed(baseUrl)
  }
  object ProviderUrl {
      fun isAllowed(url: String): Boolean
      fun normalized(url: String): String            // trimmed, no trailing slash
      fun sendsSessionHeader(url: String): Boolean   // host is opencode.ai or *.opencode.ai
  }
  @Singleton class ProviderSettingsStore @Inject constructor(@ApplicationContext context: Context) {
      val settings: StateFlow<ProviderSettings>
      fun update(change: (ProviderSettings) -> ProviderSettings)
  }
  ```

- [ ] **Step 1: Write the failing tests** in `ProviderUrlTest`: allowed — `https://opencode.ai/inference/go/v1`, `HTTPS://Example.com/v1`, `http://10.0.2.2:8765/v1`, `http://localhost:1234/v1`, `http://192.168.1.5/v1`, `http://172.20.0.1/v1`; refused — `http://example.com/v1`, `ftp://x`, `https://user@evil.com/v1`, `http://10.evil.com/v1`, `http://172.32.0.1/v1`, `https://`, blank, `not a url`. `normalized("  https://a.b/v1/ ") == "https://a.b/v1"`. `sendsSessionHeader`: true for `https://opencode.ai/x` and `https://api.opencode.ai/x`, false for `https://notopencode.ai/x` and `https://opencode.ai.evil.com/x`.
- [ ] **Step 2: Run** `--tests ru.spgsroot.peachy.translate.ProviderUrlTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `ProviderUrl` with `java.net.URI` (parse failures are "not allowed"; reject a non-null `userInfo`). Implement `ProviderSettingsStore` over `EncryptedSharedPreferences` file `translate_provider`, master key like `data/db/KeystoreManager.kt`; the key is never logged and never part of `toString` (override `ProviderSettings.toString` to mask `apiKey`).
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -am "Add translation provider settings and URL rules"` (stage new files first).

### Task 3: Page image scaling and loading

**Files:**
- Create: `translate/PageImage.kt`
- Test: `translate/PageImageTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object PageImage {
      const val MAX_SIDE = 1600
      fun scaledSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int>
      fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int   // power of two >= 1
      fun encode(source: ByteArray): ByteArray                                // JPEG q80, long side <= MAX_SIDE
  }
  interface PageSource { suspend fun jpeg(galleryId: Int, page: Int): ByteArray }
  class PageBytes @Inject constructor(...) : PageSource  // see Step 3
  ```

- [ ] **Step 1: Write the failing tests** in `PageImageTest`: `smallImageIsNotUpscaled` (800x1200 gives 800x1200), `longSideIsClamped` (1058x1500 stays, 3200x4800 gives 1067x1600), `aspectRatioIsKept` (3000x300 gives 1600x160), `tallStripKeepsWidthProportion` (800x12000 gives 107x1600), `sampleSizeIsPowerOfTwoAndNeverBelowTarget` (3200x4800 gives 2; 800x1200 gives 1; 10000x10000 gives 4).
- [ ] **Step 2: Run** `--tests ru.spgsroot.peachy.translate.PageImageTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** `encode` decodes with `BitmapFactory` using `sampleSize`, scales to `scaledSize`, compresses JPEG 80, and recycles bitmaps; it throws `IllegalArgumentException` on undecodable bytes. `PageBytes` implements `PageSource`: it needs `GalleryDetail.pagePaths`, so give it `fun bind(pagePaths: List<String>)`; for page `p` it reads the saved copy (`MangaLibraryRepository.localPage(galleryId, path)`) when the file exists, otherwise an OkHttp GET of `NhentaiCdn.page(path)`, then returns `PageImage.encode(bytes)` on `Dispatchers.IO`.
- [ ] **Step 4: Run** the same command. Expected: PASS (the Bitmap code is covered by the emulator check in Task 8).
- [ ] **Step 5: Commit** `git commit -m "Scale and load manga pages for translation"` after staging.

### Task 4: Chat client

**Files:**
- Create: `translate/TranslationClient.kt`, `translate/PagePrompt.kt`, `translate/TranslationError.kt`
- Modify: `app/build.gradle.kts` (add `testImplementation(libs.mockwebserver)`; confirm the alias in `gradle/libs.versions.toml`)
- Test: `translate/TranslationClientTest.kt`

**Interfaces:**
- Consumes: `PageTranslation.parse` (Task 1), `ProviderSettings`, `ProviderUrl` (Task 2).
- Produces:
  ```kotlin
  sealed interface TranslationError {
      object NotConfigured; object Auth; object RateLimit; object Network; object BadResponse; object Refused
      data class Server(val code: Int)
  }
  class TranslationException(val error: TranslationError, cause: Throwable? = null) : Exception()
  interface PageTranslator { suspend fun translate(galleryId: Int, jpeg: ByteArray, targetLanguage: String): PageTranslation }
  class TranslationClient(http: OkHttpClient, settings: () -> ProviderSettings, userAgent: String) : PageTranslator {
      suspend fun check()   // tiny text-only request; throws TranslationException
  }
  object PagePrompt { fun system(targetLanguage: String): String }   // "ru" -> Russian, "en" -> English
  ```
  Every failure is a `TranslationException`; nothing else escapes except `CancellationException`.

- [ ] **Step 1: Write the failing tests** (MockWebServer) in `TranslationClientTest`: `requestHasExpectedShape` (path `/chat/completions`, `Authorization: Bearer k`, User-Agent exact, body has `model`, `temperature` 0.2, `max_tokens` 4096, `response_format.type` `json_object`, a `data:image/jpeg;base64,` image part, system message mentions Russian for `"ru"`), `noSessionHeaderForLocalHost` (the recorded request has no `x-opencode-session`), `sessionHeaderValue` (top-level `internal fun sessionHeader(baseUrl: String, galleryId: Int): String?` returns `peachy-7` for `https://opencode.ai/inference/go/v1` with id 7 and null for `http://10.0.2.2:8766/v1`), `fencedReplyIsParsed`, `http401And403AreAuth`, `http429IsRateLimit`, `http500IsServerWithCode`, `connectionRefusedIsNetwork`, `invalidJsonIsRetriedOnceThenBadResponse` (two mock responses, exactly two requests recorded), `secondAttemptSuccessIsReturned`, `emptyChoicesIsRefused`, `notConfiguredWithoutKey` (no request is sent).
- [ ] **Step 2: Run** `--tests ru.spgsroot.peachy.translate.TranslationClientTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Use `OkHttpClient` with `readTimeout` 120 s (the model can take 100 s); run the call on `Dispatchers.IO` with `suspendCancellableCoroutine` and cancel the call on coroutine cancellation. Refusal means a 200 with no `choices[0].message.content` or a `finish_reason` of `content_filter`. The prompt text follows the spec's Request section, including the sentence that the content is adult fiction to be transcribed faithfully.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add the page translation chat client"`.

### Task 5: Cache table and migration

**Review:** per-task — data migration, persisted format

**Files:**
- Create: `data/db/PageTranslationEntity.kt`, `translate/TranslationStore.kt`, `app/schemas/ru.spgsroot.peachy.data.db.AppDatabase/11.json` (generated by the build)
- Modify: `data/db/AppDatabase.kt` (entity, `version = 11`, `abstract fun pageTranslationDao()`, `.addMigrations(..., MIGRATION_10_11)`), `di/DatabaseModule.kt` (provide the DAO), `data/manga/MangaLibraryRepository.kt:60` (`remove` also calls `TranslationStore.deleteGallery`), `data/manga/MangaMigrations.kt` (put `MIGRATION_10_11` here next to `MIGRATION_9_10`)
- Test: `data/manga/MangaMigrationSqlTest.kt` (extend, following its existing style), `translate/TranslationStoreTest.kt`

**Interfaces:**
- Consumes: `PageTranslation` (Task 1).
- Produces:
  ```kotlin
  @Entity(tableName = "page_translations", primaryKeys = ["galleryId", "page", "lang"])
  data class PageTranslationEntity(val galleryId: Int, val page: Int, val lang: String,
                                   val model: String, val json: String, val createdAt: Long)
  @Dao interface PageTranslationDao {
      suspend fun get(galleryId: Int, page: Int, lang: String): PageTranslationEntity?
      suspend fun upsert(entity: PageTranslationEntity)
      suspend fun upsertAll(entities: List<PageTranslationEntity>)
      suspend fun getAll(): List<PageTranslationEntity>
      suspend fun pagesDone(galleryId: Int, lang: String): List<Int>
      suspend fun deleteGallery(galleryId: Int)
  }
  interface TranslationCache {
      suspend fun get(galleryId: Int, page: Int, lang: String): PageTranslation?
      suspend fun put(galleryId: Int, page: Int, lang: String, model: String, translation: PageTranslation)
      suspend fun cachedPages(galleryId: Int, lang: String): Set<Int>
  }
  class TranslationStore @Inject constructor(dao: PageTranslationDao) : TranslationCache { suspend fun deleteGallery(galleryId: Int) }
  ```

- [ ] **Step 1: Write the failing tests.** `TranslationStoreTest` with a map-backed fake DAO: `putThenGetRoundTrips`, `otherLanguageIsAMiss`, `putReplacesSameKey`, `cachedPagesListsOnlyThatGalleryAndLanguage`, `unparseableStoredJsonIsAMiss` (stored `"{"` returns null, not an exception), `deleteGalleryRemovesAllLanguages`. In `MangaMigrationSqlTest` add a case that the `MIGRATION_10_11` SQL creates `page_translations` with the composite primary key and leaves `saved_pages` rows intact.
- [ ] **Step 2: Run** `--tests ru.spgsroot.peachy.translate.TranslationStoreTest --tests ru.spgsroot.peachy.data.manga.MangaMigrationSqlTest`. Expected: FAIL.
- [ ] **Step 3: Implement** as listed. The migration SQL must match the entity exactly (Room validates it); build once to generate `11.json` and commit it.
- [ ] **Step 4: Run** the same command, then `:app:compileReleaseKotlin`. Expected: PASS and a clean compile.
- [ ] **Step 5: Commit** `git commit -m "Cache page translations in the encrypted database"` including `11.json`.

### Task 6: Translation runner

**Review:** per-task — concurrency

**Files:**
- Create: `translate/TranslationRunner.kt`
- Modify: `app/build.gradle.kts` (add the catalog's coroutines-test as `testImplementation`)
- Test: `translate/TranslationRunnerTest.kt`

**Interfaces:**
- Consumes: `PageSource` (Task 3), `PageTranslator`, `TranslationException`, `TranslationError` (Task 4), `TranslationCache` (Task 5).
- Produces:
  ```kotlin
  sealed interface PageState { object Idle; object Loading; data class Done(val translation: PageTranslation); data class Failed(val error: TranslationError) }
  data class BatchState(val done: Int, val total: Int, val stopped: TranslationError? = null)
  class TranslationRunner(source: PageSource, translator: PageTranslator, cache: TranslationCache,
                          model: () -> String, scope: CoroutineScope, parallelism: Int = 2) {
      val pages: StateFlow<Map<Int, PageState>>      // keyed by page, one gallery per runner
      val batch: StateFlow<BatchState?>              // null when no batch is running
      suspend fun translate(galleryId: Int, page: Int, lang: String, force: Boolean = false): PageState
      fun startBatch(galleryId: Int, pageCount: Int, lang: String)
      fun cancelBatch()
  }
  ```
  `translate` returns the final state of that page and never throws for `TranslationException` (it becomes `Failed`); cancellation propagates.

- [ ] **Step 1: Write the failing tests** with fakes and `runTest`: `cacheHitSkipsTheTranslator`, `successIsCachedAndPublished`, `forceIgnoresCacheAndReplacesIt`, `concurrentRequestsForOnePageCallTheTranslatorOnce` (two `async` calls, translator suspended on a gate; one call recorded, both get `Done`), `failureBecomesFailedState`, `batchSkipsCachedPages`, `batchRunsAtMostTwoAtOnce` (translator records the maximum in-flight count over 6 pages, must be 2), `batchStopsOnAuth` (remaining pages never requested, `BatchState.stopped == Auth`), `batchStopsOnRateLimit`, `batchContinuesAfterBadResponse` (that page is `Failed`, the rest are translated, `done` counts every finished page), `cancelBatchStopsQueuedPages`, `batchAndTurnShareOneRequestPerPage` (page 3 requested through `translate` while the batch also reaches it: translator called once for page 3), `cancelledRequestDoesNotWriteTheCache`.
- [ ] **Step 2: Run** `--tests ru.spgsroot.peachy.translate.TranslationRunnerTest`. Expected: FAIL.
- [ ] **Step 3: Implement** with one `Mutex` per page key guarding the check-cache, call, write-cache sequence, a `Semaphore(parallelism)` for the batch, and `CancellationException` rethrown before the generic catch. A batch worker that sees `Auth` or `RateLimit` records `stopped` and cancels the rest of the batch.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add the translation runner with cache and batch"`.

### Task 7: Settings, language, view model

**Files:**
- Create: `translate/TargetLanguage.kt`, `ui/manga/TranslationViewModel.kt`
- Modify: `manga/ReaderSettings.kt` (add `val translateMode: TranslateMode = TranslateMode.Manual`, key `translate_mode` values `manual`, `on_turn`, `whole`), `di/AppModule.kt` (provide `OkHttpClient` for translation if no shared one exists, and `TranslationClient`)
- Test: `manga/ReaderSettingsTest.kt` (extend), `translate/TargetLanguageTest.kt`

**Interfaces:**
- Consumes: `TranslationRunner` (Task 6), `ProviderSettingsStore` (Task 2), `PageBytes` (Task 3), `TranslationClient`, `TranslationStore`.
- Produces:
  ```kotlin
  enum class TranslateMode { Manual, OnTurn, Whole }
  object TargetLanguage { fun resolve(appLanguageCode: String, deviceLanguage: String): String }  // "ru" | "en"
  @HiltViewModel class TranslationViewModel @Inject constructor(...) : ViewModel() {
      val pages: StateFlow<Map<Int, PageState>>
      val batch: StateFlow<BatchState?>
      val needsConsent: StateFlow<Boolean>      // true when a request is wanted but consent is missing
      val provider: StateFlow<ProviderSettings>
      fun open(galleryId: Int, pagePaths: List<String>)
      fun translateNow(page: Int, force: Boolean = false)
      fun onPageSettled(page: Int, mode: TranslateMode)   // OnTurn triggers here
      fun startWhole()
      fun cancelWhole()
      fun acceptConsent()
      fun declineConsent()
      fun saveProvider(change: (ProviderSettings) -> ProviderSettings)
      suspend fun testProvider(): TranslationError?       // null on success
  }
  ```

- [ ] **Step 1: Write the failing tests:** `TargetLanguageTest` (`ru` gives `ru`, `en` gives `en`, `system` with device `ru` gives `ru`, `system` with device `de` gives `en`); in `ReaderSettingsTest` a default-is-Manual case, a round trip for each mode, and an unknown stored value falling back to Manual.
- [ ] **Step 2: Run** `--tests ru.spgsroot.peachy.translate.TargetLanguageTest --tests ru.spgsroot.peachy.manga.ReaderSettingsTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** The view model builds a `TranslationRunner` per `open`, and a request without consent or without `isConfigured` sets `needsConsent` or a `Failed(NotConfigured)` state instead of calling the network. `onPageSettled` does nothing unless `mode == OnTurn`. The language comes from `LocaleManager`'s stored code and `Locale.getDefault().language`.
- [ ] **Step 4: Run** the same command, then `:app:compileDebugKotlin`. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Wire translation mode, language and view model"`.

### Task 8: Reader panel and triggers

**Files:**
- Create: `ui/manga/TranslationPanel.kt`, `app/src/main/res/values/translate.xml`, `app/src/main/res/values-en/translate.xml`
- Modify: `ui/manga/MangaReader.kt` (`NativeReader`: `hiltViewModel<TranslationViewModel>()`, call `open` in `LaunchedEffect(gallery.id)`, call `onPageSettled` from the two `settledPage` collectors, translate button in the chrome header, the panel, mode rows in the settings dialog)

**Interfaces:**
- Consumes: `TranslationViewModel` (Task 7).
- Produces: `@Composable fun TranslationPanel(state: PageState, batch: BatchState?, onAgain: () -> Unit, onProvider: () -> Unit, onCancelBatch: () -> Unit, modifier: Modifier)`.

- [ ] **Step 1: Strings** for button, panel states (translating, failed with each `TranslationError`, no text on this page), batch progress (`%1$d / %2$d`), cancel, "Translate again", mode labels (Manual, On page turn, Whole gallery), provider link. Russian in `values/translate.xml`, English in `values-en/translate.xml`.
- [ ] **Step 2: Implement** the panel: collapsed one-line state, expandable to about half the screen, scrollable lines (translation large, original small and dimmed, `sfx` dimmed italic). Reader: button shown in the chrome header; the panel sits over the bottom of the page without blocking taps above it; the strip mode and the pager both call `onPageSettled` once per settled page.
- [ ] **Step 3: Build and install** `:app:installDebug`. Expected: `Installed on 1 device.`
- [ ] **Step 4: Emulator smoke against a mock** (throwaway script outside the repo that answers `POST /chat/completions` on port 8766 with a fixed `{"sourceLanguage":"ja","lines":[...]}`; provider base URL `http://10.0.2.2:8766/v1`, any key): open a gallery, press the button, expect the lines in the panel; turn the page in `OnTurn` mode and expect a request for the new page only; reopen the first page and expect no request (cache); choose `Whole`, expect `n / total` progress and a working Cancel; make the mock answer 401 and expect the auth message and a stopped batch. Capture one screenshot of the panel.
- [ ] **Step 5: Commit** `git commit -m "Show page translations in the manga reader"`.

### Task 9: Provider dialog and consent

**Review:** per-task — security (consent, key entry)

**Files:**
- Create: `ui/dialog/ProviderDialog.kt`, `ui/dialog/TranslateConsentDialog.kt`
- Modify: `ui/manga/MangaReader.kt` (consent and provider dialogs), `ui/settings/SettingsDrawer.kt` (a "Page translation" row opening the provider dialog), the two `translate.xml` files

**Interfaces:**
- Consumes: `TranslationViewModel.provider`, `saveProvider`, `testProvider`, `acceptConsent`, `declineConsent`, `needsConsent` (Task 7).

- [ ] **Step 1: Implement** `ProviderDialog` (base URL, model, key with show toggle, a Test button showing success or the error name, Save disabled while `ProviderUrl.isAllowed` is false) and `TranslateConsentDialog` (names the host from the saved URL, says only the page image goes out, the key stays on the phone; Accept and Decline). The key field's value must not be remembered with `rememberSaveable`.
- [ ] **Step 2: Build, install, and check on the emulator:** the first translate press shows the consent; Decline sends nothing (mock sees no request); Accept then translates; an `http://example.com` URL cannot be saved; Test against the mock shows success and against a closed port shows a network error; after saving, the key is not visible in `adb shell run-as ru.spgsroot.peachy cat shared_prefs/translate_provider.xml` (encrypted values only).
- [ ] **Step 3: Commit** `git commit -m "Add provider settings and consent for page translation"`.

### Task 10: Vault

**Review:** per-task — persisted format

**Files:**
- Modify: `data/vault/VaultData.kt` (`val translations: List<VaultTranslation> = emptyList()`, new class `VaultTranslation(galleryId: Int, page: Int, lang: String, model: String, json: String, createdAt: Long)`, written in `toJson`, read optionally in `fromJson`), `data/vault/VaultMerge.kt` (`fun translations(current, incoming): List<PageTranslationEntity>` returning only rows to write: new, or incoming `createdAt` newer), `data/vault/VaultManager.kt` (`export` reads `PageTranslationDao.getAll()`; `applyData` upserts the merged rows in the existing transaction), `VaultExportResult`/`VaultImportResult` unchanged
- Test: `data/vault/VaultMergeTest.kt`, `data/vault/VaultDataTest.kt` (create if absent, next to the other vault tests)

**Interfaces:**
- Consumes: `PageTranslationEntity`, `PageTranslationDao` (Task 5).

- [ ] **Step 1: Write the failing tests:** `olderVaultWithoutTranslationsReadsAsEmpty` (a `toJson`-shaped document with no `translations` key parses), `translationsRoundTrip`, `newerIncomingTranslationWins`, `olderIncomingTranslationIsIgnored`, `translationOnlyOnOneSideIsKept`, `translationForUnknownGalleryIsStillImported` (cache rows do not need a library row), `providerKeyIsNotInTheDocument` (the JSON text contains neither `apiKey` nor the key value of a populated `ProviderSettings`).
- [ ] **Step 2: Run** `--tests ru.spgsroot.peachy.data.vault.*`. Expected: FAIL.
- [ ] **Step 3: Implement** as listed.
- [ ] **Step 4: Run** the same command. Expected: PASS. Emulator: export, wipe app data, import, reopen a translated page and expect no request.
- [ ] **Step 5: Commit** `git commit -m "Carry page translations in the vault"`.

### Task 11: Docs and live check

**Files:**
- Modify: `README.md`, `README_RU.md`

- [ ] **Step 1: Update both READMEs:** a feature bullet (panel, three modes, cache, bring your own provider) and a privacy line (page images go to the provider URL you set, only after you accept; the key is stored encrypted on the phone and is not in backups or the `.peachy` file). Keep both languages equivalent.
- [ ] **Step 2: Live run on the emulator** with the owner's real key typed into the provider dialog (not stored in any file): translate one page of a Japanese gallery on `glm-5.3-flash`, expect Russian lines in the panel within about 10 s.
- [ ] **Step 3: Full verification:** the full unit test command from Global Constraints, then `:app:compileReleaseKotlin`. Expected: zero failures, clean compile.
- [ ] **Step 4: Commit** `git commit -m "Document page translation"`.

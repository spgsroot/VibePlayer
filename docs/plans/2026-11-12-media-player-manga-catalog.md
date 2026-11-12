# Manga Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an nhentai catalog with the same search filters as NHApp, and replace the thin reader with a native Jetpack Compose reader that resumes, hides chrome, and pulses on a settled page turn.

**Architecture:** A pure `CatalogQuery` builds the search string. `NhentaiRequest.search` sends that string plus a separate `sort` parameter. Room stores read progress, the last ten queries, and a local tag blacklist. Compose screens consume those types. The reader never loads an nhentai page in a WebView.

**Tech Stack:** Kotlin, Jetpack Compose, Room, OkHttp, existing `NhentaiApi`, existing `PageTurnHaptic`.

**Spec:** `docs/specs/2026-11-12-media-player-manga-catalog-design.md`

## Global Constraints

- Catalog and reader are Jetpack Compose. Do not load nhentai HTML in a WebView. Leave the video WebView alone.
- Sort is the `sort` query parameter (`date`, `popular`, `popular-today`, `popular-week`, `popular-month`), never part of the search string.
- The API key stays only in the `Authorization` header.
- Image hosts stay `https://i1.nhentai.net` and `https://t1.nhentai.net`. Do not rewrite API paths.
- Existing `POST`/`DELETE` favorite calls stay. Do not add an online-favorites screen or read the online blacklist.
- No image search, comments, characters, recommendations, cloud, or dual-page spread.
- Catalog results are paged. No infinite list.
- New user-visible text goes in `app/src/main/res/values/strings.xml` and `app/src/main/res/values-en/strings.xml`.
- `files/manga/` stays out of backup and device transfer.
- Window theme stays dark. Navigation transitions stay `None`.
- Do not bump `versionName` or `versionCode`.

---

### Task 1: Catalog query

**Files:**
- Create: `app/src/main/java/ru/spgsroot/peachy/manga/CatalogQuery.kt`
- Test: `app/src/test/java/ru/spgsroot/peachy/manga/CatalogQueryTest.kt`

**Interfaces:**
- Produces: `TagMode`, `TagFilter`, `SortKey`, `PagesFilter`, `CatalogQuery`, `CatalogQuery.toSearchQuery()`, `SortKey.toApi()`, `CatalogQuery.nextTagMode()`

- [ ] **Step 1: Write the failing test**

```kotlin
class CatalogQueryTest {
    @Test
    fun spacedIncludeAndExcludeAreQuotedAndSortStaysOut() {
        val query = CatalogQuery(
            text = "  school  ",
            tags = listOf(
                TagFilter("tag", "school uniform", TagMode.Include),
                TagFilter("artist", "name", TagMode.Exclude)
            ),
            sort = SortKey.PopularWeek
        )
        assertEquals("""school tag:"school uniform" -artist:name""", query.toSearchQuery())
        assertFalse(query.toSearchQuery().contains("sort="))
        assertEquals("popular-week", query.sort.toApi())
    }

    @Test
    fun datePresetsAndCustomRangeUseNhentaiUploadedSyntax() {
        assertEquals("uploaded:>7d", CatalogQuery(uploaded = "uploaded:>7d").toSearchQuery())
        val ranged = CatalogQuery(uploaded = "uploaded:>2024-01-01 uploaded:<2024-02-01")
        assertEquals("uploaded:>2024-01-01 uploaded:<2024-02-01", ranged.toSearchQuery())
    }

    @Test
    fun pagesDropZeroAndExactReplacesRange() {
        assertEquals("", CatalogQuery(pages = PagesFilter(min = 0, max = -1, exact = null)).toSearchQuery())
        assertEquals("pages:>10 pages:<40", CatalogQuery(pages = PagesFilter(10, 40, null)).toSearchQuery())
        assertEquals("pages:12", CatalogQuery(pages = PagesFilter(10, 40, 12)).toSearchQuery())
    }

    @Test
    fun tagCycleIsIncludeThenExcludeThenGone() {
        assertEquals(TagMode.Include, nextTagMode(null))
        assertEquals(TagMode.Exclude, nextTagMode(TagMode.Include))
        assertEquals(null, nextTagMode(TagMode.Exclude))
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails to compile**

Run: `./gradlew :app:testDebugUnitTest --tests ru.spgsroot.peachy.manga.CatalogQueryTest`
Expected: unresolved `CatalogQuery`.

- [ ] **Step 3: Implement the types**

`SortKey.toApi()` returns `date`, `popular`, `popular-today`, `popular-week`, `popular-month`. `toSearchQuery()` joins non-blank parts with one space: trimmed text, `type:name` or `-type:name`, the `uploaded` clause, then pages. A tag name containing a space is wrapped in double quotes. No other escaping. Exact page count wins over min/max. Min/max below 1 are omitted.

- [ ] **Step 4: Run the test and confirm it passes**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/spgsroot/peachy/manga/CatalogQuery.kt app/src/test/java/ru/spgsroot/peachy/manga/CatalogQueryTest.kt
git commit -m "Add the nhentai catalog query builder"
```

---

### Task 2: Search request and parser

**Files:**
- Modify: `app/src/main/java/ru/spgsroot/peachy/manga/NhentaiRequest.kt`
- Modify: `app/src/main/java/ru/spgsroot/peachy/manga/NhentaiParser.kt`
- Modify: `app/src/main/java/ru/spgsroot/peachy/data/manga/NhentaiApi.kt`
- Modify: `app/src/main/java/ru/spgsroot/peachy/ui/manga/MangaViewModel.kt` so the current call still compiles with `sort=date`
- Test: `app/src/test/java/ru/spgsroot/peachy/manga/NhentaiRequestTest.kt`
- Test: `app/src/test/java/ru/spgsroot/peachy/manga/NhentaiParserTest.kt`

**Interfaces:**
- Consumes: `SortKey.toApi()`
- Produces: `NhentaiRequest.search(query, sort, page, key)`, `GallerySummary.tagIds`, `GalleryDetail.tags`, `GalleryTag(id, type, name)`

- [ ] **Step 1: Extend the failing tests**

Change the existing search assertion to `NhentaiRequest.search("test", "popular", 2, "secret-key")` and expect `https://nhentai.net/api/v2/search?query=test&sort=popular&page=2`. The key assertion stays: header contains the key, URL does not.

Add a parser test whose search JSON has `tag_ids: [9, 4]` and whose gallery JSON has `tags: [{id:9,type:tag,name:english}]`. Assert `summary.tagIds == listOf(9, 4)` and `detail.tags == listOf(GalleryTag(9, "tag", "english"))`.

- [ ] **Step 2: Run both tests and confirm the new assertions fail**

- [ ] **Step 3: Implement**

`search` URL-encodes `query` and `sort` and appends `page`. `NhentaiApi.search(query, sort, page)` passes them through. `MangaViewModel` passes `"date"` until Task 6. `GallerySummary.tagIds` and `GalleryDetail.tags` default to empty so old call sites compile. Missing arrays parse as empty, not as an exception.

- [ ] **Step 4: Run `NhentaiRequestTest` and `NhentaiParserTest`**

- [ ] **Step 5: Commit**

```bash
git commit -m "Pass catalog sort and gallery tags through the nhentai client"
```

---

### Task 3: Reader motion, blacklist, and search history

**Files:**
- Create: `app/src/main/java/ru/spgsroot/peachy/manga/ReaderMotion.kt`
- Create: `app/src/main/java/ru/spgsroot/peachy/manga/TagBlacklist.kt`
- Create: `app/src/main/java/ru/spgsroot/peachy/manga/SearchHistory.kt`
- Test: `app/src/test/java/ru/spgsroot/peachy/manga/ReaderMotionTest.kt`
- Test: `app/src/test/java/ru/spgsroot/peachy/manga/TagBlacklistTest.kt`
- Test: `app/src/test/java/ru/spgsroot/peachy/manga/SearchHistoryTest.kt`

**Interfaces:**
- Produces: `PageTurnGate.onPage(page, idle): Boolean`, `centerPage(centers, viewportCenter): Int`, `tapZone(x, width, mirror): TapZone`, `startPage(saved, pageCount): Int`, `blocks(tagIds, tagNames, blockedIds, blockedNames): Boolean`, `rememberQuery(current, query): List<String>`, `isGalleryId(raw): Boolean`

Task 3 API details for consumers:
- `PageTurnGate.onPage(page: Int, idle: Boolean)` tracks only settled pages; keep one gate per reader session.
- `centerPage(centers: List<Int>, viewportCenter: Int)` returns the nearest center's zero-based list index (first on a tie, `-1` for no centers). Callers map that index to the corresponding page.
- `tapZone(x: Float, width: Float, mirror: Boolean)` uses pixel coordinates and returns `TapZone.Back`, `TapZone.Chrome`, or `TapZone.Forward`.
- `startPage(saved: Int?, pageCount: Int)` returns a valid saved index or zero.
- `blocks(tagIds: Collection<Int>, tagNames: Collection<String>, blockedIds: Set<Int>, blockedNames: Set<String>)` matches IDs or exact case-insensitive tag names, never titles.
- `rememberQuery(current: List<String>, query: String)` trims the query, ignores blanks, and returns at most ten distinct queries, newest first, without mutating `current`.
- `isGalleryId(raw: String)` accepts a nonempty sequence of ASCII digits after trimming.

- [ ] **Step 1: Write the failing tests**

`PageTurnGate`: the first idle page returns false; a later idle change returns true; calls with `idle=false` return false and do not count as a change. A fling that reports pages 1 then 2 while not idle, then page 2 while idle, pulses once.

`centerPage(listOf(100, 300, 500), 310)` is 1.

`tapZone`: `x=width/3 - 1` is Back, `x=width/2` is Chrome, `x=width*2/3 + 1` is Forward. `mirror=true` swaps Back and Forward only.

`startPage(null, 10)` is 0. `startPage(3, 10)` is 3. `startPage(10, 10)` is 0. `startPage(-1, 10)` is 0.

`blocks`: id 9 against blocked ids `{9}` is true. Name `English` against blocked names `{"english"}` is true. A title is not an input. No overlap is false.

`rememberQuery`: blank is ignored; a repeat moves to the front; the eleventh distinct query drops the oldest. `isGalleryId(" 1225 ")` is true. `isGalleryId("12a")` is false. `isGalleryId("")` is false.

- [ ] **Step 2: Run the three tests and confirm they fail to compile**

- [ ] **Step 3: Implement the functions exactly to those assertions**

- [ ] **Step 4: Run the three tests**

- [ ] **Step 5: Commit**

```bash
git commit -m "Define reader motion, blacklist matching, and search history"
```

---

### Task 4: Persist progress, queries, and blacklist

**Review:** per-task. This changes the Room schema. A bad migration loses read progress on upgrade.

**Files:**
- Modify: `app/src/main/java/ru/spgsroot/peachy/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/ru/spgsroot/peachy/di/DatabaseModule.kt`
- Create: `app/src/main/java/ru/spgsroot/peachy/data/manga/MangaCatalogStore.kt`
- Test: `app/src/test/java/ru/spgsroot/peachy/data/manga/MangaMigrationSqlTest.kt`

**Interfaces:**
- Consumes: `rememberQuery`, `blocks`
- Produces: `ReadHistoryEntity`, `SearchHistoryEntity`, `TagBlacklistEntity`, `ReadHistoryDao`, `SearchHistoryDao`, `TagBlacklistDao`, `MangaMigrations.MIGRATION_5_6`, `MangaCatalogStore`

- [ ] **Step 1: Write a failing test that the migration SQL creates the three tables and does not touch `library_galleries`**

Assert `MIGRATION_5_6` contains `CREATE TABLE IF NOT EXISTS read_history`, `search_history`, and `tag_blacklist`, and does not contain `library_galleries`.

- [ ] **Step 2: Run it and confirm the migration object is missing**

- [ ] **Step 3: Add version 6**

Entities:

```kotlin
@Entity(tableName = "read_history")
data class ReadHistoryEntity(
    @PrimaryKey val galleryId: Int,
    val title: String,
    val thumbnailPath: String,
    val page: Int,
    val pageCount: Int,
    val readAt: Long
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val usedAt: Long
)

@Entity(tableName = "tag_blacklist", primaryKeys = ["kind", "value"])
data class TagBlacklistEntity(val kind: String, val value: String)
```

`kind` is `id` or `name`. Migration SQL matches those columns. `fallbackToDestructiveMigration()` stays for older gaps; `MIGRATION_5_6` is registered beside the existing migrations. Provide the three DAOs from `DatabaseModule` the same way `libraryGalleryDao` is provided.

`MangaCatalogStore` upserts history, lists it newest `readAt` first, remembers a query through `rememberQuery` then replaces the `search_history` rows, and lists blacklist ids and lowercase names. It does not filter the saved library.

Room writes `app/schemas/ru.spgsroot.peachy.data.db.AppDatabase/6.json` on compile. Commit that file.

- [ ] **Step 4: Run the SQL test and `./gradlew :app:assembleDebug`**

- [ ] **Step 5: Commit**

```bash
git commit -m "Store manga progress, search history, and the tag blacklist"
```

---

### Task 5: Catalog hub

**Files:**
- Create: `app/src/main/java/ru/spgsroot/peachy/ui/manga/MangaHubScreen.kt`
- Create: `app/src/main/java/ru/spgsroot/peachy/ui/manga/CatalogScreen.kt`
- Create: `app/src/main/java/ru/spgsroot/peachy/ui/manga/FilterSheet.kt`
- Modify: `app/src/main/java/ru/spgsroot/peachy/ui/manga/MangaViewModel.kt`
- Modify: `app/src/main/java/ru/spgsroot/peachy/ui/MainActivity.kt`
- Modify: both `strings.xml` files

**Interfaces:**
- Consumes: `CatalogQuery`, `NhentaiApi.search`, `MangaCatalogStore`, `isGalleryId`, `blocks`
- Produces: hub tabs Catalog, Library, History. `manga` route opens the hub. Library tab keeps the current save, favorite, and remove behavior.

- [ ] **Step 1: Write a failing test for the search destination**

In `CatalogQueryTest`, add `searchDestination("1225")` returns `OpenGallery(1225)` and `searchDestination("tag:english")` returns `RunSearch`. Put `searchDestination` in `CatalogQuery.kt`.

- [ ] **Step 2: Run that test and confirm it fails**

- [ ] **Step 3: Implement the hub**

`MainActivity` route `manga` shows `MangaHubScreen` instead of `MangaLibraryScreen`. Back from the hub returns to the player. The player library icon still navigates to `manga`.

Catalog: text field, search action, last ten queries, filter sheet, and paged results. A numeric query navigates to `manga/{id}` and does not call search. Other queries call `api.search(query.toSearchQuery(), query.sort.toApi(), page)`. The filter sheet edits tags, language chips, sort, date presets, two date fields, and page bounds, then rebuilds one `CatalogQuery`. Reset clears those fields and does not clear the blacklist. Result cards with a blocked tag id are not shown. Empty and error states use string resources. Previous/next page buttons, not an endless list.

Library tab is the existing library list. History tab lists `read_history` and opens `manga/{id}`.

- [ ] **Step 4: Run `CatalogQueryTest` and `./gradlew :app:assembleDebug`**

- [ ] **Step 5: Commit**

```bash
git commit -m "Open the manga hub on the player library icon"
```

---

### Task 6: Native reader

**Files:**
- Modify: `app/src/main/java/ru/spgsroot/peachy/ui/manga/MangaScreens.kt`
- Modify: `app/src/main/java/ru/spgsroot/peachy/ui/manga/MangaViewModel.kt`
- Modify: both `strings.xml` files

**Interfaces:**
- Consumes: `PageTurnGate`, `centerPage`, `tapZone`, `startPage`, `PageTurnHaptic`, `MangaCatalogStore`
- Produces: reader route `manga/{id}` with pager mode, vertical strip mode, tap zones, pinch zoom, thumbnail jump, fit mode, and a settings sheet.

- [ ] **Step 1: No new pure test.** Task 3 already pins the motion. This task wires it.

- [ ] **Step 2: Replace `MangaReaderScreen`**

On open, `startPage(saved, pageCount)` selects the first page. Horizontal mode uses `HorizontalPager`. Vertical mode uses a lazy column of page images. Both use Coil `AsyncImage` and `NhentaiCdn.page`. Neither uses `AndroidView` or `WebView`.

A tap uses `tapZone`. Back/forward animate the pager or scroll the strip. Center toggles chrome. Pinch zooms the current page. Double tap resets zoom to 1. A thumbnail strip jumps to that page. Fit is contain or fill-width.

`PageTurnGate` receives the pager page with `idle=true`, and the strip's `centerPage` with the lazy list's scroll idle flag. A true result calls `commandSender.pulse`. Leaving the screen upserts `read_history` for the current page even if the strip is still moving.

Reader prefs live in `SharedPreferences` named `manga_reader`: `mode` (`paged` or `strip`), `fit` (`contain` or `width`), `mirror` (boolean), `intensity` (float, default `PageTurnHaptic.DEFAULT_INTENSITY`). The intensity slider is in the settings sheet, not over the page. Changing it calls the existing `setIntensity`.

Chrome shows the title, page count, back, favorite, and settings. It hides until the center tap.

- [ ] **Step 3: Run `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`**

- [ ] **Step 4: Commit**

```bash
git commit -m "Replace the manga reader with a native Compose reader"
```

---

### Task 7: Docs that would otherwise lie

**Files:**
- Modify: `README.md`
- Modify: `README_RU.md`

- [ ] **Step 1: Replace the manga bullet**

The English bullet must say the catalog filters by tag, date, and page count, sorts by date or popularity, and opens a native Compose reader. Page-turn strength is in the reader settings sheet, not a slider on the page. The Russian bullet must say the same fact. Do not claim a WebView reader. Leave the video WebView bullet as it is.

- [ ] **Step 2: Commit**

```bash
git commit -m "Document the manga catalog and native reader"
```

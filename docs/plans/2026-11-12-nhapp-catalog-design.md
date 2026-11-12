# NHApp-style manga catalog implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproduce the supplied NHApp screen set in VibePlayer's native Compose catalog using real API and local-library data.

**Architecture:** Keep the existing native reader and Hilt repositories. Extend the API's actual v2 contracts, render two-column cover grids, use a manga-scoped drawer, and persist tag favorites and named tag collections locally. Keep navigation between gallery details and reading explicit.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, OkHttp, kotlinx.coroutines, existing Room/DataStore stack.

**Spec:** User-provided NHApp screenshots and instruction to implement every screen, inspecting NHApp locally where necessary. Reference checkout: C:/Users/spgs/AppData/Local/Temp/vibe-nhapp-reference.

## Global Constraints
- No fabricated counts, gallery metadata, recommendations, comments, or login state.
- Preserve existing page-turn haptics, offline library, history, blacklist, and reader gestures.
- No release/version bump or push for this redesign without a new request.
- Adapt the reference design; do not copy its React Native implementation or bundled tag dataset.
- English and Russian labels remain available.

## Review Focus
- Loading/error/empty states must remain navigable; API failures must not be disguised as empty results.
- Large metadata sets must not cause one HTTP request per result card.
- Selected tag inclusion/exclusion, favorites, and collections survive navigation and app restart as appropriate.
- Details and reading use separate routes; existing bookmarks/history still open the expected gallery.
- Small screens, long titles, and many tags retain scrollable controls and reachable actions.

## Task 1: Real metadata and discovery API
- [ ] Extend NhentaiParser.kt, NhentaiRequest.kt and NhentaiApi.kt with batch-resolved card tags, upload dates, detail metadata, tag browsing/search, random, related and comment reads according to the v2 schema and reference API modules.
- [ ] Preserve existing model constructor compatibility via default metadata fields and update affected behavioral parser/request tests.
- [ ] Integration owner verifies the combined app once parallel edits settle.

## Task 2: Large cover grid and compact filter sheet
- [ ] Replace CatalogScreen.kt single-column rows with two portrait covers per row, metadata and tappable tag boxes, real loading/errors and pagination.
- [ ] Restyle FilterSheet.kt as compact grouped controls without losing existing query semantics or blacklist controls.
- [ ] Preserve the public CatalogGalleryCard(title, thumbnailPath, pageCount, onOpen) signature for history consumers.

## Task 3: Tag browser, favorites and collections
- [ ] Create TagBrowserScreen.kt, TagBrowserViewModel.kt and TagPreferences.kt using actual paginated tags and API autocomplete.
- [ ] Implement tag search/type selection, all/favorites/collections tabs, actual counts, include/exclude selection, local tag favorites and editable named collections.
- [ ] Expose TagBrowserScreen(onSearch: (List<TagFilter>) -> Unit, initialType: String = "tag", viewModel: TagBrowserViewModel = hiltViewModel()).

## Task 4: Drawer and gallery details integration
- [ ] Replace manga hub tabs with the reference-like drawer and working catalog, downloads, bookmarks, favorites, history, characters, recommendations and reader settings destinations.
- [ ] Add GalleryDetailScreen.kt with cover/title, download/favorite/read/comment actions, real grouped tag boxes, metadata and related galleries.
- [ ] Route manga/{id} to details and manga/{id}/read to the existing native reader; connect tags to catalog queries and random to a real gallery.
- [ ] Display actual comment reads; do not claim personal account or posting support without a user token.

## Task 5: Verify and document
- [ ] Run app JVM tests and debug/release Kotlin compilation after integration.
- [ ] Install and exercise actual screens on the available emulator; inspect screenshots and accessibility hierarchy.
- [ ] Update existing project documentation with the actual supported behavior, review the full change, and commit only verified work.

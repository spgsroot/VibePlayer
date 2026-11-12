# Native manga reader

The `manga/{id}` route uses Compose and Coil. Downloaded pages take precedence over remote URLs resolved by `NhentaiCdn.page`. Saved library metadata remains usable if the gallery request fails.

## Controls

- Swipe horizontally in paged mode or scroll vertically in strip mode.
- Tap the left or right third to move backward or forward. The mirror setting swaps those two tap zones.
- Tap the center third to show or hide the title, page count, Back, favorite, settings, and page thumbnails. Chrome starts hidden.
- Pinch the current page to zoom from 1× to 5×; drag while zoomed to pan. Double-tap to reset zoom and pan.
- Tap a thumbnail to jump to its page.
- Reader settings select paged/strip mode, contain/fill-width fit, mirrored taps, and page-turn intensity. Saving the gallery remains available in this sheet.

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

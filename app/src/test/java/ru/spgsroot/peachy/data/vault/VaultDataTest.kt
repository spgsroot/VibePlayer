package ru.spgsroot.peachy.data.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultDataTest {
    private fun empty(translations: List<VaultTranslation> = emptyList()) = VaultData(
        entryCount = 0, videos = emptyList(), galleries = emptyList(), readHistory = emptyList(),
        searches = emptyList(), blacklist = emptyList(), memory = emptyList(), notes = emptyList(),
        bookmarks = emptyList(), browserHistory = emptyList(), allowlist = emptyList(), tagLibrary = null,
        translations = translations
    )

    @Test
    fun olderVaultWithoutTranslationsReadsAsEmpty() {
        val older = JSONObject(empty().toJson()).apply { remove("translations") }.toString()
        assertTrue(VaultData.fromJson(older).translations.isEmpty())
    }

    @Test
    fun translationsRoundTrip() {
        val data = empty(
            listOf(
                VaultTranslation(7, 0, "ru", "glm-5.3-flash", "{\"sourceLanguage\":\"ja\",\"lines\":[]}", 100),
                VaultTranslation(7, 12, "en", "m \"x\"", "{\"a\":\"line\\nbreak\"}", 5)
            )
        )
        assertEquals(data, VaultData.fromJson(data.toJson()))
    }

    @Test
    fun aTranslationMissingAFieldMakesTheVaultCorrupt() {
        val broken = JSONObject(empty().toJson())
            .put("translations", org.json.JSONArray().put(JSONObject().put("galleryId", 1).put("page", 0)))
            .toString()
        val error = org.junit.Assert.assertThrows(VaultException::class.java) { VaultData.fromJson(broken) }.error
        assertEquals(VaultError.CORRUPT, error)
    }

    @Test
    fun photosRoundTripWithDimensionsAndSeparateEntryNames() {
        val data = empty().copy(
            entryCount = 3,
            photos = listOf(
                VaultPhoto(7, "Фото \"holiday\"\n", 123, 45, 1200, 800, "jpg", true),
                VaultPhoto(9, "No thumbnail", 124, 89, 800, 1200, "webp", false)
            )
        )
        assertEquals(data, VaultData.fromJson(data.toJson()))
        assertEquals("photo/7.jpg", data.photos.first().entryName)
        assertEquals("photo_thumb/7", data.photos.first().thumbnailEntryName)
        assertEquals(2, JSONObject(data.toJson()).getInt("format"))
    }

    @Test
    fun legacyVersionOneWithoutPhotosOrTranslationsIsAccepted() {
        val legacy = JSONObject(empty().toJson()).apply {
            put("format", 1)
            remove("photos")
            remove("translations")
        }
        val data = VaultData.fromJson(legacy.toString())
        assertTrue(data.photos.isEmpty())
        assertTrue(data.translations.isEmpty())
    }

    @Test
    fun versionTwoWithoutPhotosIsAccepted() {
        val raw = JSONObject(empty().toJson()).apply { remove("photos") }
        assertTrue(VaultData.fromJson(raw.toString()).photos.isEmpty())
    }

    @Test
    fun malformedPhotoMetadataIsCorruptBeforeAnyFilesAreRestored() {
        val valid = JSONObject(empty().copy(
            entryCount = 1, photos = listOf(VaultPhoto(7, "photo", 1, 4, 20, 10, "png", false))
        ).toJson())
        val changes = listOf<Pair<String, Any>>(
            "id" to 0L, "id" to -1L, "fileSize" to -1L, "fileSize" to 0L,
            "width" to 0, "height" to -1, "extension" to "../jpg",
            "extension" to "jpg/escape", "extension" to "a\\b", "extension" to "",
            "extension" to "C:jpg", "extension" to "jpg\n"
        )
        for ((key, value) in changes) {
            val broken = JSONObject(valid.toString())
            broken.getJSONArray("photos").getJSONObject(0).put(key, value)
            val error = org.junit.Assert.assertThrows("$key=$value", VaultException::class.java) {
                VaultData.fromJson(broken.toString())
            }
            assertEquals(VaultError.CORRUPT, error.error)
        }
        valid.getJSONArray("photos").getJSONObject(0).remove("width")
        assertEquals(VaultError.CORRUPT, org.junit.Assert.assertThrows(VaultException::class.java) {
            VaultData.fromJson(valid.toString())
        }.error)
    }

    @Test
    fun repeatedPhotoIdsAreCorrupt() {
        val photo = VaultPhoto(7, "photo", 1, 4, 20, 10, "png", false)
        val data = empty().copy(entryCount = 2, photos = listOf(photo, photo.copy(title = "other")))
        assertEquals(VaultError.CORRUPT, org.junit.Assert.assertThrows(VaultException::class.java) {
            VaultData.fromJson(data.toJson())
        }.error)
    }

    @Test
    fun legacyProgressTotalRemainsOnlyAHint() {
        val raw = JSONObject(empty().toJson()).put("format", 1).put("entryCount", -1).apply { remove("photos") }
        val restored = VaultData.fromJson(raw.toString())
        assertEquals(-1, restored.entryCount)
        assertTrue(restored.photos.isEmpty())
    }
}

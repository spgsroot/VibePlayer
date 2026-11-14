package ru.spgsroot.peachy.playback.queue

import org.junit.Assert.*
import org.junit.Test
import ru.spgsroot.peachy.domain.model.Video

class PlaylistManagerTest {
    private fun video(id: Long) = Video(id, "Video $id", "/$id.mp4", 1000, null, id, 100)

    @Test fun nextAndPreviousStartAtTheBoundaryWhenPlaybackIsOutsideTheFilter() {
        val queue = PlaylistManager()
        val first = video(1)
        val second = video(2)
        val playing = video(3)
        queue.setPlaylist(listOf(first, second, playing))
        queue.selectVideo(playing)
        queue.setPlaylist(listOf(first, second))
        assertEquals(playing, queue.current())
        assertEquals(first, queue.next())
        queue.selectVideo(playing)
        assertEquals(second, queue.previous())
        assertEquals(listOf(first, second), queue.playlist.value)
    }

    @Test fun deletionTracksActualPlaybackNotTheFirstFilteredResult() {
        val queue = PlaylistManager()
        val first = video(1)
        val second = video(2)
        val playing = video(3)
        queue.setPlaylist(listOf(first, second, playing))
        queue.selectVideo(playing)
        queue.setPlaylist(listOf(first, second))
        val replacements = mutableListOf<Video?>()
        queue.setOnCurrentVideoRemovedListener { replacements += it }
        assertFalse(queue.removeVideo(first))
        assertTrue(replacements.isEmpty())
        assertTrue(queue.removeVideo(playing))
        assertEquals(listOf(second), replacements)
        assertEquals(second, queue.current())
    }

    @Test fun selectingGalleryMediaOutsideTheQueueKeepsTheFilteredOrder() {
        val queue = PlaylistManager()
        val first = video(1)
        val second = video(2)
        queue.setPlaylist(listOf(first, second))
        queue.selectVideo(video(3))
        assertEquals(3L, queue.current()?.id)
        assertEquals(listOf(first, second), queue.playlist.value)
        assertEquals(first, queue.next())
        assertEquals(second, queue.next())
        assertEquals(first, queue.next())
    }
}

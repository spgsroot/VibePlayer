package ru.spgsroot.vibeplayer.data.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.spgsroot.vibeplayer.data.db.PlaybackStateDao
import ru.spgsroot.vibeplayer.data.db.PlaybackStateEntity
import ru.spgsroot.vibeplayer.domain.model.PlaybackState
import javax.inject.Inject

class PlaybackStateRepository @Inject constructor(
    private val playbackStateDao: PlaybackStateDao
) {
    suspend fun saveState(state: PlaybackState) {
        playbackStateDao.insert(state.toEntity())
    }

    suspend fun restoreState(): PlaybackState? {
        return playbackStateDao.getState()?.toDomain()
    }

    suspend fun clearState() {
        playbackStateDao.clear()
    }

    private fun PlaybackState.toEntity() = PlaybackStateEntity(
        currentVideoId = currentVideoId,
        positionMs = positionMs,
        playlistOrder = Json.encodeToString(playlistOrder)
    )

    private fun PlaybackStateEntity.toDomain() = PlaybackState(
        currentVideoId = currentVideoId,
        positionMs = positionMs,
        playlistOrder = Json.decodeFromString(playlistOrder)
    )
}

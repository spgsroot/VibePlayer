package ru.spgsroot.vibeplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.spgsroot.vibeplayer.manga.rememberQuery

@Dao
abstract class SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY usedAt DESC")
    abstract fun observeAll(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY usedAt DESC")
    abstract suspend fun getAll(): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entities: List<SearchHistoryEntity>)

    @Query("DELETE FROM search_history")
    abstract suspend fun clear()

    @Transaction
    open suspend fun remember(query: String, usedAt: Long) {
        val current = getAll().map { it.query }
        val next = rememberQuery(current, query)
        if (next === current) return
        // Persist the helper's order even when consecutive calls share a millisecond.
        val rows = next.mapIndexed { index, value -> SearchHistoryEntity(value, usedAt - index) }
        clear()
        insertAll(rows)
    }
}

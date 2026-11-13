package ru.spgsroot.peachy.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val url: String,
    val title: String,
    val addedAt: Long
)

/** One row per address: visiting it again only moves it to the top. */
@Entity(tableName = "browser_history")
data class BrowserHistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val visitedAt: Long
)

/** A site where the user turned the ad and tracker blocker off. */
@Entity(tableName = "adblock_allowlist")
data class AllowlistEntity(
    @PrimaryKey val host: String
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    suspend fun getAll(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun delete(url: String)
}

@Dao
abstract class BrowserHistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC")
    abstract fun observeAll(): Flow<List<BrowserHistoryEntity>>

    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC")
    abstract suspend fun getAll(): List<BrowserHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: BrowserHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entities: List<BrowserHistoryEntity>)

    @Query("DELETE FROM browser_history WHERE url = :url")
    abstract suspend fun delete(url: String)

    @Query("DELETE FROM browser_history")
    abstract suspend fun clear()

    @Query(
        "DELETE FROM browser_history WHERE url NOT IN " +
            "(SELECT url FROM browser_history ORDER BY visitedAt DESC LIMIT :keep)"
    )
    abstract suspend fun trim(keep: Int)

    /** Records a visit and keeps the table bounded. */
    @Transaction
    open suspend fun record(entity: BrowserHistoryEntity, keep: Int = MAX_ROWS) {
        upsert(entity)
        trim(keep)
    }

    companion object {
        const val MAX_ROWS = 2000
    }
}

@Dao
interface AllowlistDao {
    @Query("SELECT host FROM adblock_allowlist")
    fun observeHosts(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entity: AllowlistEntity)

    @Query("DELETE FROM adblock_allowlist WHERE host = :host")
    suspend fun remove(host: String)
}

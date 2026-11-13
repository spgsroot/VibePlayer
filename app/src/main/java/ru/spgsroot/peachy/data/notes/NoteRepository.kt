package ru.spgsroot.peachy.data.notes

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class NoteRepository @Inject constructor(private val dao: NoteDao) {
    /** The note text, or null when there is none. */
    fun observe(kind: String, ref: String): Flow<String?> = dao.observe(kind, ref).map { it?.text }

    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    /** Saves [text]; blank text removes the note instead. */
    suspend fun save(kind: String, ref: String, text: String) {
        val clean = text.trimEnd().take(MAX_LENGTH)
        if (clean.isBlank()) {
            dao.delete(kind, ref)
        } else {
            dao.upsert(NoteEntity(kind, ref, clean, System.currentTimeMillis()))
        }
    }

    suspend fun delete(kind: String, ref: String) = dao.delete(kind, ref)

    companion object {
        const val MAX_LENGTH = 20_000
    }
}

package com.jarvis.ai.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" | "assistant"
    val text: String,
    val time: Long = System.currentTimeMillis()
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY id") fun all(): Flow<List<Message>>
    @Query("SELECT * FROM (SELECT * FROM messages ORDER BY id DESC LIMIT :n) ORDER BY id")
    suspend fun last(n: Int): List<Message>
    @Insert suspend fun insert(m: Message)
    @Query("DELETE FROM messages") suspend fun clear()
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String, // "note" | "task"
    val text: String,
    val done: Boolean = false,
    val time: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY id DESC") fun all(): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE kind = :k ORDER BY id") suspend fun byKind(k: String): List<Note>
    @Insert suspend fun insert(n: Note)
    @Update suspend fun update(n: Note)
    @Delete suspend fun delete(n: Note)
    @Query("DELETE FROM notes") suspend fun clear()
}

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val triggerAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val fired: Boolean = false
)

@Dao
interface ReminderDao {
    @Insert suspend fun insert(reminder: Reminder): Long
    @Query("SELECT * FROM reminders WHERE fired = 0 ORDER BY triggerAt") suspend fun pending(): List<Reminder>
    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1") suspend fun byId(id: Long): Reminder?
    @Update suspend fun update(reminder: Reminder)
    @Query("SELECT * FROM reminders ORDER BY triggerAt DESC") fun all(): Flow<List<Reminder>>
}

@Database(entities = [Message::class, Note::class, Reminder::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): MessageDao
    abstract fun notes(): NoteDao
    abstract fun reminders(): ReminderDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, text TEXT NOT NULL, triggerAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, fired INTEGER NOT NULL)")
            }
        }
    }
}

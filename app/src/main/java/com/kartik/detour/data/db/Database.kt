package com.kartik.detour.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A word or a loop card the user has been shown. Doubles as the archive and as the
 * spaced-repetition state (Leitner box + next due date).
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,          // "word:pragmatic" or "loop:rizz"
    val kind: String,                    // "word" | "loop"
    val type: String,                    // loop type key, or "word"
    val term: String,
    val meaning: String,
    val example: String,
    val extra: String,                   // word: "instead of"; loop: context
    val tone: String,
    val nsfw: Boolean,
    val firstSeen: String,               // ISO date
    val box: Int = 1,
    val due: String,                     // ISO date
    val lastReviewed: String = "",
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val body: String,
    val videoId: String? = null,
    val videoTitle: String? = null,
    val channel: String? = null,
    val thumbnail: String? = null,
    val videoUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val date: String,
    val instaOpens: Int = 0,
    val detours: Int = 0,                // times "watch this instead" won
    val completed: Boolean = false,
    val instaMinutes: Int = -1,          // saved daily; Android only keeps ~a week of usage history
)

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(cards: List<CardEntity>)

    @Upsert
    suspend fun save(card: CardEntity)

    @Query("SELECT * FROM cards WHERE id IN (:ids)")
    fun byIds(ids: List<String>): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE kind = 'loop' ORDER BY firstSeen DESC, term")
    fun loopArchive(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE due <= :today AND lastReviewed != :today ORDER BY box, due LIMIT 20")
    fun due(today: String): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards WHERE due <= :today AND lastReviewed != :today")
    fun dueCount(today: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards")
    fun total(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE kind = :kind AND firstSeen BETWEEN :from AND :to")
    suspend fun seenBetween(kind: String, from: String, to: String): Int

    @Query("SELECT COUNT(*) FROM cards WHERE lastReviewed BETWEEN :from AND :to")
    suspend fun reviewedBetween(from: String, to: String): Int

    @Query("SELECT COUNT(*) FROM cards WHERE box >= 3")
    suspend fun mastered(): Int
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun all(): Flow<List<NoteEntity>>

    @Upsert
    suspend fun save(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE createdAt BETWEEN :from AND :to")
    suspend fun countBetween(from: Long, to: Long): Int
}

@Dao
interface DayDao {
    @Query("SELECT * FROM days WHERE date = :date")
    suspend fun get(date: String): DayEntity?

    @Query("SELECT * FROM days WHERE date = :date")
    fun watch(date: String): Flow<DayEntity?>

    @Query("SELECT * FROM days ORDER BY date DESC LIMIT 400")
    fun recent(): Flow<List<DayEntity>>

    @Query("SELECT * FROM days WHERE date BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<DayEntity>

    @Upsert
    suspend fun save(day: DayEntity)
}

@Database(entities = [CardEntity::class, NoteEntity::class, DayEntity::class], version = 2, exportSchema = false)
abstract class DetourDb : RoomDatabase() {
    abstract fun cards(): CardDao
    abstract fun notes(): NoteDao
    abstract fun days(): DayDao

    companion object {
        fun create(context: Context): DetourDb =
            Room.databaseBuilder(context, DetourDb::class.java, "detour.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN instaMinutes INTEGER NOT NULL DEFAULT -1")
            }
        }
    }
}

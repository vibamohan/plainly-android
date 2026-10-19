package org.plainly.app.history

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "document_history")
data class HistoryEntry(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val sourceUri: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM document_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("DELETE FROM document_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM document_history")
    suspend fun deleteAll()
}

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = false)
abstract class PlainlyDatabase : androidx.room.RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    companion object {
        @Volatile private var instance: PlainlyDatabase? = null
        fun get(context: Context): PlainlyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PlainlyDatabase::class.java, "plainly.db").build().also { instance = it }
        }
    }
}

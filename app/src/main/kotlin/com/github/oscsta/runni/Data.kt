package com.github.oscsta.runni

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "run_instances")
data class RunItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
)

@Dao
interface RunItemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: Item)

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * from items WHERE id = :id")
    fun getItem(id: Int): Flow<RunItem>

    @Query("SELECT * from items ORDER BY name ASC")
    fun getAllItems(): Flow<List<RunItem>>
}

@Database(entities = [RunItem::class], version = 1, exportSchema = false)
abstract class RunItemDatabase : RoomDatabase() {

    abstract fun runItemDao(): RunItemDao

    companion object {
        @Volatile
        private var Instance: RunItemDatabase? = null

        fun getDatabase(context: Context): RunItemDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, RunItemDatabase::class.java, "run_item_database")
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

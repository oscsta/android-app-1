package com.github.oscsta.runni

import android.content.Context

import kotlin.concurrent.Volatile

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import kotlinx.coroutines.flow.Flow


// In this case an "activity" is an activity of running/jogging/walking
@Entity(tableName = "run_table")
data class TrackedActivity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
)

@Dao
interface TrackedActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: TrackedActivity)

    @Update
    suspend fun update(item: TrackedActivity)

    @Delete
    suspend fun delete(item: TrackedActivity)

    @Query("SELECT * from run_table WHERE id = :id")
    fun getItem(id: Int): Flow<TrackedActivity>

    @Query("SELECT * from run_table ORDER BY id ASC")
    fun getAllItems(): Flow<List<TrackedActivity>>
}

@Database(entities = [TrackedActivity::class], version = 1, exportSchema = false)
abstract class TrackedActivityDatabase : RoomDatabase() {

    abstract fun TrackedActivityDao(): TrackedActivityDao

    companion object {
        @Volatile
        private var Instance: TrackedActivityDatabase? = null

        fun getDatabase(context: Context): TrackedActivityDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, TrackedActivityDatabase::class.java, "run_item_database")
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

class TrackedActivityRepository(
    private val dao: TrackedActivityDao
) {
    suspend fun insertActivity(activity: TrackedActivity) = dao.insert(activity)
    suspend fun updateActivity(activity: TrackedActivity) = dao.update(activity)
    suspend fun deleteActivity(activity: TrackedActivity) = dao.delete(activity)
    fun getAllActivitiesAsFlow(): Flow<List<TrackedActivity>> = dao.getAllItems()
}

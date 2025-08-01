package com.github.oscsta.runni

import android.content.Context
import android.location.Location
import androidx.room.*

import kotlin.concurrent.Volatile

import kotlinx.coroutines.flow.Flow


// In this case an "activity" is an activity of running/jogging/walking
@Entity(tableName = "activities", indices = [Index(value = ["timestamp"])])
data class TrackedActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
)

@Entity(
    tableName = "locations",
    indices = [Index(value = ["timestamp"])],
    foreignKeys = [ForeignKey(
        entity = TrackedActivityEntity::class,
        parentColumns = ["id"],
        childColumns = ["parent_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "parent_id", index = true)
    val parentId: Int,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val bearing: Float,
    val speed: Float,
) {
    companion object {
        fun fromGooglePlayServiceLocation(location: Location, parentId: Int): LocationEntity =
            with(location) {
                LocationEntity(
                    parentId = parentId,
                    timestamp = time,
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude,
                    bearing = bearing,
                    speed = speed
                )
            }

    }
}

@Dao
interface TrackedActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: TrackedActivityEntity): Long

    @Update
    suspend fun update(item: TrackedActivityEntity)

    @Delete
    suspend fun delete(item: TrackedActivityEntity)

    @Query("SELECT * from activities WHERE id = :id")
    fun getItem(id: Long): Flow<TrackedActivityEntity>

    @Query("SELECT * from activities ORDER BY id ASC")
    fun getAllItems(): Flow<List<TrackedActivityEntity>>

//    fun getActivityWithLocation(id: Long)
//
//    fun getAllActivitiesWithLocation()
}

@Dao
interface LocationEntityDao {
    @Insert
    suspend fun insert(item: LocationEntity)

    @Update
    suspend fun update(item: LocationEntity)

    @Delete
    suspend fun delete(item: LocationEntity)
}

@Database(entities = [TrackedActivityEntity::class, LocationEntity::class], version = 1)
abstract class TrackedActivityDatabase : RoomDatabase() {

    abstract fun trackedActivityDao(): TrackedActivityDao
    abstract fun locationEntityDao(): LocationEntityDao

    companion object {
        @Volatile
        private var Instance: TrackedActivityDatabase? = null

        fun getDatabase(context: Context): TrackedActivityDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, TrackedActivityDatabase::class.java, "run_database")
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

//class TrackedActivityRepository(
//    private val dao: TrackedActivityDao
//) {
//    suspend fun insertActivity(activity: TrackedActivityEntity) = dao.insert(activity)
//    suspend fun updateActivity(activity: TrackedActivityEntity) = dao.update(activity)
//    suspend fun deleteActivity(activity: TrackedActivityEntity) = dao.delete(activity)
//    fun getAllActivitiesAsFlow(): Flow<List<TrackedActivityEntity>> = dao.getAllItems()
//}

package com.github.oscsta.runni

import android.content.Context
import android.location.Location
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.concurrent.Volatile


// In this case an "activity" is an activity of running/jogging/walking
@Entity(tableName = "activities", indices = [Index(value = ["start_timestamp"])])
data class TrackedActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "start_timestamp")
    val startTimestamp: Long,
    @ColumnInfo(name = "end_timestamp")
    val endTimestamp: Long? = null,
    @ColumnInfo(name = "total_distance")
    val totalDistanceInMeters: Float? = null,
    @ColumnInfo(name = "average_speed")
    val averageSpeedInMetersPerSecond: Float? = null
)

@Entity(
    tableName = "locations",
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
    val parentId: Long,
    @ColumnInfo(index = true)
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val bearing: Float,
    val speed: Float,
) {
    companion object {
        fun fromGooglePlayServiceLocation(location: Location, parentId: Long): LocationEntity =
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

    @Query("UPDATE activities SET end_timestamp = :end WHERE id = :id")
    suspend fun updateEndTimestampById(id: Long, end: Long)

    @Query("UPDATE activities SET total_distance = :totalDistance, average_speed = :avgSpeed WHERE id = :id")
    suspend fun updateStatsById(id: Long, totalDistance: Float, avgSpeed: Float)

    @Query("SELECT * FROM activities WHERE id = :id")
    fun getItem(id: Long): Flow<TrackedActivityEntity>

    @Query("SELECT * FROM activities ORDER BY id DESC")
    fun getAllItemsByMostRecent(): Flow<List<TrackedActivityEntity>>

    @Query("SELECT start_timestamp FROM activities ORDER BY id DESC LIMIT 1")
    fun getMostRecentStartTime(): Flow<Long>

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

    @Query("SELECT * from locations WHERE parent_id = :parentId ORDER BY id ASC")
    suspend fun getAllWithParentId(parentId: Long): List<LocationEntity>
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

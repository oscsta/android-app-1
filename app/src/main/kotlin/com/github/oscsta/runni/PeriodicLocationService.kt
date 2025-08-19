package com.github.oscsta.runni

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch

private const val CURRENTLY_TRACKING_NOTIFICATION_ID: Int = 1
private const val CURRENTLY_RUNNING_NOTIFICATION_CHANNEL_ID = "LOCATION_TRACKING_CURRENTLY_RUNNING"
private const val CURRENTLY_RUNNING_NOTIFICATION_CHANNEL_NAME =
    "Active location tracking" // Human readable name

class PeriodicLocationService : LifecycleService() {
    private val fusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val db by lazy {
        TrackedActivityDatabase.getDatabase(applicationContext)
    }
    private var activeRowId: Long? = null
    private lateinit var currentlyRunningNotification: Notification
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    companion object {
        @Suppress("unused")
        private val TAG = PeriodicLocationService::class.java.simpleName
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun setupNotificationPrerequisites() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CURRENTLY_RUNNING_NOTIFICATION_CHANNEL_ID,
            CURRENTLY_RUNNING_NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Show notification while your exact location is actively being tracked"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildCurrentlyRunningNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        currentlyRunningNotification =
            Notification.Builder(this, CURRENTLY_RUNNING_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Test Title")
                .setContentText("Test Content")
                .setContentIntent(pendingIntent)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
    }

    private fun buildLocationRequest(intervalMillis: Long = 10000) {
        locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
    }

    private fun buildLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    lifecycleScope.launch {
                        val locationEntity =
                            LocationEntity.fromGooglePlayServiceLocation(location, activeRowId!!)
                        val locationDao = db.locationEntityDao()
                        locationDao.insert(locationEntity)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        buildLocationRequest()
        buildLocationCallback()
        setupNotificationPrerequisites()
        buildCurrentlyRunningNotification()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeRowId = intent!!.getLongExtra("ACTIVE_ID", 0L)
        Log.d("PeriodicLocationService", "Consumed intent [$intent] to receive row id $activeRowId")
        ServiceCompat.startForeground(
            this,
            CURRENTLY_TRACKING_NOTIFICATION_ID,
            currentlyRunningNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest, Dispatchers.IO.asExecutor(), locationCallback
        )
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)

        val statsWorkRequest =
            OneTimeWorkRequestBuilder<TrackedActivityStatsWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf("ID" to activeRowId))
                .build()
        WorkManager.getInstance(this).enqueue(statsWorkRequest)
    }
}

class TrackedActivityStatsWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(
        context, workerParams
    ) {
    private val db = TrackedActivityDatabase.getDatabase(context)
    private val activityDao = db.trackedActivityDao()
    private val locationDao = db.locationEntityDao()

    override suspend fun doWork(): Result {
        val activityId = inputData.getLong("ID", -1L)
        if (activityId == -1L) return Result.failure()

        val locations = locationDao.getAllWithParentId(activityId)
        val endTimestamp = System.currentTimeMillis()
        activityDao.updateEndTimestampById(activityId, endTimestamp)

        val out = FloatArray(1)
        val summedDistance =
            // Remove locations with a very low speed as the user was probably stationary at that point. Also slightly prevents GPS drift inaccuracies.
            locations.filter { item -> item.speed > 0.1f }.windowed(2, 1).sumOf { (first, second) ->
                Location.distanceBetween(
                    first.latitude, first.longitude, second.latitude, second.longitude, out
                )
                out[0].toDouble() // There is no sumOf overload that returns Floats, only Doubles. Wtf? Precision reasons?
            }.toFloat()
        val avgSpeed = locations.map { it.speed }
            .average()
            .toFloat() // .average() returns Double cause precision reasons
        activityDao.updateStatsById(activityId, summedDistance, avgSpeed)

        return Result.success()
    }
}
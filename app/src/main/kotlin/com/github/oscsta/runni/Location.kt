package com.github.oscsta.runni

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch

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

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun setupNotificationPrerequisites() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CURRENTLY_RUNNING_NOTIFICATION_CHANNEL_ID,
            CURRENTLY_RUNNING_NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Show notification while your exact location is actively being tracked" }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildLocationRequest(intervalMillis: Long = 10000) {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
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
        ServiceCompat.startForeground(
            this,
            CURRENTLY_TRACKING_NOTIFICATION_ID,
            currentlyRunningNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            Dispatchers.IO.asExecutor(),
            locationCallback
        )
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    companion object {
        @Suppress("unused")
        private val TAG = PeriodicLocationService::class.java.simpleName
    }
}

private const val CURRENTLY_TRACKING_NOTIFICATION_ID: Int = 1
private const val CURRENTLY_RUNNING_NOTIFICATION_CHANNEL_ID = "LOCATION_TRACKING_CURRENTLY_RUNNING"
private const val CURRENTLY_RUNNING_NOTIFICATION_CHANNEL_NAME = "Foreground service notification"
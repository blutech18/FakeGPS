package com.nodare.geosec

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nodare.geosec.services.worker.GpsSyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NodareGeoSecApp : Application(), Configuration.Provider {

    companion object {
        const val CHANNEL_GPS_TRACKING = "gps_tracking_channel"
        const val CHANNEL_SECURITY_ALERTS = "security_alerts_channel"
        const val CHANNEL_DISPATCH = "dispatch_channel"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleGpsSyncWorker()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val gpsChannel = NotificationChannel(
                CHANNEL_GPS_TRACKING,
                "GPS Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active GPS tracking session"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_SECURITY_ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fake GPS and route deviation alerts"
            }

            val dispatchChannel = NotificationChannel(
                CHANNEL_DISPATCH,
                "Dispatch Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Dispatch session notifications"
            }

            manager.createNotificationChannels(
                listOf(gpsChannel, alertChannel, dispatchChannel)
            )
        }
    }

    private fun scheduleGpsSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<GpsSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            GpsSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}

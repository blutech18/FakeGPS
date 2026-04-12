package com.nodare.geosec.services.gps

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nodare.geosec.NodareGeoSecApp
import com.nodare.geosec.R
import com.nodare.geosec.data.repository.DispatchRepository
import com.nodare.geosec.data.repository.GpsLogRepository
import com.nodare.geosec.data.repository.SecurityAlertRepository
import com.nodare.geosec.presentation.dashboard.MainActivity
import com.nodare.geosec.services.detection.FakeGpsDetector
import com.nodare.geosec.services.detection.RouteDeviationDetector
import com.nodare.geosec.data.repository.RouteRepository
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GpsTrackingService : Service() {

    companion object {
        const val TAG = "GpsTrackingService"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"

        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    @Inject lateinit var gpsLogRepository: GpsLogRepository
    @Inject lateinit var securityAlertRepository: SecurityAlertRepository
    @Inject lateinit var dispatchRepository: DispatchRepository
    @Inject lateinit var routeRepository: RouteRepository
    @Inject lateinit var fakeGpsDetector: FakeGpsDetector
    @Inject lateinit var routeDeviationDetector: RouteDeviationDetector

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var userId: String = ""
    private var userName: String = ""
    private var sessionId: String = ""
    private var cachedPolyline: String = ""

    // Cooldown tracking: alertType -> last alert timestamp
    private val alertCooldowns = mutableMapOf<String, Long>()
    private val ALERT_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes per alert type

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                userId = intent.getStringExtra(EXTRA_USER_ID) ?: ""
                userName = intent.getStringExtra(EXTRA_USER_NAME) ?: ""
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                alertCooldowns.clear()

                try {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startLocationUpdates()
                    loadRoutePolyline()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Missing permissions to start foreground service", e)
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NodareGeoSecApp.CHANNEL_GPS_TRACKING)
            .setContentTitle("Dispatch Active")
            .setContentText("GPS tracking is running...")
            .setSmallIcon(R.drawable.ic_tracking)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fakeGpsDetector.reset()

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Constants.GPS_UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(Constants.GPS_FASTEST_INTERVAL_MS)
            .setMaxUpdateDelayMillis(Constants.GPS_MAX_WAIT_TIME_MS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    processLocation(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun isAlertOnCooldown(alertType: String): Boolean {
        val lastTime = alertCooldowns[alertType] ?: return false
        return (System.currentTimeMillis() - lastTime) < ALERT_COOLDOWN_MS
    }

    private fun recordAlertTime(alertType: String) {
        alertCooldowns[alertType] = System.currentTimeMillis()
    }

    private fun processLocation(location: Location) {
        serviceScope.launch {
            // Run fake GPS detection
            val spoofResults = fakeGpsDetector.analyze(location)

            for (result in spoofResults) {
                // Skip if this alert type is on cooldown
                if (isAlertOnCooldown(result.detectionType)) {
                    Log.d(TAG, "Skipping alert (cooldown): ${result.detectionType}")
                    continue
                }

                Log.w(TAG, "Spoof detected: ${result.detectionType} - ${result.description}")
                recordAlertTime(result.detectionType)

                // Create security alert
                securityAlertRepository.createAlert(
                    alertType = result.detectionType,
                    userId = userId,
                    userName = userName,
                    dispatchSessionId = sessionId,
                    description = result.description,
                    severity = result.severity,
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                // Mark session as suspicious
                if (result.severity == Constants.SEVERITY_CRITICAL) {
                    dispatchRepository.markSuspicious(sessionId)
                }
            }

            // Check route deviation
            if (cachedPolyline.isNotBlank() && !isAlertOnCooldown(Constants.ALERT_ROUTE_DEVIATION)) {
                val deviating = routeDeviationDetector.isDeviating(
                    location.latitude, location.longitude, cachedPolyline
                )
                if (deviating) {
                    val distance = routeDeviationDetector.getDeviationDistance(
                        location.latitude, location.longitude, cachedPolyline
                    )
                    recordAlertTime(Constants.ALERT_ROUTE_DEVIATION)
                    securityAlertRepository.createAlert(
                        alertType = Constants.ALERT_ROUTE_DEVIATION,
                        userId = userId,
                        userName = userName,
                        dispatchSessionId = sessionId,
                        description = "Route deviation: %.0f meters from expected route".format(distance),
                        severity = Constants.SEVERITY_HIGH,
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
            }

            // Save GPS log
            val isOnline = DeviceUtils.isNetworkAvailable(this@GpsTrackingService)
            if (isOnline) {
                gpsLogRepository.saveGpsLog(
                    userId = userId,
                    dispatchSessionId = sessionId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    speed = location.speed
                )
                // Also try syncing any pending offline logs
                gpsLogRepository.syncPendingLogs()
            } else {
                // Store locally for later sync
                gpsLogRepository.saveLocalGpsLog(
                    userId = userId,
                    dispatchSessionId = sessionId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    speed = location.speed
                )
            }
        }
    }

    private fun loadRoutePolyline() {
        serviceScope.launch {
            val route = routeRepository.getRouteForSession(sessionId)
            cachedPolyline = route?.expectedPolyline ?: ""
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    override fun onDestroy() {
        stopLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }
}

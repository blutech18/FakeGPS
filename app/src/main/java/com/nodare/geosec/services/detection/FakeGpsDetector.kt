package com.nodare.geosec.services.detection

import android.content.Context
import android.location.Location
import android.os.Build
import android.provider.Settings
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.LocationUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SpoofDetectionResult(
    val isSpoofed: Boolean,
    val detectionType: String,
    val description: String,
    val severity: String
)

@Singleton
class FakeGpsDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var lastLocation: Location? = null
    private var lastTimestamp: Long = 0L
    private val recentAccuracies = mutableListOf<Float>()

    fun analyze(location: Location): List<SpoofDetectionResult> {
        val results = mutableListOf<SpoofDetectionResult>()

        // Layer 1: Mock Provider Detection
        checkMockProvider(location)?.let { results.add(it) }

        // Layer 2: Developer Options / Mock Location Setting
        checkDeveloperOptions()?.let { results.add(it) }

        // Layer 3: Spoofing App Detection
        checkSpoofingApps()?.let { results.add(it) }

        // Layer 4: Movement Logic Detection
        checkMovementLogic(location)?.let { results.add(it) }

        // Layer 5: GPS Accuracy Anomaly Detection
        checkAccuracyAnomaly(location)?.let { results.add(it) }

        // Update state for next check
        lastLocation = location
        lastTimestamp = System.currentTimeMillis()

        return results
    }

    /**
     * Layer 1: Detect if location comes from a mock provider.
     */
    private fun checkMockProvider(location: Location): SpoofDetectionResult? {
        val isMocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }

        return if (isMocked) {
            SpoofDetectionResult(
                isSpoofed = true,
                detectionType = Constants.ALERT_MOCK_PROVIDER,
                description = "Location is from a mock provider",
                severity = Constants.SEVERITY_CRITICAL
            )
        } else null
    }

    /**
     * Layer 2: Detect if developer options / mock locations are enabled.
     */
    private fun checkDeveloperOptions(): SpoofDetectionResult? {
        val mockLocationEnabled = try {
            @Suppress("DEPRECATION")
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION
            ) != "0"
        } catch (e: Exception) {
            false
        }

        val devOptionsEnabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) != 0

        return if (mockLocationEnabled && devOptionsEnabled) {
            SpoofDetectionResult(
                isSpoofed = true,
                detectionType = Constants.ALERT_DEV_OPTIONS,
                description = "Developer options with mock location enabled",
                severity = Constants.SEVERITY_HIGH
            )
        } else null
    }

    /**
     * Layer 3: Scan for known GPS spoofing applications.
     */
    private fun checkSpoofingApps(): SpoofDetectionResult? {
        val packageManager = context.packageManager
        val installedPackages = try {
            packageManager.getInstalledApplications(0)
        } catch (e: Exception) {
            emptyList()
        }

        val foundApps = installedPackages.filter { appInfo ->
            appInfo.packageName in Constants.KNOWN_SPOOFING_PACKAGES
        }

        return if (foundApps.isNotEmpty()) {
            val appNames = foundApps.joinToString(", ") { it.packageName }
            SpoofDetectionResult(
                isSpoofed = true,
                detectionType = Constants.ALERT_SPOOF_APP,
                description = "Spoofing apps detected: $appNames",
                severity = Constants.SEVERITY_CRITICAL
            )
        } else null
    }

    /**
     * Layer 4: Detect impossible movement (teleportation, impossible speed).
     */
    private fun checkMovementLogic(location: Location): SpoofDetectionResult? {
        val prevLocation = lastLocation ?: return null
        val prevTime = lastTimestamp
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - prevTime

        if (timeDiff <= 0) return null

        val distance = LocationUtils.calculateDistance(
            prevLocation.latitude, prevLocation.longitude,
            location.latitude, location.longitude
        )

        val speedKmh = LocationUtils.calculateSpeedKmh(distance, timeDiff)

        // Check for impossible speed
        if (speedKmh > Constants.MAX_SPEED_KMH) {
            return SpoofDetectionResult(
                isSpoofed = true,
                detectionType = Constants.ALERT_TELEPORT,
                description = "Impossible speed detected: %.1f km/h".format(speedKmh),
                severity = Constants.SEVERITY_CRITICAL
            )
        }

        // Check for teleportation (large distance in short time)
        if (distance > Constants.MAX_DISTANCE_JUMP_METERS &&
            timeDiff < Constants.TELEPORT_TIME_THRESHOLD_MS
        ) {
            return SpoofDetectionResult(
                isSpoofed = true,
                detectionType = Constants.ALERT_TELEPORT,
                description = "Teleport detected: %.0f meters in %.1f seconds".format(
                    distance, timeDiff / 1000.0
                ),
                severity = Constants.SEVERITY_CRITICAL
            )
        }

        return null
    }

    /**
     * Layer 5: Detect abnormal GPS accuracy fluctuations.
     */
    private fun checkAccuracyAnomaly(location: Location): SpoofDetectionResult? {
        recentAccuracies.add(location.accuracy)
        if (recentAccuracies.size > 10) {
            recentAccuracies.removeAt(0)
        }

        if (recentAccuracies.size < 5) return null

        val avg = recentAccuracies.average()
        val variance = recentAccuracies.map { (it - avg) * (it - avg) }.average()
        val stdDev = kotlin.math.sqrt(variance)

        // Abnormally consistent accuracy (real GPS has natural variance)
        // or a sudden spike in accuracy value
        if (stdDev < 0.01 && recentAccuracies.size >= 10) {
            return SpoofDetectionResult(
                isSpoofed = true,
                detectionType = Constants.ALERT_ACCURACY_ANOMALY,
                description = "Unnaturally consistent GPS accuracy: stddev=%.4f".format(stdDev),
                severity = Constants.SEVERITY_MEDIUM
            )
        }

        if (location.accuracy > Constants.GPS_ACCURACY_ANOMALY_THRESHOLD) {
            return SpoofDetectionResult(
                isSpoofed = true,
                detectionType = Constants.ALERT_ACCURACY_ANOMALY,
                description = "GPS accuracy anomaly: %.1f meters".format(location.accuracy),
                severity = Constants.SEVERITY_MEDIUM
            )
        }

        return null
    }

    fun reset() {
        lastLocation = null
        lastTimestamp = 0L
        recentAccuracies.clear()
    }
}

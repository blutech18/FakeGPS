package com.nodare.geosec.services.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nodare.geosec.data.repository.GpsLogRepository
import com.nodare.geosec.util.Resource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class GpsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gpsLogRepository: GpsLogRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "GpsSyncWorker"
        const val WORK_NAME = "gps_sync_work"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting GPS log sync...")
        return when (val result = gpsLogRepository.syncPendingLogs()) {
            is Resource.Success -> {
                Log.d(TAG, "Synced ${result.data} GPS logs")
                Result.success()
            }
            is Resource.Error -> {
                Log.e(TAG, "Sync failed: ${result.message}")
                Result.retry()
            }
            is Resource.Loading -> Result.success()
        }
    }
}

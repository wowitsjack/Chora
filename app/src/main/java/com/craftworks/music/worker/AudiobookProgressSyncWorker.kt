package com.craftworks.music.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.craftworks.music.data.repository.AudiobookProgressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class AudiobookProgressSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val progressRepository: AudiobookProgressRepository
) : CoroutineWorker(context, workerParams) {
    companion object {
        const val WORK_NAME = "audiobook_progress_sync"
    }

    override suspend fun doWork(): Result = try {
        if (progressRepository.flushPending()) Result.success() else Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }
}

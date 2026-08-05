package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftworks.music.data.repository.SyncRepository
import com.craftworks.music.data.repository.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncIndicatorViewModel @Inject constructor(
    private val syncRepository: SyncRepository
) : ViewModel() {
    val isSyncing: StateFlow<Boolean> = syncRepository.isSyncing
    val isPaused: StateFlow<Boolean> = syncRepository.isPaused
    val syncState: StateFlow<SyncState> = syncRepository.syncState

    fun startSync() {
        viewModelScope.launch {
            try {
                syncRepository.syncAll(forceRefresh = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }
        }
    }

    fun cancelSync() {
        syncRepository.cancelSync()
    }
}

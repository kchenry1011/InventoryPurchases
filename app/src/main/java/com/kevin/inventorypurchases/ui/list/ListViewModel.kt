package com.kevin.inventorypurchases.ui.list

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.kevin.inventorypurchases.App
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.kevin.inventorypurchases.util.LocationHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
class ListViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo get() = (app as App).repo
    val items = repo.streamAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _isSharing = MutableStateFlow(false)
    val isSharing = _isSharing.asStateFlow()
    private val shareZipChannel = Channel<Uri>(capacity = Channel.BUFFERED)
    val shareZip = shareZipChannel.receiveAsFlow()
    // region === Cache clear event system ===
    sealed interface UiEvent {
        data class CacheCleared(val bytesFreed: Long, val entriesRemoved: Int) : UiEvent
        data class WipeCompleted(val recordsDeleted: Int, val photosDeleted: Int, val photosFailed: Int) : UiEvent
    }

    private val _events = MutableSharedFlow<UiEvent?>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // endregion
    fun shareCsvAndPhotos() {
        viewModelScope.launch {
            _isSharing.value = true
            try {
                val uri = repo.exportCsvAndPhotosZip()
                shareZipChannel.send(uri)
            } finally {
                _isSharing.value = false
            }
        }
    }
    // under your existing fields
    data class CacheClearedUi(val bytesFreed: Long, val entriesRemoved: Int)

    private val cacheClearedChannel = Channel<CacheClearedUi>(capacity = Channel.BUFFERED)
    val cacheCleared = cacheClearedChannel.receiveAsFlow()

    fun wipeAllData() {
        viewModelScope.launch {
            val r = repo.wipeAllPurchasesAndPhotos()
            _events.tryEmit(UiEvent.WipeCompleted(r.recordsDeleted, r.photosDeleted, r.photosFailed))
        }
    }
    fun clearExportCache() {
        viewModelScope.launch {
            val r = repo.clearExportCache()
            cacheClearedChannel.send(CacheClearedUi(r.bytesFreed, r.entriesRemoved))
        }
    }
    fun deleteOne(id: String) {
        viewModelScope.launch { repo.deletePurchaseById(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repo.deleteAllPurchases() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as Application
                return ListViewModel(app) as T
            }
        }
    }
}

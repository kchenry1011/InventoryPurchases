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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
class ListViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo get() = (app as App).repo
    val items = repo.streamAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _isSharing = MutableStateFlow(false)
    val isSharing = _isSharing.asStateFlow()
    private val shareZipChannel = Channel<Uri>(capacity = Channel.BUFFERED)
    val shareZip = shareZipChannel.receiveAsFlow()

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

package com.kevin.inventorypurchases.ui.list

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevin.inventorypurchases.App
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ListViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo get() = (app as App).repo
    val items = repo.streamAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val shareCsvChannel = Channel<Uri>(capacity = Channel.BUFFERED)
    val shareCsv = shareCsvChannel.receiveAsFlow()

    fun shareCsv() {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { repo.exportCsv() }
            shareCsvChannel.send(uri) // your existing one–shot event
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

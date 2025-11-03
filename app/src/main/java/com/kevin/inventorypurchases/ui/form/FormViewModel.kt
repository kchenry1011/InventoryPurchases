package com.kevin.inventorypurchases.ui.form
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevin.inventorypurchases.App
import com.kevin.inventorypurchases.data.db.Purchase
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY

sealed interface FormIntent {
    data class SetPhoto(val uri: Uri?) : FormIntent
    data class SetDescription(val v: String) : FormIntent
    data class SetPriceText(val v: String) : FormIntent
    data class SetQuantityText(val v: String) : FormIntent
    data class SetDateMillis(val v: Long) : FormIntent
    data object Save : FormIntent
    data object SaveAndNext : FormIntent
    data object ClearError : FormIntent
    data class AddPhoto(val uri: Uri) : FormIntent
    data class RemovePhotoAt(val index: Int) : FormIntent
    data class SetNotes(val v: String) : FormIntent  // <-- NEW
    object ClearPhotos : FormIntent
}


class FormViewModel(private val app: Application) : AndroidViewModel(app) {

    var state = androidx.compose.runtime.mutableStateOf(FormState())
        private set

    private val repo get() = (app as App).repo

    init {
        // If dateMillis is not set yet, default it to today's local midnight
        val s = state.value
        if (s.dateMillis == null) {
            state.value = s.copy(dateMillis = startOfTodayCompat())
        }
    }
    fun onIntent(i: FormIntent) {
        when (i) {
            // Map the legacy SetPhoto to the new list (0 or 1 item)
            is FormIntent.SetPhoto -> {
                val current = state.value
                val imported = i.uri?.let { com.kevin.inventorypurchases.util.PhotoStamp.importAndStamp(app, it) }
                state.value = current.copy(photoUris = imported?.let { listOf(it) } ?: emptyList())
            }
            is FormIntent.SetNotes -> {
                state.value = state.value.copy(notes = i.v)
            }
            // New multi-photo actions
            is FormIntent.AddPhoto -> {
                val localUri = com.kevin.inventorypurchases.util.PhotoStamp.importAndStamp(app, i.uri)
                val current = state.value
                if (!current.photoUris.contains(i.uri)) {
                    state.value = current.copy(photoUris = current.photoUris + localUri)
                }
            }
            is FormIntent.RemovePhotoAt -> {
                val s = state.value
                if (i.index in s.photoUris.indices) {
                    val newList = s.photoUris.toMutableList().apply { removeAt(i.index) }
                    state.value = s.copy(photoUris = newList)
                }
            }
            FormIntent.ClearPhotos -> {
                state.value = state.value.copy(photoUris = emptyList())
            }

            // Everything else unchanged
            is FormIntent.SetDescription -> state.value = state.value.copy(description = i.v)
            is FormIntent.SetPriceText -> state.value = state.value.copy(priceText = i.v)
            is FormIntent.SetQuantityText -> state.value = state.value.copy(quantityText = i.v)
            is FormIntent.SetDateMillis -> state.value = state.value.copy(dateMillis = i.v)
            FormIntent.ClearError -> state.value = state.value.copy(error = null)
            FormIntent.Save -> save(clearAfter = false)
            FormIntent.SaveAndNext -> save(clearAfter = true)
        }
    }
    private fun startOfTodayCompat(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    private fun save(clearAfter: Boolean) = viewModelScope.launch {
        val s = state.value
        val cents = parseCents(s.priceText) ?: return@launch fail("Enter a valid price")
        val qty = s.quantityText.toIntOrNull()?.takeIf { it >= 1 } ?: return@launch fail("Quantity must be ≥ 1")
        val desc = s.description.trim().ifEmpty { return@launch fail("Description required") }

        state.value = s.copy(isSaving = true, error = null)

        val photoListString = s.photoUris.toString()

        val p = Purchase(
            photoUri = photoListString,
            description = desc,
            priceCents = cents,
            quantity = qty,
            purchaseDateEpoch = s.dateMillis,
            notes = s.notes
        )
        repo.add(p)

        state.value =
            if (clearAfter) s.copy(photoUris = emptyList(), description = "", priceText = "", isSaving = false, error = null)
            else s.copy(isSaving = false)
    }

    private fun fail(msg: String) {
        state.value = state.value.copy(error = msg)
    }

    private fun parseCents(text: String): Long? {
        val cleaned = text.trim().replace("$", "").replace(",", "")
        val v = cleaned.toDoubleOrNull() ?: return null
        return (v * 100.0).roundToLong()
    }

    companion object {
        // Use CreationExtras to get Application without touching Compose APIs
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as Application
                return FormViewModel(app) as T
            }
        }
    }

}

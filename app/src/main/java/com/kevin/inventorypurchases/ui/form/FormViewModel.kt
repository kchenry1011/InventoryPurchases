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
    data object ClearPhoto : FormIntent
    data object ClearError : FormIntent
}


class FormViewModel(private val app: Application) : AndroidViewModel(app) {

    var state = androidx.compose.runtime.mutableStateOf(FormState())
        private set

    private val repo get() = (app as App).repo

    fun onIntent(i: FormIntent) {
        when (i) {
            is FormIntent.SetPhoto -> state.value = state.value.copy(photoUri = i.uri)
            is FormIntent.SetDescription -> state.value = state.value.copy(description = i.v)
            is FormIntent.SetPriceText -> state.value = state.value.copy(priceText = i.v)
            is FormIntent.SetQuantityText -> state.value = state.value.copy(quantityText = i.v)
            is FormIntent.SetDateMillis -> state.value = state.value.copy(dateMillis = i.v)
            FormIntent.ClearPhoto -> state.value = state.value.copy(photoUri = null)
            FormIntent.ClearError -> state.value = state.value.copy(error = null)
            FormIntent.Save -> save(clearAfter = false)
            FormIntent.SaveAndNext -> save(clearAfter = true)
        }
    }

    private fun save(clearAfter: Boolean) = viewModelScope.launch {
        val s = state.value
        val cents = parseCents(s.priceText) ?: return@launch fail("Enter a valid price")
        val qty = s.quantityText.toIntOrNull()?.takeIf { it >= 1 } ?: return@launch fail("Quantity must be ≥ 1")
        val desc = s.description.trim().ifEmpty { return@launch fail("Description required") }

        state.value = s.copy(isSaving = true, error = null)

        val p = Purchase(
            photoUri = s.photoUri?.toString(),
            description = desc,
            priceCents = cents,
            quantity = qty,
            purchaseDateEpoch = s.dateMillis
        )
        repo.add(p)

        state.value =
            if (clearAfter) s.copy(photoUri = null, description = "", priceText = "", isSaving = false, error = null)
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

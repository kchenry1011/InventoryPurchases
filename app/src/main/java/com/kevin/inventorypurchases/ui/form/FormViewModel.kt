package com.kevin.inventorypurchases.ui.form
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevin.inventorypurchases.App
import com.kevin.inventorypurchases.data.db.Purchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    data class SetNotes(val v: String) : FormIntent
    object ClearPhotos : FormIntent

    // Grouping
    data class StartGroup(val name: String) : FormIntent
    object EndGroup : FormIntent
}

class FormViewModel(private val app: Application) : AndroidViewModel(app) {

    var state = androidx.compose.runtime.mutableStateOf(FormState())
        private set

    private val repo get() = (app as App).repo
    private val session get() = (app as App).groupSession

    private val _activeGroupName = MutableStateFlow<String?>(null)
    val activeGroupName: StateFlow<String?> = _activeGroupName

    init {
        viewModelScope.launch {
            session.activeGroupName.collectLatest { _activeGroupName.value = it }
        }
    }

    fun onIntent(i: FormIntent) {
        when (i) {
            is FormIntent.SetPhoto -> {
                // Restore import-and-stamp behavior for single-photo set
                if (i.uri == null) {
                    state.value = state.value.copy(photoUris = emptyList())
                } else {
                    viewModelScope.launch {
                        importAndStamp(i.uri)?.let { stamped ->
                            state.value = state.value.copy(photoUris = listOf(stamped))
                        }
                    }
                }
            }
            is FormIntent.AddPhoto -> {
                // Restore import-and-stamp behavior for each added photo
                viewModelScope.launch {
                    importAndStamp(i.uri)?.let { stamped ->
                        state.value = state.value.copy(photoUris = state.value.photoUris + stamped)
                    }
                }
            }
            is FormIntent.RemovePhotoAt -> {
                val list = state.value.photoUris.toMutableList()
                if (i.index in list.indices) list.removeAt(i.index)
                state.value = state.value.copy(photoUris = list)
            }
            is FormIntent.ClearPhotos -> {
                state.value = state.value.copy(photoUris = emptyList())
            }
            is FormIntent.SetDescription -> state.value = state.value.copy(description = i.v)
            is FormIntent.SetPriceText -> state.value = state.value.copy(priceText = i.v)
            is FormIntent.SetQuantityText -> state.value = state.value.copy(quantityText = i.v)
            is FormIntent.SetDateMillis -> state.value = state.value.copy(dateMillis = i.v)
            is FormIntent.SetNotes -> state.value = state.value.copy(notes = i.v)
            FormIntent.ClearError -> state.value = state.value.copy(error = null)
            FormIntent.Save -> save(clearAfter = false)
            FormIntent.SaveAndNext -> save(clearAfter = true)

            is FormIntent.StartGroup -> {
                viewModelScope.launch { session.setActiveGroup(i.name) }
            }
            FormIntent.EndGroup -> {
                viewModelScope.launch { session.clearActiveGroup() }
            }
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
            notes = s.notes,
            groupName = _activeGroupName.value
        )
        repo.add(p)

        state.value =
            if (clearAfter) s.copy(
                photoUris = emptyList(),
                description = "",
                priceText = "",
                quantityText = "1",
                notes = "",
                isSaving = false,
                error = null
            )
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

    // --- import & stamp so exporter can derive IMG_yyyyMMdd_HHmmss[_SSS].ext from EXIF ---
    private suspend fun importAndStamp(source: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            // Try to copy into app cache (photos) with a timestamped filename
            val photosDir = File(app.cacheDir, "photos").apply { mkdirs() }

            // Prefer EXIF timestamp from source; fallback to now
            val (fileStamp, subsec) = readSourceTimestampOrNow(source)
            val name = buildString {
                append("IMG_")
                append(fileStamp)
                if (subsec != null) {
                    append('_')
                    append(subsec.padStart(3, '0'))
                }
                append(".jpg")
            }

            val dest = File(photosDir, name)
            app.contentResolver.openInputStream(source)?.use { ins ->
                FileOutputStream(dest).use { out -> ins.copyTo(out, 8 * 1024) }
            } ?: return@withContext null

            // Ensure EXIF DateTimeOriginal exists on the copied file
            runCatching {
                val exif = ExifInterface(dest.absolutePath)
                val dto = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                if (dto.isNullOrBlank()) {
                    // Write DateTimeOriginal and SubSec if missing
                    val now = System.currentTimeMillis()
                    val dtoStr = exifDateFmt.format(Date(now)) // "yyyy:MM:dd HH:mm:ss"
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dtoStr)
                    if (subsec != null) exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subsec)
                    exif.saveAttributes()
                }
            }

            // Return a content:// uri via FileProvider so downstream opens via ContentResolver
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", dest)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readSourceTimestampOrNow(source: Uri): Pair<String, String?> {
        // Try reading EXIF DateTimeOriginal from the source; fallback to current time
        return try {
            app.contentResolver.openInputStream(source)?.use { ins ->
                val exif = ExifInterface(ins)
                val dto = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                if (!dto.isNullOrBlank()) {
                    // dto format: "yyyy:MM:dd HH:mm:ss"
                    val yyyy = dto.substring(0, 4)
                    val MM   = dto.substring(5, 7)
                    val dd   = dto.substring(8, 10)
                    val HH   = dto.substring(11, 13)
                    val mm   = dto.substring(14, 16)
                    val ss   = dto.substring(17, 19)
                    val sub  = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL)
                    "${yyyy}${MM}${dd}_${HH}${mm}${ss}" to sub
                } else {
                    val now = Date()
                    fileDateFmt.format(now) to null
                }
            } ?: (fileDateFmt.format(Date()) to null)
        } catch (_: Throwable) {
            fileDateFmt.format(Date()) to null
        }
    }

    companion object {
        private val fileDateFmt = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        private val exifDateFmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as Application
                return FormViewModel(app) as T
            }
        }
    }
}

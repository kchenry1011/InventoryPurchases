package com.kevin.inventorypurchases.ui.form

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import android.graphics.Bitmap
import android.content.ActivityNotFoundException
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(
    state: androidx.compose.runtime.State<FormState>,
    onIntent: (FormIntent) -> Unit,
    navigateToList: () -> Unit
) {
    val s = state.value
    var pendingRefocus by remember { mutableStateOf(false) }  // NEW
    val focusManager = LocalFocusManager.current
    val priceFocus = remember { FocusRequester() }
    val qtyFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }
    var qtyTextFieldValue by remember { mutableStateOf(TextFieldValue(s.quantityText)) }
    val qtyFocusRequester = qtyFocus
    val descriptionFocus = remember { FocusRequester() }   // NEW
    val qtyFocusState = remember { mutableStateOf(false) }
    LaunchedEffect(s.error) { /* placeholder for snackbars */ }
    LaunchedEffect(qtyFocusState.value) {
        if (qtyFocusState.value) {
            // Select the full range of text when gaining focus
            qtyTextFieldValue = qtyTextFieldValue.copy(
                selection = TextRange(0, qtyTextFieldValue.text.length)
            )
        }
    }
    LaunchedEffect(s.isSaving, pendingRefocus) {
        if (pendingRefocus && !s.isSaving) {
            // Save is done → focus Description
            descriptionFocus.requestFocus()
            pendingRefocus = false
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory Purchases") },
                actions = { TextButton(onClick = navigateToList) { Text("List") } }
            )
        },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            focusManager.clearFocus(force = true) // hide keyboard now
                            pendingRefocus = true                 // refocus when save completes
                            onIntent(FormIntent.Save)
                        },                        enabled = !s.isSaving,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }

                    Button(
                        onClick = {
                            focusManager.clearFocus(force = true) // hide keyboard now
                            pendingRefocus = true                 // refocus when save completes
                            onIntent(FormIntent.SaveAndNext)
                        },                        enabled = !s.isSaving,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save & Add Next") }
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PhotoGallery(
                uris = s.photoUris,
                onAdd = { onIntent(FormIntent.AddPhoto(it)) },
                onRemoveAt = { i -> onIntent(FormIntent.RemovePhotoAt(i)) }
            )
            // Description: single line; Return -> Price
            OutlinedTextField(
                value = s.description,
                onValueChange = { onIntent(FormIntent.SetDescription(it)) },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(descriptionFocus),   // NEW
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { priceFocus.requestFocus() }
                ),
                supportingText = { Text("What is it? Include details you’ll recognize later.") }
            )

            // Price: numeric; Return -> Quantity
            OutlinedTextField(
                value = s.priceText,
                onValueChange = { onIntent(FormIntent.SetPriceText(it)) },
                label = { Text("Price") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { qtyFocus.requestFocus() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(priceFocus),
                singleLine = true
            )

            // Quantity: numeric; Return -> Notes (skip the date row)
            OutlinedTextField(
                value = qtyTextFieldValue,
                onValueChange = {
                    qtyTextFieldValue = it
                    onIntent(FormIntent.SetQuantityText(it.text))
                },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { notesFocus.requestFocus() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(qtyFocusRequester)
                    .onFocusChanged { qtyFocusState.value = it.isFocused },
                singleLine = true
            )

            DateRow(
                dateMillis = s.dateMillis,
                onSetDateMillis = { onIntent(FormIntent.SetDateMillis(it)) }
            )

            // Notes input (multi-line)
            TextField(
                value = s.notes,
                onValueChange = { onIntent(FormIntent.SetNotes(it)) },
                label = { Text("Notes") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .focusRequester(notesFocus),
                singleLine = false,
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                // no KeyboardActions -> default "newline" behavior
            )

            if (s.error != null) {
                Text(s.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    dateMillis: Long?,                    // nullable = not set yet
    onSetDateMillis: (Long) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }

    // Show the currently selected date (or Today)
    val display = formatDateForUi(dateMillis)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = { open = true }) {
            Text(display)
        }
        if (dateMillis != null) {
            TextButton(onClick = {
                // If you want a “clear” button, uncomment the next line and add an intent for clearing
                // onSetDateMillis(/* maybe Today or a special sentinel */)
            }) {
                // Text("Clear")  // optional — keeping UI minimal as requested
            }
        }
    }

    if (open) {
        // Initial selection defaults to existing date or today
        val init = dateMillis ?: startOfToday()
        val state = rememberDatePickerState(initialSelectedDateMillis = init)

        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis ?: startOfToday()
                    onSetDateMillis(startOfDayLocal(picked))  // normalize to local start-of-day
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
private val uiDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

private fun formatDateForUi(millis: Long?): String {
    val z = ZoneId.systemDefault()
    val ld = if (millis != null)
        Instant.ofEpochMilli(millis).atZone(z).toLocalDate()
    else
        LocalDate.now(z)
    return ld.format(uiDateFmt)
}

/** Normalize any millis to LOCAL start-of-day to avoid off-by-one when crossing timezones. */
private fun startOfDayLocal(millis: Long): Long {
    val z = ZoneId.systemDefault()
    val ld = Instant.ofEpochMilli(millis).atZone(z).toLocalDate()
    return ld.atStartOfDay(z).toInstant().toEpochMilli()
}

private fun startOfToday(): Long = startOfDayLocal(System.currentTimeMillis())

@Composable
private fun PhotoGallery(
    uris: List<Uri>,
    onAdd: (Uri) -> Unit,
    onRemoveAt: (Int) -> Unit
) {
    val context = LocalContext.current
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    // Full-res capture launcher (same hardened one you just got working)
    val takeFullResLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val dest = pendingCaptureUri
        if (success && dest != null) onAdd(dest)
        else if (dest != null) context.contentResolver.delete(dest, null, null)
        pendingCaptureUri = null
    }

    // Preview fallback (keeps you unblocked)
    val takePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) onAdd(saveBitmapToCache(context.cacheDir, bmp))
        else Toast.makeText(context, "Camera cancelled", Toast.LENGTH_SHORT).show()
    }

    // Permission request (CAMERA + legacy storage <= API 28)
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraOk = grants[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val needsStorage = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        val storageOk = !needsStorage || grants[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true ||
                (!needsStorage || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

        if (cameraOk && storageOk) {
            val dest = createMediaStoreUriRobust(context)
            if (dest != null) {
                pendingCaptureUri = dest
                try { takeFullResLauncher.launch(dest) }
                catch (_: ActivityNotFoundException) { takePreviewLauncher.launch(null) }
                catch (_: Throwable) { takePreviewLauncher.launch(null) }
            } else takePreviewLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission required.", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery picker (append)
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { picked: Uri? -> picked?.let(onAdd) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Thumbnails row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            uris.forEachIndexed { index, u ->
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    AsyncImage(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(MaterialTheme.shapes.medium),
                        model = u,
                        contentDescription = "Photo $index"
                    )
                    // Small remove chip
                    AssistChip(
                        onClick = { onRemoveAt(index) },
                        label = { Text("×") }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val needed = buildList {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED
                    ) add(Manifest.permission.CAMERA)
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED
                    ) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (needed.isNotEmpty()) {
                    permissionsLauncher.launch(needed.toTypedArray())
                } else {
                    val dest = createMediaStoreUriRobust(context)
                    if (dest != null) {
                        pendingCaptureUri = dest
                        try { takeFullResLauncher.launch(dest) }
                        catch (_: ActivityNotFoundException) { takePreviewLauncher.launch(null) }
                        catch (_: Throwable) { takePreviewLauncher.launch(null) }
                    } else takePreviewLauncher.launch(null)
                }
            }) { Text("Take Photo") }

            OutlinedButton(onClick = {
                pickPhotoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) { Text("Pick Photo") }

            if (uris.isNotEmpty()) {
                TextButton(onClick = { onRemoveAt(uris.lastIndex) }) { Text("Remove Last") }
            }
        }
    }
}

private fun createMediaStoreUriRobust(context: Context): Uri? {
    val cr = context.contentResolver
    val name = "inv_${System.currentTimeMillis()}.jpg"
    val candidates: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        listOf(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        )
    } else {
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.INTERNAL_CONTENT_URI
        )
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/InventoryPurchases")
        }
    }
    for (collection in candidates) {
        try { cr.insert(collection, values)?.let { return it } } catch (_: Throwable) {}
    }
    return null
}

private fun saveBitmapToCache(cacheDir: File, bmp: Bitmap): Uri {
    val name = "photo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val file = File(cacheDir, name)
    FileOutputStream(file).use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.flush()
    }
    return Uri.fromFile(file)
}
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun DateField(epochMillis: Long, onChange: (Long) -> Unit) {
    val dateStr = DateTimeFormatter.ISO_DATE.withZone(ZoneId.of("UTC"))
        .format(Instant.ofEpochMilli(epochMillis))
    OutlinedTextField(
        value = dateStr,
        onValueChange = { /* read-only for now */ },
        label = { Text("Purchase Date") },
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
        supportingText = { Text("Date picker coming next") }
    )
}

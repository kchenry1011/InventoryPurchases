package com.kevin.inventorypurchases.ui.form

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.kevin.inventorypurchases.App
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevin.inventorypurchases.ui.list.ListViewModel

private enum class ListHostScreen { MAIN, SETTINGS }

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(
    state: androidx.compose.runtime.State<FormState>,
    onIntent: (FormIntent) -> Unit,
    navigateToList: () -> Unit
) {
    val s = state.value
    var screen by rememberSaveable { mutableStateOf(ListHostScreen.MAIN) }
    val listVm: ListViewModel = viewModel()
    // Grouping UI state
    var openGroupDialog by rememberSaveable { mutableStateOf(false) }
    var groupText by rememberSaveable { mutableStateOf("") }
    val app = LocalContext.current.applicationContext as App
    val activeGroup by app.groupSession.activeGroupName.collectAsState(initial = null)

    var pendingRefocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val priceFocus = remember { FocusRequester() }
    val qtyFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }
    var qtyTextFieldValue by remember { mutableStateOf(TextFieldValue(s.quantityText)) }
    val qtyFocusRequester = qtyFocus
    val descriptionFocus = remember { FocusRequester() }
    val qtyFocusState = remember { mutableStateOf(false) }

    LaunchedEffect(s.error) { /* snackbars if needed */ }
    LaunchedEffect(qtyFocusState.value) {
        if (qtyFocusState.value) {
            qtyTextFieldValue = qtyTextFieldValue.copy(
                selection = TextRange(0, qtyTextFieldValue.text.length)
            )
        }
    }
    LaunchedEffect(s.isSaving, pendingRefocus) {
        if (pendingRefocus && !s.isSaving) {
            descriptionFocus.requestFocus()
            pendingRefocus = false
        }
    }

    Scaffold(
        topBar = {
            when (screen) {
                ListHostScreen.MAIN -> {
                    TopAppBar(
                        navigationIcon = {
                            TextButton(onClick = {
                                screen = ListHostScreen.SETTINGS
                            }) { Text("Settings") }
                        },
                        title = { Text("Purchases") },
                        actions = { TextButton(onClick = navigateToList) { Text("List") } }
                    )
                }
                ListHostScreen.SETTINGS -> {
                    TopAppBar(
                        navigationIcon = {
                            TextButton(onClick = { screen = ListHostScreen.MAIN }) { Text("Back") }
                        },
                        title = { Text("Settings") }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (screen == ListHostScreen.MAIN) {

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
                        Button(
                            onClick = {
                                focusManager.clearFocus(force = true)
                                pendingRefocus = true
                                onIntent(FormIntent.SaveAndNext)
                            },
                            enabled = !s.isSaving,
                            modifier = Modifier.weight(1f)
                        ) { Text("Save & Add Next") }

                        // Grouping toggle (chip is shown above, not here)
                        if (activeGroup.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { openGroupDialog = true },
                                enabled = !s.isSaving,
                                modifier = Modifier.weight(1f)
                            ) { Text("New Group") }
                        } else {
                            Button(
                                onClick = { onIntent(FormIntent.EndGroup) },
                                enabled = !s.isSaving,
                                modifier = Modifier.weight(1f)
                            ) { Text("End Group") }
                        }
                    }
                }
            }
        }
    ) { pad ->
        when (screen) {
            ListHostScreen.MAIN -> {
                Column(
                    modifier = Modifier
                        .padding(pad)
                        .padding(16.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Your original gallery + camera/picker/buttons
                    PhotoGallery(
                        uris = s.photoUris,
                        onAdd = { onIntent(FormIntent.AddPhoto(it)) },
                        onRemoveAt = { i -> onIntent(FormIntent.RemovePhotoAt(i)) }
                    )

                    // ✅ Active group chip moved HERE (below Take/Pick/Remove, above Description)
                    if (!activeGroup.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            AssistChip(
                                onClick = { /* optional future action */ },
                                label = { Text(activeGroup!!) }
                            )
                        }
                    }

                    // Description: single line; Return -> Price
                    OutlinedTextField(
                        value = s.description,
                        onValueChange = { onIntent(FormIntent.SetDescription(it)) },
                        label = { Text("Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(descriptionFocus),
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

                    // Quantity: numeric; Return -> Notes
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
                    )

                    if (s.error != null) {
                        Text(s.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            ListHostScreen.SETTINGS -> {
                SettingsScreen(
                    vm = listVm,
                    onBack = { screen = ListHostScreen.MAIN },
                    pad = pad
                )
            }
        }
    }

    // New Group dialog
    if (openGroupDialog) {
        AlertDialog(
            onDismissRequest = { openGroupDialog = false },
            title = { Text("New Group") },
            text = {
                OutlinedTextField(
                    value = groupText,
                    onValueChange = { groupText = it },
                    label = { Text("Group name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = groupText.trim().uppercase()
                        if (name.isNotEmpty()) {
                            onIntent(FormIntent.StartGroup(name))
                            groupText = ""
                            openGroupDialog = false
                        }
                    }
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { openGroupDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsScreen(
    vm: ListViewModel,
    onBack: () -> Unit, // kept for signature compatibility; not used now
    pad: PaddingValues
) {
    Column(
        modifier = Modifier
            .padding(pad)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Maintenance", style = MaterialTheme.typography.titleMedium)
        ClearCacheButton(vm)

        // FormScreen.kt (inside SettingsScreen)
        var askConfirm by remember { mutableStateOf(false) }

        Divider()

        Text("Danger zone", style = MaterialTheme.typography.titleMedium)
        Text("This deletes ALL photos taken with this app and ALL purchase records. This cannot be undone.")

        Button(
            onClick = { askConfirm = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) { Text("Delete ALL Photos & Records") }

        if (askConfirm) {
            AlertDialog(
                onDismissRequest = { askConfirm = false },
                title = { Text("Really delete everything?") },
                text = { Text("This will remove every photo referenced by the app and wipe all records from the database.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            askConfirm = false
                            vm.wipeAllData()
                        }
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { askConfirm = false }) { Text("Cancel") }
                }
            )
        }

    }
}


/* === Your provided button, unchanged === */
@Composable
fun ClearCacheButton(vm: ListViewModel) {
    val ctx = LocalContext.current
    val events = vm.events.collectAsState(initial = null)

    LaunchedEffect(events.value) {
        val e = events.value
        if (e is ListViewModel.UiEvent.CacheCleared) {
            val mb = (e.bytesFreed / (1024.0 * 1024.0)).let { String.format("%.1f", it) }
            Toast.makeText(ctx, "Cleared ${e.entriesRemoved} items (~${mb} MB)", Toast.LENGTH_SHORT).show()
        } else if (e is ListViewModel.UiEvent.WipeCompleted) {
            val msg = "Deleted ${e.recordsDeleted} records, ${e.photosDeleted} photos" +
                    (if (e.photosFailed > 0) ", ${e.photosFailed} failed" else "")
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    Button(onClick = { vm.clearExportCache() }) { Text("Clear Export Cache") }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    dateMillis: Long?,
    onSetDateMillis: (Long) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }

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
                // Optional clear hook
            }) {
                // Text("Clear")
            }
        }
    }

    if (open) {
        val init = dateMillis ?: startOfToday()
        val state = rememberDatePickerState(initialSelectedDateMillis = init)

        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis ?: startOfToday()
                    onSetDateMillis(startOfDayLocal(picked))
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

    // Full-res capture
    val takeFullResLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val dest = pendingCaptureUri
        if (success && dest != null) onAdd(dest)
        else if (dest != null) context.contentResolver.delete(dest, null, null)
        pendingCaptureUri = null
    }

    // Preview fallback
    val takePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) onAdd(saveBitmapToCache(context.cacheDir, bmp))
        else Toast.makeText(context, "Camera cancelled", Toast.LENGTH_SHORT).show()
    }

    // Permissions
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
                    AssistChip(
                        onClick = { onRemoveAt(index) },
                        label = { Text("×") }
                    )
                }
            }
        }

        // Take / Pick / Remove row (unchanged)
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
        // NOTE: (group chip removed from here; now rendered above Description in the main Column)
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

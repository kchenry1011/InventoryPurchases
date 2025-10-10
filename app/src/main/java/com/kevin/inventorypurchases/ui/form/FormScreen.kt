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
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(
    state: androidx.compose.runtime.State<FormState>,
    onIntent: (FormIntent) -> Unit,
    navigateToList: () -> Unit
) {
    val s = state.value
    LaunchedEffect(s.error) { /* placeholder for snackbars */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory Purchases") },
                actions = { TextButton(onClick = navigateToList) { Text("List") } }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PhotoGallery(
                uris = s.photoUris,
                onAdd = { onIntent(FormIntent.AddPhoto(it)) },
                onRemoveAt = { i -> onIntent(FormIntent.RemovePhotoAt(i)) }
            )
            OutlinedTextField(
                value = s.description,
                onValueChange = { onIntent(FormIntent.SetDescription(it)) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                supportingText = { Text("What is it? Include details you’ll recognize later.") }
            )

            OutlinedTextField(
                value = s.priceText,
                onValueChange = { onIntent(FormIntent.SetPriceText(it)) },
                label = { Text("Price") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = s.quantityText,
                onValueChange = { onIntent(FormIntent.SetQuantityText(it)) },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            DateField(
                epochMillis = s.dateMillis,
                onChange = { onIntent(FormIntent.SetDateMillis(it)) }
            )

            if (s.error != null) {
                Text(s.error!!, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !s.isSaving, onClick = { onIntent(FormIntent.Save) }) {
                    Text("Save")
                }
                Button(enabled = !s.isSaving, onClick = { onIntent(FormIntent.SaveAndNext) }) {
                    Text("Save & Add Next")
                }
            }
        }
    }
}

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

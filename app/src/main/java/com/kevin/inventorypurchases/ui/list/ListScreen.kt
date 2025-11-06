package com.kevin.inventorypurchases.ui.list

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevin.inventorypurchases.data.db.Purchase
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(onBack: () -> Unit) {
    val vm: ListViewModel = viewModel(factory = ListViewModel.Factory)
    val items by vm.items.collectAsState(initial = emptyList())
    val isSharing by vm.isSharing.collectAsState()   // <— add this
    val context = LocalContext.current

    val locationPermsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* grants -> */
        // Regardless of grant/deny, VM will embed location if available.
        vm.shareCsvAndPhotos()
    }
    LaunchedEffect(Unit) {
        vm.shareZip.collectLatest { uri ->
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "Inventory Export", uri)
            }
            context.startActivity(Intent.createChooser(i, "Share CSV + Photos"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    Button(
                        onClick = {
                            // Only trigger if not already sharing
                            if (!isSharing) {
                                locationPermsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        enabled = !isSharing
                    ) {
                        if (isSharing) {
                            // Inline spinner next to the label
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text("Preparing…")
                        } else {
                            Text("Share CSV + Photos")
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { vm.clearAll() },
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) { Text("Clear All") }
                }
            )
        }
    ) { pad ->
        Box(modifier = Modifier
            .padding(pad)
            .fillMaxSize()
        ) {

            // Main content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(items, key = { it.id }) { p ->
                    PurchaseRow(p = p, onDelete = { vm.deleteOne(p.id) })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            // Centered overlay spinner while exporting/sharing
            if (isSharing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PurchaseRow(
    p: Purchase,
    onDelete: () -> Unit
) {
    ElevatedCard {
        ListItem(
            headlineContent = { Text(p.description) },
            supportingContent = {
                Text("Qty: ${p.quantity} • $${p.priceCents / 100}.${(p.priceCents % 100).toString().padStart(2, '0')}")
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!p.groupName.isNullOrBlank()) {
                        androidx.compose.material3.AssistChip(
                            onClick = { /* optional: later */ },
                            label = { Text(p.groupName!!) }
                        )
                    }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        )
    }
}


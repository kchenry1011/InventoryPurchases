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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(onBack: () -> Unit) {
    val vm: ListViewModel = viewModel(factory = ListViewModel.Factory)
    val items by vm.items.collectAsState(initial = emptyList())
    val context = LocalContext.current

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
                title = { Text("Purchases") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    Button(onClick = { vm.shareCsvAndPhotos() }) {
                        Text("Share CSV + Photos")
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
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(items, key = { it.id }) { p ->
                PurchaseRow(p = p, onDelete = { vm.deleteOne(p.id) })
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        )
    }
}

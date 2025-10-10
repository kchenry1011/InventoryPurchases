package com.kevin.inventorypurchases.ui.list

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevin.inventorypurchases.data.db.Purchase
import androidx.compose.runtime.collectAsState
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collectLatest
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(onBack: () -> Unit) {
    // Use the new CreationExtras-based factory (property, not a function)
    val vm: ListViewModel = viewModel(factory = ListViewModel.Factory)
    // items is a StateFlow<List<Purchase>>; collect it into Compose state
    val items by vm.items.collectAsState(initial = emptyList())
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.shareCsv.collectLatest { uri ->
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                // grant read permission to the receiving app
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "CSV", uri)
            }
            context.startActivity(Intent.createChooser(i, "Share CSV"))
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Purchases") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    Button(onClick = { vm.shareCsv() }) {
                        Text("Share CSV")
                    }

                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { vm.clearAll() },
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("Clear All")
                    }
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
                PurchaseRow(
                    p = p,
                    onDelete = { vm.deleteOne(p.id) }  // no conversion needed now
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun PurchaseRow(
    p: Purchase,
    onDelete: () -> Unit   // NEW: injected action
) {
    ElevatedCard {
        ListItem(
            headlineContent = { Text(p.description) },
            supportingContent = {
                Text("Qty: ${p.quantity} • $${p.priceCents / 100}.${(p.priceCents % 100).toString().padStart(2, '0')}")
            },
            trailingContent = {
                // Simple button version (no new deps)
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        )
    }
}


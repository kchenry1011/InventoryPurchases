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

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ListScreen(onBack: () -> Unit) {
    // Use the new CreationExtras-based factory (property, not a function)
    val vm: ListViewModel = viewModel(factory = ListViewModel.Factory)
    // items is a StateFlow<List<Purchase>>; collect it into Compose state
    val items by vm.items.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Purchases") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { vm.exportCsv() }) { Text("Export CSV") } }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(items) { p -> PurchaseRow(p) }
        }
    }
}

@Composable
private fun PurchaseRow(p: Purchase) {
    ElevatedCard {
        ListItem(
            headlineContent = { Text(p.description) },
            supportingContent = {
                Text("Qty: ${p.quantity} • $${p.priceCents / 100}.${(p.priceCents % 100).toString().padStart(2, '0')}")
            }
        )
    }
}

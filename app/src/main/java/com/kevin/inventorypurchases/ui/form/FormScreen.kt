package com.kevin.inventorypurchases.ui.form

import android.net.Uri
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
            PhotoRow(s.photoUri) { onIntent(FormIntent.SetPhoto(it)) }

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
private fun PhotoRow(uri: Uri?, onPick: (Uri?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            if (uri != null) {
                AsyncImage(model = uri, contentDescription = "Photo")
            } else {
                Text("No Photo")
            }
        }
        OutlinedButton(onClick = { /* TODO picker */ }) { Text("Add Photo") }
        if (uri != null) {
            TextButton(onClick = { onPick(null) }) { Text("Remove") }
        }
    }
}

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

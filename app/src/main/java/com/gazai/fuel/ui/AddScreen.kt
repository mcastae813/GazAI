package com.gazai.fuel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel

@Composable
fun AddScreen(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsState()
    val s = vm.settings
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.imageUri = uri
    }
    val current = vm.currentVehicle()
    val distUnit = if (s.imperial) "mi" else "km"
    val volUnit = if (s.imperial) "gal" else "L"
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Add fill-up", style = MaterialTheme.typography.headlineMedium)

        if (vehicles.isEmpty()) {
            Text("Add a vehicle first (Vehicles tab).")
            Button(onClick = { vm.tab = 2 }) { Text("Add vehicle") }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vehicles.forEach { v ->
                FilterChip(
                    selected = current?.id == v.id,
                    onClick = { vm.selectedVehicleId = v.id },
                    label = { Text(v.name) },
                )
            }
        }

        Text("Let AI fill it in", style = MaterialTheme.typography.titleMedium)
        Text(
            "Describe the fill-up and/or add a photo of the receipt or pump. " +
                "Example: \"filled the bike, 8.2 L for 15.40 at Shell, 12340 km\".",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = vm.note,
            onValueChange = { vm.note = it },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (vm.imageUri == null) "Add photo" else "Change photo") }
            if (vm.imageUri != null) OutlinedButton(onClick = { vm.imageUri = null }) { Text("Remove") }
        }
        if (vm.imageUri != null) Text("Photo attached", style = MaterialTheme.typography.bodySmall)

        Button(onClick = { vm.analyze() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
            if (vm.busy) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            Text(if (vm.busy) "Reading..." else if (s.autoSave) "Read with AI and add" else "Read with AI")
        }
        if (s.apiKey.isBlank()) {
            Text("No OpenRouter key set. Add one in Settings (free).", color = MaterialTheme.colorScheme.error)
        }

        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Details", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(vm.date, { vm.date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.odometer, { vm.odometer = it }, label = { Text("Odometer ($distUnit, optional)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.volume, { vm.volume = it }, label = { Text("Fuel ($volUnit)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.cost, { vm.cost = it }, label = { Text("Total cost (${s.currency})") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.station, { vm.station = it }, label = { Text("Station (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = vm.fullTank, onCheckedChange = { vm.fullTank = it })
            Text("Filled the tank completely")
        }
        Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }
}

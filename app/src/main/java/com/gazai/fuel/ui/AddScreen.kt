package com.gazai.fuel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel

@Composable
fun AddScreen(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsState()
    val s = vm.settings
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        vm.onPhotoTaken(ok)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { vm.imageUri = uri; vm.analyze() }
    }
    val current = vm.currentVehicle()
    val distUnit = if (s.imperial) "mi" else "km"
    val volUnit = if (s.imperial) "gal" else "L"
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    // Open the camera right away on app start, aimed at the default vehicle.
    LaunchedEffect(vehicles.size, s.apiKey) {
        if (vm.consumeAutoCamera()) camera.launch(vm.newCameraUri())
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Fuel up", style = MaterialTheme.typography.headlineMedium)

        if (vehicles.isEmpty()) {
            Text("Add your car or motorcycle first, then fuel-ups take one photo.")
            Button(onClick = { vm.tab = 3 }) { Text("Add vehicle") }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vehicles.forEach { v ->
                FilterChip(
                    selected = current?.id == v.id,
                    onClick = { vm.selectedVehicleId = v.id },
                    label = { Text(v.name + if (v.id == vm.defaultVehicle()?.id) " *" else "") },
                )
            }
        }

        Button(
            onClick = { camera.launch(vm.newCameraUri()) },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
            Text(
                if (vm.busy) "Reading the pump..." else "Take photo of the pump",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            "Point the camera at the pump so total cost and liters are visible. " +
                "GazAI reads them and adds the fill-up to ${current?.name ?: "your vehicle"}.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (s.apiKey.isBlank()) {
            Text("No OpenRouter key set. Add one in Settings (free).", color = MaterialTheme.colorScheme.error)
        }

        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        vm.lastAdded?.let { added ->
            var odo by remember(added.id) { mutableStateOf("") }
            if (added.odometerKm == null) {
                Text("Add the odometer to get km/L for this fill-up:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        odo, { odo = it }, label = { Text("Odometer ($distUnit)") }, singleLine = true,
                        keyboardOptions = decimal, modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { vm.attachOdometerToLast(odo) }, enabled = odo.isNotBlank()) { Text("Save") }
                }
            }
            TextButton(onClick = { vm.undoLast() }) { Text("Undo this fill-up") }
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Other ways to add", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = vm.note,
            onValueChange = { vm.note = it },
            label = { Text("Note (e.g. 8.2 L for 15.40, 12340 km)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Pick photo") }
            OutlinedButton(onClick = { vm.analyze() }, enabled = !vm.busy) { Text("Read note with AI") }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Details", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(vm.date, { vm.date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.odometer, { vm.odometer = it }, label = { Text("Odometer ($distUnit, optional - needed for km/L)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
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

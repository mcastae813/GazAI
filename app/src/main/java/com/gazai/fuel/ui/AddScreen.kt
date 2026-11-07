package com.gazai.fuel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel
import java.time.Instant
import java.time.ZoneId

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
    val editing = vm.editing
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
        Text(if (editing == null) "Fuel up" else "Edit fill-up", style = MaterialTheme.typography.headlineMedium)

        if (vehicles.isEmpty()) {
            Welcome(vm)
            return@Column
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vehicles.forEach { v ->
                FilterChip(
                    selected = current?.id == v.id,
                    onClick = { vm.pickVehicle(v.id) },
                    label = { Text(v.name + if (v.id == vm.defaultVehicle()?.id) " *" else "") },
                )
            }
        }

        if (editing != null) {
            val day = Instant.ofEpochMilli(editing.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Editing the fill-up from $day", style = MaterialTheme.typography.titleMedium)
                    Text("Change the details below and tap Save changes.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.cancelEdit() }) { Text("Cancel edit") }
                }
            }
        } else {
            Button(
                onClick = { camera.launch(vm.newCameraUri()) },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                if (vm.busy) CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
                Text(
                    if (vm.busy) (vm.status ?: "Reading the pump...") else "Take photo of the pump",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (vm.busy) {
                OutlinedButton(onClick = { vm.cancelAnalysis() }, modifier = Modifier.fillMaxWidth()) { Text("Stop reading") }
            }
            Text(
                "Point the camera at the pump so total cost and liters are visible. " +
                    "GazAI reads them and adds the fill-up to ${current?.name ?: "your vehicle"}.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (s.apiKey.isBlank()) {
                Text("No OpenRouter key set. Add one in Settings (free).", color = MaterialTheme.colorScheme.error)
            }
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

        if (editing == null) {
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
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !vm.busy,
                ) { Text("Pick photo") }
                OutlinedButton(onClick = { vm.analyze() }, enabled = !vm.busy) { Text("Read note with AI") }
            }
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
        // Disabled while the AI is reading, so a slow answer can't add a second copy of a hand-typed fill-up.
        Button(onClick = { vm.save() }, enabled = !vm.busy && !vm.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (editing == null) "Save" else "Save changes")
        }
    }
}

/** First run: ask for the vehicle details and the OpenRouter key before anything else. */
@Composable
private fun Welcome(vm: AppViewModel) {
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    Text("Welcome to GazAI", style = MaterialTheme.typography.titleLarge)
    Text("Tell me about your vehicle. You can add more (like your motorcycle) later in the Garage tab.")
    VehicleForm(vm, null, onDone = {})
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text("Free AI key", style = MaterialTheme.typography.titleMedium)
    Text(
        "Create a free key at openrouter.ai/keys and paste it here so GazAI can read your pump photos.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        key, { key = it }, label = { Text("OpenRouter API key") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
    )
    if (key.trim() != vm.settings.apiKey) {
        Button(onClick = { vm.updateSettings(vm.settings.copy(apiKey = key.trim())) }) { Text("Save key") }
    } else if (key.isNotBlank()) {
        Text("Key saved.", color = MaterialTheme.colorScheme.primary)
    }
}

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel
import com.gazai.fuel.data.Vehicle

@Composable
fun VehiclesScreen(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsState()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("car") }
    var photoTarget by remember { mutableStateOf<Vehicle?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val v = photoTarget
        if (uri != null && v != null) vm.setVehiclePhoto(v, uri)
        photoTarget = null
    }
    val defaultId = vm.defaultVehicle()?.id

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Garage", style = MaterialTheme.typography.headlineMedium)
        vehicles.forEach { v ->
            Card(Modifier.fillMaxWidth()) {
                Column {
                    VehiclePhoto(v.photoPath, v.name, 140.dp)
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                (if (v.type == "motorcycle") "Motorcycle" else "Car") + if (v.id == defaultId) "  |  Default" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { vm.deleteVehicle(v) }) { Icon(Icons.Default.Delete, contentDescription = "Delete vehicle and its history") }
                    }
                    Row(Modifier.padding(start = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            photoTarget = v
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(if (v.photoPath == null) "Add photo" else "Change photo") }
                        if (v.id != defaultId) OutlinedButton(onClick = { vm.setDefault(v) }) { Text("Make default") }
                    }
                }
            }
        }
        Text("Add vehicle", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Honda CB500, Corolla)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "car", onClick = { type = "car" }, label = { Text("Car") })
            FilterChip(selected = type == "motorcycle", onClick = { type = "motorcycle" }, label = { Text("Motorcycle") })
        }
        Button(onClick = { vm.addVehicle(name, type); name = "" }, enabled = name.isNotBlank()) { Text("Add") }
        Text(
            "The default vehicle is the one the app opens the camera for. Add a photo of each vehicle to recognise it quickly.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

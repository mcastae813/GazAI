package com.gazai.fuel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel
import com.gazai.fuel.data.Vehicle

@Composable
fun VehiclesScreen(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsState()
    val entries by vm.entries.collectAsState()
    var editingId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Vehicle?>(null) }
    // Saveable: Android may recreate the screen (or restart GazAI) while the photo picker is open.
    var photoTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = photoTargetId
        if (uri != null && id != null) vm.setVehiclePhoto(id, uri)
        photoTargetId = null
    }
    val defaultId = vm.defaultVehicle()?.id
    val s = vm.settings

    confirmDelete?.let { v ->
        val fills = entries.count { it.vehicleId == v.id }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${v.name}?") },
            text = { Text("This also deletes its $fills fill-ups and all its service records. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteVehicle(v); confirmDelete = null; if (editingId == v.id) editingId = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Garage", style = MaterialTheme.typography.headlineMedium)
        vehicles.forEach { v ->
            Card(Modifier.fillMaxWidth()) {
                Column {
                    VehiclePhoto(v.photoPath, v.name, 140.dp)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (editingId == v.id) {
                            Text("Edit ${v.name}", style = MaterialTheme.typography.titleMedium)
                            VehicleForm(vm, v, onDone = { editingId = null }, onCancel = { editingId = null })
                        } else {
                            Text(v.name + if (v.id == defaultId) "  (default)" else "", style = MaterialTheme.typography.titleMedium)
                            Text(vehicleSubtitle(v, s.imperial), style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editingId = v.id; adding = false }) { Text("Edit") }
                                OutlinedButton(onClick = {
                                    photoTargetId = v.id
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) { Text(if (v.photoPath == null) "Add photo" else "Photo") }
                                if (v.id != defaultId) OutlinedButton(onClick = { vm.setDefault(v) }) { Text("Make default") }
                                TextButton(onClick = { confirmDelete = v }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
        if (adding || vehicles.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Add vehicle", style = MaterialTheme.typography.titleMedium)
                    VehicleForm(vm, null, onDone = { adding = false }, onCancel = if (vehicles.isEmpty()) null else ({ adding = false }))
                }
            }
        } else {
            Button(onClick = { adding = true; editingId = null }) { Text("Add vehicle") }
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "The default vehicle is the one the camera opens for when you start the app.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

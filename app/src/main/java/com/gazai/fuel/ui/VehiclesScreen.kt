package com.gazai.fuel.ui

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

@Composable
fun VehiclesScreen(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsState()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("car") }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Vehicles", style = MaterialTheme.typography.headlineMedium)
        vehicles.forEach { v ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (v.type == "motorcycle") "Motorcycle" else "Car", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.deleteVehicle(v) }) { Icon(Icons.Default.Delete, contentDescription = "Delete vehicle and its fill-ups") }
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
    }
}

package com.gazai.fuel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel
import com.gazai.fuel.data.FuelEntry
import com.gazai.fuel.data.MaintenanceRecord
import com.gazai.fuel.data.consumptionByEntry
import com.gazai.fuel.data.summarize
import java.time.Instant
import java.time.ZoneId

@Composable
private fun FuelLog(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsState()
    val entries by vm.entries.collectAsState()
    val s = vm.settings
    var confirmDelete by remember { mutableStateOf<FuelEntry?>(null) }

    confirmDelete?.let { e ->
        val vName = vehicles.firstOrNull { it.id == e.vehicleId }?.name ?: "?"
        val day = Instant.ofEpochMilli(e.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this fill-up?") },
            text = { Text("$day, $vName: ${s.volume(e.liters)} for ${s.money(e.totalCost)}. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteEntry(e); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        if (vehicles.isEmpty()) {
            item { Text("No vehicles yet. Add your car and motorcycle in the Vehicles tab.") }
        }

        items(vehicles, key = { "v${it.id}" }) { v ->
            val mine = entries.filter { it.vehicleId == v.id }
            val sum = summarize(mine)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        (if (v.type == "motorcycle") "Motorcycle - " else "Car - ") + v.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("Average: " + (sum.avgLPer100?.let { s.efficiency(it) } ?: "needs 2 full-tank fills with odometer"))
                    Text("Spent: ${s.money(sum.totalCost)}  |  Fuel: ${s.volume(sum.totalLiters)}  |  Fills: ${sum.fills}")
                    sum.lastPricePerLiter?.let { Text("Last price: " + s.unitPrice(it, 1.0)) }
                }
            }
        }

        if (entries.isNotEmpty()) {
            item { Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        }

        // consumption is per vehicle, so compute it per vehicle to avoid mixing odometers
        val perEntry = vehicles.flatMap { v -> consumptionByEntry(entries.filter { it.vehicleId == v.id }).toList() }.toMap()

        items(entries, key = { "e${it.id}" }) { e ->
            val vName = vehicles.firstOrNull { it.id == e.vehicleId }?.name ?: "?"
            val day = Instant.ofEpochMilli(e.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$day  -  $vName" + if (e.source == "ai") "  (AI)" else "", style = MaterialTheme.typography.titleSmall)
                        Text("${s.volume(e.liters)}  |  ${s.money(e.totalCost)}  |  ${s.unitPrice(e.totalCost, e.liters)}")
                        val extra = listOfNotNull(
                            e.odometerKm?.let { s.distance(it) },
                            perEntry[e.id]?.let { s.efficiency(it) },
                            e.station,
                            if (!e.fullTank) "partial fill" else null,
                        ).joinToString("  |  ")
                        if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.startEdit(e) }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { confirmDelete = e }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
        }
        item { Column(Modifier.padding(8.dp)) {} }
    }
}

@Composable
fun LogScreen(vm: AppViewModel) {
    var showService by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("Log", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showService, onClick = { showService = false }, label = { Text("Fuel") })
            FilterChip(selected = showService, onClick = { showService = true }, label = { Text("Service") })
        }
        if (showService) ServiceLog(vm) else FuelLog(vm)
    }
}

private val serviceTypes = listOf("Oil change", "Tires", "Brakes", "Chain", "Air filter", "Battery", "Inspection", "Other")

@Composable
private fun ServiceLog(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsState()
    val records by vm.maintenance.collectAsState()
    val s = vm.settings
    var adding by remember { mutableStateOf(false) }
    var vehicleId by remember { mutableStateOf<Long?>(null) }
    var type by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var odo by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var nextDue by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<MaintenanceRecord?>(null) }
    val unit = if (s.imperial) "mi" else "km"
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val target = vehicles.firstOrNull { it.id == vehicleId } ?: vm.defaultVehicle()

    confirmDelete?.let { m ->
        val vName = vehicles.firstOrNull { it.id == m.vehicleId }?.name ?: "?"
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this service record?") },
            text = { Text("${m.type} on $vName. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteMaintenance(m); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            if (vehicles.isEmpty()) {
                Text("Add a vehicle in the Garage tab first.")
            } else if (!adding) {
                Button(onClick = { adding = true }) { Text("Add service") }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vehicles.forEach { v ->
                                FilterChip(selected = target?.id == v.id, onClick = { vehicleId = v.id }, label = { Text(v.name) })
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            serviceTypes.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) }
                        }
                        OutlinedTextField(type, { type = it }, label = { Text("What was done") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(odo, { odo = it }, label = { Text("Odometer ($unit, optional)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(cost, { cost = it }, label = { Text("Cost (${s.currency}, optional)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(nextDue, { nextDue = it }, label = { Text("Next due at odometer ($unit, optional)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val id = target?.id
                                err = if (id == null) "Pick a vehicle." else vm.addMaintenance(id, type, date, odo, cost, notes, nextDue)
                                if (err == null) {
                                    adding = false; type = ""; odo = ""; cost = ""; notes = ""; nextDue = ""
                                    date = LocalDate.now().toString()
                                }
                            }) { Text("Save") }
                            TextButton(onClick = { adding = false; err = null }) { Text("Cancel") }
                        }
                    }
                }
            }
        }
        items(records, key = { "m${it.id}" }) { m ->
            val vName = vehicles.firstOrNull { it.id == m.vehicleId }?.name ?: "?"
            val day = Instant.ofEpochMilli(m.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.type}  -  $vName", style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(
                                day.toString(),
                                m.odometerKm?.let { s.distance(it) },
                                if (m.cost > 0) s.money(m.cost) else null,
                                m.nextDueKm?.let { "next at ${s.distance(it)}" },
                            ).joinToString("  |  "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        m.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    IconButton(onClick = { confirmDelete = m }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
        }
        item { Column(Modifier.padding(8.dp)) {} }
    }
}

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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel
import com.gazai.fuel.data.consumptionByEntry
import com.gazai.fuel.data.summarize
import java.time.Instant
import java.time.ZoneId

@Composable
fun LogScreen(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsState()
    val entries by vm.entries.collectAsState()
    val s = vm.settings

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Fuel log", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }

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
                    IconButton(onClick = { vm.deleteEntry(e) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
        }
        item { Column(Modifier.padding(8.dp)) {} }
    }
}

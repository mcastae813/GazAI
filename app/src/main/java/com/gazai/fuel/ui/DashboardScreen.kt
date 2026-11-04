package com.gazai.fuel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel
import com.gazai.fuel.data.Settings
import com.gazai.fuel.data.dueItems
import com.gazai.fuel.data.efficiencySeries
import com.gazai.fuel.data.latestOdometer
import com.gazai.fuel.data.monthlySpend
import com.gazai.fuel.data.summarize
import com.gazai.fuel.data.usageStats
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** "5.2% better than average" - lower consumption is better. */
@Composable
private fun Compare(label: String, current: Double, reference: Double?) {
    if (reference == null || reference <= 0) return
    val pct = (reference - current) / reference * 100.0
    val better = pct >= 0
    Text(
        (if (abs(pct) < 0.05) "same as $label" else "${fmtPct(abs(pct))} ${if (better) "better" else "worse"} than $label"),
        color = if (abs(pct) < 0.05) Color.Unspecified else if (better) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

private fun fmtPct(v: Double) = String.format(Locale.US, "%.1f%%", v)

private fun Settings.dateOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsState()
    val allEntries by vm.entries.collectAsState()
    val allService by vm.maintenance.collectAsState()
    val s = vm.settings
    var selected by remember { mutableStateOf<Long?>(null) }
    val v = vehicles.firstOrNull { it.id == selected } ?: vm.defaultVehicle()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Stats", style = MaterialTheme.typography.headlineMedium)
        if (v == null) {
            Text("Add a vehicle in the Garage tab to see stats.")
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vehicles.forEach {
                FilterChip(selected = it.id == v.id, onClick = { selected = it.id }, label = { Text(it.name) })
            }
        }

        val entries = allEntries.filter { it.vehicleId == v.id }
        val service = allService.filter { it.vehicleId == v.id }
        val series = efficiencySeries(entries)
        val sum = summarize(entries)
        val usage = usageStats(entries)
        val today = LocalDate.now()

        Card(Modifier.fillMaxWidth()) {
            Column {
                VehiclePhoto(v.photoPath, v.name, 150.dp)
                Column(Modifier.padding(16.dp)) {
                    Text(v.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        vehicleSubtitle(v, s.imperial) +
                            latestOdometer(entries, service)?.let { "  |  ${s.distance(it)}" }.orEmpty(),
                    )
                }
            }
        }

        StatCard("Efficiency") {
            val last = series.lastOrNull()
            if (last == null) {
                Text("Needs two full-tank fill-ups with odometer readings.")
            } else {
                Text(s.efficiency(last.lPer100), style = MaterialTheme.typography.headlineMedium)
                Text("Last fill-up", style = MaterialTheme.typography.bodySmall)
                Compare("average (${sum.avgLPer100?.let { s.efficiency(it) } ?: "-"})", last.lPer100, sum.avgLPer100)
                series.getOrNull(series.size - 2)?.let { prev ->
                    Compare("previous (${s.efficiency(prev.lPer100)})", last.lPer100, prev.lPer100)
                }
                Text(
                    "Average ${sum.avgLPer100?.let { s.efficiency(it) } ?: "-"}  |  " +
                        "Best ${s.efficiency(series.minOf { it.lPer100 })}  |  Worst ${s.efficiency(series.maxOf { it.lPer100 })}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (series.size >= 2) LineChart(series.takeLast(12).map { s.effValue(it.lPer100) })
            }
        }

        StatCard("Spending") {
            val months = monthlySpend(entries, 6, YearMonth.now())
            val thisM = months.last().second
            val lastM = months[months.size - 2].second
            Text(s.money(thisM), style = MaterialTheme.typography.headlineMedium)
            Text(
                "This month  |  last month ${s.money(lastM)}" +
                    if (lastM > 0) "  (${if (thisM >= lastM) "+" else "-"}${fmtPct(abs(thisM - lastM) / lastM * 100)})" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            BarChart(months.map { it.second })
            Text(
                months.joinToString("   ") { it.first.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        StatCard("Usage") {
            Text("Total spent ${s.money(sum.totalCost)} on ${s.volume(sum.totalLiters)} in ${sum.fills} fill-ups")
            sum.lastPricePerLiter?.let { Text("Last price ${s.unitPrice(it, 1.0)}") }
            usage.distanceKm?.let { Text("Tracked distance ${s.distance(it)}") }
            usage.costPerKm?.let {
                Text("Fuel cost per ${if (s.imperial) "mile" else "km"}: " +
                    "${s.currency}${String.format(Locale.US, "%.3f", if (s.imperial) it * com.gazai.fuel.data.Units.KM_PER_MI else it)}")
            }
        }

        StatCard("Maintenance") {
            val lastService = service.maxByOrNull { it.dateMillis }
            if (lastService == null) {
                Text("No service recorded yet. Add one in the Log tab.")
            } else {
                val days = ChronoUnit.DAYS.between(s.dateOf(lastService.dateMillis), today)
                val odo = latestOdometer(entries, service)
                Text("Last: ${lastService.type}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${s.dateOf(lastService.dateMillis)}  ($days days ago)" +
                        if (odo != null && lastService.odometerKm != null) "  |  ${s.distance(odo - lastService.odometerKm)} since" else "",
                )
                val due = dueItems(service, odo)
                if (due.isNotEmpty()) {
                    Text("Coming up", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    due.take(3).forEach { d ->
                        val over = d.remainingKm < 0
                        Text(
                            "${d.record.type}: " + if (over) "overdue by ${s.distance(-d.remainingKm)}" else "in ${s.distance(d.remainingKm)}",
                            color = if (over) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                    }
                }
            }
        }
    }
}

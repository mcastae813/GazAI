package com.gazai.fuel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.gazai.fuel.AppViewModel
import com.gazai.fuel.data.Settings
import com.gazai.fuel.data.Units
import com.gazai.fuel.fmt

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Log", Icons.Default.Home),
    Tab("Add", Icons.Default.Add),
    Tab("Vehicles", Icons.Default.Menu),
    Tab("Settings", Icons.Default.Settings),
)

@Composable
fun App(vm: AppViewModel) {
    Surface(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = vm.tab == i,
                            onClick = { vm.tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                when (vm.tab) {
                    0 -> LogScreen(vm)
                    1 -> AddScreen(vm)
                    2 -> VehiclesScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}

// ---- display helpers ----
fun Settings.volume(liters: Double) =
    if (imperial) "${fmt(liters / Units.L_PER_GAL)} gal" else "${fmt(liters)} L"

fun Settings.distance(km: Double) =
    if (imperial) "${fmt(km / Units.KM_PER_MI, 0)} mi" else "${fmt(km, 0)} km"

fun Settings.money(v: Double) = "$currency${fmt(v)}"

fun Settings.efficiency(lPer100: Double) =
    if (imperial) "${fmt(Units.MPG_FACTOR / lPer100, 1)} mpg" else "${fmt(lPer100, 1)} L/100km"

fun Settings.unitPrice(totalCost: Double, liters: Double): String =
    if (liters <= 0) "-" else if (imperial) "$currency${fmt(totalCost / (liters / Units.L_PER_GAL))}/gal"
    else "$currency${fmt(totalCost / liters)}/L"

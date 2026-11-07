package com.gazai.fuel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
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
    Tab("Fuel up", Icons.Default.Add),
    Tab("Stats", Icons.Default.Star),
    Tab("Log", Icons.Default.DateRange),
    Tab("Garage", Icons.Default.Home),
    Tab("Settings", Icons.Default.Settings),
)

@OptIn(ExperimentalLayoutApi::class)
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
            // Edge-to-edge: the window no longer shrinks for the keyboard, so pad for it here.
            Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize()) {
                when (vm.tab) {
                    0 -> AddScreen(vm)
                    1 -> DashboardScreen(vm)
                    2 -> LogScreen(vm)
                    3 -> VehiclesScreen(vm)
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

fun Settings.efficiency(lPer100: Double) = when (effUnit) {
    "mpg" -> "${fmt(Units.MPG_FACTOR / lPer100, 1)} mpg"
    "l100" -> "${fmt(lPer100, 1)} L/100km"
    else -> "${fmt(100.0 / lPer100, 1)} km/L"
}

fun Settings.unitPrice(totalCost: Double, liters: Double): String =
    if (liters <= 0) "-" else if (imperial) "$currency${fmt(totalCost / (liters / Units.L_PER_GAL))}/gal"
    else "$currency${fmt(totalCost / liters)}/L"

/** Efficiency as a plain number in the user's chosen unit (for charts). */
fun Settings.effValue(lPer100: Double): Double = when (effUnit) {
    "mpg" -> Units.MPG_FACTOR / lPer100
    "l100" -> lPer100
    else -> 100.0 / lPer100
}

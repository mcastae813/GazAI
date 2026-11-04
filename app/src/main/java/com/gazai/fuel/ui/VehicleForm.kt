package com.gazai.fuel.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gazai.fuel.AppViewModel
import com.gazai.fuel.data.Units
import com.gazai.fuel.data.Vehicle
import com.gazai.fuel.fmt

private val fuelTypes = listOf("Regular", "Premium", "Diesel", "Other")

/** Add ([existing] == null) or edit a vehicle's details. */
@Composable
fun VehicleForm(vm: AppViewModel, existing: Vehicle?, onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val s = vm.settings
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: "car") }
    var make by remember(existing?.id) { mutableStateOf(existing?.make.orEmpty()) }
    var model by remember(existing?.id) { mutableStateOf(existing?.model.orEmpty()) }
    var year by remember(existing?.id) { mutableStateOf(existing?.year?.toString().orEmpty()) }
    var nickname by remember(existing?.id) {
        // Only keep a custom name; an auto name (e.g. "2019 Honda Civic") is rebuilt from the details.
        mutableStateOf(existing?.let { e -> e.name.takeIf { it != e.describe() } }.orEmpty())
    }
    var plate by remember(existing?.id) { mutableStateOf(existing?.plate.orEmpty()) }
    var fuel by remember(existing?.id) { mutableStateOf(existing?.fuelType.orEmpty()) }
    var tank by remember(existing?.id) {
        mutableStateOf(existing?.tankLiters?.let { fmt(if (s.imperial) it / Units.L_PER_GAL else it, 1) }.orEmpty())
    }
    var err by remember { mutableStateOf<String?>(null) }
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "car", onClick = { type = "car" }, label = { Text("Car") })
            FilterChip(selected = type == "motorcycle", onClick = { type = "motorcycle" }, label = { Text("Motorcycle") })
        }
        OutlinedTextField(make, { make = it }, label = { Text("Make / brand (e.g. Honda)") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(model, { model = it }, label = { Text("Model (e.g. Civic, CB500F)") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        Text("Fuel", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fuelTypes.forEach { f -> FilterChip(selected = fuel == f, onClick = { fuel = if (fuel == f) "" else f }, label = { Text(f) }) }
        }
        OutlinedTextField(
            tank, { tank = it }, label = { Text("Tank size (${if (s.imperial) "gal" else "L"}, optional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(plate, { plate = it }, label = { Text("Plate (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname (optional)") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                err = vm.saveVehicle(existing, type, make, model, year, nickname, plate, fuel, tank)
                if (err == null) onDone()
            }) { Text(if (existing == null) "Add vehicle" else "Save changes") }
            if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** One line of details: "2019 Honda Civic | Car | Regular | 47 L tank | ABC-123". */
fun vehicleSubtitle(v: Vehicle, imperial: Boolean): String = listOfNotNull(
    v.describe().takeIf { it.isNotBlank() && it != v.name },
    if (v.type == "motorcycle") "Motorcycle" else "Car",
    v.fuelType,
    v.tankLiters?.let { if (imperial) "${fmt(it / Units.L_PER_GAL, 1)} gal tank" else "${fmt(it, 0)} L tank" },
    v.plate,
).joinToString("  |  ")

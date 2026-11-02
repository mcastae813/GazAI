package com.gazai.fuel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gazai.fuel.ai.Images
import com.gazai.fuel.ai.OpenRouter
import com.gazai.fuel.ai.ParsedFill
import com.gazai.fuel.data.AppDb
import com.gazai.fuel.data.FuelEntry
import com.gazai.fuel.data.Settings
import com.gazai.fuel.data.SettingsStore
import com.gazai.fuel.data.Units
import com.gazai.fuel.data.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

fun fmt(d: Double, digits: Int = 2): String = String.format(Locale.US, "%.${digits}f", d)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.create(app).dao()
    private val store = SettingsStore(app)

    var settings by mutableStateOf(store.load()); private set

    val vehicles = dao.vehicles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val entries = dao.entries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var tab by mutableIntStateOf(0)

    // ---- Add screen state (form values are in the user's display units) ----
    var selectedVehicleId by mutableStateOf<Long?>(null)
    var note by mutableStateOf("")
    var imageUri by mutableStateOf<Uri?>(null)
    var date by mutableStateOf(LocalDate.now().toString())
    var odometer by mutableStateOf("")
    var volume by mutableStateOf("")
    var cost by mutableStateOf("")
    var station by mutableStateOf("")
    var fullTank by mutableStateOf(true)
    var fromAi by mutableStateOf(false)
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    fun currentVehicle(): Vehicle? =
        vehicles.value.firstOrNull { it.id == selectedVehicleId } ?: vehicles.value.firstOrNull()

    fun updateSettings(s: Settings) {
        store.save(s)
        settings = store.load()
    }

    fun addVehicle(name: String, type: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = dao.insertVehicle(Vehicle(name = name.trim(), type = type))
        if (selectedVehicleId == null) selectedVehicleId = id
    }

    fun deleteVehicle(v: Vehicle) = viewModelScope.launch { dao.deleteVehicle(v) }
    fun deleteEntry(e: FuelEntry) = viewModelScope.launch { dao.deleteEntry(e) }

    /** Called when another app shares a receipt photo or text to GazAI. */
    fun handleShared(text: String?, uri: Uri?) {
        tab = 1
        if (text != null) note = text
        if (uri != null) imageUri = uri
        if (settings.apiKey.isNotBlank()) analyze()
    }

    fun clearForm() {
        note = ""; imageUri = null; odometer = ""; volume = ""; cost = ""; station = ""
        date = LocalDate.now().toString(); fullTank = true; fromAi = false
    }

    fun analyze() {
        if (busy) return
        if (note.isBlank() && imageUri == null) {
            error = "Type a note or pick a photo first."; return
        }
        viewModelScope.launch {
            busy = true; error = null; message = null
            try {
                val uri = imageUri
                val dataUri = uri?.let {
                    withContext(Dispatchers.IO) { Images.toDataUri(getApplication(), it) }
                }
                val parsed = OpenRouter.parseFillUp(
                    apiKey = settings.apiKey,
                    explicitModel = if (uri != null) settings.visionModel else settings.textModel,
                    text = note,
                    imageDataUri = dataUri,
                    vehicles = vehicles.value,
                    today = LocalDate.now(),
                )
                applyParsed(parsed)
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    private suspend fun applyParsed(p: ParsedFill) {
        val imperial = settings.imperial
        p.vehicle?.let { name -> matchVehicle(name)?.let { selectedVehicleId = it.id } }

        val odoKm = p.odometer?.let { v ->
            when (p.odometerUnit) { "mi" -> v * Units.KM_PER_MI; "km" -> v; else -> if (imperial) v * Units.KM_PER_MI else v }
        }
        var liters = p.volume?.let { v ->
            when (p.volumeUnit?.lowercase()) { "gal" -> v * Units.L_PER_GAL; "l" -> v; else -> if (imperial) v * Units.L_PER_GAL else v }
        }
        var total = p.totalCost
        // price_per_unit is per the same unit as volume, so liters/cost can be derived either way.
        if (total == null && p.volume != null && p.pricePerUnit != null) total = p.volume * p.pricePerUnit
        if (liters == null && total != null && p.pricePerUnit != null && p.pricePerUnit > 0) {
            val vol = total / p.pricePerUnit
            liters = if (p.volumeUnit?.lowercase() == "gal" || (p.volumeUnit == null && imperial)) vol * Units.L_PER_GAL else vol
        }

        date = (p.date ?: LocalDate.now()).toString()
        odometer = odoKm?.let { fmt(if (imperial) it / Units.KM_PER_MI else it, 0) } ?: ""
        volume = liters?.let { fmt(if (imperial) it / Units.L_PER_GAL else it) } ?: ""
        cost = total?.let { fmt(it) } ?: ""
        station = p.station ?: ""
        fullTank = p.fullTank ?: true
        fromAi = true

        if (settings.autoSave && liters != null && total != null && currentVehicle() != null) {
            save(auto = true)
        } else {
            message = "Review the details below and tap Save."
        }
    }

    private fun matchVehicle(name: String): Vehicle? {
        val n = name.lowercase()
        val list = vehicles.value
        list.firstOrNull { it.name.lowercase() == n }?.let { return it }
        list.firstOrNull { n.contains(it.name.lowercase()) || it.name.lowercase().contains(n) }?.let { return it }
        val bike = listOf("moto", "bike", "scooter")
        val wantType = when {
            bike.any { n.contains(it) } -> "motorcycle"
            n.contains("car") -> "car"
            else -> return null
        }
        return list.singleOrNull { it.type == wantType }
    }

    fun save(auto: Boolean = false) {
        val vehicle = currentVehicle()
        if (vehicle == null) { error = "Add a vehicle first."; return }
        val imperial = settings.imperial
        val vol = volume.replace(',', '.').toDoubleOrNull()
        val money = cost.replace(',', '.').toDoubleOrNull()
        val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
        val odo = odometer.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        when {
            vol == null || vol <= 0 -> { error = "Enter the fuel amount."; return }
            money == null || money < 0 -> { error = "Enter the total cost."; return }
            day == null -> { error = "Date must look like 2026-11-02."; return }
            odometer.isNotBlank() && odo == null -> { error = "Odometer must be a number."; return }
        }
        val entry = FuelEntry(
            vehicleId = vehicle.id,
            dateMillis = day!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            odometerKm = odo?.let { if (imperial) it * Units.KM_PER_MI else it },
            liters = if (imperial) vol!! * Units.L_PER_GAL else vol!!,
            totalCost = money!!,
            fullTank = fullTank,
            station = station.trim().ifBlank { null },
            source = if (fromAi) "ai" else "manual",
        )
        val unit = if (imperial) "gal" else "L"
        val label = "${fmt(vol)} $unit for ${settings.currency}${fmt(money)} on ${vehicle.name}"
        viewModelScope.launch {
            dao.insertEntry(entry)
            clearForm()
            error = null
            message = if (auto) "Added automatically: $label" else "Saved: $label"
        }
    }
}

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
import com.gazai.fuel.ai.Ocr
import com.gazai.fuel.ai.OpenRouter
import com.gazai.fuel.ai.ParsedFill
import com.gazai.fuel.data.AppDb
import com.gazai.fuel.data.FuelEntry
import com.gazai.fuel.data.MaintenanceRecord
import com.gazai.fuel.data.Settings
import com.gazai.fuel.data.SettingsStore
import com.gazai.fuel.data.Units
import com.gazai.fuel.data.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

fun fmt(d: Double, digits: Int = 2): String = String.format(Locale.US, "%.${digits}f", d)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.create(app).dao()
    private val store = SettingsStore(app)
    /** Survives the app being killed while the camera app is open. */
    private val state = app.getSharedPreferences("gazai_state", android.content.Context.MODE_PRIVATE)

    var settings by mutableStateOf(store.load()); private set

    val vehicles = dao.vehicles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val entries = dao.entries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val maintenance = dao.maintenance().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 0 Add, 1 Stats, 2 Log, 3 Garage, 4 Settings. The app opens on Add. */
    var tab by mutableIntStateOf(0)

    private var pendingAutoCamera = settings.openCameraOnStart
    var lastAdded by mutableStateOf<FuelEntry?>(null); private set

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
    var status by mutableStateOf<String?>(null); private set
    var aiTestResult by mutableStateOf<String?>(null); private set
    var aiTesting by mutableStateOf(false); private set

    fun defaultVehicle(): Vehicle? =
        vehicles.value.firstOrNull { it.id == settings.defaultVehicleId } ?: vehicles.value.firstOrNull()

    fun currentVehicle(): Vehicle? =
        vehicles.value.firstOrNull { it.id == selectedVehicleId } ?: defaultVehicle()

    /** True once per app start when the camera should pop open for a quick pump photo. */
    fun consumeAutoCamera(): Boolean {
        if (!pendingAutoCamera || tab != 0 || imageUri != null) return false
        if (vehicles.value.isEmpty() || settings.apiKey.isBlank()) return false
        pendingAutoCamera = false
        selectedVehicleId = null // quick add always targets the default vehicle
        return true
    }

    /** The activity was recreated (e.g. Android killed it while the camera was open): don't pop the camera again. */
    fun onRestored() { pendingAutoCamera = false }

    fun newCameraUri(): Uri = Images.newCameraUri(getApplication()).also {
        state.edit().putString("cameraUri", it.toString()).commit()
    }

    fun onPhotoTaken(success: Boolean) {
        val uri = state.getString("cameraUri", null)?.let(Uri::parse)
        state.edit().remove("cameraUri").apply()
        pendingAutoCamera = false
        if (!success || uri == null) return
        imageUri = uri
        analyze()
    }

    fun testAi(apiKey: String, textModel: String) {
        if (aiTesting) return
        aiTesting = true
        aiTestResult = null
        viewModelScope.launch {
            aiTestResult = try { OpenRouter.selfTest(apiKey.trim(), textModel.trim()) } catch (e: Exception) { e.message }
            aiTesting = false
        }
    }

    fun updateSettings(s: Settings) {
        store.save(s)
        settings = store.load()
    }

    /**
     * Adds ([existing] == null) or updates a vehicle from the form. Text inputs are as typed; tank is in
     * display units. Returns an error message, or null on success.
     */
    fun saveVehicle(
        existing: Vehicle?, type: String, make: String, model: String, year: String,
        nickname: String, plate: String, fuelType: String, tank: String,
    ): String? {
        val y = year.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: return "Year must be a number like 2019." }
        if (y != null && (y < 1900 || y > LocalDate.now().year + 1)) return "Year looks wrong."
        val t = tank.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: return "Tank size must be a number." }
        val tankL = t?.let { if (settings.imperial) it * Units.L_PER_GAL else it }
        val clean = { x: String -> x.trim().ifBlank { null } }
        val describe = listOfNotNull(y?.toString(), clean(make), clean(model)).joinToString(" ")
        val name = nickname.trim().ifBlank { describe }
        if (name.isBlank()) return "Enter the make and model, or a name."
        val v = (existing ?: Vehicle(name = name, type = type)).copy(
            name = name, type = type, make = clean(make), model = clean(model), year = y,
            plate = clean(plate), fuelType = clean(fuelType), tankLiters = tankL,
        )
        viewModelScope.launch {
            if (existing == null) {
                val first = vehicles.value.isEmpty()
                val id = dao.insertVehicle(v)
                if (first) updateSettings(settings.copy(defaultVehicleId = id))
            } else {
                dao.updateVehicle(v)
            }
        }
        return null
    }

    fun deleteVehicle(v: Vehicle) = viewModelScope.launch {
        dao.deleteVehicle(v)
        v.photoPath?.let { File(it).delete() }
        if (settings.defaultVehicleId == v.id) updateSettings(settings.copy(defaultVehicleId = -1))
        if (selectedVehicleId == v.id) selectedVehicleId = null
    }

    fun setDefault(v: Vehicle) = updateSettings(settings.copy(defaultVehicleId = v.id))

    fun setVehiclePhoto(v: Vehicle, uri: Uri) = viewModelScope.launch {
        try {
            val path = withContext(Dispatchers.IO) { Images.saveVehiclePhoto(getApplication(), uri) }
            dao.updateVehicle(v.copy(photoPath = path))
            v.photoPath?.let { File(it).delete() }
        } catch (e: Exception) {
            error = "Could not save photo: ${e.message}"
        }
    }

    fun deleteEntry(e: FuelEntry) = viewModelScope.launch { dao.deleteEntry(e) }
    fun deleteMaintenance(m: MaintenanceRecord) = viewModelScope.launch { dao.deleteMaintenance(m) }

    fun undoLast() {
        val e = lastAdded ?: return
        lastAdded = null
        message = "Removed."
        viewModelScope.launch { dao.deleteEntry(e) }
    }

    /** Adds the odometer (display units) to the entry that was just auto-added. */
    fun attachOdometerToLast(text: String) {
        val e = lastAdded ?: return
        val v = text.replace(',', '.').trim().toDoubleOrNull()
        if (v == null || v <= 0) { error = "Odometer must be a number."; return }
        val km = if (settings.imperial) v * Units.KM_PER_MI else v
        val updated = e.copy(odometerKm = km)
        lastAdded = updated
        error = null
        message = "Odometer saved."
        viewModelScope.launch { dao.updateEntry(updated) }
    }

    /** Returns an error message, or null when the record was saved. Inputs are in display units. */
    fun addMaintenance(
        vehicleId: Long, type: String, date: String, odo: String, cost: String, notes: String, nextDue: String,
    ): String? {
        val imperial = settings.imperial
        val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull() ?: return "Date must look like 2026-11-02."
        if (type.isBlank()) return "Enter what was serviced."
        fun dist(t: String): Double? = t.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            ?.let { if (imperial) it * Units.KM_PER_MI else it }
        if (odo.isNotBlank() && dist(odo) == null) return "Odometer must be a number."
        if (nextDue.isNotBlank() && dist(nextDue) == null) return "Next due must be a number."
        val money = cost.replace(',', '.').trim().ifEmpty { "0" }.toDoubleOrNull() ?: return "Cost must be a number."
        val rec = MaintenanceRecord(
            vehicleId = vehicleId,
            dateMillis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            odometerKm = dist(odo),
            type = type.trim(),
            cost = money,
            notes = notes.trim().ifBlank { null },
            nextDueKm = dist(nextDue),
        )
        viewModelScope.launch { dao.insertMaintenance(rec) }
        return null
    }

    /** Called when another app shares a receipt photo or text to GazAI. */
    fun handleShared(text: String?, uri: Uri?) {
        pendingAutoCamera = false
        tab = 0
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
            error = "Type a note or take a photo first."; return
        }
        viewModelScope.launch {
            busy = true; error = null; message = null; lastAdded = null
            try {
                val uri = imageUri
                var dataUri: String? = null
                var ocr: String? = null
                if (uri != null) {
                    status = "Preparing photo..."
                    dataUri = try {
                        withContext(Dispatchers.IO) { Images.toDataUri(getApplication(), uri) }
                    } catch (e: Exception) {
                        throw IllegalStateException("Couldn't open the photo (${e.message}). Try taking it again.")
                    }
                    status = "Reading text on the photo..."
                    ocr = Ocr.read(getApplication(), uri)
                }
                status = "Asking a free AI model..."
                val result = OpenRouter.parseFillUp(
                    apiKey = settings.apiKey,
                    visionModel = settings.visionModel,
                    textModel = settings.textModel,
                    text = note,
                    imageDataUri = dataUri,
                    ocrText = ocr,
                    vehicles = vehicles.value,
                    today = LocalDate.now(),
                )
                applyParsed(result.fill, result.model)
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
                status = null
            }
        }
    }

    private fun applyParsed(p: ParsedFill, model: String) {
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

        val tank = currentVehicle()?.tankLiters
        val tooBig = tank != null && liters != null && liters > tank * 1.15
        val readBy = " (read by ${model.substringAfter('/')})"
        when {
            tooBig -> message = "The AI read more fuel than your ${fmt(tank!!, 0)} L tank holds. Check the numbers and tap Save.$readBy"
            settings.autoSave && liters != null && total != null && currentVehicle() != null -> save(auto = true, suffix = readBy)
            else -> message = "Check the details below and tap Save.$readBy"
        }
    }

    private fun matchVehicle(name: String): Vehicle? {
        val n = name.lowercase()
        val list = vehicles.value
        list.firstOrNull { it.name.lowercase() == n }?.let { return it }
        list.firstOrNull { n.contains(it.name.lowercase()) || it.name.lowercase().contains(n) }?.let { return it }
        list.filter { v -> listOfNotNull(v.make, v.model).any { it.isNotBlank() && n.contains(it.lowercase()) } }
            .singleOrNull()?.let { return it }
        val bike = listOf("moto", "bike", "scooter")
        val wantType = when {
            bike.any { n.contains(it) } -> "motorcycle"
            n.contains("car") -> "car"
            else -> return null
        }
        return list.singleOrNull { it.type == wantType }
    }

    fun save(auto: Boolean = false, suffix: String = "") {
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
            val id = dao.insertEntry(entry)
            lastAdded = entry.copy(id = id)
            clearForm()
            error = null
            message = (if (auto) "Added automatically: $label" else "Saved: $label") + suffix
        }
    }
}

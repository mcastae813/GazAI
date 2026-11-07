package com.gazai.fuel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gazai.fuel.ai.FillRules
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

fun fmt(d: Double, digits: Int = 2): String = String.format(Locale.US, "%.${digits}f", d)

/** Up to [maxDigits] decimals without trailing zeros: 32.415, 40, 8.56. */
fun plain(d: Double, maxDigits: Int = 3): String =
    BigDecimal(d).setScale(maxDigits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
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

    /** Vehicle the fill-up goes to (picked with a chip, or named in a typed note); null = the default. */
    var selectedVehicleId by mutableStateOf<Long?>(null); private set

    /** The user tapped a vehicle chip for the current form; the AI's guess never overrides that. */
    private var vehiclePicked = false

    /** Fill-up being changed (opened from the Log), or null when adding a new one. */
    var editing by mutableStateOf<FuelEntry?>(null); private set

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
    var saving by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var status by mutableStateOf<String?>(null); private set
    var aiTestResult by mutableStateOf<String?>(null); private set
    var aiTesting by mutableStateOf(false); private set

    private var analysis: Job? = null

    fun defaultVehicle(): Vehicle? = defaultVehicleIn(vehicles.value)

    fun currentVehicle(): Vehicle? = currentVehicleIn(vehicles.value)

    private fun defaultVehicleIn(list: List<Vehicle>): Vehicle? =
        list.firstOrNull { it.id == settings.defaultVehicleId } ?: list.firstOrNull()

    private fun currentVehicleIn(list: List<Vehicle>): Vehicle? =
        list.firstOrNull { it.id == selectedVehicleId } ?: defaultVehicleIn(list)

    /** A vehicle chip was tapped. */
    fun pickVehicle(id: Long) {
        selectedVehicleId = id
        vehiclePicked = true
    }

    /** True once per app start when the camera should pop open for a quick pump photo. */
    fun consumeAutoCamera(): Boolean {
        if (!pendingAutoCamera || tab != 0 || imageUri != null || editing != null) return false
        if (vehicles.value.isEmpty() || settings.apiKey.isBlank()) return false
        pendingAutoCamera = false
        selectedVehicleId = null // quick add always targets the default vehicle
        vehiclePicked = false
        return true
    }

    /** The activity was recreated (e.g. Android killed it while the camera was open): don't pop the camera again. */
    fun onRestored() { pendingAutoCamera = false }

    /** Settings may have been changed by another GazAI window (sharing to GazAI opens a second one). */
    fun reloadSettings() { settings = store.load() }

    fun newCameraUri(): Uri = Images.newCameraUri(getApplication()).also {
        state.edit()
            .putString("cameraUri", it.toString())
            .putLong("cameraVehicleId", selectedVehicleId ?: -1L)
            .commit()
    }

    fun onPhotoTaken(success: Boolean) {
        val uri = state.getString("cameraUri", null)?.let(Uri::parse)
        // Android may have closed GazAI while the camera was open; the photo is for the vehicle chosen before.
        val vehicleId = state.getLong("cameraVehicleId", -1L).takeIf { it >= 0 }
        state.edit().remove("cameraUri").remove("cameraVehicleId").apply()
        pendingAutoCamera = false
        if (!success || uri == null) return
        if (vehicleId != null) selectedVehicleId = vehicleId
        imageUri = uri
        analyze()
    }

    fun testAi(apiKey: String, textModel: String, visionModel: String) {
        if (aiTesting) return
        aiTesting = true
        aiTestResult = null
        viewModelScope.launch {
            try {
                val photo = withContext(Dispatchers.Default) { Images.testPumpPhoto() }
                aiTestResult = OpenRouter.selfTest(apiKey.trim(), textModel.trim(), visionModel.trim(), photo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aiTestResult = e.message
            } finally {
                aiTesting = false
            }
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
        fun Vehicle.withForm() = copy(
            name = name, type = type, make = clean(make), model = clean(model), year = y,
            plate = clean(plate), fuelType = clean(fuelType), tankLiters = tankL,
        )
        viewModelScope.launch {
            if (existing == null) {
                val first = vehicles.value.isEmpty()
                val id = dao.insertVehicle(Vehicle(name = name, type = type).withForm())
                if (first) updateSettings(settings.copy(defaultVehicleId = id))
            } else {
                // Re-read so a photo saved meanwhile isn't overwritten with the old one.
                val fresh = dao.vehicle(existing.id) ?: return@launch
                dao.updateVehicle(fresh.withForm())
            }
        }
        return null
    }

    fun deleteVehicle(v: Vehicle) = viewModelScope.launch {
        if (editing?.vehicleId == v.id) cancelEdit()
        if (lastAdded?.vehicleId == v.id) lastAdded = null
        dao.deleteVehicle(v)
        v.photoPath?.let { File(it).delete() }
        if (settings.defaultVehicleId == v.id) updateSettings(settings.copy(defaultVehicleId = -1))
        if (selectedVehicleId == v.id) selectedVehicleId = null
    }

    fun setDefault(v: Vehicle) = updateSettings(settings.copy(defaultVehicleId = v.id))

    /** Takes the vehicle id, not a [Vehicle], so it still works if the screen was recreated during the picker. */
    fun setVehiclePhoto(vehicleId: Long, uri: Uri) = viewModelScope.launch {
        try {
            val path = withContext(Dispatchers.IO) { Images.saveVehiclePhoto(getApplication(), uri) }
            val old = dao.vehicle(vehicleId)
            if (old == null) {
                File(path).delete()
                return@launch
            }
            dao.setVehiclePhoto(vehicleId, path)
            old.photoPath?.let { File(it).delete() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Could not save photo: ${e.message}"
        }
    }

    fun deleteEntry(e: FuelEntry) {
        if (editing?.id == e.id) cancelEdit()
        if (lastAdded?.id == e.id) lastAdded = null
        viewModelScope.launch { dao.deleteEntry(e) }
    }

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
        if (editing != null) cancelEdit()
        if (text != null) note = text
        if (uri != null) imageUri = uri
        if (settings.apiKey.isNotBlank()) analyze()
    }

    fun clearForm() {
        note = ""; imageUri = null; odometer = ""; volume = ""; cost = ""; station = ""
        date = LocalDate.now().toString(); fullTank = true; fromAi = false
        editing = null; vehiclePicked = false
    }

    /** Opens a saved fill-up in the Fuel up form so it can be corrected. */
    fun startEdit(e: FuelEntry) {
        cancelAnalysis()
        val imperial = settings.imperial
        clearForm()
        editing = e
        selectedVehicleId = e.vehicleId
        vehiclePicked = true
        date = Instant.ofEpochMilli(e.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        odometer = e.odometerKm?.let { plain(if (imperial) it / Units.KM_PER_MI else it, 1) }.orEmpty()
        volume = plain(if (imperial) e.liters / Units.L_PER_GAL else e.liters)
        cost = fmt(e.totalCost)
        station = e.station.orEmpty()
        fullTank = e.fullTank
        lastAdded = null; message = null; error = null
        tab = 0
    }

    fun cancelEdit() {
        clearForm()
        selectedVehicleId = null
        message = null; error = null
    }

    fun cancelAnalysis() { analysis?.cancel() }

    fun analyze() {
        if (busy) return
        if (note.isBlank() && imageUri == null) {
            error = "Type a note or take a photo first."; return
        }
        analysis = viewModelScope.launch {
            busy = true; error = null; message = null; lastAdded = null
            try {
                val uri = imageUri
                val text = note
                var dataUri: String? = null
                var ocr: String? = null
                if (uri != null) {
                    status = "Preparing photo..."
                    dataUri = try {
                        withContext(Dispatchers.IO) { Images.toDataUri(getApplication(), uri) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw IllegalStateException("Couldn't open the photo (${e.message}). Try taking it again.")
                    }
                    status = "Reading text on the photo..."
                    ocr = Ocr.read(getApplication(), uri)
                }
                // Read from the database: after Android restarts GazAI for the camera result, the Flows may be empty still.
                val list = dao.vehicleList()
                status = "Asking a free AI model..."
                val result = OpenRouter.parseFillUp(
                    apiKey = settings.apiKey,
                    visionModel = settings.visionModel,
                    textModel = settings.textModel,
                    text = text,
                    imageDataUri = dataUri,
                    ocrText = ocr,
                    vehicles = list,
                    today = LocalDate.now(),
                )
                applyParsed(result.fill, result.model, text, list)
            } catch (e: CancellationException) {
                if (editing == null) message = "Stopped. Type the details below, or try again."
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
                status = null
            }
        }
    }

    private suspend fun applyParsed(p: ParsedFill, model: String, noteText: String, list: List<Vehicle>) {
        val imperial = settings.imperial
        // A pump photo says nothing about which vehicle it is, so only a typed note may choose one,
        // and never over a vehicle the user picked.
        if (noteText.isNotBlank() && !vehiclePicked) {
            p.vehicle?.let { FillRules.matchVehicle(it, list) }?.let { selectedVehicleId = it.id }
        }
        val r = FillRules.resolve(p, imperial)

        date = (p.date ?: LocalDate.now()).toString()
        odometer = r.odometerKm?.let { fmt(if (imperial) it / Units.KM_PER_MI else it, 0) } ?: ""
        volume = r.liters?.let { plain(if (imperial) it / Units.L_PER_GAL else it) } ?: ""
        cost = r.totalCost?.let { fmt(it) } ?: ""
        station = p.station ?: ""
        fullTank = p.fullTank ?: true
        fromAi = true

        val vehicle = currentVehicleIn(list)
        val last = vehicle?.let { dao.lastEntry(it.id) } ?: dao.lastEntryAnyVehicle()
        val lastPrice = last?.takeIf { it.liters > 0 }?.let { it.totalCost / it.liters }
        val problem = FillRules.problem(p, r, vehicle?.tankLiters, lastPrice, imperial)
        val readBy = " (read by ${model.substringAfter('/')})"
        when {
            problem != null -> message = "$problem Check the numbers and tap Save.$readBy"
            editing == null && settings.autoSave && r.liters != null && r.totalCost != null && vehicle != null ->
                saveNow(auto = true, suffix = readBy)
            else -> message = "Check the details below and tap Save.$readBy"
        }
    }

    /** Save button: adds the form as a new fill-up, or saves the changes to [editing]. */
    fun save() {
        if (busy || saving) return
        viewModelScope.launch { saveNow(auto = false, suffix = "") }
    }

    private suspend fun saveNow(auto: Boolean, suffix: String) {
        val imperial = settings.imperial
        val vol = volume.replace(',', '.').trim().toDoubleOrNull()
        val money = cost.replace(',', '.').trim().toDoubleOrNull()
        val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
        val odo = odometer.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        when {
            vol == null || vol <= 0 -> { error = "Enter the fuel amount."; return }
            money == null || money < 0 -> { error = "Enter the total cost."; return }
            day == null -> { error = "Date must look like 2026-11-02."; return }
            odometer.isNotBlank() && odo == null -> { error = "Odometer must be a number."; return }
        }
        val edit = editing
        saving = true
        try {
            val vehicle = currentVehicleIn(dao.vehicleList())
            if (vehicle == null) { error = "Add a vehicle first."; return }
            val entry = FuelEntry(
                id = edit?.id ?: 0,
                vehicleId = vehicle.id,
                dateMillis = day!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                odometerKm = odo?.let { if (imperial) it * Units.KM_PER_MI else it },
                liters = if (imperial) vol!! * Units.L_PER_GAL else vol!!,
                totalCost = money!!,
                fullTank = fullTank,
                station = station.trim().ifBlank { null },
                source = edit?.source ?: if (fromAi) "ai" else "manual",
            )
            val label = "${plain(vol)} ${if (imperial) "gal" else "L"} for ${settings.currency}${fmt(money)} on ${vehicle.name}"
            if (edit != null) {
                dao.updateEntry(entry)
                clearForm()
                selectedVehicleId = null
                error = null
                message = "Updated: $label"
            } else {
                val id = dao.insertEntry(entry)
                lastAdded = entry.copy(id = id)
                clearForm()
                error = null
                message = (if (auto) "Added automatically: $label" else "Saved: $label") + suffix
            }
        } finally {
            saving = false
        }
    }
}

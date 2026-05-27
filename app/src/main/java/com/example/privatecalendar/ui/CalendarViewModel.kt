package com.example.privatecalendar.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.privatecalendar.R
import com.example.privatecalendar.data.*
import com.example.privatecalendar.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalTime

@OptIn(FlowPreview::class)
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    // PRIORIDAD CRÍTICA: Base de datos y eventos lo primero
    private val db = AppDatabase.getDatabase(application)
    val eventDao = db.eventDao()
    
    val allEvents = eventDao.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allCategories = eventDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val taskDao = db.quickTaskDao()
    private val settingsManager = SettingsManager(application)

    // Settings (Carga asíncrona pero sin bloquear los eventos)
    val isDarkMode = settingsManager.isDarkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val notificationLeadTime = settingsManager.notificationLeadTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)
    val isBiometricEnabled = settingsManager.isBiometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showHolidays = settingsManager.showHolidays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val holidayCountryCode = settingsManager.holidayCountryCode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ES")
    val languageCode = settingsManager.languageCode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "es")
    val themeName = settingsManager.themeName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DEFAULT")
    val allDayNotificationHour = settingsManager.allDayNotificationHour.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 9)
    val allDayNotificationDayBefore = settingsManager.allDayNotificationDayBefore.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val defaultViewMode = settingsManager.defaultViewMode.stateIn(viewModelScope, SharingStarted.Eagerly, "MONTH")

    // Holidays State
    private val _holidays = MutableStateFlow<List<Holiday>>(emptyList())
    val holidays: StateFlow<List<Holiday>> = _holidays.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(false)
    val isInitialLoading = _isInitialLoading.asStateFlow()

    init {
        // Retrasar festivos para no competir con la base de datos de eventos al arrancar
        viewModelScope.launch {
            delay(2000) 
            combine(holidayCountryCode, showHolidays) { code, show -> Pair(code, show) }
                .collect { (code, show) ->
                    if (show) {
                        val currentYear = LocalDate.now().year
                        val h1 = async(Dispatchers.IO) { HolidayProvider.getHolidaysForYear(getApplication(), currentYear, code) }
                        val h2 = async(Dispatchers.IO) { HolidayProvider.getHolidaysForYear(getApplication(), currentYear + 1, code) }
                        _holidays.value = (h1.await() + h2.await()).distinctBy { it.date to it.name }
                    } else {
                        _holidays.value = emptyList()
                    }
                }
        }
    }

    // Recalcular notificaciones con retardo para priorizar la UI inicial
    init {
        viewModelScope.launch {
            delay(3000)
            combine(
                notificationLeadTime, 
                allDayNotificationHour, 
                allDayNotificationDayBefore,
                allEvents
            ) { lead, hour, dayBefore, events ->
                Quadruple(lead, hour, dayBefore, events)
            }.distinctUntilChanged { old, new ->
                old.first == new.first && 
                old.second == new.second && 
                old.third == new.third &&
                (old.fourth.isNotEmpty() || new.fourth.isEmpty())
            }
            .debounce(1500)
            .collect {
                viewModelScope.launch(Dispatchers.Default) {
                    recalculateAllNotifications()
                }
            }
        }
    }

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { settingsManager.setDarkMode(enabled) }
    fun setNotificationLeadTime(minutes: Int) = viewModelScope.launch { 
        settingsManager.setNotificationLeadTime(minutes)
    }
    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch { settingsManager.setBiometricEnabled(enabled) }
    fun setShowHolidays(show: Boolean) = viewModelScope.launch { settingsManager.setShowHolidays(show) }
    fun setHolidayCountryCode(code: String) = viewModelScope.launch { settingsManager.setHolidayCountryCode(code) }
    fun setLanguageCode(code: String) = viewModelScope.launch { settingsManager.setLanguageCode(code) }
    fun setThemeName(name: String) = viewModelScope.launch { settingsManager.setThemeName(name) }

    fun setAllDayNotificationHour(hour: Int) = viewModelScope.launch {
        settingsManager.setAllDayNotificationHour(hour)
    }

    fun setAllDayNotificationDayBefore(dayBefore: Boolean) = viewModelScope.launch {
        settingsManager.setAllDayNotificationDayBefore(dayBefore)
    }

    fun setDefaultViewMode(mode: String) = viewModelScope.launch {
        settingsManager.setDefaultViewMode(mode)
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun recalculateAllNotifications() {
        val events = allEvents.value
        val lead = notificationLeadTime.value
        val adHour = allDayNotificationHour.value
        val adDayBefore = allDayNotificationDayBefore.value
        events.forEach { event ->
            NotificationHelper.scheduleNotification(
                getApplication(), event.date, event.time, event.title.text, event.id, lead, event.isAllDay, adHour, adDayBefore, event.recurrence
            )
        }
    }

    fun deleteEvent(event: Event) = viewModelScope.launch { eventDao.deleteEvent(event) }
    suspend fun insertEvent(event: Event): Long = eventDao.insertEvent(event)
    fun updateEvent(event: Event) = viewModelScope.launch { eventDao.updateEvent(event) }

    fun insertCategory(category: EventCategory) = viewModelScope.launch { eventDao.insertCategory(category) }
    fun updateCategory(category: EventCategory) = viewModelScope.launch { eventDao.updateCategory(category) }
    fun deleteCategory(category: EventCategory) = viewModelScope.launch { eventDao.deleteCategory(category) }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val events = allEvents.value
                val jsonArray = JSONArray()
                events.forEach { event ->
                    val obj = JSONObject().apply {
                        put("title", event.title.text)
                        put("description", event.description.text)
                        put("location", event.location?.text ?: JSONObject.NULL)
                        put("date", event.date.toString())
                        put("time", event.time?.toString() ?: JSONObject.NULL)
                        put("isAllDay", event.isAllDay)
                        put("recurrence", event.recurrence.name)
                        put("categoryId", event.categoryId ?: JSONObject.NULL)
                        put("attachments", JSONArray(event.attachments))
                    }
                    jsonArray.put(obj)
                }
                context.contentResolver.openOutputStream(uri)?.use { 
                    it.write(jsonArray.toString(4).toByteArray())
                }
                Toast.makeText(context, context.getString(R.string.backup_exported), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importData(context: Context, uri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isInitialLoading.value = true
            try {
                val data = withContext(Dispatchers.IO) {
                    val stringBuilder = StringBuilder()
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            var line: String? = reader.readLine()
                            while (line != null) {
                                stringBuilder.append(line)
                                line = reader.readLine()
                            }
                        }
                    }
                    stringBuilder.toString()
                }

                val jsonArray = JSONArray(data)
                val leadTime = notificationLeadTime.value
                val adHour = allDayNotificationHour.value
                val adDayBefore = allDayNotificationDayBefore.value
                val today = LocalDate.now()

                db.withTransaction {
                    for (i in 0 until jsonArray.length()) {
                        try {
                            val obj = jsonArray.getJSONObject(i)
                            val eventDate = LocalDate.parse(obj.getString("date"))

                            val event = Event(
                                title = EncryptedString(obj.getString("title")),
                                description = EncryptedString(obj.getString("description")),
                                location = if (obj.isNull("location")) null else EncryptedString(obj.getString("location")),
                                date = eventDate,
                                time = if (obj.isNull("time")) null else LocalTime.parse(obj.getString("time")),
                                isAllDay = obj.getBoolean("isAllDay"),
                                recurrence = RecurrenceType.valueOf(obj.optString("recurrence", "NONE")),
                                categoryId = if (obj.isNull("categoryId")) null else obj.getInt("categoryId"),
                                attachments = if (obj.isNull("attachments")) emptyList() else {
                                    val arr = obj.getJSONArray("attachments")
                                    val list = mutableListOf<String>()
                                    for (j in 0 until arr.length()) list.add(arr.getString(j))
                                    list
                                }
                            )
                            val id = eventDao.insertEvent(event)

                            // Solo programar si no es un evento pasado
                            if (!eventDate.isBefore(today)) {
                                NotificationHelper.scheduleNotification(
                                    context, event.date, event.time, event.title.text, id.toInt(),
                                    leadTime, event.isAllDay, adHour, adDayBefore, event.recurrence
                                )
                            }
                        } catch (_: Exception) {
                            // Error individual no detiene el proceso
                        }
                    }
                }
                Toast.makeText(context, context.getString(R.string.data_imported), Toast.LENGTH_SHORT).show()
                onComplete()
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Error importing backup", e)
                Toast.makeText(context, context.getString(R.string.import_error), Toast.LENGTH_SHORT).show()
            } finally {
                _isInitialLoading.value = false
            }
        }
    }

    fun importFromGoogleCalendar(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isInitialLoading.value = true
            try {
                val leadTime = notificationLeadTime.value
                val adHour = allDayNotificationHour.value
                val adDayBefore = allDayNotificationDayBefore.value
                val count = GoogleCalendarImporter.importGoogleEventsSuspend(context, eventDao, leadTime, adHour, adDayBefore)
                
                if (count > 0) {
                    Toast.makeText(context, "Se importaron $count eventos de Google Calendar", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No se encontraron eventos nuevos para importar", Toast.LENGTH_SHORT).show()
                }
                onComplete()
            } catch (_: SecurityException) {
                Toast.makeText(context, "Permiso denegado para acceder al calendario", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Error importing from Google Calendar", e)
                Toast.makeText(context, "Error al importar de Google Calendar", Toast.LENGTH_SHORT).show()
            } finally {
                _isInitialLoading.value = false
            }
        }
    }
}

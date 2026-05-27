package com.example.privatecalendar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

data class Holiday(val date: LocalDate, val name: String)

object HolidayProvider {
    private val cache = mutableMapOf<String, List<Holiday>>()

    val SUPPORTED_COUNTRIES = mapOf(
        "ES" to "España",
        "MX" to "México",
        "AR" to "Argentina",
        "US" to "EE.UU.",
        "CO" to "Colombia",
        "CL" to "Chile",
        "PE" to "Perú",
        "BR" to "Brasil",
        "VE" to "Venezuela",
        "UY" to "Uruguay",
        "EC" to "Ecuador",
        "PA" to "Panamá",
        "CR" to "Costa Rica",
        "DO" to "Rep. Dominicana",
        "GT" to "Guatemala",
        "HN" to "Honduras",
        "NI" to "Nicaragua",
        "SV" to "El Salvador",
        "PY" to "Paraguay",
        "BO" to "Bolivia",
        "FR" to "Francia",
        "DE" to "Alemania",
        "IT" to "Italia",
        "GB" to "Reino Unido",
        "PT" to "Portugal",
        "CA" to "Canadá"
    )

    suspend fun getHolidaysForYear(context: Context, year: Int, countryCode: String): List<Holiday> = withContext(Dispatchers.IO) {
        val cacheKey = "$year-$countryCode"
        cache[cacheKey]?.let { return@withContext it }

        // Intentar cargar de disco primero
        val diskHolidays = loadFromDisk(context, year, countryCode)
        if (diskHolidays.isNotEmpty()) {
            cache[cacheKey] = diskHolidays
            return@withContext diskHolidays
        }

        // Obtener de API externa (Nager.Date)
        val apiHolidays = fetchFromApi(year, countryCode)
        
        // Obtener locales (especialmente para fechas que no son festivos oficiales pero son importantes)
        val localHolidays = when (countryCode.uppercase()) {
            "ES" -> getSpanishHolidays(year)
            "MX" -> getMexicanHolidays(year)
            "AR" -> getArgentineHolidays(year)
            "US" -> getUSHolidays(year)
            else -> emptyList()
        }

        // Combinar ambas listas eliminando duplicados
        val combined = (apiHolidays + localHolidays).distinctBy { 
            // Normalizar un poco el nombre para evitar duplicados como "Año Nuevo" vs "Año nuevo"
            it.date.toString() + "_" + it.name.lowercase().trim()
        }.sortedBy { it.date }

        if (combined.isNotEmpty()) {
            cache[cacheKey] = combined
            saveToDisk(context, year, countryCode, combined)
        }
        
        return@withContext combined
    }

    private fun saveToDisk(context: Context, year: Int, countryCode: String, holidays: List<Holiday>) {
        try {
            val prefs = context.getSharedPreferences("holiday_cache", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            holidays.forEach { 
                val obj = org.json.JSONObject()
                obj.put("date", it.date.toString())
                obj.put("name", it.name)
                jsonArray.put(obj)
            }
            prefs.edit().putString("cache_${year}_$countryCode", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromDisk(context: Context, year: Int, countryCode: String): List<Holiday> {
        return try {
            val prefs = context.getSharedPreferences("holiday_cache", Context.MODE_PRIVATE)
            val json = prefs.getString("cache_${year}_$countryCode", null) ?: return emptyList()
            val jsonArray = JSONArray(json)
            val result = mutableListOf<Holiday>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(Holiday(LocalDate.parse(obj.getString("date")), obj.getString("name")))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchFromApi(year: Int, countryCode: String): List<Holiday> {
        return try {
            val url = URL("https://date.nager.at/api/v3/PublicHolidays/$year/$countryCode")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val result = mutableListOf<Holiday>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val date = LocalDate.parse(obj.getString("date"))
                    val name = obj.getString("localName")
                    result.add(Holiday(date, name))
                }
                result
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    internal fun getSpanishHolidays(year: Int): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(LocalDate.of(year, Month.JANUARY, 6), "Epifanía del Señor"),
            Holiday(LocalDate.of(year, Month.MARCH, 19), "San José / Día del Padre"),
            Holiday(easterSunday.minusDays(3), "Jueves Santo"),
            Holiday(easterSunday.minusDays(2), "Viernes Santo"),
            Holiday(easterSunday, "Domingo de Resurrección"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Fiesta del Trabajo"),
            Holiday(nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 1), "Día de la Madre"),
            Holiday(LocalDate.of(year, Month.AUGUST, 15), "Asunción de la Virgen"),
            Holiday(LocalDate.of(year, Month.OCTOBER, 12), "Fiesta Nacional de España"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 1), "Todos los Santos"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 6), "Día de la Constitución"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 8), "Inmaculada Concepción"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Natividad del Señor")
        )
    }

    internal fun getMexicanHolidays(year: Int): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(nthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 1), "Día de la Constitución"),
            Holiday(nthWeekdayOfMonth(year, Month.MARCH, DayOfWeek.MONDAY, 3), "Natalicio de Benito Juárez"),
            Holiday(easterSunday.minusDays(3), "Jueves Santo"),
            Holiday(easterSunday.minusDays(2), "Viernes Santo"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Día del Trabajo"),
            Holiday(LocalDate.of(year, Month.MAY, 5), "Batalla de Puebla"),
            Holiday(LocalDate.of(year, Month.MAY, 10), "Día de la Madre"),
            Holiday(nthWeekdayOfMonth(year, Month.JUNE, DayOfWeek.SUNDAY, 3), "Día del Padre"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 16), "Día de la Independencia"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 1), "Día de Todos los Santos"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 2), "Día de Muertos"),
            Holiday(nthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.MONDAY, 3), "Día de la Revolución"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 12), "Día de la Virgen de Guadalupe"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Navidad")
        )
    }

    internal fun getArgentineHolidays(year: Int): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(easterSunday.minusDays(48), "Carnaval (Lunes)"),
            Holiday(easterSunday.minusDays(47), "Carnaval (Martes)"),
            Holiday(LocalDate.of(year, Month.MARCH, 24), "Día de la Memoria"),
            Holiday(LocalDate.of(year, Month.APRIL, 2), "Día del Veterano y de los Caídos en Malvinas"),
            Holiday(easterSunday.minusDays(3), "Jueves Santo"),
            Holiday(easterSunday.minusDays(2), "Viernes Santo"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Día del Trabajador"),
            Holiday(LocalDate.of(year, Month.MAY, 25), "Revolución de Mayo"),
            Holiday(LocalDate.of(year, Month.JUNE, 17), "Paso a la Inmortalidad de Güemes"),
            Holiday(LocalDate.of(year, Month.JUNE, 20), "Paso a la Inmortalidad de Belgrano"),
            Holiday(nthWeekdayOfMonth(year, Month.JUNE, DayOfWeek.SUNDAY, 3), "Día del Padre"),
            Holiday(LocalDate.of(year, Month.JULY, 9), "Día de la Independencia"),
            Holiday(LocalDate.of(year, Month.AUGUST, 17), "Paso a la Inmortalidad de San Martín"),
            Holiday(LocalDate.of(year, Month.OCTOBER, 12), "Día del Respeto a la Diversidad Cultural"),
            Holiday(nthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.SUNDAY, 3), "Día de la Madre"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 20), "Día de la Soberanía Nacional"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 8), "Inmaculada Concepción"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Navidad")
        )
    }

    internal fun getUSHolidays(year: Int): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "New Year's Day"),
            Holiday(nthWeekdayOfMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3), "Martin Luther King Jr. Day"),
            Holiday(nthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3), "Washington's Birthday"),
            Holiday(easterSunday, "Easter Sunday"),
            Holiday(nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2), "Mother's Day"),
            Holiday(lastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY), "Memorial Day"),
            Holiday(LocalDate.of(year, Month.JUNE, 19), "Juneteenth National Independence Day"),
            Holiday(nthWeekdayOfMonth(year, Month.JUNE, DayOfWeek.SUNDAY, 3), "Father's Day"),
            Holiday(LocalDate.of(year, Month.JULY, 4), "Independence Day"),
            Holiday(nthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1), "Labor Day"),
            Holiday(nthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2), "Columbus Day"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 11), "Veterans Day"),
            Holiday(nthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4), "Thanksgiving Day"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Christmas Day")
        )
    }

    private fun nthWeekdayOfMonth(
        year: Int,
        month: Month,
        dayOfWeek: DayOfWeek,
        occurrence: Int
    ): LocalDate {
        require(occurrence >= 1) { "occurrence must be >= 1" }

        var date = LocalDate.of(year, month, 1)
        while (date.dayOfWeek != dayOfWeek) {
            date = date.plusDays(1)
        }

        return date.plusWeeks((occurrence - 1).toLong())
    }

    private fun lastWeekdayOfMonth(year: Int, month: Month, dayOfWeek: DayOfWeek): LocalDate {
        var date = LocalDate.of(year, month, 1).withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())
        while (date.dayOfWeek != dayOfWeek) {
            date = date.minusDays(1)
        }
        return date
    }

    // Algoritmo de Meeus/Jones/Butcher para calendario gregoriano.
    private fun calculateEasterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1

        return LocalDate.of(year, month, day)
    }
}

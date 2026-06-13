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
        val normalizedCountryCode = countryCode.uppercase()
        val cacheKey = "$year-$normalizedCountryCode"
        cache[cacheKey]?.let { return@withContext it }

        val localHolidays = getLocalHolidays(year, normalizedCountryCode)

        // Intentar cargar de disco primero, pero no devolverlo tal cual: las versiones antiguas
        // de la cache pueden no tener celebraciones locales como Día del Padre/Madre.
        val diskHolidays = loadFromDisk(context, year, normalizedCountryCode)
        if (diskHolidays.isNotEmpty()) {
            val enrichedDiskHolidays = mergeHolidays(diskHolidays, localHolidays)
            cache[cacheKey] = enrichedDiskHolidays
            if (enrichedDiskHolidays.size != diskHolidays.size) {
                saveToDisk(context, year, normalizedCountryCode, enrichedDiskHolidays)
            }
            return@withContext enrichedDiskHolidays
        }

        // Obtener de API externa (Nager.Date)
        val apiHolidays = fetchFromApi(year, normalizedCountryCode)

        // Combinar ambas listas eliminando duplicados
        val combined = mergeHolidays(apiHolidays, localHolidays)

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
            prefs.edit().putString("cache_${year}_${countryCode.uppercase()}", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromDisk(context: Context, year: Int, countryCode: String): List<Holiday> {
        return try {
            val prefs = context.getSharedPreferences("holiday_cache", Context.MODE_PRIVATE)
            val json = prefs.getString("cache_${year}_${countryCode.uppercase()}", null) ?: return emptyList()
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

    private fun getLocalHolidays(year: Int, countryCode: String): List<Holiday> {
        return when (countryCode.uppercase()) {
            "ES" -> getSpanishHolidays(year)
            "MX" -> getMexicanHolidays(year)
            "AR" -> getArgentineHolidays(year)
            "US" -> getUSHolidays(year)
            "CO" -> getColombianHolidays(year)
            "CL" -> getChileanHolidays(year)
            "PE" -> getPeruvianHolidays(year)
            "BR" -> getBrazilianHolidays(year)
            "VE" -> getVenezuelanHolidays(year)
            "UY" -> getUruguayanHolidays(year)
            "EC" -> getEcuadorianHolidays(year)
            "PA" -> getPanamanianHolidays(year)
            "CR" -> getCostaRicanHolidays(year)
            "DO" -> getDominicanHolidays(year)
            "GT" -> getGuatemalanHolidays(year)
            "HN" -> getHonduranHolidays(year)
            "NI" -> getNicaraguanHolidays(year)
            "SV" -> getSalvadoranHolidays(year)
            "PY" -> getParaguayanHolidays(year)
            "BO" -> getBolivianHolidays(year)
            "FR" -> getFrenchHolidays(year)
            "DE" -> getGermanHolidays(year)
            "IT" -> getItalianHolidays(year)
            "GB" -> getBritishHolidays(year)
            "PT" -> getPortugueseHolidays(year)
            "CA" -> getCanadianHolidays(year)
            else -> emptyList()
        }
    }

    private fun mergeHolidays(first: List<Holiday>, second: List<Holiday>): List<Holiday> {
        return (first + second)
            .distinctBy { it.date.toString() + "_" + it.name.lowercase().trim() }
            .sortedBy { it.date }
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

    internal fun getColombianHolidays(year: Int): List<Holiday> = commonLatinFamilyHolidays(year) + listOf(
        Holiday(LocalDate.of(year, Month.JULY, 20), "Día de la Independencia"),
        Holiday(LocalDate.of(year, Month.AUGUST, 7), "Batalla de Boyacá"),
        Holiday(LocalDate.of(year, Month.DECEMBER, 7), "Día de las Velitas")
    )

    internal fun getChileanHolidays(year: Int): List<Holiday> = commonLatinFamilyHolidays(year) + listOf(
        Holiday(LocalDate.of(year, Month.SEPTEMBER, 18), "Fiestas Patrias"),
        Holiday(LocalDate.of(year, Month.SEPTEMBER, 19), "Día de las Glorias del Ejército")
    )

    internal fun getPeruvianHolidays(year: Int): List<Holiday> = commonLatinFamilyHolidays(year) + listOf(
        Holiday(LocalDate.of(year, Month.JULY, 28), "Fiestas Patrias"),
        Holiday(LocalDate.of(year, Month.JULY, 29), "Fiestas Patrias")
    )

    internal fun getBrazilianHolidays(year: Int): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return commonFamilyHolidays(year, mothersDay = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2)) + listOf(
            Holiday(easterSunday.minusDays(48), "Carnaval"),
            Holiday(easterSunday.minusDays(47), "Carnaval"),
            Holiday(LocalDate.of(year, Month.APRIL, 21), "Tiradentes"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 7), "Independência do Brasil"),
            Holiday(LocalDate.of(year, Month.OCTOBER, 12), "Nossa Senhora Aparecida"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 2), "Finados"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 15), "Proclamação da República")
        )
    }

    internal fun getVenezuelanHolidays(year: Int): List<Holiday> = commonLatinFamilyHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.JULY, 5), "Día de la Independencia"))
    internal fun getUruguayanHolidays(year: Int): List<Holiday> = commonLatinFamilyHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.AUGUST, 25), "Declaratoria de la Independencia"))
    internal fun getEcuadorianHolidays(year: Int): List<Holiday> = commonLatinFamilyHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.AUGUST, 10), "Primer Grito de Independencia"))
    internal fun getPanamanianHolidays(year: Int): List<Holiday> = commonLatinFamilyHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.NOVEMBER, 3), "Separación de Panamá de Colombia"))
    internal fun getCostaRicanHolidays(year: Int): List<Holiday> = commonLatinFamilyHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getDominicanHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = lastWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY), fathersDay = lastWeekdayOfMonth(year, Month.JULY, DayOfWeek.SUNDAY)) + listOf(Holiday(LocalDate.of(year, Month.FEBRUARY, 27), "Día de la Independencia"))
    internal fun getGuatemalanHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 10)) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getHonduranHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 10)) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getNicaraguanHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 30), fathersDay = LocalDate.of(year, Month.JUNE, 23)) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getSalvadoranHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 10), fathersDay = LocalDate.of(year, Month.JUNE, 17)) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getParaguayanHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, fathersDay = LocalDate.of(year, Month.JUNE, 16)) + listOf(Holiday(LocalDate.of(year, Month.MAY, 14), "Día de la Independencia"), Holiday(LocalDate.of(year, Month.MAY, 15), "Día de la Madre"))
    internal fun getBolivianHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 27), fathersDay = LocalDate.of(year, Month.MARCH, 19)) + listOf(Holiday(LocalDate.of(year, Month.AUGUST, 6), "Día de la Independencia"))

    internal fun getFrenchHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = lastWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY)) + listOf(Holiday(LocalDate.of(year, Month.JULY, 14), "Fête nationale"))
    internal fun getGermanHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2), fathersDay = calculateEasterSunday(year).plusDays(39)) + listOf(Holiday(LocalDate.of(year, Month.OCTOBER, 3), "Tag der Deutschen Einheit"))
    internal fun getItalianHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2), fathersDay = LocalDate.of(year, Month.MARCH, 19)) + listOf(Holiday(LocalDate.of(year, Month.JUNE, 2), "Festa della Repubblica"))
    internal fun getBritishHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = calculateEasterSunday(year).minusDays(21)) + listOf(Holiday(LocalDate.of(year, Month.NOVEMBER, 5), "Bonfire Night"))
    internal fun getPortugueseHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 1), fathersDay = LocalDate.of(year, Month.MARCH, 19)) + listOf(Holiday(LocalDate.of(year, Month.JUNE, 10), "Dia de Portugal"))
    internal fun getCanadianHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year, mothersDay = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2)) + listOf(Holiday(LocalDate.of(year, Month.JULY, 1), "Canada Day"), Holiday(nthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2), "Thanksgiving Day"))

    private fun commonLatinFamilyHolidays(year: Int): List<Holiday> = commonFamilyHolidays(year)

    private fun commonFamilyHolidays(
        year: Int,
        mothersDay: LocalDate = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2),
        fathersDay: LocalDate = nthWeekdayOfMonth(year, Month.JUNE, DayOfWeek.SUNDAY, 3)
    ): List<Holiday> = listOf(
        Holiday(LocalDate.of(year, Month.FEBRUARY, 14), "Día de San Valentín"),
        Holiday(mothersDay, "Día de la Madre"),
        Holiday(fathersDay, "Día del Padre"),
        Holiday(LocalDate.of(year, Month.OCTOBER, 31), "Halloween"),
        Holiday(LocalDate.of(year, Month.DECEMBER, 24), "Nochebuena"),
        Holiday(LocalDate.of(year, Month.DECEMBER, 31), "Nochevieja")
    )

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

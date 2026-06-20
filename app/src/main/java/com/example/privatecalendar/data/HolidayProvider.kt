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
        
        // Cargar siempre las observancias locales/comunes (que pueden cambiar en el código)
        val localHolidays = getLocalHolidays(year, normalizedCountryCode)
        val commonObservances = getCommonObservances(year, normalizedCountryCode)
        val codeBasedHolidays = mergeHolidays(localHolidays, commonObservances)

        // Intentar obtener de la memoria primero
        cache[cacheKey]?.let { cached ->
            return@withContext mergeHolidays(cached, codeBasedHolidays)
        }

        // Cargar de disco para tener algo inmediato
        val diskHolidays = loadFromDisk(context, year, normalizedCountryCode)
        
        // Intentar actualizar de la API
        val apiHolidays = fetchFromApi(year, normalizedCountryCode)
        
        val finalResult = if (apiHolidays.isNotEmpty()) {
            // Si la API responde, combinamos con lo local y actualizamos disco
            val combined = mergeHolidays(apiHolidays, codeBasedHolidays)
            saveToDisk(context, year, normalizedCountryCode, combined)
            combined
        } else if (diskHolidays.isNotEmpty()) {
            // Si la API falla pero hay disco, combinamos disco + local actual
            mergeHolidays(diskHolidays, codeBasedHolidays)
        } else {
            // Si todo falla, al menos tenemos lo que está en el código
            codeBasedHolidays
        }

        cache[cacheKey] = finalResult
        return@withContext finalResult
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
        val all = first + second
        val result = mutableListOf<Holiday>()
        
        // Agrupar por fecha
        val byDate = all.groupBy { it.date }
        
        for ((_, holidaysOnDate) in byDate) {
            if (holidaysOnDate.size == 1) {
                result.add(holidaysOnDate[0])
                continue
            }
            
            // Si hay varios, filtrar duplicados inteligentes
            val uniqueForDay = mutableListOf<Holiday>()
            for (h in holidaysOnDate.sortedByDescending { it.name.length }) { // Preferir nombres más largos/descriptivos
                if (uniqueForDay.none { isSimilar(it.name, h.name) }) {
                    uniqueForDay.add(h)
                }
            }
            result.addAll(uniqueForDay)
        }
        
        return result.sortedBy { it.date }
    }

    private fun isSimilar(name1: String, name2: String): Boolean {
        val n1 = normalize(name1)
        val n2 = normalize(name2)
        
        if (n1 == n2) return true
        if (n1.contains(n2) || n2.contains(n1)) return true
        
        // Sinónimos comunes
        val synonyms = listOf(
            setOf("hispanidad", "fiesta nacional de espana", "dia de la fiesta nacional de espana", "fiesta nacional"),
            setOf("constitucion", "dia de la constitucion", "dia de la constitucion espanola"),
            setOf("inmaculada concepcion", "la inmaculada concepcion"),
            setOf("reyes", "epifania", "dia de reyes", "epifania del senor"),
            setOf("trabajo", "trabajador", "dia del trabajo", "dia del trabajador", "fiesta del trabajo"),
            setOf("navidad", "natividad del senor", "dia de navidad"),
            setOf("ano nuevo", "confraternizacao universal"),
            setOf("viernes santo", "good friday"),
            setOf("jueves santo", "maundy thursday"),
            setOf("domingo de resurreccion", "easter sunday", "pascua"),
            setOf("lunes de pascua", "easter monday"),
            setOf("todos los santos", "all saints day")
        )
        
        for (s in synonyms) {
            if (s.any { n1.contains(it) } && s.any { n2.contains(it) }) return true
        }
        
        return false
    }

    private fun normalize(name: String): String {
        return name.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .trim()
    }

    private fun getCommonObservances(year: Int, countryCode: String): List<Holiday> {
        val isEnglish = countryCode in listOf("US", "GB", "CA")
        val isPortuguese = countryCode in listOf("BR", "PT")
        
        return listOfNotNull(
            Holiday(LocalDate.of(year, Month.FEBRUARY, 14), if (isEnglish) "Valentine's Day" else if (isPortuguese) "Dia dos Namorados" else "Día de San Valentín"),
            Holiday(LocalDate.of(year, Month.OCTOBER, 31), if (isEnglish) "Halloween" else "Halloween"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 24), if (isEnglish) "Christmas Eve" else if (isPortuguese) "Véspera de Natal" else "Nochebuena"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 31), if (isEnglish) "New Year's Eve" else if (isPortuguese) "Véspera de Ano Novo" else "Nochevieja"),
            
            // Día de los Inocentes / April Fools
            if (countryCode == "ES" || countryCode in listOf("MX", "AR", "CO", "CL", "PE", "VE", "UY", "EC", "PA", "CR", "DO", "GT", "HN", "NI", "SV", "PY", "BO")) {
                Holiday(LocalDate.of(year, Month.DECEMBER, 28), "Día de los Santos Inocentes")
            } else if (isEnglish || countryCode in listOf("FR", "DE", "IT")) {
                Holiday(LocalDate.of(year, Month.APRIL, 1), if (isEnglish) "April Fools' Day" else if (countryCode == "FR") "Poisson d'avril" else "April Fools")
            } else null
        )
    }

    internal fun getSpanishHolidays(year: Int): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        val holidays = mutableListOf(
            // Festivos nacionales (No laborables en toda España)
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(LocalDate.of(year, Month.JANUARY, 6), "Epifanía del Señor (Reyes)"),
            Holiday(easterSunday.minusDays(2), "Viernes Santo"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Fiesta del Trabajo"),
            Holiday(LocalDate.of(year, Month.AUGUST, 15), "Asunción de la Virgen"),
            Holiday(LocalDate.of(year, Month.OCTOBER, 12), "Fiesta Nacional de España"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 1), "Todos los Santos"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 6), "Día de la Constitución"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 8), "Inmaculada Concepción"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Natividad del Señor (Navidad)"),
            
            // Festivos comunes (Suelen ser no laborables en casi todas las CCAA)
            Holiday(easterSunday.minusDays(3), "Jueves Santo"),
            Holiday(LocalDate.of(year, Month.MARCH, 19), "San José"),
            Holiday(easterSunday.plusDays(1), "Lunes de Pascua"),
            Holiday(LocalDate.of(year, Month.JULY, 25), "Santiago Apóstol"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 26), "San Esteban"),

            // Observancias y Festividades Populares (Google Calendar "Other observances")
            Holiday(easterSunday.minusDays(46), "Miércoles de Ceniza"),
            Holiday(easterSunday.minusDays(7), "Domingo de Ramos"),
            Holiday(easterSunday, "Domingo de Resurrección"),
            Holiday(nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 1), "Día de la Madre"),
            Holiday(LocalDate.of(year, Month.MARCH, 19), "Día del Padre"),
            Holiday(LocalDate.of(year, Month.APRIL, 23), "Sant Jordi / Día del Libro"),
            Holiday(LocalDate.of(year, Month.JUNE, 23), "Víspera de San Juan"),
            Holiday(LocalDate.of(year, Month.JUNE, 24), "San Juan"),
            Holiday(easterSunday.plusDays(60), "Corpus Christi"),
            Holiday(easterSunday.plusDays(49), "Pentecostés (Lunes de Pentecostés)"),
            Holiday(lastWeekdayOfMonth(year, Month.MARCH, DayOfWeek.SUNDAY), "Cambio de hora (Verano)"),
            Holiday(lastWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.SUNDAY), "Cambio de hora (Invierno)"),
            
            // Festividades autonómicas habituales
            Holiday(LocalDate.of(year, Month.FEBRUARY, 28), "Día de Andalucía"),
            Holiday(LocalDate.of(year, Month.MARCH, 1), "Día de las Illes Balears"),
            Holiday(LocalDate.of(year, Month.APRIL, 23), "Día de Aragón / Castilla y León"),
            Holiday(LocalDate.of(year, Month.MAY, 2), "Día de la Comunidad de Madrid"),
            Holiday(LocalDate.of(year, Month.MAY, 17), "Día das Letras Galegas"),
            Holiday(LocalDate.of(year, Month.MAY, 30), "Día de Canarias"),
            Holiday(LocalDate.of(year, Month.MAY, 31), "Día de Castilla-La Mancha"),
            Holiday(LocalDate.of(year, Month.JUNE, 9), "Día de La Rioja / Región de Murcia"),
            Holiday(LocalDate.of(year, Month.JULY, 28), "Día de las Instituciones de Cantabria"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 2), "Día de Ceuta"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 8), "Día de Asturias / Extremadura"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 11), "Diada Nacional de Catalunya"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "La Bien Aparecida (Cantabria)"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 17), "Día de Melilla"),
            Holiday(LocalDate.of(year, Month.OCTOBER, 9), "Día de la Comunitat Valenciana")
        )

        // Manejo de traslados (Ej: Si el 12 de Octubre es domingo, se suele pasar al lunes en algunas CCAA)
        // Pero para simplificar y coincidir con Google Calendar "General Spain", mantenemos el día original
        // y Google suele marcarlo como "Fiesta Nacional (observado)" si se traslada.
        
        return holidays.distinctBy { it.date to it.name }.sortedBy { it.date }
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
            Holiday(LocalDate.of(year, Month.MARCH, 17), "St. Patrick's Day"),
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

    internal fun getColombianHolidays(year: Int): List<Holiday> = commonLatinHolidays(year) + listOf(
        Holiday(LocalDate.of(year, Month.JULY, 20), "Día de la Independencia"),
        Holiday(LocalDate.of(year, Month.AUGUST, 7), "Batalla de Boyacá"),
        Holiday(LocalDate.of(year, Month.DECEMBER, 7), "Día de las Velitas")
    )

    internal fun getChileanHolidays(year: Int): List<Holiday> = commonLatinHolidays(year) + listOf(
        Holiday(LocalDate.of(year, Month.SEPTEMBER, 18), "Fiestas Patrias"),
        Holiday(LocalDate.of(year, Month.SEPTEMBER, 19), "Día de las Glorias del Ejército")
    )

    internal fun getPeruvianHolidays(year: Int): List<Holiday> = commonLatinHolidays(year) + listOf(
        Holiday(LocalDate.of(year, Month.JULY, 28), "Fiestas Patrias"),
        Holiday(LocalDate.of(year, Month.JULY, 29), "Fiestas Patrias")
    )

    internal fun getBrazilianHolidays(year: Int): List<Holiday> {
        return commonBrazilianHolidays(year) + listOf(
            Holiday(LocalDate.of(year, Month.APRIL, 21), "Tiradentes"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 7), "Independência do Brasil"),
            Holiday(LocalDate.of(year, Month.OCTOBER, 12), "Nossa Senhora Aparecida"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 2), "Finados"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 15), "Proclamação da República")
        )
    }

    internal fun getVenezuelanHolidays(year: Int): List<Holiday> = commonLatinHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.JULY, 5), "Día de la Independencia"))
    internal fun getUruguayanHolidays(year: Int): List<Holiday> = commonLatinHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.AUGUST, 25), "Declaratoria de la Independencia"))
    internal fun getEcuadorianHolidays(year: Int): List<Holiday> = commonLatinHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.AUGUST, 10), "Primer Grito de Independencia"))
    internal fun getPanamanianHolidays(year: Int): List<Holiday> = commonLatinHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.NOVEMBER, 3), "Separación de Panamá de Colombia"))
    internal fun getCostaRicanHolidays(year: Int): List<Holiday> = commonLatinHolidays(year) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getDominicanHolidays(year: Int): List<Holiday> = commonLatinHolidays(year, mothersDay = lastWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY), fathersDay = lastWeekdayOfMonth(year, Month.JULY, DayOfWeek.SUNDAY)) + listOf(Holiday(LocalDate.of(year, Month.FEBRUARY, 27), "Día de la Independencia"))
    internal fun getGuatemalanHolidays(year: Int): List<Holiday> = commonLatinHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 10)) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getHonduranHolidays(year: Int): List<Holiday> = commonLatinHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 10)) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getNicaraguanHolidays(year: Int): List<Holiday> = commonLatinHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 30), fathersDay = LocalDate.of(year, Month.JUNE, 23)) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getSalvadoranHolidays(year: Int): List<Holiday> = commonLatinHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 10), fathersDay = LocalDate.of(year, Month.JUNE, 17)) + listOf(Holiday(LocalDate.of(year, Month.SEPTEMBER, 15), "Día de la Independencia"))
    internal fun getParaguayanHolidays(year: Int): List<Holiday> = commonLatinHolidays(year, fathersDay = LocalDate.of(year, Month.JUNE, 16)) + listOf(Holiday(LocalDate.of(year, Month.MAY, 14), "Día de la Independencia"), Holiday(LocalDate.of(year, Month.MAY, 15), "Día de la Madre"))
    internal fun getBolivianHolidays(year: Int): List<Holiday> = commonLatinHolidays(year, mothersDay = LocalDate.of(year, Month.MAY, 27), fathersDay = LocalDate.of(year, Month.MARCH, 19)) + listOf(Holiday(LocalDate.of(year, Month.AUGUST, 6), "Día de la Independencia"))

    internal fun getFrenchHolidays(year: Int): List<Holiday> = commonEuropeanHolidays(year, mothersDay = lastWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY)) + listOf(Holiday(LocalDate.of(year, Month.JULY, 14), "Fête nationale"))
    internal fun getGermanHolidays(year: Int): List<Holiday> = commonEuropeanHolidays(year, mothersDay = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2), fathersDay = calculateEasterSunday(year).plusDays(39)) + listOf(Holiday(LocalDate.of(year, Month.OCTOBER, 3), "Tag der Deutschen Einheit"))
    internal fun getItalianHolidays(year: Int): List<Holiday> = commonEuropeanHolidays(year, mothersDay = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2), fathersDay = LocalDate.of(year, Month.MARCH, 19)) + listOf(Holiday(LocalDate.of(year, Month.JUNE, 2), "Festa della Repubblica"))
    internal fun getBritishHolidays(year: Int): List<Holiday> = commonEuropeanHolidays(year, mothersDay = calculateEasterSunday(year).minusDays(21)) + listOf(Holiday(LocalDate.of(year, Month.NOVEMBER, 5), "Bonfire Night"))
    internal fun getPortugueseHolidays(year: Int): List<Holiday> = commonEuropeanHolidays(year, mothersDay = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 1), fathersDay = LocalDate.of(year, Month.MARCH, 19)) + listOf(Holiday(LocalDate.of(year, Month.JUNE, 10), "Dia de Portugal"))
    internal fun getCanadianHolidays(year: Int): List<Holiday> = commonNorthAmericanHolidays() + listOf(Holiday(LocalDate.of(year, Month.JULY, 1), "Canada Day"), Holiday(nthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2), "Thanksgiving Day"))

    private fun commonLatinHolidays(
        year: Int,
        mothersDay: LocalDate = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2),
        fathersDay: LocalDate = nthWeekdayOfMonth(year, Month.JUNE, DayOfWeek.SUNDAY, 3)
    ): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return commonFamilyHolidays(year, mothersDay, fathersDay) + listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(easterSunday.minusDays(48), "Carnaval (Lunes)"),
            Holiday(easterSunday.minusDays(47), "Carnaval (Martes)"),
            Holiday(easterSunday.minusDays(3), "Jueves Santo"),
            Holiday(easterSunday.minusDays(2), "Viernes Santo"),
            Holiday(easterSunday, "Domingo de Resurrección"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Día del Trabajo"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 1), "Día de Todos los Santos"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 8), "Inmaculada Concepción"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Navidad")
        )
    }

    private fun commonBrazilianHolidays(year: Int): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return commonFamilyHolidays(
            year,
            mothersDay = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2)
        ) + listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Confraternização Universal"),
            Holiday(easterSunday.minusDays(48), "Carnaval"),
            Holiday(easterSunday.minusDays(47), "Carnaval"),
            Holiday(easterSunday.minusDays(2), "Sexta-feira Santa"),
            Holiday(easterSunday, "Páscoa"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Dia do Trabalhador"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 2), "Finados"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Natal")
        )
    }

    private fun commonEuropeanHolidays(
        year: Int,
        mothersDay: LocalDate = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2),
        fathersDay: LocalDate = nthWeekdayOfMonth(year, Month.JUNE, DayOfWeek.SUNDAY, 3)
    ): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return commonFamilyHolidays(year, mothersDay, fathersDay) + listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(easterSunday.minusDays(2), "Viernes Santo"),
            Holiday(easterSunday, "Domingo de Resurrección"),
            Holiday(easterSunday.plusDays(1), "Lunes de Pascua"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Día del Trabajo"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 1), "Todos los Santos"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Navidad"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 26), "San Esteban / Boxing Day")
        )
    }

    private fun commonNorthAmericanHolidays(): List<Holiday> = emptyList()

    private fun commonFamilyHolidays(
        year: Int,
        mothersDay: LocalDate = nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, 2),
        fathersDay: LocalDate = nthWeekdayOfMonth(year, Month.JUNE, DayOfWeek.SUNDAY, 3)
    ): List<Holiday> = listOf(
        Holiday(mothersDay, "Día de la Madre"),
        Holiday(fathersDay, "Día del Padre")
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

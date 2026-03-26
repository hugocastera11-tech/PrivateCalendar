package com.example.privatecalendar

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.*
import com.example.privatecalendar.data.*
import com.example.privatecalendar.ui.tasks.QuickTasksScreen
import com.example.privatecalendar.ui.theme.PrivateCalendarTheme
import com.example.privatecalendar.widget.CalendarWidget
import com.example.privatecalendar.worker.NotificationWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import androidx.glance.appwidget.updateAll

class MainActivity : AppCompatActivity() {
    companion object {
        const val CHANNEL_ID = "events_channel"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current.applicationContext
            val settingsManager = remember { SettingsManager(context) }
            val isDarkTheme by settingsManager.isDarkMode.collectAsState(initial = false)
            val leadTime by settingsManager.notificationLeadTime.collectAsState(initial = 30)
            val isBiometricEnabled by settingsManager.isBiometricEnabled.collectAsState(initial = false)
            val showHolidays by settingsManager.showHolidays.collectAsState(initial = true)
            val holidayCountryCode by settingsManager.holidayCountryCode.collectAsState(initial = Locale.getDefault().country)
            
            val navController = rememberNavController()
            val db = remember { AppDatabase.getDatabase(context) }
            val eventDao = db.eventDao()
            val taskDao = db.quickTaskDao()
            val scope = rememberCoroutineScope()

            var isAuthenticated by remember { mutableStateOf(false) }
            var isAuthChecked by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val biometricEnabled = settingsManager.isBiometricEnabled.first()
                if (biometricEnabled) {
                    showBiometricPrompt(
                        onSuccess = { 
                            isAuthenticated = true
                            isAuthChecked = true
                            checkAndRequestNotifications()
                        },
                        onError = { 
                            Toast.makeText(context, "Autenticación necesaria", Toast.LENGTH_SHORT).show()
                            finish() 
                        }
                    )
                } else {
                    isAuthenticated = true
                    isAuthChecked = true
                    checkAndRequestNotifications()
                }
            }

            LaunchedEffect(Unit) {
                eventDao.getAllEvents()
                    .distinctUntilChanged()
                    .collect {
                        updateWidget(context)
                    }
            }

            PrivateCalendarTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isAuthenticated) {
                        NavHost(
                            navController = navController, 
                            startDestination = "calendar",
                            enterTransition = { 
                                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = spring(stiffness = Spring.StiffnessLow)) 
                            },
                            exitTransition = { 
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = spring(stiffness = Spring.StiffnessLow)) 
                            },
                            popEnterTransition = { 
                                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = spring(stiffness = Spring.StiffnessLow)) 
                            },
                            popExitTransition = { 
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = spring(stiffness = Spring.StiffnessLow)) 
                            }
                        ) {
                            composable("calendar") {
                                CalendarScreen(
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onNavigateToTasks = { navController.navigate("quick_tasks") },
                                    eventDao = eventDao,
                                    leadTime = leadTime,
                                    showHolidays = showHolidays,
                                    holidayCountryCode = holidayCountryCode
                                )
                            }
                            composable("quick_tasks") {
                                QuickTasksScreen(
                                    onBack = { navController.popBackStack() },
                                    taskDao = taskDao
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    isDarkTheme = isDarkTheme,
                                    onThemeChange = { 
                                        scope.launch { settingsManager.setDarkMode(it) }
                                    },
                                    leadTime = leadTime,
                                    onLeadTimeChange = {
                                        scope.launch { settingsManager.setNotificationLeadTime(it) }
                                    },
                                    isBiometricEnabled = isBiometricEnabled,
                                    onBiometricChange = { enabled ->
                                        if (enabled) {
                                            checkBiometricAvailability(
                                                onAvailable = {
                                                    showBiometricPrompt(
                                                        onSuccess = {
                                                            scope.launch { settingsManager.setBiometricEnabled(true) }
                                                        },
                                                        onError = {
                                                            Toast.makeText(context, "Error biometría", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                },
                                                onUnavailable = {
                                                    Toast.makeText(context, "No disponible", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        } else {
                                            scope.launch { settingsManager.setBiometricEnabled(false) }
                                        }
                                    },
                                    showHolidays = showHolidays,
                                    onShowHolidaysChange = { scope.launch { settingsManager.setShowHolidays(it) } },
                                    holidayCountryCode = holidayCountryCode,
                                    onHolidayCountryChange = { scope.launch { settingsManager.setHolidayCountryCode(it) } },
                                    eventDao = eventDao
                                )
                            }
                        }
                    } else if (!isAuthChecked) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkBiometricAvailability(onAvailable: () -> Unit, onUnavailable: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> onAvailable()
            else -> onUnavailable()
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit, onError: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError()
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Acceso Seguro")
            .setSubtitle("Autentícate para entrar")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Avisos", NotificationManager.IMPORTANCE_HIGH)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }
}

enum class CalendarViewMode {
    WEEK, MONTH, YEAR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToTasks: () -> Unit,
    eventDao: EventDao,
    leadTime: Int,
    showHolidays: Boolean,
    holidayCountryCode: String
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var displayedDate by remember { mutableStateOf(LocalDate.now()) }
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    
    var showAddEditDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<Event?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<Event?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    
    val allEvents by eventDao.getAllEvents().collectAsState(initial = emptyList())
    
    val holidays = remember(displayedDate.year, holidayCountryCode, showHolidays) {
        if (showHolidays) HolidayProvider.getHolidaysForYear(displayedDate.year, holidayCountryCode)
        else emptyList()
    }

    val filteredResults = remember(allEvents, holidays, searchQuery) {
        if (searchQuery.isEmpty()) emptyList()
        else {
            val eventMatches = allEvents.filter { 
                it.title.contains(searchQuery, ignoreCase = true) || 
                it.description.contains(searchQuery, ignoreCase = true)
            }.map { SearchResult.UserEvent(it) }

            val holidayMatches = holidays.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }.map { SearchResult.HolidayEvent(it) }

            (eventMatches + holidayMatches).sortedBy { 
                when(it) {
                    is SearchResult.UserEvent -> it.event.date
                    is SearchResult.HolidayEvent -> it.holiday.date
                }
            }
        }
    }

    val eventsOnSelectedDate = remember(allEvents, selectedDate) {
        allEvents.filter { isEventOnDate(it, selectedDate) }
    }

    val holidayOnSelectedDate = remember(holidays, selectedDate) {
        holidays.find { it.date == selectedDate }
    }
    
    val datesWithEvents = remember(allEvents, displayedDate, viewMode) {
        calculateDatesWithEvents(allEvents, displayedDate, viewMode)
    }

    val holidayDates = remember(holidays) {
        holidays.map { it.date }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + slideInVertically { -it })
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + slideOutVertically { -it })
                },
                label = "TopBarSearchTransition"
            ) { active ->
                if (active) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Buscar eventos...") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = { 
                            Text("Private Calendar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light) 
                        },
                        actions = {
                            IconButton(onClick = onNavigateToTasks) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, "Tareas", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { eventToEdit = null; showAddEditDialog = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) { Icon(Icons.Default.Add, "Add") }
        }
    ) { innerPadding ->
        BackHandler(isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + scaleIn(initialScale = 0.95f))
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + scaleOut(targetScale = 0.95f))
                },
                label = "SearchContentTransition"
            ) { active ->
                if (active) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                    ) {
                        items(filteredResults) { result ->
                            Box(modifier = Modifier.animateItem()) {
                                when (result) {
                                    is SearchResult.UserEvent -> {
                                        EventItem(
                                            event = result.event,
                                            onEdit = { 
                                                selectedDate = result.event.date
                                                displayedDate = result.event.date
                                                isSearchActive = false
                                                eventToEdit = result.event
                                                showAddEditDialog = true 
                                            },
                                            onDelete = { eventToDelete = result.event; showDeleteConfirmDialog = true }
                                        )
                                    }
                                    is SearchResult.HolidayEvent -> {
                                        HolidaySearchItem(holiday = result.holiday) {
                                            selectedDate = result.holiday.date
                                            displayedDate = result.holiday.date
                                            isSearchActive = false
                                        }
                                    }
                                }
                            }
                        }
                        if (filteredResults.isEmpty() && searchQuery.isNotEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No se encontraron resultados", color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                } else {
                    Column {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            SegmentedButton(
                                selected = viewMode == CalendarViewMode.WEEK,
                                onClick = { viewMode = CalendarViewMode.WEEK },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                            ) { Text("Semana") }
                            SegmentedButton(
                                selected = viewMode == CalendarViewMode.MONTH,
                                onClick = { viewMode = CalendarViewMode.MONTH },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                            ) { Text("Mes") }
                            SegmentedButton(
                                selected = viewMode == CalendarViewMode.YEAR,
                                onClick = { viewMode = CalendarViewMode.YEAR },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                            ) { Text("Año") }
                        }

                        AnimatedContent(
                            targetState = viewMode,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                 scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                                .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                            },
                            label = "CalendarViewTransition"
                        ) { targetMode ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(16.dp)
                            ) {
                                AnimatedContent(
                                    targetState = displayedDate,
                                    transitionSpec = {
                                        if (targetState.isAfter(initialState)) {
                                            (slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn())
                                                .togetherWith(slideOutHorizontally(targetOffsetX = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut())
                                        } else {
                                            (slideInHorizontally(initialOffsetX = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn())
                                                .togetherWith(slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut())
                                        }.using(SizeTransform(clip = false))
                                    },
                                    label = "DateContentTransition"
                                ) { targetDate ->
                                    when (targetMode) {
                                        CalendarViewMode.WEEK -> WeekView(
                                            currentDate = targetDate,
                                            selectedDate = selectedDate,
                                            onDateSelected = { selectedDate = it; displayedDate = it },
                                            onDateChange = { displayedDate = it },
                                            datesWithEvents = datesWithEvents,
                                            holidayDates = holidayDates
                                        )
                                        CalendarViewMode.MONTH -> MonthView(
                                            displayedMonth = YearMonth.from(targetDate),
                                            selectedDate = selectedDate,
                                            onDateSelected = { selectedDate = it; displayedDate = it },
                                            onMonthChange = { displayedDate = it.atDay(1) },
                                            datesWithEvents = datesWithEvents,
                                            holidayDates = holidayDates
                                        )
                                        CalendarViewMode.YEAR -> YearView(
                                            displayedYear = targetDate.year,
                                            onMonthSelected = { month ->
                                                displayedDate = LocalDate.of(targetDate.year, month, 1)
                                                viewMode = CalendarViewMode.MONTH
                                            },
                                            onYearChange = { displayedDate = LocalDate.of(it, targetDate.monthValue, 1) }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        AnimatedContent(
                            targetState = selectedDate,
                            transitionSpec = {
                                if (targetState.isAfter(initialState)) {
                                    (slideInVertically(initialOffsetY = { it / 2 }) + fadeIn())
                                        .togetherWith(slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut())
                                } else {
                                    (slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn())
                                        .togetherWith(slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut())
                                }
                            },
                            label = "DateTextTransition"
                        ) { date ->
                            Column {
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                holidayOnSelectedDate?.let { holiday ->
                                    Text(
                                        text = "🚩 ${holiday.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            if (eventsOnSelectedDate.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("Sin eventos", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            items(eventsOnSelectedDate, key = { it.id }) { event ->
                                Box(modifier = Modifier.animateItem()) {
                                    EventItem(
                                        event = event,
                                        onEdit = { eventToEdit = event; showAddEditDialog = true },
                                        onDelete = { eventToDelete = event; showDeleteConfirmDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        EventDialog(
            selectedDate = selectedDate,
            event = eventToEdit,
            onDismiss = { showAddEditDialog = false },
            onConfirm = { title, desc, time, isAllDay, date, recurrence ->
                scope.launch {
                    if (eventToEdit == null) {
                        val newEvent = Event(date = date, time = time, title = title, description = desc, isAllDay = isAllDay, recurrence = recurrence)
                        val id = eventDao.insertEvent(newEvent)
                        scheduleNotification(context, date, time, title, desc, id.toInt(), leadTime, isAllDay)
                    } else {
                        val updatedEvent = eventToEdit!!.copy(title = title, description = desc, time = time, date = date, isAllDay = isAllDay, recurrence = recurrence)
                        eventDao.updateEvent(updatedEvent)
                        scheduleNotification(context, date, time, title, desc, updatedEvent.id, leadTime, isAllDay)
                    }
                    updateWidget(context)
                }
                showAddEditDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog && eventToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Eliminar", fontWeight = FontWeight.Medium) },
            text = { Text("¿Deseas eliminar este evento?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        eventDao.deleteEvent(eventToDelete!!)
                        WorkManager.getInstance(context).cancelUniqueWork("event_notification_${eventToDelete!!.id}")
                        updateWidget(context)
                    }
                    showDeleteConfirmDialog = false
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

sealed class SearchResult {
    data class UserEvent(val event: Event) : SearchResult()
    data class HolidayEvent(val holiday: Holiday) : SearchResult()
}

@Composable
fun HolidaySearchItem(holiday: Holiday, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🚩")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = holiday.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = holiday.date.format(DateTimeFormatter.ofPattern("d MMM, yyyy")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

fun calculateDatesWithEvents(allEvents: List<Event>, displayedDate: LocalDate, viewMode: CalendarViewMode): List<LocalDate> {
    if (allEvents.isEmpty()) return emptyList()
    val range = when(viewMode) {
        CalendarViewMode.WEEK -> {
            val start = displayedDate.with(DayOfWeek.MONDAY)
            (0..6).map { start.plusDays(it.toLong()) }
        }
        CalendarViewMode.MONTH -> {
            val start = YearMonth.from(displayedDate).atDay(1)
            (0 until YearMonth.from(displayedDate).lengthOfMonth()).map { start.plusDays(it.toLong()) }
        }
        CalendarViewMode.YEAR -> emptyList()
    }
    return range.filter { date -> allEvents.any { isEventOnDate(it, date) } }
}

@Composable
fun WeekView(
    currentDate: LocalDate,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    datesWithEvents: List<LocalDate>,
    holidayDates: List<LocalDate>
) {
    val firstDayOfWeek = currentDate.with(DayOfWeek.MONDAY)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentDate.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }} ${currentDate.year}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            Row {
                IconButton(onClick = { onDateChange(currentDate.minusWeeks(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Prev", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onDateChange(currentDate.plusWeeks(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", modifier = Modifier.size(20.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            (0..6).forEach { i ->
                val date = firstDayOfWeek.plusDays(i.toLong())
                val isSelected = date == selectedDate
                val isToday = date == LocalDate.now()
                val hasEvent = datesWithEvents.contains(date)
                val isHoliday = holidayDates.contains(date)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onDateSelected(date) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else if (isHoliday) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.primary else if (isHoliday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected || isToday || isHoliday) FontWeight.Bold else FontWeight.Normal
                    )
                    if (hasEvent && !isSelected) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                    }
                }
            }
        }
    }
}

@Composable
fun MonthView(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    datesWithEvents: List<LocalDate>,
    holidayDates: List<LocalDate>
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text(
                text = "${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }} ${displayedMonth.year}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            Row {
                IconButton(onClick = { onMonthChange(displayedMonth.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Prev", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onMonthChange(displayedMonth.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", modifier = Modifier.size(20.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                )
            }
        }
        val firstDayOfMonth = displayedMonth.atDay(1)
        val firstDayIndex = (firstDayOfMonth.dayOfWeek.value - 1)
        val totalDays = displayedMonth.lengthOfMonth()
        var dayCounter = 1
        for (week in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in 0..6) {
                    val cellIndex = week * 7 + dow
                    if (cellIndex >= firstDayIndex && dayCounter <= totalDays) {
                        val date = displayedMonth.atDay(dayCounter)
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()
                        val hasEvent = datesWithEvents.contains(date)
                        val isHoliday = holidayDates.contains(date)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onDateSelected(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayCounter.toString(),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.primary else if (isHoliday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isToday || isSelected || isHoliday) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (hasEvent && !isSelected) {
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                                }
                            }
                        }
                        dayCounter++
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
            if (dayCounter > totalDays) break
        }
    }
}

@Composable
fun YearView(
    displayedYear: Int,
    onMonthSelected: (Int) -> Unit,
    onYearChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayedYear.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraLight,
                color = MaterialTheme.colorScheme.primary
            )
            Row {
                IconButton(onClick = { onYearChange(displayedYear - 1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
                IconButton(onClick = { onYearChange(displayedYear + 1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().height(400.dp)
        ) {
            items((1..12).toList()) { month ->
                val monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
                Surface(
                    onClick = { onMonthSelected(month) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.aspectRatio(1.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = monthName.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventItem(event: Event, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (event.isAllDay) Icons.Default.CalendarToday else Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                val recurrenceText = if (event.recurrence != RecurrenceType.NONE) " • 🔁" else ""
                Text(
                    text = "${if (event.isAllDay) "Todo el día" else event.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""}$recurrenceText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    selectedDate: LocalDate,
    event: Event? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalTime?, Boolean, LocalDate, RecurrenceType) -> Unit
) {
    var title by remember { mutableStateOf(event?.title ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var isAllDay by remember { mutableStateOf(event?.isAllDay ?: false) }
    var eventDate by remember { mutableStateOf(event?.date ?: selectedDate) }
    var recurrence by remember { mutableStateOf(event?.recurrence ?: RecurrenceType.NONE) }
    
    val context = LocalContext.current
    val timePickerState = rememberTimePickerState(
        initialHour = event?.time?.hour ?: 12,
        initialMinute = event?.time?.minute ?: 0,
        is24Hour = true
    )

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(if (event == null) "Nuevo Evento" else "Editar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light)
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = title, onValueChange = { title = it }, placeholder = { Text("Título") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Surface(
                onClick = {
                    val dialog = DatePickerDialog(context, { _, year, month, dayOfMonth ->
                        eventDate = LocalDate.of(year, month + 1, dayOfMonth)
                    }, eventDate.year, eventDate.monthValue - 1, eventDate.dayOfMonth)
                    dialog.show()
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(eventDate.format(DateTimeFormatter.ofPattern("d MMM, yyyy")))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description, onValueChange = { description = it }, placeholder = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
                Spacer(modifier = Modifier.width(12.dp))
                Text("Todo el día", style = MaterialTheme.typography.bodyMedium)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Repetir:", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RecurrenceType.entries.forEach { type ->
                    FilterChip(
                        selected = recurrence == type,
                        onClick = { recurrence = type },
                        label = { Text(when(type){
                            RecurrenceType.NONE -> "No"
                            RecurrenceType.DAILY -> "Día"
                            RecurrenceType.WEEKLY -> "Sem"
                            RecurrenceType.MONTHLY -> "Mes"
                            RecurrenceType.YEARLY -> "Año"
                        }, fontSize = 10.sp) }
                    )
                }
            }
            
            AnimatedVisibility(visible = !isAllDay) {
                Column {
                    Spacer(modifier = Modifier.height(24.dp))
                    TimePicker(state = timePickerState, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val time = if (isAllDay) null else LocalTime.of(timePickerState.hour, timePickerState.minute)
                        onConfirm(title, description, time, isAllDay, eventDate, recurrence)
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = title.isNotBlank()
                ) { Text("Guardar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    leadTime: Int,
    onLeadTimeChange: (Int) -> Unit,
    isBiometricEnabled: Boolean,
    onBiometricChange: (Boolean) -> Unit,
    showHolidays: Boolean,
    onShowHolidaysChange: (Boolean) -> Unit,
    holidayCountryCode: String,
    onHolidayCountryChange: (String) -> Unit,
    eventDao: EventDao
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportData(context, it, eventDao, scope) }
    }
    
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importData(context, it, eventDao, scope) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Light) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                SettingsItem(
                    title = "Modo Oscuro",
                    icon = Icons.Default.BrightnessMedium,
                    trailing = { Switch(checked = isDarkTheme, onCheckedChange = onThemeChange) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsItem(
                    title = "Bloqueo Biométrico",
                    icon = Icons.Default.Lock,
                    trailing = { Switch(checked = isBiometricEnabled, onCheckedChange = onBiometricChange) }
                )
            }

            SettingsCard {
                SettingsItem(
                    title = "Mostrar Festivos",
                    icon = Icons.Default.Flag,
                    trailing = { Switch(checked = showHolidays, onCheckedChange = onShowHolidaysChange) }
                )
                AnimatedVisibility(visible = showHolidays) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Public, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("País de festivos", style = MaterialTheme.typography.bodyLarge)
                            }
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { showMenu = true }) {
                                    Text(HolidayProvider.SUPPORTED_COUNTRIES[holidayCountryCode] ?: holidayCountryCode)
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    HolidayProvider.SUPPORTED_COUNTRIES.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = { 
                                                onHolidayCountryChange(code)
                                                showMenu = false 
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Aviso previo", style = MaterialTheme.typography.titleSmall)
                    Text("$leadTime minutos antes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Slider(
                        value = leadTime.toFloat(),
                        onValueChange = { onLeadTimeChange(it.roundToInt()) },
                        valueRange = 0f..120f,
                        steps = 23
                    )
                }
            }
            
            SettingsCard {
                SettingsItem(
                    title = "Exportar Copia (.json)",
                    icon = Icons.Default.Download,
                    trailing = { 
                        IconButton(onClick = { exportLauncher.launch("calendar_backup.json") }) {
                            Icon(Icons.Default.Download, null)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsItem(
                    title = "Importar Copia",
                    icon = Icons.Default.Upload,
                    trailing = { 
                        IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                            Icon(Icons.Default.Upload, null)
                        }
                    }
                )
            }
        }
    }
}

fun exportData(context: Context, uri: Uri, eventDao: EventDao, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        try {
            val events = eventDao.getAllEvents().first()
            val jsonArray = JSONArray()
            events.forEach { event ->
                val obj = JSONObject().apply {
                    put("title", event.title)
                    put("description", event.description)
                    put("date", event.date.toString())
                    put("time", event.time?.toString() ?: JSONObject.NULL)
                    put("isAllDay", event.isAllDay)
                    put("recurrence", event.recurrence.name)
                }
                jsonArray.put(obj)
            }
            context.contentResolver.openOutputStream(uri)?.use { 
                it.write(jsonArray.toString(4).toByteArray())
            }
            Toast.makeText(context, "Copia exportada", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Error al exportar", Toast.LENGTH_SHORT).show()
        }
    }
}

fun importData(context: Context, uri: Uri, eventDao: EventDao, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        try {
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
            val jsonArray = JSONArray(stringBuilder.toString())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val event = Event(
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    date = LocalDate.parse(obj.getString("date")),
                    time = if (obj.isNull("time")) null else LocalTime.parse(obj.getString("time")),
                    isAllDay = obj.getBoolean("isAllDay"),
                    recurrence = RecurrenceType.valueOf(obj.optString("recurrence", "NONE"))
                )
                eventDao.insertEvent(event)
            }
            Toast.makeText(context, "Datos importados", Toast.LENGTH_SHORT).show()
            updateWidget(context)
        } catch (_: Exception) {
            Toast.makeText(context, "Error al importar", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        content = { Column(content = content) }
    )
}

@Composable
fun SettingsItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        trailing()
    }
}

fun scheduleNotification(
    context: Context,
    date: LocalDate, 
    time: LocalTime?, 
    title: String, 
    description: String, 
    id: Int,
    leadTimeMinutes: Int,
    isAllDay: Boolean
) {
    val eventDateTime = if (isAllDay) {
        LocalDateTime.of(date, LocalTime.of(9, 0))
    } else {
        LocalDateTime.of(date, time!!)
    }
    
    val now = LocalDateTime.now()
    val notificationTime = if (isAllDay) eventDateTime else eventDateTime.minusMinutes(leadTimeMinutes.toLong())
    
    val delayInSeconds = if (now.isAfter(notificationTime)) {
        if (now.isAfter(eventDateTime) && !isAllDay) return
        0L 
    } else {
        val duration = Duration.between(now, notificationTime).seconds
        if (duration < 0) 0L else duration
    }
    
    val data = Data.Builder()
        .putString("title", title)
        .putString("description", if (isAllDay) "Evento de hoy: $description" else "Tu evento empieza en $leadTimeMinutes min: $description")
        .putInt("notificationId", id)
        .build()

    val notificationRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
        .setInitialDelay(delayInSeconds, TimeUnit.SECONDS)
        .setInputData(data)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "event_notification_$id",
        ExistingWorkPolicy.REPLACE,
        notificationRequest
    )
}

suspend fun updateWidget(context: Context) {
    delay(300)
    CalendarWidget().updateAll(context.applicationContext)
}

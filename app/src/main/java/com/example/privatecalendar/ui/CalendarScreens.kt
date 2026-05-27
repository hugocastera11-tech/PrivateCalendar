package com.example.privatecalendar.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.example.privatecalendar.BuildConfig
import com.example.privatecalendar.R
import com.example.privatecalendar.data.*
import com.example.privatecalendar.worker.NotificationWorker
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.comparisons.compareByDescending
import kotlin.comparisons.thenBy
import kotlin.math.roundToInt
import androidx.glance.appwidget.updateAll
import com.example.privatecalendar.utils.NotificationHelper

enum class CalendarViewMode {
    DAY, WEEK, MONTH, YEAR
}

sealed class SearchResult {
    data class UserEvent(val event: Event) : SearchResult()
    data class HolidayEvent(val holiday: Holiday) : SearchResult()
}

// Especificaciones de animación globales para consistencia - Refinado para fluidez
private val FluidSpringFloat = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
private val FluidSpringOffset = spring<IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
private val FluidSpringSize = spring<IntSize>(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
private val BouncySpringFloat = spring<Float>(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
private val StandardTween = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToDay: (LocalDate) -> Unit
) {
    val allEvents by viewModel.allEvents.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val holidays by viewModel.holidays.collectAsState()
    val leadTime by viewModel.notificationLeadTime.collectAsState()
    val allDayHour by viewModel.allDayNotificationHour.collectAsState()
    val allDayDayBefore by viewModel.allDayNotificationDayBefore.collectAsState()
    val defaultViewModeSetting by viewModel.defaultViewMode.collectAsState()

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var displayedDate by remember { mutableStateOf(LocalDate.now()) }
    var viewMode by remember {
        mutableStateOf(
            try {
                val initial = viewModel.defaultViewMode.value
                if (initial == "LOADING") CalendarViewMode.MONTH else CalendarViewMode.valueOf(initial)
            } catch (_: Exception) {
                CalendarViewMode.MONTH
            }
        )
    }
    
    // Flag para evitar la animación inicial al cargar la app
    var isFirstLoad by remember { mutableStateOf(true) }

    LaunchedEffect(defaultViewModeSetting) {
        if (defaultViewModeSetting == "LOADING") return@LaunchedEffect
        
        val targetMode = try {
            CalendarViewMode.valueOf(defaultViewModeSetting)
        } catch (e: Exception) {
            CalendarViewMode.MONTH
        }
        
        if (isFirstLoad) {
            viewMode = targetMode
            isFirstLoad = false
        } else if (viewMode != targetMode) {
            viewMode = targetMode
        }
    }
    
    var showAddEditDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<Event?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<Event?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var eventIdBeingDeleted by remember { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val haptic = LocalHapticFeedback.current

    // Helper para navegación segura (evita clics dobles)
    var lastNavTime by remember { mutableLongStateOf(0L) }
    val safeNavigate: (() -> Unit) -> Unit = { action ->
        val now = System.currentTimeMillis()
        if (now - lastNavTime > 500) {
            lastNavTime = now
            action()
        }
    }
    
    val filteredResults by remember(allEvents, holidays, searchQuery) {
        derivedStateOf {
            if (searchQuery.isEmpty()) emptyList()
            else {
                val eventMatches = allEvents.filter { 
                    it.title.text.contains(searchQuery, ignoreCase = true) || 
                    it.description.text.contains(searchQuery, ignoreCase = true)
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
    }

    val eventsOnSelectedDate by remember(allEvents, selectedDate) {
        derivedStateOf { allEvents.filter { isEventOnDate(it, selectedDate) } }
    }

    val holidaysOnSelectedDate by remember(holidays, selectedDate) {
        derivedStateOf { holidays.filter { it.date == selectedDate } }
    }
    
    val datesWithEvents by remember(allEvents, displayedDate, viewMode) {
        derivedStateOf { calculateDatesWithEvents(allEvents, displayedDate, viewMode) }
    }

    val holidayDates by remember(holidays) {
        derivedStateOf { holidays.map { it.date }.toSet() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    (slideInVertically(animationSpec = FluidSpringOffset) { -it } + fadeIn(StandardTween) togetherWith
                     slideOutVertically(animationSpec = FluidSpringOffset) { -it } + fadeOut(StandardTween))
                        .using(SizeTransform(clip = false))
                },
                label = "TopBarSearch"
            ) { searching ->
                if (searching) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.search_events_placeholder)) },
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
                            IconButton(onClick = { safeNavigate { isSearchActive = false; searchQuery = "" } }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                            }
                        },
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, stringResource(R.string.clear))
                                }
                            }
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = { 
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light) 
                        },
                        actions = {
                            IconButton(onClick = { safeNavigate(onNavigateToTasks) }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, stringResource(R.string.tasks), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, stringResource(R.string.search), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { safeNavigate(onNavigateToSettings) }) {
                                Icon(Icons.Default.Settings, stringResource(R.string.settings), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isSearchActive,
                enter = scaleIn(FluidSpringFloat) + fadeIn(),
                exit = scaleOut(tween(200)) + fadeOut()
            ) {
                var fabClicked by remember { mutableStateOf(false) }
                val fabScale by animateFloatAsState(
                    targetValue = if (fabClicked) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                    label = "FabClick",
                    finishedListener = { fabClicked = false }
                )
                
                LargeFloatingActionButton(
                    onClick = { 
                        fabClicked = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            delay(100)
                            eventToEdit = null
                            showAddEditDialog = true 
                        }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                    modifier = Modifier.scale(fabScale)
                ) { 
                    Icon(
                        imageVector = Icons.Default.Add, 
                        contentDescription = stringResource(R.string.add),
                        modifier = Modifier.scale(1.2f)
                    ) 
                }
            }
        }
    ) { innerPadding ->
        BackHandler(isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }

        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = FluidSpringFloat)) togetherWith
                        fadeOut(tween(250))
                    } else {
                        fadeIn(tween(300)) togetherWith
                        (fadeOut(tween(250)) + scaleOut(targetScale = 0.95f, animationSpec = FluidSpringFloat))
                    }
                },
                label = "SearchTransition"
            ) { searching ->
                if (searching) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                    ) {
                        items(filteredResults, key = { 
                            when(it) {
                                is SearchResult.UserEvent -> "user_${it.event.id}"
                                is SearchResult.HolidayEvent -> "holiday_${it.holiday.name}_${it.holiday.date}"
                            }
                        }) { result ->
                            val isDeleting = result is SearchResult.UserEvent && result.event.id == eventIdBeingDeleted
                            
                            AnimatedVisibility(
                                visible = !isDeleting,
                                exit = slideOutHorizontally(
                                    targetOffsetX = { it }, 
                                    animationSpec = tween(500, easing = LinearOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(400)) + 
                                shrinkVertically(animationSpec = tween(500, easing = LinearOutSlowInEasing)),
                                label = "SearchResultDeletion"
                            ) {
                                when (result) {
                                    is SearchResult.UserEvent -> {
                                        EventItem(
                                            event = result.event,
                                            category = allCategories.find { it.id == result.event.categoryId },
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
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            SegmentedButton(
                                selected = viewMode == CalendarViewMode.DAY,
                                onClick = { viewMode = CalendarViewMode.DAY; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                            ) { Text(stringResource(R.string.day)) }
                            SegmentedButton(
                                selected = viewMode == CalendarViewMode.WEEK,
                                onClick = { viewMode = CalendarViewMode.WEEK; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                            ) { Text(stringResource(R.string.week)) }
                            SegmentedButton(
                                selected = viewMode == CalendarViewMode.MONTH,
                                onClick = { viewMode = CalendarViewMode.MONTH; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                            ) { Text(stringResource(R.string.month)) }
                            SegmentedButton(
                                selected = viewMode == CalendarViewMode.YEAR,
                                onClick = { viewMode = CalendarViewMode.YEAR; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                            ) { Text(stringResource(R.string.year)) }
                        }

                        AnimatedContent(
                            targetState = viewMode,
                            transitionSpec = {
                                if (isFirstLoad) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.98f)).togetherWith(
                                     fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.02f))
                                }
                            },
                            label = "ViewModeTransition"
                        ) { targetMode ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(animationSpec = FluidSpringSize)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(16.dp)
                            ) {
                                when (targetMode) {
                                    CalendarViewMode.DAY -> DayView(
                                        date = displayedDate,
                                        events = eventsOnSelectedDate,
                                        holidays = holidaysOnSelectedDate,
                                        onDateChange = { displayedDate = it; selectedDate = it },
                                        onEditEvent = { eventToEdit = it; showAddEditDialog = true },
                                        onDeleteEvent = { eventToDelete = it; showDeleteConfirmDialog = true },
                                        eventIdBeingDeleted = eventIdBeingDeleted
                                    )
                                    CalendarViewMode.WEEK -> WeekView(
                                        currentDate = displayedDate,
                                        selectedDate = selectedDate,
                                        onDateSelected = { selectedDate = it; displayedDate = it; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                        onDateChange = { displayedDate = it },
                                        datesWithEvents = datesWithEvents,
                                        holidayDates = holidayDates
                                    )
                                    CalendarViewMode.MONTH -> MonthView(
                                        displayedMonth = YearMonth.from(displayedDate),
                                        selectedDate = selectedDate,
                                        onDateSelected = { selectedDate = it; displayedDate = it; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                        onMonthChange = { displayedDate = it.atDay(1) },
                                        datesWithEvents = datesWithEvents,
                                        holidayDates = holidayDates
                                    )
                                    CalendarViewMode.YEAR -> YearView(
                                        displayedYear = displayedDate.year,
                                        onMonthSelected = { month ->
                                            displayedDate = LocalDate.of(displayedDate.year, month, 1)
                                            viewMode = CalendarViewMode.MONTH
                                        },
                                        onYearChange = { displayedDate = LocalDate.of(it, displayedDate.monthValue, 1) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        if (viewMode != CalendarViewMode.YEAR) {
                            AnimatedContent(
                                targetState = selectedDate,
                                transitionSpec = {
                                    val direction = if (targetState.isAfter(initialState)) 1 else -1
                                    (slideInVertically(animationSpec = FluidSpringOffset) { direction * it / 3 } + fadeIn() togetherWith
                                     slideOutVertically(animationSpec = FluidSpringOffset) { -direction * it / 3 } + fadeOut())
                                },
                                label = "SelectedDateText"
                            ) { date ->
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { safeNavigate { onNavigateToDay(date) } }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                    }
                                    holidaysOnSelectedDate.forEach { holiday ->
                                        Text(
                                            text = "\uD83D\uDEA9 ${holiday.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (viewMode != CalendarViewMode.DAY && viewMode != CalendarViewMode.YEAR) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(animationSpec = tween(500, easing = LinearOutSlowInEasing))
                            ) {
                                if (eventsOnSelectedDate.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.no_events), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                eventsOnSelectedDate.forEach { event ->
                                    val isDeleting = event.id == eventIdBeingDeleted
                                    
                                    AnimatedVisibility(
                                        visible = !isDeleting,
                                        exit = slideOutHorizontally(
                                            targetOffsetX = { it }, 
                                            animationSpec = tween(500, easing = LinearOutSlowInEasing)
                                        ) + fadeOut(animationSpec = tween(400)),
                                        label = "EventDeletion"
                                    ) {
                                        Box(modifier = Modifier.padding(bottom = 12.dp)) {
                                            EventItem(
                                                event = event,
                                                category = allCategories.find { it.id == event.categoryId },
                                                onEdit = { eventToEdit = event; showAddEditDialog = true },
                                                onDelete = { eventToDelete = event; showDeleteConfirmDialog = true }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        if (eventIdBeingDeleted != null) {
            LaunchedEffect(eventIdBeingDeleted) {
                val event = allEvents.find { it.id == eventIdBeingDeleted }
                if (event != null) {
                    delay(300) // Animación más ajustada
                    viewModel.deleteEvent(event)
                    NotificationHelper.cancelNotification(context, event.id)
                    updateWidget(context)
                }
                eventIdBeingDeleted = null
            }
        }
    }

    if (showAddEditDialog) {
        EventDialog(
            selectedDate = selectedDate,
            event = eventToEdit,
            categories = allCategories,
            onDismiss = { showAddEditDialog = false },
            onConfirm = { title, desc, location, time, isAllDay, date, recurrence, categoryId, attachments ->
                if (eventToEdit == null) {
                    val newEvent = Event(
                        date = date, 
                        time = time, 
                        title = EncryptedString(title), 
                        description = EncryptedString(desc), 
                        location = location?.let { EncryptedString(it) },
                        isAllDay = isAllDay, 
                        recurrence = recurrence, 
                        categoryId = categoryId, 
                        attachments = attachments
                    )
                    scope.launch {
                        val id = viewModel.insertEvent(newEvent)
                        NotificationHelper.scheduleNotification(context, date, time, title, id.toInt(), leadTime, isAllDay, allDayHour, allDayDayBefore, recurrence)
                        updateWidget(context)
                    }
                } else {
                    val updatedEvent = eventToEdit!!.copy(
                        title = EncryptedString(title), 
                        description = EncryptedString(desc), 
                        location = location?.let { EncryptedString(it) },
                        time = time, 
                        date = date, 
                        isAllDay = isAllDay, 
                        recurrence = recurrence, 
                        categoryId = categoryId, 
                        attachments = attachments
                    )
                    scope.launch {
                        viewModel.updateEvent(updatedEvent)
                        NotificationHelper.scheduleNotification(context, date, time, title, updatedEvent.id, leadTime, isAllDay, allDayHour, allDayDayBefore, recurrence)
                        updateWidget(context)
                    }
                }
                showAddEditDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog && eventToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.delete), fontWeight = FontWeight.Medium) },
            text = { Text(stringResource(R.string.delete_event_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    eventIdBeingDeleted = eventToDelete!!.id
                    showDeleteConfirmDialog = false
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

fun calculateDatesWithEvents(allEvents: List<Event>, displayedDate: LocalDate, viewMode: CalendarViewMode): Set<LocalDate> {
    if (allEvents.isEmpty()) return emptySet()
    val range = when(viewMode) {
        CalendarViewMode.DAY -> listOf(displayedDate)
        CalendarViewMode.WEEK -> {
            val start = displayedDate.with(java.time.DayOfWeek.MONDAY)
            (0..6).map { start.plusDays(it.toLong()) }
        }
        CalendarViewMode.MONTH -> {
            val start = YearMonth.from(displayedDate).atDay(1)
            val daysInMonth = YearMonth.from(displayedDate).lengthOfMonth()
            (0 until daysInMonth).map { start.plusDays(it.toLong()) }
        }
        CalendarViewMode.YEAR -> return emptySet()
    }
    
    // Optimización: Dividir eventos en recurrentes y no recurrentes
    val nonRecurrentEventDates = allEvents.filter { it.recurrence == RecurrenceType.NONE }.map { it.date }.toSet()
    val recurrentEvents = allEvents.filter { it.recurrence != RecurrenceType.NONE }
    
    return range.filter { date -> 
        nonRecurrentEventDates.contains(date) || recurrentEvents.any { isEventOnDate(it, date) }
    }.toSet()
}

@Composable
fun DayView(
    date: LocalDate,
    events: List<Event>,
    holidays: List<Holiday>,
    onDateChange: (LocalDate) -> Unit,
    onEditEvent: (Event) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    eventIdBeingDeleted: Int? = null
) {
    val isToday = date == LocalDate.now()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(500, easing = LinearOutSlowInEasing))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("d MMMM")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = date.year.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary
                )
            }
            Row {
                IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onDateChange(date.plusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.next), modifier = Modifier.size(20.dp))
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (holidays.isNotEmpty()) {
                    holidays.forEach { holiday ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            Text("\uD83D\uDEA9", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(holiday.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                }

                if (events.isEmpty()) {
                    Text(
                        stringResource(R.string.no_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    events.sortedWith(compareBy({ !it.isAllDay }, { it.time })).forEach { event ->
                        val isDeleting = event.id == eventIdBeingDeleted
                        AnimatedVisibility(
                            visible = !isDeleting,
                            exit = slideOutHorizontally(
                                targetOffsetX = { it }, 
                                animationSpec = tween(500, easing = LinearOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(400)),
                            label = "DayViewEventDeletion"
                        ) {
                            Column {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onEditEvent(event) }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = event.title.text,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (event.isAllDay) stringResource(R.string.all_day) else event.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        IconButton(onClick = { onDeleteEvent(event) }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                        }
                                    }
                                    if (event.location != null && event.location.text.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 22.dp, top = 2.dp)) {
                                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                            Spacer(Modifier.width(4.dp))
                                            Text(event.location.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                        }
                                    }
                                    if (event.description.text.isNotBlank()) {
                                        Text(
                                            text = event.description.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 22.dp, top = 4.dp)
                                        )
                                    }
                                }
                                if (event != events.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 22.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekView(
    currentDate: LocalDate,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    datesWithEvents: Set<LocalDate>,
    holidayDates: Set<LocalDate>
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onDateChange(currentDate.plusWeeks(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.next), modifier = Modifier.size(20.dp))
                }
            }
        }
        
        AnimatedContent(
            targetState = firstDayOfWeek,
            transitionSpec = {
                val direction = if (targetState.isAfter(initialState)) 1 else -1
                (slideInHorizontally(animationSpec = FluidSpringOffset) { direction * it } + fadeIn() togetherWith
                 slideOutHorizontally(animationSpec = FluidSpringOffset) { -direction * it } + fadeOut())
            },
            label = "WeekNavigation"
        ) { startOfWeek ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0..6).forEach { i ->
                    val date = startOfWeek.plusDays(i.toLong())
                    val isSelected = date == selectedDate
                    val isToday = date == LocalDate.now()
                    val hasEvent = datesWithEvents.contains(date)
                    val isHoliday = holidayDates.contains(date)
                    
                    val backgroundSelected = animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "ColorAnim")
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(backgroundSelected.value)
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
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            )
                        } else {
                            // Mantenemos el espacio para que los números no bailen
                            Spacer(modifier = Modifier.height(6.dp))
                        }
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
    datesWithEvents: Set<LocalDate>,
    holidayDates: Set<LocalDate>
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onMonthChange(displayedMonth.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.next), modifier = Modifier.size(20.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            val days = listOf("L", "M", "X", "J", "V", "S", "D")
            days.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                )
            }
        }
        
        AnimatedContent(
            targetState = displayedMonth,
            transitionSpec = {
                val direction = if (targetState.isAfter(initialState)) 1 else -1
                (slideInHorizontally(animationSpec = FluidSpringOffset) { direction * it } + fadeIn() togetherWith
                 slideOutHorizontally(animationSpec = FluidSpringOffset) { -direction * it } + fadeOut())
            },
            label = "MonthNavigation"
        ) { targetMonth ->
            Column {
                val firstDayOfMonth = targetMonth.atDay(1)
                val firstDayIndex = (firstDayOfMonth.dayOfWeek.value - 1)
                val totalDays = targetMonth.lengthOfMonth()
                var dayCounter = 1
                for (week in 0 until 6) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (dow in 0..6) {
                            val cellIndex = week * 7 + dow
                            if (cellIndex >= firstDayIndex && dayCounter <= totalDays) {
                                val date = targetMonth.atDay(dayCounter)
                                val isSelected = date == selectedDate
                                val isToday = date == LocalDate.now()
                                val hasEvent = datesWithEvents.contains(date)
                                val isHoliday = holidayDates.contains(date)
                                
                                val backgroundSelected = animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "ColorAnim")

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(backgroundSelected.value)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                IconButton(onClick = { onYearChange(displayedYear + 1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.next))
                }
            }
        }
        
        AnimatedContent(
            targetState = displayedYear,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(animationSpec = FluidSpringOffset) { direction * it } + fadeIn() togetherWith
                 slideOutHorizontally(animationSpec = FluidSpringOffset) { -direction * it } + fadeOut())
            },
            label = "YearNavigation"
        ) { targetYear ->
            val now = LocalDate.now()
            val currentYear = now.year
            val currentMonthValue = now.monthValue

            key(targetYear) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().height(400.dp)
                ) {
                    items((1..12).toList()) { month ->
                        val isCurrentMonth = targetYear == currentYear && month == currentMonthValue
                        val monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, label = "Scale")

                        Surface(
                            onClick = { onMonthSelected(month) },
                            interactionSource = interactionSource,
                            shape = RoundedCornerShape(20.dp),
                            color = if (isCurrentMonth) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = if (isCurrentMonth) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.aspectRatio(1.2f).scale(scale)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = monthName.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                }
            }
        }
    }
}



@Composable
fun EventItem(event: Event, category: EventCategory?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val categoryColor = if (category != null) Color(category.color) else MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "Scale"
    )

    Surface(
        onClick = onEdit,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().scale(scale)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (event.isAllDay) Icons.Default.CalendarToday else Icons.Default.Notifications,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title.text, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (event.location != null && event.location.text.isNotBlank()) {
                    Text(
                        text = "\uD83D\uDCCD ${event.location.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                val recurrenceText = if (event.recurrence != RecurrenceType.NONE) " \u2022 \uD83D\uDD01" else ""
                val categoryText = if (category != null) " \u2022 ${category.name.text}" else ""
                val attachmentText = if (event.attachments.isNotEmpty()) " \u2022 \uD83D\uDCCE" else ""
                Text(
                    text = "${if (event.isAllDay) stringResource(R.string.all_day) else event.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""}$recurrenceText$categoryText$attachmentText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    selectedDate: LocalDate,
    event: Event? = null,
    categories: List<EventCategory>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, LocalTime?, Boolean, LocalDate, RecurrenceType, Int?, List<String>) -> Unit
) {
    var title by remember { mutableStateOf(event?.title?.text ?: "") }
    var description by remember { mutableStateOf(event?.description?.text ?: "") }
    var location by remember { mutableStateOf(event?.location?.text ?: "") }
    var isAllDay by remember { mutableStateOf(event?.isAllDay ?: false) }
    var eventDate by remember { mutableStateOf(event?.date ?: selectedDate) }
    var recurrence by remember { mutableStateOf(event?.recurrence ?: RecurrenceType.NONE) }
    var categoryId by remember { mutableStateOf(event?.categoryId) }
    var attachments by remember { mutableStateOf(event?.attachments ?: emptyList()) }
    
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val attachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            uris.forEach { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            attachments = attachments + uris.map { it.toString() }
        }
    )
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
            Text(if (event == null) stringResource(R.string.new_event) else stringResource(R.string.edit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light)
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = title, onValueChange = { title = it }, placeholder = { Text(stringResource(R.string.title)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (categories.isNotEmpty()) {
                Text(stringResource(R.string.category_label), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (categoryId == null) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable { categoryId = null; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    }
                    
                    categories.forEach { cat ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(cat.color))
                                .clickable { categoryId = cat.id; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (categoryId == cat.id) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Surface(
                onClick = {
                    val dialog = DatePickerDialog(context, { _, year, month, dayOfMonth ->
                        val newDate = LocalDate.of(year, month + 1, dayOfMonth)
                        if (newDate.isBefore(LocalDate.now())) {
                            android.widget.Toast.makeText(context, context.getString(R.string.past_date_error), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            eventDate = newDate
                        }
                    }, eventDate.year, eventDate.monthValue - 1, eventDate.dayOfMonth)
                    dialog.datePicker.minDate = System.currentTimeMillis() - 1000
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
                value = description, onValueChange = { description = it }, placeholder = { Text(stringResource(R.string.description)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = location, onValueChange = { location = it }, placeholder = { Text("Ubicación") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.attachments_label), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { attachmentLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (attachments.isNotEmpty()) {
                attachments.forEach { uriString ->
                    val fileName = remember(uriString) {
                        try {
                            val uri = android.net.Uri.parse(uriString)
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                cursor.moveToFirst()
                                cursor.getString(nameIndex)
                            } ?: uri.path?.substringAfterLast('/') ?: uriString
                        } catch (e: Exception) {
                            uriString.substringAfterLast('/')
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse(uriString)
                                        flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error al abrir archivo", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(fileName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { attachments = attachments - uriString }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }

                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isAllDay, onCheckedChange = { 
                    isAllDay = it
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                })
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.all_day), style = MaterialTheme.typography.bodyMedium)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RecurrenceType.entries.forEach { type ->
                    FilterChip(
                        selected = recurrence == type,
                        onClick = { recurrence = type; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        label = { Text(when(type){
                            RecurrenceType.NONE -> stringResource(R.string.recurrence_none)
                            RecurrenceType.DAILY -> stringResource(R.string.recurrence_daily)
                            RecurrenceType.WEEKLY -> stringResource(R.string.recurrence_weekly)
                            RecurrenceType.MONTHLY -> stringResource(R.string.recurrence_monthly)
                            RecurrenceType.YEARLY -> stringResource(R.string.recurrence_yearly)
                        }, fontSize = 10.sp) }
                    )
                }
            }
            
            AnimatedVisibility(
                visible = !isAllDay,
                enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(24.dp))
                    TimePicker(state = timePickerState, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val time = if (isAllDay) null else LocalTime.of(timePickerState.hour, timePickerState.minute)
                                
                                // Validar que no sea una hora pasada si es hoy
                                val now = LocalDateTime.now()
                                val selectedDateTime = if (isAllDay) LocalDateTime.of(eventDate, LocalTime.MAX) else LocalDateTime.of(eventDate, time!!)
                                
                                if (selectedDateTime.isBefore(now)) {
                                    android.widget.Toast.makeText(context, context.getString(R.string.past_date_error), android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    onConfirm(title, description, location.ifBlank { null }, time, isAllDay, eventDate, recurrence, categoryId, attachments)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = title.isNotBlank()
                        ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CalendarViewModel,
    onBack: () -> Unit,
    onNavigateToCountrySelector: () -> Unit,
    onRecreate: () -> Unit
) {
    val isDarkTheme by viewModel.isDarkMode.collectAsState()
    val themeName by viewModel.themeName.collectAsState()
    val leadTime by viewModel.notificationLeadTime.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val showHolidays by viewModel.showHolidays.collectAsState()
    val allDayHour by viewModel.allDayNotificationHour.collectAsState()
    val allDayDayBefore by viewModel.allDayNotificationDayBefore.collectAsState()
    val defaultViewModeSetting by viewModel.defaultViewMode.collectAsState()
    val holidayCountryCode by viewModel.holidayCountryCode.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<EventCategory?>(null) }
    var categoryIdBeingDeleted by remember { mutableStateOf<Int?>(null) }
    
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportData(context, it) }
    }
    
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importData(context, it) {
            scope.launch { updateWidget(context) }
        } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Light) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
            // Apariencia
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsItem(
                        title = stringResource(R.string.dark_mode),
                        icon = Icons.Default.BrightnessMedium,
                        trailing = { Switch(checked = isDarkTheme, onCheckedChange = { viewModel.setDarkMode(it); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.theme),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        com.example.privatecalendar.ui.theme.AppTheme.entries.forEach { theme ->
                            val isSelected = theme.name == themeName
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setThemeName(theme.name) }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(getThemePreviewColor(theme))
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when(theme.name) {
                                        "FOREST" -> "Bosque"
                                        "LAVENDER" -> "Lavanda"
                                        "MIDNIGHT" -> "Noche"
                                        "ROSE" -> "Rosa"
                                        else -> "Default"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Text(stringResource(R.string.default_view), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(CalendarViewMode.DAY, CalendarViewMode.WEEK, CalendarViewMode.MONTH).forEach { mode ->
                            FilterChip(
                                selected = defaultViewModeSetting == mode.name,
                                onClick = { viewModel.setDefaultViewMode(mode.name) },
                                label = { Text(when(mode) {
                                    CalendarViewMode.DAY -> stringResource(R.string.day)
                                    CalendarViewMode.WEEK -> stringResource(R.string.week)
                                    CalendarViewMode.MONTH -> stringResource(R.string.month)
                                    else -> ""
                                }) }
                            )
                        }
                    }
                }
            }

            // Notificaciones
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Notificaciones", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Aviso para eventos con hora
                    Text("Recordatorio eventos con hora", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.minutes_before, leadTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Slider(
                        value = leadTime.toFloat(),
                        onValueChange = { viewModel.setNotificationLeadTime(it.roundToInt()) },
                        valueRange = 0f..120f,
                        steps = 23
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    
                    // Aviso para eventos de todo el día
                    Text(stringResource(R.string.all_day_notif_settings), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    SettingsItem(
                        title = stringResource(R.string.all_day_notif_day_before),
                        icon = Icons.Default.EventRepeat,
                        trailing = {
                            Switch(
                                checked = allDayDayBefore,
                                onCheckedChange = { viewModel.setAllDayNotificationDayBefore(it) }
                            )
                        }
                    )
                    Text(
                        text = "${stringResource(R.string.all_day_notif_hour)}: ${String.format("%02d:00", allDayHour)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Slider(
                        value = allDayHour.toFloat(),
                        onValueChange = { viewModel.setAllDayNotificationHour(it.roundToInt()) },
                        valueRange = 0f..23f,
                        steps = 23
                    )
                }
            }

            // Seguridad
            SettingsCard {
                SettingsItem(
                    title = stringResource(R.string.biometric_lock),
                    icon = Icons.Default.Lock,
                    trailing = { Switch(checked = isBiometricEnabled, onCheckedChange = { enabled ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.setBiometricEnabled(enabled)
                    }) }
                )
            }

            // Categorías
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { categoryToEdit = null; showCategoryDialog = true }) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    allCategories.forEach { cat ->
                        val isDeleting = cat.id == categoryIdBeingDeleted
                        Column {
                            AnimatedVisibility(
                                visible = !isDeleting,
                                exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(500, easing = LinearOutSlowInEasing)) + 
                                       fadeOut(animationSpec = tween(400)) + 
                                       shrinkVertically(animationSpec = tween(500, easing = LinearOutSlowInEasing)),
                                label = "CategoryDeletion"
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { categoryToEdit = cat; showCategoryDialog = true }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(cat.color)))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(cat.name.text, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    IconButton(onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        categoryIdBeingDeleted = cat.id
                                    }) {
                                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }

                        if (isDeleting) {
                            LaunchedEffect(cat.id) {
                                delay(500)
                                viewModel.deleteCategory(cat)
                                updateWidget(context)
                                categoryIdBeingDeleted = null
                            }
                        }
                    }

                }
            }

            // Idioma
            SettingsCard {
                SettingsItem(
                    title = stringResource(R.string.language),
                    icon = Icons.Default.Language,
                    trailing = {
                        var showLangMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { showLangMenu = true }) {
                                Text(if (languageCode == "es") stringResource(R.string.language_es) else stringResource(R.string.language_en))
                            }
                            DropdownMenu(expanded = showLangMenu, onDismissRequest = { showLangMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.language_es)) },
                                    onClick = {
                                        viewModel.setLanguageCode("es")
                                        showLangMenu = false
                                        onRecreate()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.language_en)) },
                                    onClick = {
                                        viewModel.setLanguageCode("en")
                                        showLangMenu = false
                                        onRecreate()
                                    }
                                )
                            }
                        }
                    }
                )
            }

            // Festivos
            SettingsCard {
                SettingsItem(
                    title = stringResource(R.string.show_holidays),
                    icon = Icons.Default.Flag,
                    trailing = { Switch(checked = showHolidays, onCheckedChange = { viewModel.setShowHolidays(it); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }) }
                )
                if (showHolidays) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stringResource(R.string.holiday_country), style = MaterialTheme.typography.bodyLarge)
                        }
                        TextButton(onClick = onNavigateToCountrySelector) {
                            Text(HolidayProvider.SUPPORTED_COUNTRIES[holidayCountryCode] ?: holidayCountryCode)
                        }
                    }

                }
            }

            // Backup y Sincronización
            SettingsCard {
                val googleCalendarLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.importFromGoogleCalendar(context) {
                            scope.launch { updateWidget(context) }
                        }
                    }
                }

                SettingsItem(
                    title = "Importar de Google Calendar",
                    icon = Icons.Default.CloudDownload,
                    trailing = {
                        IconButton(onClick = {
                            googleCalendarLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                        }) {
                            Icon(Icons.Default.CloudDownload, null)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsItem(
                    title = stringResource(R.string.export_backup),
                    icon = Icons.Default.Download,
                    trailing = {
                        IconButton(onClick = { exportLauncher.launch("calendar_backup.json") }) {
                            Icon(Icons.Default.Download, null)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsItem(
                    title = stringResource(R.string.import_backup),
                    icon = Icons.Default.Upload,
                    trailing = {
                        IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                            Icon(Icons.Default.Upload, null)
                        }
                    }
                )
            }

            // Acerca de
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.about_app),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                        data = android.net.Uri.parse("mailto:hugxperez@proton.me")
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No se encontró aplicación de correo", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "hugxperez@proton.me",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showCategoryDialog) {
        CategoryEditDialog(
            category = categoryToEdit,
            onDismiss = { showCategoryDialog = false },
            onConfirm = { name, color ->
                scope.launch {
                    if (categoryToEdit == null) {
                        viewModel.insertCategory(EventCategory(name = name, color = color))
                    } else {
                        viewModel.updateCategory(categoryToEdit!!.copy(name = name, color = color))
                    }
                    updateWidget(context)
                }
                showCategoryDialog = false
            }
        )
    }
}

@Composable
fun CategoryEditDialog(
    category: EventCategory?,
    onDismiss: () -> Unit,
    onConfirm: (EncryptedString, Long) -> Unit
) {
    var name by remember { mutableStateOf(category?.name?.text ?: "") }
    var selectedColor by remember { mutableStateOf(category?.color ?: 0xFFF44336) }
    
    val presetColors = listOf(
        0xFFF44336, // Red
        0xFF2196F3, // Blue
        0xFF4CAF50, // Green
        0xFFFF9800  // Orange
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) stringResource(R.string.new_category) else stringResource(R.string.edit_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.category_color), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    presetColors.forEach { colorValue ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorValue))
                                .clickable { selectedColor = colorValue }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == colorValue) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(EncryptedString(name), selectedColor) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun HolidaySearchItem(holiday: Holiday, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "ScaleAnimation")

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().scale(scale)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83D\uDEA9")
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

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        content = { Column(content = content) }
    )
}

@Composable
fun getThemePreviewColor(theme: com.example.privatecalendar.ui.theme.AppTheme): Color {
    return when(theme) {
        com.example.privatecalendar.ui.theme.AppTheme.FOREST -> Color(0xFF2E7D32)
        com.example.privatecalendar.ui.theme.AppTheme.LAVENDER -> Color(0xFF673AB7)
        com.example.privatecalendar.ui.theme.AppTheme.MIDNIGHT -> Color(0xFF2196F3)
        com.example.privatecalendar.ui.theme.AppTheme.ROSE -> Color(0xFFD81B60)
        else -> Color(0xFF1A1A1A)
    }
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

suspend fun updateWidget(context: android.content.Context) {
    kotlinx.coroutines.delay(600)
    try {
        com.example.privatecalendar.widget.CalendarWidget().updateAll(context.applicationContext)
    } catch (e: Exception) {
        android.util.Log.e("CalendarWidget", "Error al actualizar widget", e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidayCountrySelectorScreen(
    viewModel: CalendarViewModel,
    onBack: () -> Unit
) {
    val holidayCountryCode by viewModel.holidayCountryCode.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val deviceCountry = remember { Locale.getDefault().country.uppercase() }
    
    val sortedCountries = remember(searchQuery) {
        HolidayProvider.SUPPORTED_COUNTRIES.toList()
            .filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
            .sortedWith(compareByDescending<Pair<String, String>> { it.first == deviceCountry }
                .thenBy { it.second })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.holiday_country), fontWeight = FontWeight.Light) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp)
                ) {}
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedCountries) { (code, name) ->
                val isSelected = code == holidayCountryCode
                val isDeviceCountry = code == deviceCountry

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setHolidayCountryCode(code)
                        onBack()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isDeviceCountry) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Actual",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                            )
                        }
                    }

                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayViewScreen(
    date: LocalDate,
    viewModel: CalendarViewModel,
    onBack: () -> Unit
) {
    val allEvents by viewModel.allEvents.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val holidays by viewModel.holidays.collectAsState()
    val leadTime by viewModel.notificationLeadTime.collectAsState()
    val allDayHour by viewModel.allDayNotificationHour.collectAsState()
    val allDayDayBefore by viewModel.allDayNotificationDayBefore.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<Event?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<Event?>(null) }
    var eventIdBeingDeleted by remember { mutableStateOf<Int?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    val eventsOnDay = remember(allEvents, date) {
        allEvents.filter { isEventOnDate(it, date) }.sortedWith(compareBy({ !it.isAllDay }, { it.time }))
    }
    
    val holidayOnDay = holidays.find { it.date == date }
    val isToday = date == LocalDate.now()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEEE")), 
                            style = MaterialTheme.typography.labelMedium, 
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("d MMMM, yyyy")), 
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ... (resto del contenido de DayViewScreen igual)
            if (holidayOnDay != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("\uD83D\uDEA9", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(holidayOnDay.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (eventsOnDay.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_events), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                eventsOnDay.forEach { event ->
                    val category = allCategories.find { it.id == event.categoryId }
                    val color = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
                    val isDeleting = event.id == eventIdBeingDeleted

                        AnimatedVisibility(
                            visible = !isDeleting,
                            exit = slideOutHorizontally(
                                targetOffsetX = { it }, 
                                animationSpec = tween(500, easing = LinearOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(400)) + 
                            shrinkVertically(animationSpec = tween(500, easing = LinearOutSlowInEasing)),
                            label = "DayViewScreenEventDeletion"
                        ) {
                            Surface(
                                onClick = { 
                                    eventToEdit = event
                                    showAddEditDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
                            ) {
                                Column(Modifier.padding(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = if (event.isAllDay) stringResource(R.string.all_day) else event.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        if (event.recurrence != RecurrenceType.NONE) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Default.Repeat, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(onClick = { 
                                            eventToDelete = event
                                            showDeleteConfirmDialog = true
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                        }
                                    }
                                    
                                    Spacer(Modifier.height(8.dp))
                                    Text(event.title.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                    
                                    if (event.location != null && event.location.text.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(4.dp))
                                            Text(event.location.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    
                                    if (event.description.text.isNotBlank()) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(event.description.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    if (event.attachments.isNotEmpty()) {
                                        Spacer(Modifier.height(12.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                            Spacer(Modifier.width(4.dp))
                                            Text("${event.attachments.size} archivos adjuntos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                }
                            }
                        }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }

        if (eventIdBeingDeleted != null) {
            val event = eventsOnDay.find { it.id == eventIdBeingDeleted }
            if (event != null) {
                LaunchedEffect(event.id) {
                    delay(500)
                    viewModel.deleteEvent(event)
                    NotificationHelper.cancelNotification(context, event.id)
                    updateWidget(context)
                    delay(50)
                    eventIdBeingDeleted = null
                }
            } else {
                // Safety: if event not found, clear the ID
                eventIdBeingDeleted = null
            }
        }
    }

    if (showDeleteConfirmDialog && eventToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.delete), fontWeight = FontWeight.Medium) },
            text = { Text(stringResource(R.string.delete_event_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    eventIdBeingDeleted = eventToDelete!!.id
                    showDeleteConfirmDialog = false
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showAddEditDialog) {
        EventDialog(
            selectedDate = date,
            event = eventToEdit,
            categories = allCategories,
            onDismiss = { showAddEditDialog = false },
            onConfirm = { title, desc, loc, time, isAllDay, d, recurrence, categoryId, attachments ->
                val updatedEvent = eventToEdit!!.copy(
                    title = EncryptedString(title),
                    description = EncryptedString(desc),
                    location = loc?.let { EncryptedString(it) },
                    time = time,
                    date = d,
                    isAllDay = isAllDay,
                    recurrence = recurrence,
                    categoryId = categoryId,
                    attachments = attachments
                )
                scope.launch {
                    viewModel.updateEvent(updatedEvent)
                    NotificationHelper.scheduleNotification(context, d, time, title, updatedEvent.id, leadTime, isAllDay, allDayHour, allDayDayBefore, recurrence)
                    updateWidget(context)
                }
                showAddEditDialog = false
            }
        )
    }
}

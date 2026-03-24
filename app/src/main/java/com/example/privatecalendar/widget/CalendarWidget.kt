package com.example.privatecalendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.privatecalendar.data.AppDatabase
import com.example.privatecalendar.data.Event
import com.example.privatecalendar.data.isEventOnDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CalendarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        // Usamos lectura directa (Sync) para asegurar datos frescos en el proceso del widget
        val allEvents = try {
            db.eventDao().getAllEventsSync()
        } catch (e: Exception) {
            emptyList()
        }
        
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        
        val todayEvents = allEvents.filter { isEventOnDate(it, today) }
        val tomorrowEvents = allEvents.filter { isEventOnDate(it, tomorrow) }

        provideContent {
            GlanceTheme {
                WidgetContent(todayEvents, tomorrowEvents)
            }
        }
    }

    @Composable
    private fun WidgetContent(todayEvents: List<Event>, tomorrowEvents: List<Event>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(24.dp)
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(10.dp)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(5.dp)
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Próximos",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )
            }
            
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                item { SectionTitle("Hoy") }
                if (todayEvents.isEmpty()) {
                    item { EmptyLabel() }
                } else {
                    items(todayEvents) { event ->
                        CompactEventRow(event)
                    }
                }
                
                item { Spacer(modifier = GlanceModifier.height(8.dp)) }
                
                item { SectionTitle("Mañana") }
                if (tomorrowEvents.isEmpty()) {
                    item { EmptyLabel() }
                } else {
                    items(tomorrowEvents) { event ->
                        CompactEventRow(event)
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text = text,
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = GlanceTheme.colors.primary
            ),
            modifier = GlanceModifier.padding(bottom = 2.dp)
        )
    }

    @Composable
    private fun CompactEventRow(event: Event) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.title,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            val timeText = if (event.isAllDay) "•" else event.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
            Text(
                text = timeText,
                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.secondary),
                modifier = GlanceModifier.padding(start = 4.dp)
            )
        }
    }

    @Composable
    private fun EmptyLabel() {
        Text(
            text = "Todo libre",
            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.secondary),
            modifier = GlanceModifier.padding(start = 2.dp)
        )
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

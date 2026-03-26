package com.example.privatecalendar.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.privatecalendar.data.QuickTask
import com.example.privatecalendar.data.QuickTaskDao
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTasksScreen(
    onBack: () -> Unit,
    taskDao: QuickTaskDao
) {
    val pendingTasks by taskDao.observePendingTasks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var taskTitle by rememberSaveable { mutableStateOf("") }
    
    val sevenDaysAgo = remember { LocalDateTime.now().minusDays(7) }
    val trashedTasks by taskDao.observeRecentCompletedTasks(sevenDaysAgo).collectAsState(initial = emptyList())
    var showTaskTrash by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Tareas rápidas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showTaskTrash = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Papelera", tint = MaterialTheme.colorScheme.primary)
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("¿Qué tienes pendiente?") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                IconButton(
                    onClick = {
                        val cleanTitle = taskTitle.trim()
                        if (cleanTitle.isNotEmpty()) {
                            scope.launch {
                                taskDao.insertTask(QuickTask(title = cleanTitle, createdAt = LocalDateTime.now()))
                            }
                            taskTitle = ""
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (pendingTasks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Todo al día",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(pendingTasks, key = { it.id }) { task ->
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = { 
                                        scope.launch {
                                            taskDao.markTaskCompleted(task.id, LocalDateTime.now())
                                            taskDao.deleteCompletedBefore(LocalDateTime.now().minusDays(7))
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Completar", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTaskTrash) {
        TaskTrashDialog(
            completedTasks = trashedTasks,
            onDismiss = { showTaskTrash = false },
            onEmptyTrash = {
                scope.launch {
                    taskDao.clearTrash()
                    showTaskTrash = false
                }
            }
        )
    }
}

@Composable
fun TaskTrashDialog(
    completedTasks: List<QuickTask>,
    onDismiss: () -> Unit,
    onEmptyTrash: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Papelera (7 días)", fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (completedTasks.isEmpty()) {
                    Text(
                        text = "No hay tareas recientes en la papelera.",
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(280.dp), 
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(completedTasks, key = { it.id }) { task ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "Hecho: ${task.completedAt?.format(formatter)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (completedTasks.isNotEmpty()) {
                TextButton(onClick = onEmptyTrash) {
                    Text("Vaciar", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

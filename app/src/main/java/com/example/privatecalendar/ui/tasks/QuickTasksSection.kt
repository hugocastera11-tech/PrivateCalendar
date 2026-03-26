package com.example.privatecalendar.ui.tasks

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.animateItem
import com.example.privatecalendar.data.QuickTask
import com.example.privatecalendar.ui.theme.AppMotion
import java.time.format.DateTimeFormatter

@Composable
fun QuickTasksSection(
    pendingTasks: List<QuickTask>,
    onAddTask: (String) -> Unit,
    onCompleteTask: (QuickTask) -> Unit,
    onOpenTrash: () -> Unit
) {
    var taskTitle by rememberSaveable { mutableStateOf("") }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tareas rápidas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenTrash) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Abrir papelera")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Añade una tarea rápida") },
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        val cleanTitle = taskTitle.trim()
                        if (cleanTitle.isNotEmpty()) {
                            onAddTask(cleanTitle)
                            taskTitle = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Crear tarea")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (pendingTasks.isEmpty()) {
                Text(
                    text = "No hay tareas pendientes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(170.dp)
                ) {
                    items(pendingTasks, key = { it.id }) { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(
                                    fadeInSpec = tween(AppMotion.MEDIUM, easing = FastOutSlowInEasing),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    fadeOutSpec = tween(AppMotion.SHORT)
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.title,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { onCompleteTask(task) }) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Completar tarea")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Deprecated(
    message = "Use QuickTasksSection instead",
    replaceWith = ReplaceWith("QuickTasksSection(pendingTasks, onAddTask, onCompleteTask, onOpenTrash)")
)
@Composable
fun QuickTasksScreen(
    pendingTasks: List<QuickTask>,
    onAddTask: (String) -> Unit,
    onCompleteTask: (QuickTask) -> Unit,
    onOpenTrash: () -> Unit
) {
    QuickTasksSection(
        pendingTasks = pendingTasks,
        onAddTask = onAddTask,
        onCompleteTask = onCompleteTask,
        onOpenTrash = onOpenTrash
    )
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
        title = { Text("Papelera (7 días)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (completedTasks.isEmpty()) {
                    Text(
                        text = "No hay tareas completadas en la papelera.",
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(completedTasks, key = { it.id }) { task ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TaskAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "Completada: ${task.completedAt?.format(formatter)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEmptyTrash) {
                Text("Vaciar papelera", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

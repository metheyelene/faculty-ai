package com.bits.facultyai.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bits.facultyai.ui.theme.KineticBorder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.TaskEntity
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.ui.components.KineticBadge
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import java.time.ZoneId

@Composable
fun TasksScreen(vm: TasksViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var quickAdd by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        KineticDisplayText(text = "MY TASKS", style = KineticType.display.copy(fontSize = 44.sp))
        Spacer(Modifier.height(KineticSpacing.md))

        // Natural-language quick add
        KineticTextField(
            value = quickAdd,
            onValueChange = { quickAdd = it },
            hint = "e.g. REMIND ME TOMORROW 9AM SUBMIT INTERNAL MARKS",
        )
        Spacer(Modifier.height(KineticSpacing.md))
        KineticButton(
            text = "ADD TASK",
            onClick = {
                if (quickAdd.isNotBlank()) {
                    vm.quickAdd(quickAdd) { created -> confirmation = "ADDED: " + created.uppercase() }
                    quickAdd = ""
                }
            },
        )
        confirmation?.let {
            Spacer(Modifier.height(KineticSpacing.sm))
            Text(text = it, style = KineticType.labelBold, color = k.accent)
        }

        Spacer(Modifier.height(KineticSpacing.xl))

        val open = tasks.filter { !it.completed }
        val done = tasks.filter { it.completed }
        val today = java.time.LocalDate.now()

        KineticSectionHeader(title = "DUE", trailing = { Text(text = open.size.toString(), style = KineticType.labelBold, color = k.accent) })
        if (open.isEmpty()) {
            KineticEmptyState(
                title = "YOU'RE ALL CAUGHT UP",
                message = "NO OPEN TASKS OR REMINDERS",
            )
        } else {
            open.forEach { task ->
                TaskRow(task = task, onToggle = { vm.toggleComplete(task) }, onDelete = { vm.delete(task) })
                KineticDivider()
            }
        }

        if (done.isNotEmpty()) {
            Spacer(Modifier.height(KineticSpacing.lg))
            KineticSectionHeader(title = "COMPLETED")
            done.forEach { task ->
                TaskRow(task = task, onToggle = { vm.toggleComplete(task) }, onDelete = { vm.delete(task) })
                KineticDivider()
            }
        }
        Spacer(Modifier.height(KineticSpacing.xl))
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    val k = LocalKineticColors.current
    val dueLabel = task.dueAt?.let {
        val d = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        TimeUtils.relativeDayLabel(d)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Checkbox fills with a quick spring; content cross-fades when completed.
    val checkFill by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (task.completed) 1f else 0f,
        animationSpec = com.bits.facultyai.ui.theme.KineticMotion.springFast(),
        label = "checkFill",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KineticSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkbox: 24dp visual inside a 40dp touch target.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable {
                    com.bits.facultyai.ui.components.KineticHaptics.success(context)
                    onToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(k.accent.copy(alpha = checkFill))
                    .border(KineticBorder.heavy, if (task.completed) k.accent else k.foreground),
                contentAlignment = Alignment.Center,
            ) {
                if (checkFill > 0.6f) {
                    Text(
                        text = "\u2713",
                        style = KineticType.labelBold,
                        color = k.accentForeground,
                        modifier = Modifier.graphicsLayer { alpha = ((checkFill - 0.6f) / 0.4f).coerceIn(0f, 1f) },
                    )
                }
            }
        }
        Spacer(Modifier.width(KineticSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = KineticType.bodyMedium,
                color = if (task.completed) k.mutedForeground else k.foreground,
            )
            if (dueLabel != null) {
                Text(text = dueLabel, style = KineticType.label, color = if (task.completed) k.mutedForeground else k.accent)
            }
        }
        if (task.priority == "HIGH" && !task.completed) {
            KineticBadge(text = "HIGH", filled = true, color = k.accent)
            Spacer(Modifier.width(KineticSpacing.sm))
        }
        Text(
            text = "✕",
            style = KineticType.labelBold,
            color = k.mutedForeground,
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(horizontal = 10.dp, vertical = 14.dp),
        )
    }
}

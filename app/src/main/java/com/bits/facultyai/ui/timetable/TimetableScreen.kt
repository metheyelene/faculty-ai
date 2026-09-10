package com.bits.facultyai.ui.timetable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.domain.TimetableExtractor
import com.bits.facultyai.ui.components.GlassDialogSurface
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.GlassSurface
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticLoadingState
import com.bits.facultyai.ui.components.KineticOutlinedButton
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun TimetableScreen(vm: TimetableViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val timetable by vm.timetable.collectAsStateWithLifecycle()
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val showAdd by vm.showAddDialog.collectAsStateWithLifecycle()
    val import by vm.import.collectAsStateWithLifecycle()
    val showHistory by vm.showHistory.collectAsStateWithLifecycle()
    var expandedSlotId by remember { mutableStateOf<Long?>(null) }
    var editingSlot by remember { mutableStateOf<ClassSlotEntity?>(null) }
    var lastSelectedDay by remember { mutableStateOf(selectedDay) }
    val dayShift = remember {
        androidx.compose.animation.core.Animatable(0f)
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.onImagePicked(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Text(text = "MY", style = KineticType.display, color = k.foreground)
        Text(text = "TIMETABLE", style = KineticType.display, color = k.accent)
        Spacer(Modifier.height(KineticSpacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            KineticButton(
                text = "UPLOAD PHOTO",
                onClick = { pickImage.launch("image/*") },
                modifier = Modifier.weight(1f),
                height = 44,
            )
            KineticOutlinedButton(
                text = "HISTORY",
                onClick = { vm.showHistory() },
                modifier = Modifier.weight(1f),
                height = 44,
            )
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs)) {
            listOf(1, 2, 3, 4, 5, 6).forEach { day ->
                val selected = selectedDay == day
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(if (selected) KineticBorder.heavy else KineticBorder.hair, if (selected) k.accent else k.border)
                        .background(if (selected) k.accent else Color.Transparent)
                        .clickable { vm.selectDay(day) }
                        .padding(vertical = KineticSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = TimeUtils.dayShort(day),
                        style = KineticType.labelBold,
                        color = if (selected) k.accentForeground else k.mutedForeground,
                    )
                }
            }
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        // Directional day transition: content slides toward the day you picked.
        val dayChanged = selectedDay != lastSelectedDay
        LaunchedEffect(selectedDay) {
            if (dayChanged) {
                dayShift.snapTo(if (selectedDay > lastSelectedDay) 60f else -60f)
                dayShift.animateTo(
                    0f,
                    com.bits.facultyai.ui.theme.KineticMotion.springStandard(),
                )
            }
            lastSelectedDay = selectedDay
        }
        val slots = timetable.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startTimeMinutes }
        Column(
            modifier = Modifier.graphicsLayer {
                translationX = dayShift.value
                alpha = 1f - (kotlin.math.abs(dayShift.value) / 120f)
            },
        ) {
        if (slots.isEmpty()) {
            KineticEmptyState(
                title = "NO CLASSES",
                message = "NOTHING SCHEDULED FOR " + TimeUtils.dayName(selectedDay),
                actionText = "ADD CLASS",
                onAction = { vm.openAddDialog() },
            )
        } else {
            slots.forEach { slot ->
                val nowMin = TimeUtils.nowMinutes()
                val isToday = selectedDay == java.time.LocalDate.now().dayOfWeek.value
                ClassCard(
                    slot = slot,
                    isNow = isToday && nowMin >= slot.startTimeMinutes && nowMin < slot.endTimeMinutes,
                    isPast = isToday && nowMin >= slot.endTimeMinutes,
                    expanded = expandedSlotId == slot.id,
                    onClick = { expandedSlotId = if (expandedSlotId == slot.id) null else slot.id },
                    onTakeAttendance = { vm.onAttendanceRequested(slot) },
                    onEdit = { editingSlot = slot },
                    onDelete = { vm.deleteSlot(slot.id) },
                )
                Spacer(Modifier.height(KineticSpacing.sm))
            }
        }
        }

        Spacer(Modifier.height(KineticSpacing.md))
        KineticButton(text = "ADD CLASS", onClick = { vm.openAddDialog() })
        Spacer(Modifier.height(KineticSpacing.xl))
    }

    if (showAdd) {
        ClassEditorDialog(vm = vm, existing = null)
    }
    if (editingSlot != null) {
        ClassEditorDialog(vm = vm, existing = editingSlot, onDismiss = { editingSlot = null })
    }
    if (import.step != TimetableViewModel.ImportStep.IDLE) {
        ImportFlowOverlay(vm = vm, import = import)
    }
    if (showHistory) {
        VersionHistorySheet(vm = vm)
    }
}

@Composable
private fun ClassCard(
    slot: ClassSlotEntity,
    isNow: Boolean,
    isPast: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onTakeAttendance: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val k = LocalKineticColors.current
    val fg = when {
        isNow -> k.accentForeground
        isPast -> k.mutedForeground
        else -> k.foreground
    }
    // Current class: solid accent (yellow + black text). Upcoming: normal
    // glass. Completed: muted glass. Spec-mandated, no exceptions.
    val cellModifier = Modifier
        .fillMaxWidth()
        .animateContentSize(
            animationSpec = com.bits.facultyai.ui.theme.KineticMotion.springStandard(),
        )
    if (isNow) {
        Column(
            modifier = cellModifier
                .border(KineticBorder.heavy, k.accent)
                .background(k.accent)
                .clickable(onClick = onClick)
                .padding(KineticSpacing.lg),
        ) {
            ClassCellContent(slot, fg, isNow, expanded, onTakeAttendance, onEdit, onDelete)
        }
    } else {
        GlassSurface(
            modifier = cellModifier,
            strength = if (isPast) GlassStrength.ULTRA_THIN else GlassStrength.THIN,
            onClick = onClick,
        ) {
            Column(Modifier.padding(KineticSpacing.lg)) {
                ClassCellContent(slot, fg, isNow, expanded, onTakeAttendance, onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun ClassCellContent(
    slot: ClassSlotEntity,
    fg: Color,
    isNow: Boolean,
    expanded: Boolean,
    onTakeAttendance: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val k = LocalKineticColors.current
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = TimeUtils.formatTime(slot.startTimeMinutes),
                style = KineticType.headingSm,
                color = fg,
            )
            Spacer(Modifier.width(KineticSpacing.sm))
            Text(
                text = "— " + TimeUtils.formatTime(slot.endTimeMinutes),
                style = KineticType.label.copy(fontSize = 12.sp),
                color = fg.copy(alpha = 0.7f),
            )
            Spacer(Modifier.weight(1f))
            if (isNow) {
                Text(text = "NOW", style = KineticType.labelBold, color = fg)
            }
        }
        Text(
            text = slot.subject.uppercase(),
            style = KineticType.heading,
            color = fg,
        )
        Text(
            text = slot.section + " · ROOM " + slot.room,
            style = KineticType.label.copy(fontSize = 12.sp),
            color = fg.copy(alpha = 0.8f),
        )
        if (expanded) {
            Spacer(Modifier.height(KineticSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticButton(text = "ATTENDANCE", onClick = onTakeAttendance, height = 44, modifier = Modifier.weight(1f))
                KineticOutlinedButton(text = "EDIT", onClick = onEdit, height = 44, modifier = Modifier.weight(1f))
                KineticOutlinedButton(text = "DELETE", onClick = onDelete, height = 44, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ClassEditorDialog(vm: TimetableViewModel, existing: ClassSlotEntity?, onDismiss: (() -> Unit)? = null) {
    val k = LocalKineticColors.current
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    var subject by remember { mutableStateOf(existing?.subject ?: "") }
    var section by remember { mutableStateOf(existing?.section ?: "") }
    var room by remember { mutableStateOf(existing?.room ?: "") }
    var day by remember { mutableStateOf(existing?.dayOfWeek ?: selectedDay) }
    var startH by remember { mutableStateOf(((existing?.startTimeMinutes ?: 540) / 60).toString()) }
    var startM by remember { mutableStateOf(((existing?.startTimeMinutes ?: 540) % 60).toString().padStart(2, '0')) }
    var endH by remember { mutableStateOf(((existing?.endTimeMinutes ?: 600) / 60).toString()) }
    var endM by remember { mutableStateOf(((existing?.endTimeMinutes ?: 600) % 60).toString().padStart(2, '0')) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = { onDismiss?.invoke() ?: vm.closeAddDialog() }) {
        GlassDialogSurface(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Text(
                if (existing == null) "ADD CLASS" else "EDIT CLASS",
                style = KineticType.heading,
                color = k.foreground,
            )
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = subject, onValueChange = { subject = it }, hint = "SUBJECT")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = section, onValueChange = { section = it }, hint = "SECTION (e.g. III ECE-A)")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = room, onValueChange = { room = it }, hint = "ROOM")
            Spacer(Modifier.height(KineticSpacing.sm))
            Text(text = "DAY", style = KineticType.labelBold, color = k.mutedForeground)
            Spacer(Modifier.height(KineticSpacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                listOf(1, 2, 3, 4, 5, 6).forEach { d ->
                    val selected = day == d
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(if (selected) KineticBorder.heavy else KineticBorder.hair, if (selected) k.accent else k.border)
                            .background(if (selected) k.accent else Color.Transparent)
                            .clickable { day = d }
                            .padding(vertical = KineticSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = TimeUtils.dayShort(d).take(1),
                            style = KineticType.labelBold,
                            color = if (selected) k.accentForeground else k.mutedForeground,
                        )
                    }
                }
            }
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticTextField(value = startH, onValueChange = { startH = it }, hint = "START H", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                KineticTextField(value = startM, onValueChange = { startM = it }, hint = "MIN", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                KineticTextField(value = endH, onValueChange = { endH = it }, hint = "END H", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                KineticTextField(value = endM, onValueChange = { endM = it }, hint = "MIN", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            }
            error?.let {
                Spacer(Modifier.height(KineticSpacing.xs))
                Text(text = it, style = KineticType.label, color = k.statusError)
            }
            Spacer(Modifier.height(KineticSpacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticOutlinedButton(
                    text = "CANCEL",
                    onClick = { onDismiss?.invoke() ?: vm.closeAddDialog() },
                    modifier = Modifier.weight(1f),
                )
                KineticButton(
                    text = "SAVE",
                    onClick = {
                        val s = (startH.toIntOrNull() ?: -1) * 60 + (startM.toIntOrNull() ?: -1)
                        val e = (endH.toIntOrNull() ?: -1) * 60 + (endM.toIntOrNull() ?: -1)
                        when {
                            subject.isBlank() -> error = "Please enter a subject."
                            s !in 0..1439 || e !in 0..1440 -> error = "Times must be valid (0–24h)."
                            e <= s -> error = "End time must be after start time."
                            else -> {
                                val entity = ClassSlotEntity(
                                    id = existing?.id ?: 0,
                                    dayOfWeek = day,
                                    startTimeMinutes = s,
                                    endTimeMinutes = e,
                                    subject = subject.trim(),
                                    section = section.ifBlank { "—" },
                                    room = room.ifBlank { "—" },
                                )
                                if (existing == null) vm.addSlot(entity) else vm.updateSlot(entity)
                                onDismiss?.invoke() ?: vm.closeAddDialog()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// Photo import flow: PROCESSING → REVIEW → CONFIRM
// ------------------------------------------------------------------

@Composable
private fun ImportFlowOverlay(vm: TimetableViewModel, import: TimetableViewModel.ImportState) {
    val k = LocalKineticColors.current
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = { if (import.step != TimetableViewModel.ImportStep.PROCESSING) vm.cancelImport() }) {
        GlassDialogSurface {
            when (import.step) {
                TimetableViewModel.ImportStep.PROCESSING -> {
                    Text(text = "PROCESSING PHOTO", style = KineticType.heading, color = k.foreground)
                    Spacer(Modifier.height(KineticSpacing.md))
                    KineticLoadingState(label = "READING YOUR TIMETABLE")
                    Spacer(Modifier.height(KineticSpacing.sm))
                    Text(
                        text = "You'll review every row before anything is saved.",
                        style = KineticType.label,
                        color = k.mutedForeground,
                    )
                }
                TimetableViewModel.ImportStep.REVIEW -> {
                    Text(text = "REVIEW TIMETABLE", style = KineticType.heading, color = k.foreground)
                    Spacer(Modifier.height(KineticSpacing.xs))
                    Text(
                        text = "Correct anything the scan got wrong, then confirm.",
                        style = KineticType.label,
                        color = k.mutedForeground,
                    )
                    import.error?.let {
                        Spacer(Modifier.height(KineticSpacing.sm))
                        Text(text = it, style = KineticType.label, color = k.statusWarning)
                    }
                    Spacer(Modifier.height(KineticSpacing.md))
                    Column(
                        modifier = Modifier
                            .height(340.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        import.draft.forEachIndexed { index, slot ->
                            DraftRowEditor(
                                index = index,
                                slot = slot,
                                onUpdate = { vm.updateDraft(index, it) },
                                onRemove = { vm.removeDraftRow(index) },
                            )
                            Spacer(Modifier.height(KineticSpacing.sm))
                        }
                        KineticGhostButton(text = "+ ADD ROW", onClick = { vm.addDraftRow() })
                    }
                    Spacer(Modifier.height(KineticSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                        KineticOutlinedButton(text = "CANCEL", onClick = { vm.cancelImport() }, modifier = Modifier.weight(1f))
                        KineticButton(
                            text = "CONFIRM",
                            enabled = import.draft.any { it.subject.isNotBlank() },
                            onClick = {
                                com.bits.facultyai.ui.components.KineticHaptics.success(context)
                                vm.confirmImport()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun DraftRowEditor(
    index: Int,
    slot: TimetableExtractor.ExtractedSlot,
    onUpdate: (TimetableExtractor.ExtractedSlot) -> Unit,
    onRemove: () -> Unit,
) {
    val k = LocalKineticColors.current
    var subject by remember(index) { mutableStateOf(slot.subject) }
    var section by remember(index) { mutableStateOf(slot.section) }
    var room by remember(index) { mutableStateOf(slot.room) }
    var startH by remember(index) { mutableStateOf((slot.startMinutes / 60).toString()) }
    var startM by remember(index) { mutableStateOf((slot.startMinutes % 60).toString().padStart(2, '0')) }
    var endH by remember(index) { mutableStateOf((slot.endMinutes / 60).toString()) }
    var endM by remember(index) { mutableStateOf((slot.endMinutes % 60).toString().padStart(2, '0')) }
    var day by remember(index) { mutableStateOf(slot.dayOfWeek) }

    fun commit() {
        val s = (startH.toIntOrNull() ?: slot.startMinutes / 60) * 60 + (startM.toIntOrNull() ?: slot.startMinutes % 60)
        val e = (endH.toIntOrNull() ?: slot.endMinutes / 60) * 60 + (endM.toIntOrNull() ?: slot.endMinutes % 60)
        onUpdate(
            slot.copy(
                dayOfWeek = day,
                startMinutes = s.coerceIn(0, 1439),
                endMinutes = e.coerceIn(1, 1440),
                subject = subject,
                section = section,
                room = room,
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(KineticBorder.hair, k.border)
            .padding(KineticSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "ROW ${index + 1}", style = KineticType.labelBold, color = k.accent)
            Spacer(Modifier.weight(1f))
            Text(
                text = "REMOVE",
                style = KineticType.label,
                color = k.statusError,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(KineticSpacing.xs),
            )
        }
        Spacer(Modifier.height(KineticSpacing.xs))
        KineticTextField(value = subject, onValueChange = { subject = it; commit() }, hint = "SUBJECT")
        Spacer(Modifier.height(KineticSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs)) {
            KineticTextField(value = section, onValueChange = { section = it; commit() }, hint = "SECTION", modifier = Modifier.weight(1f))
            KineticTextField(value = room, onValueChange = { room = it; commit() }, hint = "ROOM", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(KineticSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs)) {
            KineticTextField(value = startH, onValueChange = { startH = it; commit() }, hint = "SH", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            KineticTextField(value = startM, onValueChange = { startM = it; commit() }, hint = "SM", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            KineticTextField(value = endH, onValueChange = { endH = it; commit() }, hint = "EH", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            KineticTextField(value = endM, onValueChange = { endM = it; commit() }, hint = "EM", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        }
        Spacer(Modifier.height(KineticSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs)) {
            listOf(1, 2, 3, 4, 5, 6).forEach { d ->
                val selected = day == d
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(if (selected) KineticBorder.heavy else KineticBorder.hair, if (selected) k.accent else k.border)
                        .background(if (selected) k.accent else Color.Transparent)
                        .clickable { day = d; commit() }
                        .padding(vertical = KineticSpacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = TimeUtils.dayShort(d).take(1),
                        style = KineticType.labelBold.copy(fontSize = 11.sp),
                        color = if (selected) k.accentForeground else k.mutedForeground,
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionHistorySheet(vm: TimetableViewModel) {
    val k = LocalKineticColors.current
    val versions by vm.versions.collectAsStateWithLifecycle()

    androidx.compose.ui.window.Dialog(onDismissRequest = { vm.dismissHistory() }) {
        GlassDialogSurface {
            Text(text = "TIMETABLE HISTORY", style = KineticType.heading, color = k.foreground)
            Spacer(Modifier.height(KineticSpacing.xs))
            Text(
                text = "Previous versions are archived — nothing is lost when you replace your timetable.",
                style = KineticType.label,
                color = k.mutedForeground,
            )
            Spacer(Modifier.height(KineticSpacing.md))
            if (versions.isEmpty()) {
                Text(
                    text = "NO SAVED VERSIONS YET",
                    style = KineticType.labelBold,
                    color = k.mutedForeground,
                )
            } else {
                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    versions.forEach { version ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(KineticBorder.hair, k.border)
                                .clickable { vm.restoreVersion(version) }
                                .padding(KineticSpacing.md),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "V${version.versionNumber}",
                                    style = KineticType.labelBold,
                                    color = k.accent,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${version.slotCount} classes",
                                    style = KineticType.label,
                                    color = k.mutedForeground,
                                )
                            }
                            Text(
                                text = version.sourceLabel + " · " + TimeUtils.formatDate(
                                    java.time.Instant.ofEpochMilli(version.createdAt)
                                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                ),
                                style = KineticType.label,
                                color = k.mutedForeground,
                            )
                            Text(
                                text = "TAP TO RESTORE THIS VERSION",
                                style = KineticType.label.copy(fontSize = 10.sp),
                                color = k.accent,
                            )
                        }
                        Spacer(Modifier.height(KineticSpacing.sm))
                    }
                }
            }
            Spacer(Modifier.height(KineticSpacing.md))
            KineticOutlinedButton(text = "CLOSE", onClick = { vm.dismissHistory() })
        }
    }
}

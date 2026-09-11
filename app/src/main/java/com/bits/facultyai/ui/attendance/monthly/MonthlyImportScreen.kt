package com.bits.facultyai.ui.attendance.monthly

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.domain.AttendanceExcelParser
import com.bits.facultyai.ui.components.GlassChip
import com.bits.facultyai.ui.components.GlassStat
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticOutlinedButton
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.navigation.KineticBottomNavigation
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

/** MIME filter for the .xlsx picker; the reader is content-sniffing, so extras don't hurt. */
private val XLSX_MIMES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/octet-stream", // WhatsApp/Drive shares often serve xlsx as a generic stream
)

/**
 * Excel monthly-attendance import for the class/month chosen on the hub:
 * pick .xlsx -> choose the stamping subject -> map columns if detection
 * fails -> preview data + errors -> conflict policy -> guarded commit.
 * Nothing is written before confirmation.
 */
@Composable
fun MonthlyImportScreen(
    payload: String,
    onDone: () -> Unit,
    vm: MonthlyImportViewModel = viewModel(
        key = payload,
        factory = run {
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
            MonthlyImportViewModel.factory(app, payload)
        },
    ),
) {
    val k = LocalKineticColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val step by vm.step.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadPreview(uri, fileNameFrom(context, uri))
    }

    var pendingCommit by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Text(text = "IMPORT", style = KineticType.display, color = k.foreground)
        Text(text = "MONTHLY ATTENDANCE", style = KineticType.display, color = k.accent)
        Spacer(Modifier.height(KineticSpacing.lg))

        Text(
            text = "${romanOf(vm.year)} ${vm.section} \u00B7 ${vm.subject.uppercase()} \u00B7 ${
                vm.month.month.name.take(3).replaceFirstChar { it.uppercase() }
            } ${vm.month.year}",
            style = KineticType.label,
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(KineticSpacing.lg))

        when (val s = step) {
            ImportStep.Idle -> {
                KineticEmptyState(
                    title = "SELECT AN EXCEL FILE",
                    message = "SUPPORTED FORMATS:\n\u2022 ROLL NO | STUDENT NAME | 01 | 02 | ... (day columns, P/A/L/E marks)\n\u2022 STUDENT ID | NAME | DATE | STATUS (one row per day)",
                    actionText = "CHOOSE .XLSX FILE",
                    onAction = { picker.launch(XLSX_MIMES) },
                )
                Spacer(Modifier.height(KineticSpacing.lg))
                KineticGhostButton(text = "CANCEL", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }

            is ImportStep.NeedSubject -> SubjectGate(
                fileName = s.fileName,
                onPick = { subject -> vm.setImportSubject(s.fileName, s.table, subject) },
                onLoadOptions = vm::subjectOptions,
                onCancel = onDone,
            )

            is ImportStep.NeedMapping -> MappingEditor(
                fileName = s.fileName,
                table = s.table,
                headers = s.headers,
                onApply = { mapping -> vm.applyMapping(s.fileName, s.table, mapping, vm.subject) },
                onCancel = { vm.cancel() },
            )

            is ImportStep.Failed -> {
                KineticEmptyState(title = "COULDN'T IMPORT", message = s.message)
                Spacer(Modifier.height(KineticSpacing.lg))
                KineticButton(text = "TRY ANOTHER FILE", onClick = { vm.cancel() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(KineticSpacing.sm))
                KineticGhostButton(text = "CANCEL", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }

            is ImportStep.Previewing -> ImportPreview(
                step = s,
                onPickAgain = { picker.launch(XLSX_MIMES) },
                onCancel = onDone,
                onImport = { pendingCommit = true },
            )

            is ImportStep.Done -> {
                KineticSectionHeader(title = "IMPORT COMPLETE")
                Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                    GlassStat(value = s.imported.toString(), label = "IMPORTED", modifier = Modifier.weight(1f), emphasized = true)
                    GlassStat(value = s.updated.toString(), label = "UPDATED", modifier = Modifier.weight(1f))
                    GlassStat(value = s.skipped.toString(), label = "SKIPPED", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(KineticSpacing.lg))
                KineticButton(text = "VIEW MONTHLY SUMMARY", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(KineticBottomNavigation.bottomClearance()))
    }

    // Conflict gate: the explicit choice before anything already-stored is touched.
    val previewing = step as? ImportStep.Previewing
    if (pendingCommit && previewing != null) {
        if (previewing.conflicts.isEmpty()) {
            pendingCommit = false
            vm.commitImport(ConflictPolicy.UPDATE)
        } else {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingCommit = false },
                title = { Text("CONFLICTS DETECTED", style = KineticType.heading, color = k.foreground) },
                text = {
                    Text(
                        text = "${previewing.conflicts.size} MARKS ALREADY EXIST FOR ${previewing.subject.uppercase()} THIS MONTH.\n\n" +
                            "UPDATE replaces the stored marks with the sheet's values.\n" +
                            "KEEP EXISTING imports only the new marks.",
                        style = KineticType.bodyMedium,
                        color = k.mutedForeground,
                    )
                },
                confirmButton = {
                    KineticButton(text = "UPDATE EXISTING", onClick = {
                        pendingCommit = false
                        vm.commitImport(ConflictPolicy.UPDATE)
                    })
                },
                dismissButton = {
                    KineticOutlinedButton(text = "KEEP EXISTING", onClick = {
                        pendingCommit = false
                        vm.commitImport(ConflictPolicy.SKIP)
                    })
                },
                containerColor = k.glassRegular,
            )
        }
    }
}

/** Subject gate: imports must stamp sessions with a real subject (never a sentinel). */
@Composable
private fun SubjectGate(
    fileName: String,
    onPick: (String) -> Unit,
    onLoadOptions: ((List<String>) -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    val k = LocalKineticColors.current
    val options = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { onLoadOptions { list -> options.clear(); options.addAll(list) } }

    KineticSectionHeader(title = "WHICH SUBJECT IS THIS SHEET FOR?")
    Text(
        text = "Imported sessions are stored under this subject so they appear in every per-subject view.",
        style = KineticType.label,
        color = k.mutedForeground,
    )
    Spacer(Modifier.height(KineticSpacing.md))
    Text(text = fileName, style = KineticType.label, color = k.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(KineticSpacing.lg))
    if (options.isEmpty()) {
        Text(text = "NO SUBJECTS YET \u2014 ADD SUBJECTS IN YOUR TIMETABLE OR PROFILE FIRST", style = KineticType.label, color = k.statusWarning)
    } else {
        options.forEach { subject ->
            GlassChip(label = subject.take(24), selected = false, onClick = { onPick(subject) })
            Spacer(Modifier.height(KineticSpacing.sm))
        }
    }
    Spacer(Modifier.height(KineticSpacing.lg))
    KineticGhostButton(text = "CANCEL", onClick = onCancel, modifier = Modifier.fillMaxWidth())
}

/** Manual column mapping — same machinery the automatic detection produces. */
@Composable
private fun MappingEditor(
    fileName: String,
    table: List<List<String?>>,
    headers: List<String?>,
    onApply: (AttendanceExcelParser.Mapping) -> Unit,
    onCancel: () -> Unit,
) {
    val k = LocalKineticColors.current
    val columnCount = remember(headers) { headers.maxOfOrNull { it?.length ?: 0 } ?: 0 }.let { headers.size }
    var rollCol by remember { mutableStateOf(-1) }
    var nameCol by remember { mutableStateOf(-1) }
    var dateCol by remember { mutableStateOf(-1) }
    var statusCol by remember { mutableStateOf(-1) }
    val dayCols = remember { mutableStateListOf<Pair<Int, Int>>() }

    KineticSectionHeader(title = "MAP COLUMNS")
    Text(
        text = "Automatic detection couldn't read this sheet's headers. Pick the columns by hand.",
        style = KineticType.label,
        color = k.mutedForeground,
    )
    Spacer(Modifier.height(KineticSpacing.lg))

    fun columnLabel(i: Int): String = "${'A' + i} \u00B7 ${headers.getOrNull(i)?.take(12) ?: "(empty)"}"

    ColumnPicker("ROLL NUMBER COLUMN", columnCount, rollCol) { rollCol = it }
    ColumnPicker("STUDENT NAME COLUMN", columnCount, nameCol) { nameCol = it }

    // Layout choice: day columns OR (date + status).
    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
        GlassChip(label = "DAY COLUMNS", selected = dayCols.isNotEmpty(), onClick = { dateCol = -1; statusCol = -1 })
        GlassChip(
            label = "DATE + STATUS ROWS",
            selected = dateCol >= 0 || statusCol >= 0,
            onClick = { dayCols.clear() },
        )
    }
    Spacer(Modifier.height(KineticSpacing.md))

    if (dayCols.isNotEmpty()) {
        KineticSectionHeader(title = "DAY COLUMNS (TAP TO TOGGLE)")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            repeat(columnCount) { i ->
                val already = dayCols.any { it.first == i }
                val day = remember(i) { AttendanceExcelParser.dayFromHeader(headers.getOrNull(i), java.time.MonthDay.now().monthValue) ?: i + 1 }
                GlassChip(
                    label = columnLabel(i),
                    selected = already,
                    onClick = {
                        if (already) dayCols.removeAll { it.first == i } else dayCols.add(i to day)
                        dayCols.sortBy { it.second }
                    },
                )
            }
        }
    } else {
        ColumnPicker("DATE COLUMN", columnCount, dateCol) { dateCol = it }
        ColumnPicker("STATUS COLUMN (P/A/L/E)", columnCount, statusCol) { statusCol = it }
    }

    Spacer(Modifier.height(KineticSpacing.lg))
    val wideReady = rollCol >= 0 && dayCols.size >= 2
    val longReady = rollCol >= 0 && dateCol >= 0 && statusCol >= 0
    KineticButton(
        text = "PARSE WITH THESE COLUMNS",
        enabled = wideReady || longReady,
        onClick = {
            val mapping = if (longReady) {
                AttendanceExcelParser.Mapping(
                    format = AttendanceExcelParser.Format.LONG,
                    rollColumn = rollCol,
                    nameColumn = nameCol.takeIf { it >= 0 },
                    dateColumn = dateCol,
                    statusColumn = statusCol,
                )
            } else {
                AttendanceExcelParser.Mapping(
                    format = AttendanceExcelParser.Format.WIDE,
                    rollColumn = rollCol,
                    nameColumn = nameCol.takeIf { it >= 0 },
                    dayColumns = dayCols.toList(),
                )
            }
            onApply(mapping)
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(KineticSpacing.sm))
    KineticGhostButton(text = "CANCEL", onClick = onCancel, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnPicker(title: String, columnCount: Int, selected: Int, onSelect: (Int) -> Unit) {
    KineticSectionHeader(title = title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
    ) {
        repeat(columnCount) { i ->
            GlassChip(label = "${'A' + i}", selected = selected == i, onClick = { onSelect(i) })
        }
    }
    Spacer(Modifier.height(KineticSpacing.md))
}

@Composable
private fun ImportPreview(
    step: ImportStep.Previewing,
    onPickAgain: () -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit,
) {
    val k = LocalKineticColors.current
    val p = step.preview

    KineticSectionHeader(title = "IMPORT SUMMARY \u00B7 ${step.subject.uppercase()}")
    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
        GlassStat(value = p.marks.size.toString(), label = "MATCHED", modifier = Modifier.weight(1f), emphasized = true)
        GlassStat(value = p.unmatched.size.toString(), label = "UNMATCHED", modifier = Modifier.weight(1f))
        GlassStat(value = p.invalid.size.toString(), label = "INVALID", modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(KineticSpacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
        GlassStat(value = p.fileDuplicates.size.toString(), label = "DUPLICATES", modifier = Modifier.weight(1f))
        GlassStat(value = step.conflicts.size.toString(), label = "EXISTING", modifier = Modifier.weight(1f), emphasized = step.conflicts.isNotEmpty())
        GlassStat(value = p.detectedDates.size.toString(), label = "DATES", modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(KineticSpacing.sm))
    Text(
        text = "${step.fileName} \u00B7 ${step.format ?: AttendanceExcelParser.Format.WIDE}",
        style = KineticType.label,
        color = k.mutedForeground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(KineticSpacing.lg))

    // ---- the actual data about to be written (not just the errors) ----
    if (p.marks.isNotEmpty()) MarksGrid(p)
    if (p.unmatched.isNotEmpty()) ReviewList("UNMATCHED ROLL NUMBERS", p.unmatched.map { "ROW ${it.rowNumber}: \u201C${it.rollNumber}\u201D NOT IN THIS CLASS ROSTER" })
    if (p.invalid.isNotEmpty()) ReviewList("INVALID ROWS", p.invalid.map { "ROW ${it.rowNumber}: ${it.reason.uppercase()}" })
    if (p.fileDuplicates.isNotEmpty()) ReviewList("DUPLICATES IN FILE", p.fileDuplicates.map { "ROW ${it.rowNumber}: ${it.rollNumber} ${it.dateIso} (FIRST AT ROW ${it.firstRowNumber})" })
    if (step.conflicts.isNotEmpty()) {
        ReviewList(
            "ALREADY MARKED (YOU'LL CHOOSE UPDATE OR KEEP)",
            step.conflicts.map {
                "ROW ${it.mark.rowNumber}: ${it.mark.studentName} ${it.mark.dateIso} \u2014 STORED ${it.existingStatus}, SHEET ${it.mark.status}"
            },
        )
    }

    val importable = p.marks.isNotEmpty()
    if (!importable) {
        Spacer(Modifier.height(KineticSpacing.lg))
        KineticEmptyState(
            title = "NOTHING TO IMPORT",
            message = "NO READABLE P/A/L/E MARKS WERE FOUND FOR ${step.month.month.name.take(3)} ${step.month.year}",
        )
    }

    Spacer(Modifier.height(KineticSpacing.lg))
    KineticButton(
        text = if (importable) "IMPORT ATTENDANCE" else "CHOOSE A DIFFERENT FILE",
        onClick = { if (importable) onImport() else onPickAgain() },
        modifier = Modifier.fillMaxWidth(),
    )
    if (importable) {
        Spacer(Modifier.height(KineticSpacing.sm))
        KineticGhostButton(text = "CANCEL", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

/** Compact preview grid: per student, the parsed mark letters for the first dates. */
@Composable
private fun MarksGrid(p: AttendanceExcelParser.Preview) {
    val k = LocalKineticColors.current
    KineticSectionHeader(title = "DATA PREVIEW")
    val byStudent = p.marks.groupBy { it.studentId }
    val dates = p.detectedDates.take(8)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = KineticSpacing.xs)) {
        Text(text = "ROLL", style = KineticType.label, color = k.mutedForeground, modifier = Modifier.width(56.dp))
        Text(text = "STUDENT", style = KineticType.label, color = k.mutedForeground, modifier = Modifier.weight(1f))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            dates.forEach { d ->
                Text(
                    text = d.substring(8, 10),
                    style = KineticType.label.copy(fontSize = 9.sp),
                    color = k.mutedForeground,
                    modifier = Modifier.width(22.dp),
                )
            }
        }
    }
    KineticDivider()
    byStudent.values.take(12).forEach { marksOfDay ->
        val first = marksOfDay.first()
        val byDate = marksOfDay.associateBy { it.dateIso }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(text = first.rollNumber, style = KineticType.label, color = k.accent, modifier = Modifier.width(56.dp))
            Text(
                text = first.studentName,
                style = KineticType.label,
                color = k.foreground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                dates.forEach { d ->
                    val m = byDate[d]
                    Text(
                        text = when (m?.status) {
                            AttendanceExcelParser.Status.PRESENT -> "P"
                            AttendanceExcelParser.Status.LATE -> "L"
                            AttendanceExcelParser.Status.EXCUSED -> "E"
                            AttendanceExcelParser.Status.ABSENT -> "A"
                            else -> "\u00B7"
                        },
                        style = KineticType.labelBold.copy(fontSize = 10.sp),
                        color = when (m?.status) {
                            AttendanceExcelParser.Status.PRESENT -> k.statusSuccess
                            AttendanceExcelParser.Status.LATE -> k.statusWarning
                            AttendanceExcelParser.Status.EXCUSED -> k.mutedForeground
                            AttendanceExcelParser.Status.ABSENT -> k.statusError
                            else -> k.border
                        },
                        modifier = Modifier.width(22.dp),
                    )
                }
            }
        }
        KineticDivider()
    }
    val shown = byStudent.size
    if (shown > 12) {
        Text(text = "+ ${shown - 12} MORE STUDENTS", style = KineticType.label.copy(fontSize = 10.sp), color = k.mutedForeground)
        Spacer(Modifier.height(KineticSpacing.sm))
    }
}

@Composable
private fun ReviewList(title: String, items: List<String>) {
    val k = LocalKineticColors.current
    KineticSectionHeader(title = "$title (${items.size})")
    items.take(8).forEach { line ->
        Text(
            text = line,
            style = KineticType.label.copy(fontSize = 10.sp),
            color = k.mutedForeground,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
    if (items.size > 8) {
        Text(
            text = "+ ${items.size - 8} MORE",
            style = KineticType.label.copy(fontSize = 10.sp),
            color = k.mutedForeground,
        )
    }
    Spacer(Modifier.height(KineticSpacing.sm))
}

/** Display name of a content URI (falls back to the last path segment). */
private fun fileNameFrom(context: android.content.Context, uri: android.net.Uri): String {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "attendance.xlsx"
}

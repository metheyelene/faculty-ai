package com.bits.facultyai.ui.attendance.monthly

import androidx.compose.foundation.background
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.domain.MonthlyAttendance
import com.bits.facultyai.ui.components.GlassChip
import com.bits.facultyai.ui.components.GlassStat
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.components.KineticGhostButton
import androidx.compose.ui.platform.LocalContext
import com.bits.facultyai.ui.navigation.KineticBottomNavigation
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import java.time.YearMonth

private enum class SortMode(val label: String) {
    ROLL("ROLL"), NAME("NAME"), LOW_PCT("LOWEST %"), HIGH_PCT("HIGHEST %"),
}

/**
 * MONTHLY ATTENDANCE hub: class + subject + month selectors, class statistics,
 * and the per-student summary table. Rows open the day-by-day detail; the
 * import button opens the Excel import flow for the current selection.
 */
@Composable
fun MonthlyAttendanceScreen(
    onOpenStudent: (studentId: Long, monthIso: String) -> Unit,
    onImport: (payload: String) -> Unit,
    vm: MonthlyAttendanceViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Application missing in CreationExtras")
                MonthlyAttendanceViewModel(FacultyDatabase.get(app).facultyDao())
            }
        },
    ),
) {
    val k = LocalKineticColors.current
    val selection by vm.selection.collectAsStateWithLifecycle()
    val sections by vm.availableSections.collectAsStateWithLifecycle()
    val subjects by vm.availableSubjects.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()

    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortMode.ROLL) }
    val context = LocalContext.current

    // SAF export: faculty picks the destination; the writer streams the summary in.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                runCatching { vm.exportCurrentSummary(out) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Text(text = "MONTHLY", style = KineticType.display, color = k.foreground)
        Text(text = "ATTENDANCE", style = KineticType.display, color = k.accent)
        Spacer(Modifier.height(KineticSpacing.lg))

        // ---- selectors ----
        KineticSectionHeader(title = "CLASS")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            listOf(1 to "1ST", 2 to "2ND", 3 to "3RD", 4 to "4TH").forEach { (y, label) ->
                GlassChip(label = label, selected = selection.year == y, onClick = { vm.setYear(y) })
            }
            sections.forEach { s ->
                GlassChip(label = s, selected = selection.section == s, onClick = { vm.setSection(s) })
            }
        }

        if (subjects.isNotEmpty()) {
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
            ) {
                GlassChip(label = "ALL SUBJECTS", selected = selection.subject == "ALL", onClick = { vm.setSubject("ALL") })
                subjects.forEach { s ->
                    GlassChip(
                        label = s.take(18),
                        selected = selection.subject == s,
                        onClick = { vm.setSubject(s) },
                    )
                }
            }
        }

        Spacer(Modifier.height(KineticSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            KineticGhostButton(text = "\u25C0", onClick = {
                vm.setMonth(selection.month.minusMonths(1))
            })
            Text(
                text = selection.month.month.name
                    .take(3).replaceFirstChar { it.uppercase() } + " " + selection.month.year,
                style = KineticType.headingSm,
                color = k.foreground,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            KineticGhostButton(text = "\u25B6", onClick = {
                vm.setMonth(selection.month.plusMonths(1))
            })
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        // ---- content states ----
        val sel = selection
        when {
            sel.year == 0 -> KineticEmptyState(
                title = "PICK A CLASS",
                message = "SELECT THE YEAR AND SECTION TO SEE THE MONTHLY SUMMARY",
            )
            sel.section.isEmpty() && sections.isEmpty() -> KineticEmptyState(
                title = "NO STUDENTS FOR THIS YEAR",
                message = "IMPORT YOUR STUDENT LIST IN STUDENTS FIRST",
            )
            sel.section.isEmpty() -> KineticSectionHeader(title = "SECTION")
            summary == null -> Unit
            else -> MonthlySummaryContent(
                summary = summary!!,
                search = search,
                onSearch = { search = it },
                sort = sort,
                onSort = { sort = it },
                onOpenStudent = onOpenStudent,
            )
        }
        if (summary != null) {
            Spacer(Modifier.height(KineticSpacing.lg))
            KineticButton(
                text = "IMPORT MONTHLY ATTENDANCE",
                onClick = { onImport(vm.importPayload()) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticGhostButton(
                text = "EXPORT MONTHLY SUMMARY (.XLSX)",
                onClick = { exportLauncher.launch(exportFileName(summary!!)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(KineticBottomNavigation.bottomClearance()))
    }
}

@Composable
private fun MonthlySummaryContent(
    summary: MonthlySummaryUi,
    search: String,
    onSearch: (String) -> Unit,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    onOpenStudent: (Long, String) -> Unit,
) {
    val k = LocalKineticColors.current
    val stats = summary.classStats
    val pct = { d: Double -> "${"%.1f".format(d)}%" }

    // ---- class statistics ----
    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
        GlassStat(value = stats.totalStudents.toString(), label = "STUDENTS", modifier = Modifier.weight(1f), emphasized = true)
        GlassStat(value = pct(stats.averageAttendance), label = "AVERAGE", modifier = Modifier.weight(1f))
        GlassStat(value = pct(stats.highest), label = "HIGHEST", modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(KineticSpacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
        GlassStat(value = pct(stats.lowest), label = "LOWEST", modifier = Modifier.weight(1f))
        GlassStat(value = stats.shortage.toString(), label = "SHORTAGE", modifier = Modifier.weight(1f), emphasized = stats.shortage > 0)
        GlassStat(value = summary.sessionDates.size.toString(), label = "SESSIONS", modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(KineticSpacing.lg))

    KineticSectionHeader(title = "${summary.classLabel} \u00B7 ${summary.subjectLabel} \u00B7 ${summary.monthLabel}")

    if (summary.students.isEmpty()) {
        KineticEmptyState(
            title = "NO STUDENTS",
            message = "IMPORT YOUR STUDENT LIST TO SEE THE MONTHLY SUMMARY",
        )
        return
    }

    // ---- search + sort controls ----
    com.bits.facultyai.ui.components.KineticTextField(
        value = search,
        onValueChange = onSearch,
        hint = "SEARCH NAME OR ROLL NO",
    )
    Spacer(Modifier.height(KineticSpacing.sm))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
    ) {
        SortMode.entries.forEach { mode ->
            GlassChip(label = mode.label, selected = sort == mode, onClick = { onSort(mode) })
        }
    }
    Spacer(Modifier.height(KineticSpacing.md))

    val monthIso = "%04d-%02d".format(summary.month.year, summary.month.monthValue)
    val rows = summary.students
        .filter { s ->
            val q = search.trim()
            q.isEmpty() || s.student.name.contains(q, ignoreCase = true) || s.student.rollNumber.contains(q, ignoreCase = true)
        }
        .let { list ->
            when (sort) {
                SortMode.ROLL -> list
                SortMode.NAME -> list.sortedBy { it.student.name.lowercase() }
                SortMode.LOW_PCT -> list.sortedBy { it.percentage }
                SortMode.HIGH_PCT -> list.sortedByDescending { it.percentage }
            }
        }

    if (rows.isEmpty()) {
        KineticEmptyState(title = "NO MATCHES", message = "NO STUDENT MATCHES \"$search\"")
        return
    }

    // ---- responsive table: scrolls horizontally on phones, full width on wide screens ----
    val tableMinWidth = 640.dp
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MonthlyTableHeader()
                KineticDivider()
                rows.forEachIndexed { index, row ->
                    MonthlyTableRow(row = row, index = index, onClick = { onOpenStudent(row.student.id, monthIso) })
                    KineticDivider()
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Column(modifier = Modifier.widthIn(min = tableMinWidth)) {
                    MonthlyTableHeader()
                    KineticDivider()
                    rows.forEachIndexed { index, row ->
                        MonthlyTableRow(row = row, index = index, onClick = { onOpenStudent(row.student.id, monthIso) })
                        KineticDivider()
                    }
                }
            }
        }
    }
}

/** Single source for the table's columns: label, width share, header flag. */
private val COLUMNS = listOf(
    Triple("ROLL", 0.12f, false),
    Triple("STUDENT", 0.34f, false),
    Triple("DAYS", 0.10f, false),
    Triple("P", 0.09f, false),
    Triple("L", 0.08f, false),
    Triple("E", 0.08f, false),
    Triple("A", 0.09f, false),
    Triple("%", 0.10f, true),
)

@Composable
private fun MonthlyTableHeader() {
    val k = LocalKineticColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = KineticSpacing.sm)) {
        COLUMNS.forEach { (label, weight, emphasized) ->
            TableCell(
                text = label,
                weight = weight,
                color = if (emphasized) k.accent else k.mutedForeground,
                bold = emphasized,
            )
        }
    }
}

@Composable
private fun MonthlyTableRow(
    row: MonthlyAttendance.StudentMonthly,
    index: Int,
    onClick: () -> Unit,
) {
    val k = LocalKineticColors.current
    val pctColor = when {
        row.workingDays == 0 -> k.mutedForeground
        row.shortage -> k.statusError
        else -> k.statusSuccess
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = KineticSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableCell(text = row.student.rollNumber, weight = COLUMNS[0].second, color = k.accent)
        Column(modifier = Modifier.weight(COLUMNS[1].second)) {
            Text(
                text = row.student.name,
                style = KineticType.bodyMedium,
                color = k.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.notMarked > 0) {
                Text(text = "${row.notMarked} NOT MARKED", style = KineticType.label.copy(fontSize = 9.sp), color = k.mutedForeground)
            }
        }
        TableCell(text = row.workingDays.toString(), weight = COLUMNS[2].second, color = k.foreground)
        TableCell(text = row.present.toString(), weight = COLUMNS[3].second, color = k.statusSuccess)
        TableCell(text = row.late.toString(), weight = COLUMNS[4].second, color = k.statusWarning)
        TableCell(text = row.excused.toString(), weight = COLUMNS[5].second, color = k.mutedForeground)
        TableCell(text = row.absent.toString(), weight = COLUMNS[6].second, color = k.statusError)
        TableCell(
            text = if (row.workingDays == 0) "\u2014" else "${"%.0f".format(row.percentage)}%",
            weight = COLUMNS[7].second,
            color = pctColor,
            bold = true,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(
    text: String,
    weight: Float,
    color: androidx.compose.ui.graphics.Color,
    bold: Boolean = false,
) {
    Box(modifier = Modifier.weight(weight)) {
        Text(
            text = text,
            style = if (bold) KineticType.labelBold else KineticType.label,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun exportFileName(s: MonthlySummaryUi): String =
    "attendance-${s.classLabel.replace(" ", "")}-${s.subjectLabel.replace(" ", "")}-" +
        "%04d-%02d".format(s.month.year, s.month.monthValue) + ".xlsx"

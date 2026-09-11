package com.bits.facultyai.ui.students

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.domain.StudentExcelParser
import com.bits.facultyai.ui.components.GlassCard
import com.bits.facultyai.ui.components.GlassStat
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.GlassDialogSurface
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticLoadingState
import com.bits.facultyai.ui.components.KineticOutlinedButton
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.components.KineticStat
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.navigation.studentDetailRoute
import com.bits.facultyai.ui.navigation.KineticBottomNavigation
import com.bits.facultyai.ui.students.StudentsViewModel.ImportState
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun StudentsScreen(
    onNavigate: (String) -> Unit,
    vm: StudentsViewModel = viewModel(),
) {
    val k = LocalKineticColors.current
    val context = LocalContext.current
    val students by vm.students.collectAsStateWithLifecycle()
    val search by vm.searchQuery.collectAsStateWithLifecycle()
    val records by vm.attendanceRecords.collectAsStateWithLifecycle()
    val selectedYear by vm.selectedYear.collectAsStateWithLifecycle()
    val selectedSection by vm.selectedSection.collectAsStateWithLifecycle()
    val availableSections by vm.availableSections.collectAsStateWithLifecycle()
    val importState by vm.import.collectAsStateWithLifecycle()
    var showAddStudent by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.startImport(uri, queryFileName(context, uri))
    }

    val xlsxMime = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "application/octet-stream",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        KineticDisplayText(text = "STUDENTS", style = KineticType.display.copy(fontSize = 44.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            GlassStat(
                value = students.size.toString().padStart(2, '0'),
                label = "IN VIEW",
                modifier = Modifier.weight(1f),
                emphasized = students.isNotEmpty(),
            )
            GlassStat(
                value = records.size.toString().padStart(2, '0'),
                label = "SESSIONS",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(KineticSpacing.md))

        KineticTextField(
            value = search,
            onValueChange = vm::setSearch,
            hint = "SEARCH NAME, ROLL NUMBER, SECTION...",
        )
        Spacer(Modifier.height(KineticSpacing.lg))

        // Year selector — 1ST..4TH year chips. Scrollable: overflows at
        // large font scales otherwise.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            ACADEMIC_YEARS.forEach { year ->
                SectionChip(label = ordinal(year), selected = selectedYear == year, onClick = { vm.selectYear(year) })
            }
        }
        Spacer(Modifier.height(KineticSpacing.sm))
        // Section chips for the chosen year (ALL + every imported section).
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            val chipSections: List<String?> = listOf(null) + availableSections.toList()
            chipSections.forEach { section ->
                SectionChip(
                    label = section?.let { "SEC $it" } ?: "ALL",
                    selected = selectedSection == section,
                    onClick = { vm.selectSection(section) },
                )
            }
            if (availableSections.isEmpty()) {
                Text(
                    text = "NO SECTIONS IMPORTED FOR THIS YEAR YET",
                    style = KineticType.label,
                    color = k.mutedForeground,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = KineticSpacing.sm),
                )
            }
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        // Actions: import from Excel or add one student manually.
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.md)) {
            KineticButton(
                text = "IMPORT EXCEL",
                onClick = { filePicker.launch(xlsxMime) },
                modifier = Modifier.weight(1f),
                height = 44,
            )
            KineticOutlinedButton(
                text = "ADD STUDENT",
                onClick = { showAddStudent = true },
                modifier = Modifier.weight(1f),
                height = 44,
            )
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        if (students.isEmpty()) {
            KineticEmptyState(
                title = "NO STUDENTS",
                message = if (availableSections.isEmpty())
                    "NO STUDENTS HAVE BEEN IMPORTED FOR ${selectedYear.ordinalYear()} YEAR YET"
                else "NO STUDENTS IN THE SELECTED SECTION",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = rememberLazyListState(),
            ) {
                items(students, key = { it.id }) { student ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(studentDetailRoute(student.id)) }
                            .padding(vertical = KineticSpacing.md),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = student.name, style = KineticType.bodyMedium, color = k.foreground)
                            Text(
                                text = student.rollNumber + " · " + student.section + (student.registrationNumber.takeIf { it.isNotBlank() }?.let { " · REG $it" } ?: ""),
                                style = KineticType.label,
                                color = k.mutedForeground,
                            )
                        }
                        Text(text = "→", style = KineticType.headingSm, color = k.accent)
                    }
                    KineticDivider()
                }
                // Clears the floating glass dock + system navigation area.
                item { Spacer(Modifier.height(KineticBottomNavigation.bottomClearance())) }
            }
        }
    }

    // ---- Overlays ----
    if (showAddStudent) {
        AddStudentDialog(
            defaultYear = selectedYear,
            onAdd = { name, roll, reg, year, section ->
                vm.addStudent(name, roll, reg, year, section)
                showAddStudent = false
            },
            onDismiss = { showAddStudent = false },
        )
    }
    when (importState.phase) {
        ImportState.Phase.PARSING -> FullScreenScrim { KineticLoadingState(label = "READING FILE") }
        ImportState.Phase.PREVIEW -> ImportPreview(
            state = importState,
            onChooseYear = vm::chooseImportYear,
            onChooseSection = vm::chooseImportSection,
            onDuplicateChoice = vm::setDuplicateChoice,
            onConfirm = { vm.confirmImport() },
            onDismiss = vm::dismissImport,
        )
        ImportState.Phase.IMPORTING -> FullScreenScrim { KineticLoadingState(label = "IMPORTING") }
        ImportState.Phase.DONE -> ImportDone(count = importState.importedCount, onDismiss = vm::dismissImport)
        ImportState.Phase.ERROR -> ImportError(message = importState.errorMessage, onDismiss = vm::dismissError)
        ImportState.Phase.IDLE -> Unit
    }
}

@Composable
private fun AddStudentDialog(
    defaultYear: Int,
    onAdd: (name: String, roll: String, reg: String, year: Int, section: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val k = LocalKineticColors.current
    var name by remember { mutableStateOf("") }
    var roll by remember { mutableStateOf("") }
    var reg by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("A") }
    var year by remember { mutableStateOf(defaultYear) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassDialogSurface {
            Text(text = "ADD STUDENT", style = KineticType.heading, color = k.foreground)
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = name, onValueChange = { name = it }, hint = "FULL NAME *")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = roll, onValueChange = { roll = it }, hint = "ROLL NUMBER *")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = reg, onValueChange = { reg = it }, hint = "REGISTRATION NUMBER (OPTIONAL)")
            Spacer(Modifier.height(KineticSpacing.sm))
            Text(text = "YEAR", style = KineticType.labelBold, color = k.mutedForeground)
            Spacer(Modifier.height(KineticSpacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs)) {
                ACADEMIC_YEARS.forEach { y ->
                    SectionChip(label = ordinal(y), selected = year == y, onClick = { year = y })
                }
            }
            Spacer(Modifier.height(KineticSpacing.sm))
            Text(text = "SECTION", style = KineticType.labelBold, color = k.mutedForeground)
            Spacer(Modifier.height(KineticSpacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs)) {
                listOf("A", "B", "C", "D").forEach { s ->
                    SectionChip(label = s, selected = section == s, onClick = { section = s })
                }
            }
            Spacer(Modifier.height(KineticSpacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticGhostButton(text = "CANCEL", onClick = onDismiss)
                KineticButton(
                    text = "ADD",
                    enabled = name.isNotBlank() && roll.isNotBlank(),
                    onClick = { onAdd(name, roll, reg, year, section) },
                )
            }
        }
    }
}

@Composable
private fun ImportPreview(
    state: StudentsViewModel.ImportState,
    onChooseYear: (Int) -> Unit,
    onChooseSection: (String) -> Unit,
    onDuplicateChoice: (StudentsViewModel.ImportState.DuplicateChoice) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val k = LocalKineticColors.current
    val p = state.preview ?: return
    val hasRosterDupes = state.rosterDuplicates.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background.copy(alpha = 0.97f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(KineticSpacing.lg)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            KineticDisplayText(text = "IMPORT PREVIEW", style = KineticType.display.copy(fontSize = 32.sp))
            Spacer(Modifier.height(KineticSpacing.xs))
            Text(
                text = state.fileName ?: "spreadsheet.xlsx",
                style = KineticType.label,
                color = k.mutedForeground,
            )
            Spacer(Modifier.height(KineticSpacing.lg))

            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xl)) {
                KineticStat(value = "${p.valid.size}", label = "READY", emphasized = true)
                KineticStat(value = "${p.duplicatesInFile.size}", label = "DUP IN FILE")
                KineticStat(value = "${p.invalid.size}", label = "NEED FIXES")
            }
            Spacer(Modifier.height(KineticSpacing.lg))

            // Year/section destination: picked here when the sheet lacks columns.
            KineticSectionHeader(title = "IMPORT INTO")
            if (p.needsYearSectionPick) {
                Text(
                    text = "THE SHEET HAS NO YEAR/SECTION COLUMN — CHOOSE THE DESTINATION BELOW",
                    style = KineticType.label,
                    color = k.statusWarning,
                )
                Spacer(Modifier.height(KineticSpacing.sm))
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
            ) {
                ACADEMIC_YEARS.forEach { year ->
                    SectionChip(label = ordinal(year), selected = state.chosenYear == year, onClick = { onChooseYear(year) })
                }
            }
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                listOf("A", "B", "C", "D").forEach { sec ->
                    SectionChip(label = "SEC $sec", selected = state.chosenSection == sec, onClick = { onChooseSection(sec) })
                }
            }
            Text(
                text = "IMPORTING INTO: ${state.chosenYear.ordinalYear()} YEAR · SEC ${state.chosenSection}" +
                    if (state.preview?.yearDetected == true || state.preview?.sectionDetected == true) {
                        " (rows with their own year/section keep it)"
                    } else "",
                style = KineticType.label,
                color = k.mutedForeground,
            )

            if (hasRosterDupes) {
                KineticSectionHeader(title = "ALREADY IN ROSTER")
                Text(
                    text = "${state.rosterDuplicates.size} student(s) already exist in ${ordinal(state.chosenYear)} year · SEC ${state.chosenSection}. Choose what to do:",
                    style = KineticType.label,
                    color = k.mutedForeground,
                )
                Spacer(Modifier.height(KineticSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                    SectionChip(
                        label = "SKIP EXISTING",
                        selected = state.duplicateChoice == StudentsViewModel.ImportState.DuplicateChoice.SKIP,
                        onClick = { onDuplicateChoice(StudentsViewModel.ImportState.DuplicateChoice.SKIP) },
                    )
                    SectionChip(
                        label = "UPDATE EXISTING",
                        selected = state.duplicateChoice == StudentsViewModel.ImportState.DuplicateChoice.UPDATE,
                        onClick = { onDuplicateChoice(StudentsViewModel.ImportState.DuplicateChoice.UPDATE) },
                    )
                }
                Spacer(Modifier.height(KineticSpacing.sm))
            }

            if (p.invalid.isNotEmpty()) {
                KineticSectionHeader(title = "ROWS NEEDING ATTENTION (${p.invalid.size})")
                p.invalid.take(8).forEach { row ->
                    Text("ROW ${row.rowNumber}: ${row.reason}", style = KineticType.label, color = k.statusError)
                    Text("  ${row.raw}", style = KineticType.label, color = k.mutedForeground)
                    Spacer(Modifier.height(KineticSpacing.xs))
                }
                if (p.invalid.size > 8) {
                    Text("+ ${p.invalid.size - 8} more rows", style = KineticType.label, color = k.mutedForeground)
                }
                Spacer(Modifier.height(KineticSpacing.sm))
            }

            if (p.duplicatesInFile.isNotEmpty()) {
                KineticSectionHeader(title = "DUPLICATES IN FILE (${p.duplicatesInFile.size})")
                p.duplicatesInFile.take(8).forEach { d ->
                    Text(
                        text = "ROW ${d.rowNumber}: ${d.name} (${d.rollNumber}) — same roll as row ${d.firstRowNumber}",
                        style = KineticType.label,
                        color = k.statusWarning,
                    )
                    Spacer(Modifier.height(KineticSpacing.xs))
                }
                if (p.duplicatesInFile.size > 8) {
                    Text("+ ${p.duplicatesInFile.size - 8} more", style = KineticType.label, color = k.mutedForeground)
                }
                Spacer(Modifier.height(KineticSpacing.sm))
            }

            Spacer(Modifier.height(KineticSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.md)) {
                KineticButton(
                    text = "IMPORT ${p.valid.size} STUDENTS",
                    enabled = p.valid.isNotEmpty() && (!hasRosterDupes || state.duplicateChoice != StudentsViewModel.ImportState.DuplicateChoice.UNDECIDED),
                    onClick = onConfirm,
                )
                KineticGhostButton(text = "CANCEL", onClick = onDismiss)
            }
            Spacer(Modifier.height(KineticSpacing.xl))
        }
    }
}

@Composable
private fun ImportDone(count: Int, onDismiss: () -> Unit) {
    val k = LocalKineticColors.current
    FullScreenScrim {
        KineticEmptyState(
            title = "IMPORTED $count STUDENTS",
            message = "THEY'RE NOW IN THE SELECTED YEAR AND SECTION",
            actionText = "DONE",
            onAction = onDismiss,
        )
    }
}

@Composable
private fun ImportError(message: String?, onDismiss: () -> Unit) {
    FullScreenScrim {
        KineticEmptyState(
            title = "COULDN'T IMPORT",
            message = (message ?: "UNKNOWN ERROR").uppercase(),
            actionText = "OK",
            onAction = onDismiss,
        )
    }
}

@Composable
private fun FullScreenScrim(content: @Composable () -> Unit) {
    val k = LocalKineticColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    Box(
        modifier = Modifier
            .border(if (selected) KineticBorder.heavy else KineticBorder.hair, if (selected) k.accent else k.border)
            .background(if (selected) k.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.sm),
    ) {
        Text(
            text = label.uppercase(),
            style = KineticType.labelBold,
            color = if (selected) k.accentForeground else k.mutedForeground,
        )
    }
}

private fun ordinal(year: Int): String = when (year) {
    1 -> "1ST"
    2 -> "2ND"
    3 -> "3RD"
    else -> "4TH"
}

private fun queryFileName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}

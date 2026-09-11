package com.bits.facultyai.ui.events

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bits.facultyai.data.local.EventEntity
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.ui.components.GlassChip
import com.bits.facultyai.ui.components.GlassTopBar
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// Plain ViewModel with (SavedStateHandle, Application) — the only constructor
// shape the default Compose factory reliably resolves for non-AndroidViewModels.
class EventEditorViewModel(savedStateHandle: SavedStateHandle, application: Application) : ViewModel() {
    private val dao = FacultyDatabase.get(application).facultyDao()
    val eventId: Long = savedStateHandle.get<String>("eventId")?.toLongOrNull() ?: 0L

    val existing = MutableStateFlow<EventEntity?>(null)

    init {
        if (eventId > 0) {
            viewModelScope.launch { existing.value = dao.getEvent(eventId) }
        }
    }

    fun save(
        name: String,
        dateIso: String,
        startMinutes: Int,
        endMinutes: Int,
        venue: String,
        description: String,
        organizer: String,
        department: String,
        participants: String,
        category: String,
        notes: String,
        onDone: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = existing.value
            val id = if (current == null) {
                dao.insertEvent(
                    EventEntity(
                        name = name.trim(), date = dateIso,
                        startTimeMinutes = startMinutes, endTimeMinutes = endMinutes,
                        venue = venue.trim(), description = description.trim(),
                        organizer = organizer.trim(), department = department.trim(),
                        participants = participants.trim(),
                        category = category, notes = notes.trim(),
                        createdAt = now, updatedAt = now,
                    )
                )
            } else {
                dao.updateEvent(
                    current.copy(
                        name = name.trim(), date = dateIso,
                        startTimeMinutes = startMinutes, endTimeMinutes = endMinutes,
                        venue = venue.trim(), description = description.trim(),
                        organizer = organizer.trim(), department = department.trim(),
                        participants = participants.trim(),
                        category = category, notes = notes.trim(),
                        updatedAt = now, syncState = "PENDING_SYNC",
                    )
                )
                current.id
            }
            onDone(id)
        }
    }
}

private val CATEGORIES = listOf("FEST", "WORKSHOP", "SEMINAR", "MEETING", "CULTURAL", "SPORTS", "OTHER")

@Composable
fun EventEditorScreen(
    onBack: () -> Unit = {},
    onSaved: (Long) -> Unit,
    vm: EventEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                EventEditorViewModel(
                    createSavedStateHandle(),
                    checkNotNull(this[APPLICATION_KEY]),
                )
            }
        },
    ),
) {
    val k = LocalKineticColors.current
    val existing by vm.existing.collectAsStateWithLifecycle()

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var dateText by remember(existing) { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var startText by remember(existing) { mutableStateOf(existing?.let { EventFormat.minutesToTime(it.startTimeMinutes) } ?: "10:00 AM") }
    var endText by remember(existing) { mutableStateOf(existing?.let { EventFormat.minutesToTime(it.endTimeMinutes) } ?: "4:00 PM") }
    var venue by remember(existing) { mutableStateOf(existing?.venue ?: "") }
    var description by remember(existing) { mutableStateOf(existing?.description ?: "") }
    var organizer by remember(existing) { mutableStateOf(existing?.organizer ?: "") }
    var department by remember(existing) { mutableStateOf(existing?.department ?: "") }
    var participants by remember(existing) { mutableStateOf(existing?.participants ?: "") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: "FEST") }
    var customCategory by remember(existing) { mutableStateOf(if (existing != null && existing?.category !in CATEGORIES) existing?.category ?: "" else "") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val parsedDate = EventFormat.parse(dateText)
    val startMinutes = EventFormat.timeToMinutes(startText)
    val endMinutes = EventFormat.timeToMinutes(endText)
    val nameError = showErrors && name.isBlank()
    val dateError = showErrors && parsedDate == null
    val timeError = showErrors && (startMinutes == null || endMinutes == null || endMinutes <= (startMinutes ?: 0))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        GlassTopBar(
            title = if (vm.eventId > 0) "EDIT EVENT" else "NEW EVENT",
            onBack = onBack,
        )
        Spacer(Modifier.height(KineticSpacing.md))

        KineticSectionHeader(title = "BASICS")
        KineticTextField(value = name, onValueChange = { name = it }, hint = "EVENT NAME *", isError = nameError, errorMessage = if (nameError) "NAME IS REQUIRED" else null)
        Spacer(Modifier.height(KineticSpacing.sm))
        KineticTextField(value = dateText, onValueChange = { dateText = it }, hint = "DATE (YYYY-MM-DD) *", isError = dateError, errorMessage = if (dateError) "USE YYYY-MM-DD" else null)
        // The day is derived from the date — shown live, never stored separately.
        parsedDate?.let { d ->
            Spacer(Modifier.height(KineticSpacing.xs))
            Text(
                text = "${EventFormat.fullDate(dateText)} · ${EventFormat.dayName(dateText)}",
                style = KineticType.labelBold,
                color = k.accent,
            )
        }
        Spacer(Modifier.height(KineticSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            KineticTextField(value = startText, onValueChange = { startText = it }, hint = "START (10:00 AM) *", modifier = Modifier.weight(1f), isError = timeError && startMinutes == null)
            KineticTextField(value = endText, onValueChange = { endText = it }, hint = "END (4:00 PM) *", modifier = Modifier.weight(1f), isError = timeError && endMinutes == null)
        }
        if (timeError && startMinutes != null && endMinutes != null && endMinutes <= startMinutes) {
            Spacer(Modifier.height(KineticSpacing.xs))
            Text(text = "END TIME MUST BE AFTER START", style = KineticType.label, color = k.statusError)
        }

        Spacer(Modifier.height(KineticSpacing.lg))
        KineticSectionHeader(title = "CATEGORY")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            val effectiveCategory = if (customCategory.isNotBlank()) customCategory.uppercase() else category
            CATEGORIES.forEach { c ->
                GlassChip(label = c, selected = effectiveCategory == c, onClick = {
                    category = c
                    customCategory = ""
                })
            }
            GlassChip(
                label = "CUSTOM",
                selected = customCategory.isNotBlank(),
                onClick = { customCategory = if (customCategory.isBlank()) " " else "" },
            )
        }
        if (customCategory.isNotBlank()) {
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = customCategory.trim(), onValueChange = { customCategory = it }, hint = "CUSTOM CATEGORY NAME")
        }

        Spacer(Modifier.height(KineticSpacing.lg))
        KineticSectionHeader(title = "DETAILS")
        KineticTextField(value = venue, onValueChange = { venue = it }, hint = "VENUE (E.G. SEMINAR HALL)")
        Spacer(Modifier.height(KineticSpacing.sm))
        KineticTextField(value = organizer, onValueChange = { organizer = it }, hint = "ORGANIZER")
        Spacer(Modifier.height(KineticSpacing.sm))
        KineticTextField(value = department, onValueChange = { department = it }, hint = "DEPARTMENT / SECTION")
        Spacer(Modifier.height(KineticSpacing.sm))
        KineticTextField(value = participants, onValueChange = { participants = it }, hint = "PARTICIPANTS (E.G. III ECE-A, III ECE-B)")
        Spacer(Modifier.height(KineticSpacing.sm))
        KineticTextField(value = description, onValueChange = { description = it }, hint = "DESCRIPTION", minLines = 3, maxLines = 6)
        Spacer(Modifier.height(KineticSpacing.sm))
        KineticTextField(value = notes, onValueChange = { notes = it }, hint = "INTERNAL NOTES", minLines = 2, maxLines = 4)

        Spacer(Modifier.height(KineticSpacing.lg))
        KineticButton(
            text = if (vm.eventId > 0) "SAVE CHANGES" else "CREATE EVENT",
            onClick = {
                if (name.isBlank() || parsedDate == null || startMinutes == null || endMinutes == null || endMinutes <= startMinutes) {
                    showErrors = true
                } else {
                    val effectiveCategory = if (customCategory.isNotBlank()) customCategory.trim().uppercase() else category
                    vm.save(
                        name, dateText, startMinutes, endMinutes, venue, description, organizer, department, participants, effectiveCategory, notes,
                        onDone = { id -> onSaved(id) },
                    )
                }
            },
        )
        Spacer(Modifier.height(KineticSpacing.xl))
    }
}

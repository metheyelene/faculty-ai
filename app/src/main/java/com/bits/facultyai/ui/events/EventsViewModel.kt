package com.bits.facultyai.ui.events

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.attachments.EventMedia
import com.bits.facultyai.data.local.EventCollectionEntity
import com.bits.facultyai.data.local.EventEntity
import com.bits.facultyai.data.local.EventExpenseEntity
import com.bits.facultyai.data.local.EventPhotoEntity
import com.bits.facultyai.data.local.FacultyDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which cohort of events is shown. */
enum class EventFilter { ALL, UPCOMING, PAST, THIS_MONTH, THIS_YEAR }

class EventsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()
    val media = EventMedia(application, dao)

    val searchQuery = MutableStateFlow("")
    val selectedFilter = MutableStateFlow(EventFilter.ALL)

    fun setSearch(query: String) { searchQuery.value = query }

    private val allEvents = dao.observeEvents()

    /** Live spend/collection totals per event (from SQL aggregates) for list badges. */
    val spentByEvent: StateFlow<Map<Long, Long>> = dao.observeSpentByEvent()
        .map { rows -> rows.associate { it.eventId to it.totalPaisa } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val collectedByEvent: StateFlow<Map<Long, Long>> = dao.observeCollectedByEvent()
        .map { rows -> rows.associate { it.eventId to it.totalPaisa } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Filtered + searched event list; date math is done on real ISO dates. */
    val events: StateFlow<List<EventEntity>> = combine(allEvents, searchQuery, selectedFilter) { list, q, filter ->
        val today = LocalDate.now()
        list.filter { e ->
            val passesFilter = when (filter) {
                EventFilter.ALL -> true
                EventFilter.UPCOMING -> runCatching { LocalDate.parse(e.date) >= today }.getOrDefault(false)
                EventFilter.PAST -> runCatching { LocalDate.parse(e.date) < today }.getOrDefault(false)
                EventFilter.THIS_MONTH -> runCatching {
                    val d = LocalDate.parse(e.date); d.year == today.year && d.month == today.month
                }.getOrDefault(false)
                EventFilter.THIS_YEAR -> runCatching {
                    LocalDate.parse(e.date).year == today.year
                }.getOrDefault(false)
            }
            if (!passesFilter) return@filter false
            if (q.isBlank()) return@filter true
            val needle = q.trim().lowercase()
            listOf(e.name, e.venue, e.category, e.department, e.organizer, e.date)
                .any { it.lowercase().contains(needle) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --------------------------------------------------------------- CRUD

    fun saveEvent(
        existing: EventEntity?,
        name: String,
        dateIso: String,
        startMinutes: Int,
        endMinutes: Int,
        venue: String,
        description: String,
        organizer: String,
        department: String,
        category: String,
        notes: String,
        onSaved: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = if (existing == null) {
                dao.insertEvent(
                    EventEntity(
                        name = name.trim(), date = dateIso,
                        startTimeMinutes = startMinutes, endTimeMinutes = endMinutes,
                        venue = venue.trim(), description = description.trim(),
                        organizer = organizer.trim(), department = department.trim(),
                        category = category, notes = notes.trim(),
                        createdAt = now, updatedAt = now,
                    )
                )
            } else {
                dao.updateEvent(
                    existing.copy(
                        name = name.trim(), date = dateIso,
                        startTimeMinutes = startMinutes, endTimeMinutes = endMinutes,
                        venue = venue.trim(), description = description.trim(),
                        organizer = organizer.trim(), department = department.trim(),
                        category = category, notes = notes.trim(),
                        updatedAt = now, syncState = "PENDING_SYNC",
                    )
                )
                existing.id
            }
            onSaved(id)
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            media.deleteEventFiles(event.id)
            dao.deleteEventPhotosFor(event.id)
            dao.deleteEventExpensesFor(event.id)
            dao.deleteEventCollectionsFor(event.id)
            dao.deleteEvent(event.id)
        }
    }

    // -------------------------------------------------------- Expenses

    fun saveExpense(existing: EventExpenseEntity?, eventId: Long, expense: EventExpenseEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = if (existing == null) {
                dao.insertEventExpense(expense.copy(eventId = eventId, createdAt = now, updatedAt = now))
            } else {
                dao.updateEventExpense(expense.copy(id = existing.id, eventId = eventId, updatedAt = now, syncState = "PENDING_SYNC"))
                existing.id
            }
            dao.setEventSyncState(eventId, "PENDING_SYNC")
            onSaved(id)
        }
    }

    fun deleteExpense(expense: EventExpenseEntity) {
        viewModelScope.launch {
            // Receipt file goes with the record once the user confirms deletion;
            // attachment removal alone never deletes the record.
            if (expense.receiptFileName != null) media.removeReceipt(expense.id)
            dao.deleteEventExpense(expense.id)
        }
    }

    // ----------------------------------------------------- Collections

    fun saveCollection(existing: EventCollectionEntity?, eventId: Long, collection: EventCollectionEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (existing == null) {
                dao.insertEventCollection(collection.copy(eventId = eventId, createdAt = now, updatedAt = now))
            } else {
                dao.updateEventCollection(collection.copy(id = existing.id, eventId = eventId, updatedAt = now, syncState = "PENDING_SYNC"))
            }
            dao.setEventSyncState(eventId, "PENDING_SYNC")
        }
    }

    fun deleteCollection(collection: EventCollectionEntity) {
        viewModelScope.launch { dao.deleteEventCollection(collection.id) }
    }
}

/** Per-event live streams for the detail screen. */
class EventDetailViewModel(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    application: Application,
) : androidx.lifecycle.ViewModel() {
    private val dao = FacultyDatabase.get(application).facultyDao()
    val media = EventMedia(application, dao)
    val eventId: Long = savedStateHandle.get<String>("eventId")?.toLongOrNull() ?: -1L

    val event = dao.observeEvent(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val photos = media.observePhotos(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses = dao.observeEventExpenses(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collections = dao.observeEventCollections(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Totals recomputed from live rows — balance = collected − spent, always. */
    val finance: StateFlow<Pair<Long, Long>> =
        combine(expenses, collections) { ex, col ->
            Pair(col.sumOf { it.amountPaisa }, ex.sumOf { it.amountPaisa })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0L, 0L))

    fun attachPhotos(uris: List<android.net.Uri>) {
        if (eventId > 0) media.attachPhotos(eventId, uris)
    }

    fun capturePhoto(uri: android.net.Uri) {
        if (eventId > 0) media.attachCameraPhoto(eventId, uri)
    }

    fun removePhoto(id: Long) = media.removePhoto(id)
    fun setCover(id: Long) = media.setCover(id)
    fun setCaption(id: Long, caption: String) = media.setCaption(id, caption)
    fun retryPhoto(id: Long) = media.retryPhoto(id)

    /** Attaches a receipt to a stored expense; the record itself is untouched. */
    fun attachReceipt(expenseId: Long, uri: android.net.Uri) {
        if (expenseId > 0) media.attachReceipt(expenseId, uri)
    }

    fun removeReceipt(expenseId: Long) = media.removeReceipt(expenseId)
    fun openReceipt(expenseId: Long, onMissing: () -> Unit) = media.openReceipt(expenseId, onMissing)

    // ---- Finance CRUD — totals recompute via the live flows ----

    fun saveExpense(existing: EventExpenseEntity?, expense: EventExpenseEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = if (existing == null) {
                dao.insertEventExpense(expense.copy(eventId = eventId, createdAt = now, updatedAt = now))
            } else {
                dao.updateEventExpense(expense.copy(id = existing.id, eventId = eventId, updatedAt = now, syncState = "PENDING_SYNC"))
                existing.id
            }
            dao.setEventSyncState(eventId, "PENDING_SYNC")
            onSaved(id)
        }
    }

    fun deleteExpense(expense: EventExpenseEntity) {
        viewModelScope.launch {
            // Receipt file goes with the record once deletion is confirmed.
            if (expense.receiptFileName != null) media.removeReceipt(expense.id)
            dao.deleteEventExpense(expense.id)
            dao.setEventSyncState(eventId, "PENDING_SYNC")
        }
    }

    fun saveCollection(existing: EventCollectionEntity?, collection: EventCollectionEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (existing == null) {
                dao.insertEventCollection(collection.copy(eventId = eventId, createdAt = now, updatedAt = now))
            } else {
                dao.updateEventCollection(collection.copy(id = existing.id, eventId = eventId, updatedAt = now, syncState = "PENDING_SYNC"))
            }
            dao.setEventSyncState(eventId, "PENDING_SYNC")
        }
    }

    fun deleteCollection(collection: EventCollectionEntity) {
        viewModelScope.launch {
            dao.deleteEventCollection(collection.id)
            dao.setEventSyncState(eventId, "PENDING_SYNC")
        }
    }
}

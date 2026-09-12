package com.bits.facultyai.data.sync

import android.content.Context
import com.bits.facultyai.data.auth.AuthState
import com.bits.facultyai.data.local.AttendanceEntryEntity
import com.bits.facultyai.data.local.AttendanceRecordEntity
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.PendingDeletionEntity
import com.bits.facultyai.data.local.StudentEntity
import com.bits.facultyai.data.local.SyncMetaEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/** User-visible sync state surfaced in Settings. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

/**
 * The only owner of Firestore I/O. Room stays the source of truth; the cloud
 * holds three aggregates per account — slots, students, sessions (each session
 * embeds its entries) — plus tombstones for deletions. Shapes and conflict
 * rules live in [SyncPolicy].
 *
 * Lifecycle: [start] subscribes to auth state. Signed-in → identity backfill,
 * then push/pull whenever Room invalidates the tracked tables (debounced) and
 * whenever other devices' tombstones change (snapshot listener). Guest/un-
 * configured → engine idles; local data untouched. A different account signing
 * in on this install wipes local data first, so accounts never mix.
 */
class SyncEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = FacultyDatabase.get(appContext)
    private val dao = db.facultyDao()
    private val syncDao = db.syncDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile private var uid: String? = null
    private var authJob: Job? = null
    private var liveJob: Job? = null

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status

    private val _signedIn = MutableStateFlow(false)

    /** True while a signed-in account's sync loops are live (drives the Settings UI). */
    val signedIn: StateFlow<Boolean> = _signedIn

    fun start(authStates: Flow<AuthState>) {
        authJob?.cancel()
        authJob = scope.launch {
            authStates.distinctUntilChanged().collect { state ->
                when (state) {
                    is AuthState.SignedIn -> switchTo(state.user.uid)
                    else -> stopSync()
                }
            }
        }
    }

    /** Idempotent per-uid activation; wipes local data when the account changes. */
    private suspend fun switchTo(newUid: String) {
        if (uid == newUid && liveJob != null) return
        withContext(Dispatchers.IO) {
            if (uid != null && uid != newUid) {
                // Local data belonged to the previous user — clear it before the
                // new account's cloud state flows in.
                db.clearAllUserData()
                syncDao.clearDeletions()
                syncDao.clearMeta()
            }
            uid = newUid
            backfillIfNeeded(newUid)
        }
        _signedIn.value = true
        _status.value = SyncStatus.Idle
        liveJob?.cancel()
        liveJob = scope.launch {
            launch { runSync() } // initial convergence
            launch { localWrites().collectLatest { delay(WRITE_DEBOUNCE_MS); runSync() } }
            launch { remoteTombstones(newUid).collectLatest { runSync() } }
        }
    }

    private fun stopSync() {
        liveJob?.cancel(); liveJob = null
        uid = null
        _signedIn.value = false
        _status.value = SyncStatus.Idle
    }

    /** Explicit sign-out hook — same as the auth-driven stop, but immediate. */
    fun signOut() = stopSync()

    /** Manual "SYNC NOW". */
    suspend fun syncNow() = runSync()

    /**
     * Called before a signed-in reset: tombstone every cloud-known row so the
     * drain erases the account's cloud home too and a later pull cannot
     * resurrect the erased data.
     */
    suspend fun enqueueTombstonesForAllUserData() {
        val now = System.currentTimeMillis()
        val outbox = buildList {
            dao.getAllSlotsForSync().filter { it.uuid.isNotEmpty() }
                .forEach { add(PendingDeletionEntity(entityType = "slot", uuid = it.uuid, requestedAt = now)) }
            dao.getAllStudentsForSync().filter { it.uuid.isNotEmpty() }
                .forEach { add(PendingDeletionEntity(entityType = "student", uuid = it.uuid, requestedAt = now)) }
            dao.getAllRecordsForSync().filter { it.uuid.isNotEmpty() }
                .forEach { add(PendingDeletionEntity(entityType = "record", uuid = it.uuid, requestedAt = now)) }
            dao.getAllEntriesForSync().filter { it.uuid.isNotEmpty() }
                .forEach { add(PendingDeletionEntity(entityType = "entry", uuid = it.uuid, requestedAt = now)) }
        }
        outbox.forEach { syncDao.insertDeletion(it) }
    }

    // ---- internals ----

    /** One-shot per uid: give every legacy row a uuid + sync currency. */
    private suspend fun backfillIfNeeded(uid: String) {
        if (syncDao.isBackfillDone(uid) > 0) return
        val now = System.currentTimeMillis()
        dao.getAllSlotsForSync().filter { it.uuid.isEmpty() }
            .forEach { syncDao.assignSlotUuid(it.id, UUID.randomUUID().toString()) }
        dao.getAllStudentsForSync().filter { it.uuid.isEmpty() }
            .forEach { syncDao.assignStudentUuid(it.id, UUID.randomUUID().toString()) }
        dao.getAllRecordsForSync().filter { it.uuid.isEmpty() }
            .forEach { syncDao.assignRecordUuid(it.id, UUID.randomUUID().toString()) }
        dao.getAllEntriesForSync().filter { it.uuid.isEmpty() }
            .forEach { syncDao.assignEntryUuid(it.id, UUID.randomUUID().toString()) }
        dao.backfillRecordSlotUuids()
        dao.backfillEntryRecordUuids()
        dao.backfillEntryStudentUuids()
        dao.stampLegacySlots(now)
        dao.stampLegacyStudents(now)
        dao.stampLegacyRecords(now)
        dao.stampLegacyEntries(now)
        syncDao.insertMeta(SyncMetaEntity(uid = uid, backfillDone = true))
    }

    /** Room invalidation → hot flow; any write anywhere triggers a debounced sync. */
    private fun localWrites() = callbackFlow {
        val observer = object : androidx.room.InvalidationTracker.Observer(
            "class_slot", "student", "attendance_record", "attendance_entry",
        ) {
            override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
        }
        db.invalidationTracker.addObserver(observer)
        awaitClose { db.invalidationTracker.removeObserver(observer) }
    }

    /** Tombstones pushed by this account's other devices. */
    private fun remoteTombstones(uid: String) = callbackFlow {
        val reg = FirebaseFirestore.getInstance()
            .collection(SyncPolicy.collectionPath(uid, "tombstones"))
            .addSnapshotListener { _, _ -> trySend(Unit) }
        awaitClose { reg.remove() }
    }

    private suspend fun runSync() {
        val activeUid = uid ?: return
        mutex.withLock {
            _status.value = SyncStatus.Syncing
            try {
                val meta = syncDao.getMeta(activeUid)
                    ?: SyncMetaEntity(uid = activeUid).also { syncDao.insertMeta(it) }
                drainDeletions(activeUid)
                val pushed = push(activeUid, meta)
                syncDao.insertMeta(pushed)
                pull(activeUid, pushed)
                touchAccountDoc(activeUid)
                _status.value = SyncStatus.Synced(System.currentTimeMillis())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _status.value = SyncStatus.Error(friendly(e))
            }
        }
    }

    /** Local pending deletions → cloud tombstones + row-doc removal. */
    private suspend fun drainDeletions(uid: String) {
        val pending = syncDao.pendingDeletions()
        if (pending.isEmpty()) return
        val firestore = FirebaseFirestore.getInstance()
        firestore.runBatch { batch ->
            for (d in pending) {
                val table = CLOUD_TABLE[d.entityType] ?: continue
                batch.set(
                    firestore.document(SyncPolicy.tombstonePath(uid, d.uuid)),
                    mapOf("table" to table, "deletedAt" to d.requestedAt),
                    SetOptions.merge(),
                )
                batch.delete(firestore.document(SyncPolicy.collectionPath(uid, table) + "/" + d.uuid))
            }
        }.await()
        syncDao.deleteDeletions(pending.map { it.id })
    }

    /** Dirty local rows → cloud. Watermarks advance only over written rows. */
    private suspend fun push(uid: String, meta: SyncMetaEntity): SyncMetaEntity {
        val firestore = FirebaseFirestore.getInstance()

        val slots = dao.dirtySlots(meta.wmSlot)
        val students = dao.dirtyStudents(meta.wmStudent)
        val entries = dao.dirtyEntries(meta.wmEntry)

        // Entry-only edits ride inside their parent session doc; the parent's
        // updatedAt is bumped so the session is re-pushed (and its watermark
        // advances) without corrupting LWW by backdating the wire currency.
        val dirtyRecordIds = entries.map { it.recordId }.toSet()
        if (dirtyRecordIds.isNotEmpty()) {
            val now = System.currentTimeMillis()
            for (r in dao.getAllRecordsForSync().filter { it.id in dirtyRecordIds }) {
                dao.touchRecordCurrency(r.id, now)
            }
        }
        val records = dao.getAllRecordsForSync()
            .filter { it.id in dirtyRecordIds || it.updatedAt > meta.wmRecord || it.uuid.isEmpty() }

        // Assign identity first; entry uuids must exist before embedding.
        slots.filter { it.uuid.isEmpty() }.forEach { syncDao.assignSlotUuid(it.id, UUID.randomUUID().toString()) }
        students.filter { it.uuid.isEmpty() }.forEach { syncDao.assignStudentUuid(it.id, UUID.randomUUID().toString()) }
        records.filter { it.uuid.isEmpty() }.forEach { syncDao.assignRecordUuid(it.id, UUID.randomUUID().toString()) }
        entries.filter { it.uuid.isEmpty() }.forEach { syncDao.assignEntryUuid(it.id, UUID.randomUUID().toString()) }

        if (slots.isEmpty() && students.isEmpty() && records.isEmpty() && entries.isEmpty()) return meta

        // Re-read exactly the rows touched above so payloads carry their uuid.
        val freshSlots = reRead(slots) { dao.getAllSlotsForSync() }
        val freshStudents = reRead(students) { dao.getAllStudentsForSync() }
        val freshRecords = reRead(records) { dao.getAllRecordsForSync() }
        val freshEntries = reRead(entries) { dao.getAllEntriesForSync() }
        val slotById = freshSlots.associateBy { it.id } + dao.getAllSlotsForSync().associateBy { it.id }
        val studentById = dao.getAllStudentsForSync().associateBy { it.id }

        if (freshSlots.isNotEmpty()) {
            writeBatched(firestore, SyncPolicy.collectionPath(uid, "slots"), freshSlots.map { it.uuid to slotDoc(it) })
        }
        if (freshStudents.isNotEmpty()) {
            writeBatched(firestore, SyncPolicy.collectionPath(uid, "students"), freshStudents.map { it.uuid to studentDoc(it) })
        }
        if (freshRecords.isNotEmpty()) {
            // Sessions are aggregates: record fields + ALL of the record's entries.
            val entriesByRecord = dao.getAllEntriesForSync().groupBy { it.recordId }
            writeBatched(firestore, SyncPolicy.collectionPath(uid, "sessions"), freshRecords.map { r ->
                r.uuid to sessionDoc(r, slotById, entriesByRecord[r.id].orEmpty(), studentById)
            })
        }

        return meta.copy(
            wmSlot = SyncPolicy.nextWatermark(meta.wmSlot, freshSlots.map { it.updatedAt }),
            wmStudent = SyncPolicy.nextWatermark(meta.wmStudent, freshStudents.map { it.updatedAt }),
            wmRecord = SyncPolicy.nextWatermark(meta.wmRecord, freshRecords.map { it.updatedAt }),
            wmEntry = SyncPolicy.nextWatermark(meta.wmEntry, freshEntries.map { it.updatedAt }),
        )
    }

    private suspend fun <T> reRead(touched: List<T>, readAll: suspend () -> List<T>): List<T> {
        if (touched.isEmpty()) return emptyList()
        val ids = when (val first = touched.first()) {
            is ClassSlotEntity -> touched.map { (it as ClassSlotEntity).id }.toSet()
            is StudentEntity -> touched.map { (it as StudentEntity).id }.toSet()
            is com.bits.facultyai.data.local.AttendanceRecordEntity ->
                touched.map { (it as com.bits.facultyai.data.local.AttendanceRecordEntity).id }.toSet()
            is AttendanceEntryEntity -> touched.map { (it as AttendanceEntryEntity).id }.toSet()
            else -> return touched
        }
        @Suppress("UNCHECKED_CAST")
        return readAll().filter {
            when (it) {
                is ClassSlotEntity -> it.id in ids
                is StudentEntity -> it.id in ids
                is com.bits.facultyai.data.local.AttendanceRecordEntity -> it.id in ids
                is AttendanceEntryEntity -> it.id in ids
                else -> false
            }
        } as List<T>
    }

    private fun slotDoc(s: ClassSlotEntity) = mapOf(
        "dayOfWeek" to s.dayOfWeek,
        "startTimeMinutes" to s.startTimeMinutes,
        "endTimeMinutes" to s.endTimeMinutes,
        "subject" to s.subject,
        "section" to s.section,
        "room" to s.room,
        "year" to s.year,
        "updatedAt" to s.updatedAt,
    )

    private fun studentDoc(s: StudentEntity) = mapOf(
        "rollNumber" to s.rollNumber,
        "name" to s.name,
        "section" to s.section,
        "year" to s.year,
        "registrationNumber" to s.registrationNumber,
        "email" to s.email,
        "phone" to s.phone,
        "degree" to s.degree,
        "updatedAt" to s.updatedAt,
    )

    /** The session aggregate: record fields + embedded entries. */
    private fun sessionDoc(
        r: com.bits.facultyai.data.local.AttendanceRecordEntity,
        slotById: Map<Long, ClassSlotEntity>,
        entries: List<AttendanceEntryEntity>,
        studentById: Map<Long, StudentEntity>,
    ) = mapOf(
        "subject" to r.subject,
        "section" to r.section,
        "date" to r.date,
        "year" to r.year,
        "markedAt" to r.markedAt,
        "presentCount" to r.presentCount,
        "absentCount" to r.absentCount,
        "lateCount" to r.lateCount,
        "excusedCount" to r.excusedCount,
        "slotUuid" to r.slotUuid.ifBlank { slotById[r.classSlotId]?.uuid ?: "" },
        "updatedAt" to r.updatedAt,
        "entries" to entries.map { e ->
            mapOf(
                "uuid" to e.uuid,
                "status" to e.status,
                "studentUuid" to e.studentUuid.ifBlank { studentById[e.studentId]?.uuid ?: "" },
            )
        },
    )

    /** Cloud → local; LWW per [SyncPolicy.merge]; ids preserved via uuid lookup. */
    private suspend fun pull(uid: String, meta: SyncMetaEntity) {
        val firestore = FirebaseFirestore.getInstance()

        for (doc in fetchAll(firestore, SyncPolicy.collectionPath(uid, "slots"))) {
            val remoteAt = doc.getLong("updatedAt") ?: 0L
            val local = dao.findSlotByUuid(doc.id)
            if (SyncPolicy.merge(local?.updatedAt, remoteAt) != SyncPolicy.Merge.ApplyRemote) continue
            dao.upsertSlot(
                (local ?: ClassSlotEntity(dayOfWeek = 1, startTimeMinutes = 0, endTimeMinutes = 0, subject = "", section = "", room = "")).copy(
                    dayOfWeek = (doc.getLong("dayOfWeek") ?: 1L).toInt(),
                    startTimeMinutes = (doc.getLong("startTimeMinutes") ?: 0L).toInt(),
                    endTimeMinutes = (doc.getLong("endTimeMinutes") ?: 0L).toInt(),
                    subject = doc.getString("subject") ?: "",
                    section = doc.getString("section") ?: "",
                    room = doc.getString("room") ?: "",
                    year = (doc.getLong("year") ?: 1L).toInt(),
                    uuid = doc.id,
                    updatedAt = remoteAt,
                ),
            )
        }

        for (doc in fetchAll(firestore, SyncPolicy.collectionPath(uid, "students"))) {
            val remoteAt = doc.getLong("updatedAt") ?: 0L
            val local = dao.findStudentByUuid(doc.id)
            if (SyncPolicy.merge(local?.updatedAt, remoteAt) != SyncPolicy.Merge.ApplyRemote) continue
            dao.upsertStudent(
                (local ?: StudentEntity(rollNumber = "", name = "", section = "", year = 0)).copy(
                    rollNumber = doc.getString("rollNumber") ?: "",
                    name = doc.getString("name") ?: "",
                    section = doc.getString("section") ?: "",
                    year = (doc.getLong("year") ?: 0L).toInt(),
                    registrationNumber = doc.getString("registrationNumber") ?: "",
                    email = doc.getString("email") ?: "",
                    phone = doc.getString("phone") ?: "",
                    degree = doc.getString("degree") ?: "",
                    uuid = doc.id,
                    updatedAt = remoteAt,
                ),
            )
        }

        val studentsByUuid = dao.getAllStudentsForSync().associateBy { it.uuid }
        for (doc in fetchAll(firestore, SyncPolicy.collectionPath(uid, "sessions"))) {
            val remoteAt = doc.getLong("updatedAt") ?: 0L
            val local = dao.findRecordByUuid(doc.id)
            if (SyncPolicy.merge(local?.updatedAt, remoteAt) != SyncPolicy.Merge.ApplyRemote) continue
            val slotUuid = doc.getString("slotUuid") ?: ""
            val slotId = if (slotUuid.isBlank()) 0L else dao.findSlotByUuid(slotUuid)?.id ?: 0L
            val recordId = dao.upsertRecord(
                (local ?: AttendanceRecordEntity(
                    classSlotId = 0, subject = "", section = "", date = "",
                    markedAt = 0, presentCount = 0, absentCount = 0, lateCount = 0,
                )).copy(
                    classSlotId = slotId,
                    subject = doc.getString("subject") ?: "",
                    section = doc.getString("section") ?: "",
                    date = doc.getString("date") ?: "",
                    year = (doc.getLong("year") ?: 0L).toInt(),
                    markedAt = doc.getLong("markedAt") ?: 0L,
                    presentCount = (doc.getLong("presentCount") ?: 0L).toInt(),
                    absentCount = (doc.getLong("absentCount") ?: 0L).toInt(),
                    lateCount = (doc.getLong("lateCount") ?: 0L).toInt(),
                    excusedCount = (doc.getLong("excusedCount") ?: 0L).toInt(),
                    uuid = doc.id,
                    slotUuid = slotUuid,
                    updatedAt = remoteAt,
                ),
            )
            applyEmbeddedEntries(recordId, doc.id, doc, studentsByUuid, remoteAt)
        }

        applyTombstones(firestore, uid, meta)
    }

    private suspend fun applyEmbeddedEntries(
        recordId: Long,
        sessionUuid: String,
        doc: com.google.firebase.firestore.DocumentSnapshot,
        studentsByUuid: Map<String, StudentEntity>,
        remoteAt: Long,
    ) {
        @Suppress("UNCHECKED_CAST")
        val entryDocs = doc.get("entries") as? List<Map<String, Any?>> ?: return
        val localsByUuid = dao.getEntriesForRecordSync(recordId).associateBy { it.uuid }
        for (e in entryDocs) {
            val eUuid = e["uuid"] as? String ?: continue
            val studentUuid = e["studentUuid"] as? String ?: ""
            val studentId = studentsByUuid[studentUuid]?.id ?: continue // roster gap: skip
            val status = e["status"] as? String ?: "ABSENT"
            val local = localsByUuid[eUuid]
            if (local != null) {
                if (local.status != status) dao.updateEntryById(local.id, status, remoteAt)
            } else {
                dao.upsertEntry(
                    AttendanceEntryEntity(
                        recordId = recordId, studentId = studentId, status = status,
                        uuid = eUuid, recordUuid = sessionUuid, studentUuid = studentUuid,
                        updatedAt = remoteAt,
                    ),
                )
            }
        }
    }

    /** Other devices' deletions → local rows, honoring post-delete edits. */
    private suspend fun applyTombstones(firestore: FirebaseFirestore, uid: String, meta: SyncMetaEntity) {
        val docs = fetchAll(firestore, SyncPolicy.collectionPath(uid, "tombstones"))
        for (doc in docs) {
            val deletedAt = doc.getLong("deletedAt") ?: continue
            if (deletedAt <= meta.wmTombstone) continue
            when (doc.getString("table")) {
                "slots" -> dao.findSlotByUuid(doc.id)?.let {
                    if (!SyncPolicy.survivesTombstone(it.updatedAt, deletedAt)) dao.deleteSlotByUuid(doc.id)
                }
                "students" -> dao.findStudentByUuid(doc.id)?.let {
                    if (!SyncPolicy.survivesTombstone(it.updatedAt, deletedAt)) dao.deleteStudentByUuid(doc.id)
                }
                "sessions" -> dao.findRecordByUuid(doc.id)?.let {
                    if (!SyncPolicy.survivesTombstone(it.updatedAt, deletedAt)) {
                        dao.getEntriesForRecordSync(it.id).forEach { e -> dao.deleteEntryByUuid(e.uuid) }
                        dao.deleteRecordByUuid(doc.id)
                    }
                }
            }
        }
        docs.mapNotNull { it.getLong("deletedAt") }.maxOrNull()?.let { max ->
            if (max > meta.wmTombstone) syncDao.insertMeta(meta.copy(wmTombstone = max))
        }
    }

    /**
     * One mutable doc per account. A freshly signed-in device reads it to
     * confirm the account was seen before (vs. "everything deleted by another
     * device" — an empty-account ambiguity the tombstone model alone can't
     * resolve). Cheap; written once per sync.
     */
    private suspend fun touchAccountDoc(uid: String) {
        FirebaseFirestore.getInstance()
            .document("users/$uid")
            .set(mapOf("lastSyncAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }

    private suspend fun fetchAll(firestore: FirebaseFirestore, path: String) =
        firestore.collection(path).get().await().documents

    /** Firestore caps a batch at 500 writes; chunk and await each. */
    private suspend fun writeBatched(
        firestore: FirebaseFirestore,
        collectionPath: String,
        docs: List<Pair<String, Map<String, Any?>>>,
    ) {
        docs.chunked(450).forEach { chunk ->
            firestore.runBatch { batch ->
                for ((uuid, doc) in chunk) {
                    batch.set(firestore.document("$collectionPath/$uuid"), doc, SetOptions.merge())
                }
            }.await()
        }
    }

    private fun friendly(e: Exception): String = when {
        e.message?.contains("OFFLINE", true) == true ||
            e.message?.contains("network", true) == true ->
            "You're offline — changes will sync when you reconnect"
        else -> e.message ?: "Sync failed"
    }

    companion object {
        private const val WRITE_DEBOUNCE_MS = 2_000L
        private const val SESSIONS_TABLE = "sessions"
        private val CLOUD_TABLE = mapOf(
            "slot" to "slots",
            "student" to "students",
            "record" to SESSIONS_TABLE,
            "entry" to SESSIONS_TABLE, // entries ride inside their session doc
        )

        @Volatile private var instance: SyncEngine? = null

        fun get(context: Context): SyncEngine =
            instance ?: synchronized(this) {
                instance ?: SyncEngine(context).also { instance = it }
            }
    }
}

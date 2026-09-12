package com.bits.facultyai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Sync bookkeeping. Three concerns, all owned here and nowhere else:
 *
 *  1. Deletion propagation — UI deletes move rows here; [SyncEngine] drains
 *     them to the cloud and reconciles remote deletions via full-set compare.
 *     UI-facing tables never carry tombstone flags.
 *  2. Outbox — rows with `updatedAt > watermark` need pushing. Watermarks
 *     live in [SyncEngine], not the schema.
 *  3. Idempotency — an empty [PendingDeletionEntity] table after a drain is
 *     the "nothing left to delete" state.
 */
@Dao
interface SyncDao {

    // ---- deletions ----

    @Insert
    suspend fun insertDeletion(entry: PendingDeletionEntity)

    @Query("SELECT * FROM pending_deletion ORDER BY requestedAt ASC")
    suspend fun pendingDeletions(): List<PendingDeletionEntity>

    @Query("DELETE FROM pending_deletion WHERE id IN (:ids)")
    suspend fun deleteDeletions(ids: List<Long>)

    /** Legacy pre-sync deletes (nothing pending): full reconcile will handle them. */
    @Query("SELECT COUNT(*) FROM pending_deletion")
    suspend fun pendingDeletionCount(): Int

    // ---- id assignment (engine-only writes) ----

    @Query("UPDATE class_slot SET uuid = :uuid WHERE id = :id AND uuid = ''")
    suspend fun assignSlotUuid(id: Long, uuid: String)

    @Query("UPDATE student SET uuid = :uuid WHERE id = :id AND uuid = ''")
    suspend fun assignStudentUuid(id: Long, uuid: String)

    @Query("UPDATE attendance_record SET uuid = :uuid WHERE id = :id AND uuid = ''")
    suspend fun assignRecordUuid(id: Long, uuid: String)

    @Query("UPDATE attendance_entry SET uuid = :uuid WHERE id = :id AND uuid = ''")
    suspend fun assignEntryUuid(id: Long, uuid: String)

    // ---- one-shot backfill bookkeeping (per-uid, persisted across runs) ----

    @Query("SELECT * FROM sync_meta WHERE uid = :uid LIMIT 1")
    suspend fun getMeta(uid: String): SyncMetaEntity?

    @Query("SELECT COUNT(*) FROM sync_meta WHERE uid = :uid AND backfillDone = 1")
    suspend fun isBackfillDone(uid: String): Int

    @Insert
    suspend fun insertMeta(meta: SyncMetaEntity)

    @Query("DELETE FROM sync_meta WHERE uid = :uid")
    suspend fun deleteMeta(uid: String)

    /** Sign-out / reset hygiene: outbox entries and watermarks never outlive their account. */
    @Query("DELETE FROM pending_deletion")
    suspend fun clearDeletions()

    @Query("DELETE FROM sync_meta")
    suspend fun clearMeta()
}

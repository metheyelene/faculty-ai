package com.bits.facultyai.data.sync

/**
 * Pure decision logic for the Firestore sync. No Android, Room or Firebase
 * types — every rule here is directly unit-testable and the [SyncEngine]
 * merely executes its verdicts.
 *
 * Conflict rule: last-write-wins on [updatedAt] (device wall clock at local
 * mutation). Equal timestamps are rare (sub-ms collisions); treated as equal,
 * which keeps pull and push from ping-ponging the same row forever.
 *
 * Cloud model — three aggregates + a deletion ledger, one document per uuid:
 *
 *  - `users/{uid}/slots/{uuid}`        one weekly class slot
 *  - `users/{uid}/students/{uuid}`     one roster student
 *  - `users/{uid}/sessions/{uuid}`     one attendance session WITH its entries
 *                                      embedded (record + entries are written
 *                                      and conflicted as a single unit — the
 *                                      marking flow always rewrites them
 *                                      together, so aggregate-level LWW is the
 *                                      honest granularity)
 *  - `users/{uid}/tombstones/{uuid}`   a deletion other devices must apply
 */
object SyncPolicy {

    /** Verdict for an incoming cloud row against the local row with the same uuid. */
    sealed interface Merge {
        /** Cloud is newer (or local is absent): overwrite local. */
        data object ApplyRemote : Merge

        /** Local is newer: the push path will win on the next pass. */
        data object KeepLocal : Merge

        /** Same currency — nothing to do on either side. */
        data object Equal : Merge
    }

    fun merge(localUpdatedAt: Long?, remoteUpdatedAt: Long): Merge = when {
        localUpdatedAt == null -> Merge.ApplyRemote
        remoteUpdatedAt > localUpdatedAt -> Merge.ApplyRemote
        localUpdatedAt > remoteUpdatedAt -> Merge.KeepLocal
        else -> Merge.Equal
    }

    /**
     * The push watermark may only move forward to the largest `updatedAt` of
     * the rows actually written this pass — never to "now" (a clock skew would
     * silently swallow future local writes) and never backwards.
     */
    fun nextWatermark(current: Long, writtenTimestamps: List<Long>): Long =
        maxOf(current, writtenTimestamps.maxOrNull() ?: current)

    /**
     * Applying a tombstone: a local row edited at or after the delete was
     * requested (`updatedAt >= tombstoneAt`) survived its own deletion on this
     * device — it wins and will be re-pushed. Anything older is truly deleted.
     */
    fun survivesTombstone(localUpdatedAt: Long, tombstoneAt: Long): Boolean =
        localUpdatedAt >= tombstoneAt

    /** Pull order — parents before children (entries ride inside sessions). */
    val PULL_TABLES = listOf("slot", "student", "session")

    /** Tombstone drain order — children first so no parent outlives its entries. */
    val TOMBSTONE_ORDER = listOf("entry", "record", "student", "slot")

    /** Firestore paths, single source of truth for the cloud layout. */
    fun collectionPath(uid: String, table: String) = "users/$uid/$table"
    fun tombstonePath(uid: String, uuid: String) = "users/$uid/tombstones/$uuid"
}

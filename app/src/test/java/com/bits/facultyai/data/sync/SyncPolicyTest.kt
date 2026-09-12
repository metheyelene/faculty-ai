package com.bits.facultyai.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync's conflict and watermark rules, verified as pure math. These are
 * the invariants the cloud model leans on:
 *
 *  - LWW never flips a newer local row back to remote state (pull) and never
 *    hides a newer remote row (push retry covers it on the next pass)
 *  - watermarks only move forward, and only over rows actually written
 *  - a tombstone never erases a row edited after the delete was requested
 */
class SyncPolicyTest {

    // ---- merge (last-write-wins) ----

    @Test
    fun `remote absent locally is applied`() {
        assertEquals(SyncPolicy.Merge.ApplyRemote, SyncPolicy.merge(null, 100L))
    }

    @Test
    fun `newer remote wins`() {
        assertEquals(SyncPolicy.Merge.ApplyRemote, SyncPolicy.merge(localUpdatedAt = 100L, remoteUpdatedAt = 200L))
    }

    @Test
    fun `newer local is kept`() {
        assertEquals(SyncPolicy.Merge.KeepLocal, SyncPolicy.merge(localUpdatedAt = 300L, remoteUpdatedAt = 200L))
    }

    @Test
    fun `equal currency is equal`() {
        assertEquals(SyncPolicy.Merge.Equal, SyncPolicy.merge(localUpdatedAt = 200L, remoteUpdatedAt = 200L))
        // The classic tie: both devices wrote within the same millisecond.
        assertEquals(SyncPolicy.Merge.Equal, SyncPolicy.merge(0L, 0L))
    }

    // ---- watermarks ----

    @Test
    fun `watermark advances to the newest written timestamp`() {
        assertEquals(500L, SyncPolicy.nextWatermark(current = 100L, writtenTimestamps = listOf(300L, 500L, 200L)))
    }

    @Test
    fun `watermark never regresses`() {
        assertEquals(900L, SyncPolicy.nextWatermark(current = 900L, writtenTimestamps = listOf(100L, 200L)))
    }

    @Test
    fun `watermark never jumps to now - empty write set keeps current`() {
        // Advancing to "now" would swallow future local writes after a clock skew.
        assertEquals(100L, SyncPolicy.nextWatermark(current = 100L, writtenTimestamps = emptyList()))
    }

    @Test
    fun `watermark stays when nothing written is newer`() {
        assertEquals(400L, SyncPolicy.nextWatermark(current = 400L, writtenTimestamps = listOf(100L, 400L)))
    }

    // ---- tombstone survival ----

    @Test
    fun `row edited after the delete request survives`() {
        // User deleted on device A, then edited the row on device B before the
        // tombstone arrived: the edit wins and will be re-pushed.
        assertTrue(SyncPolicy.survivesTombstone(localUpdatedAt = 1_100L, tombstoneAt = 1_000L))
    }

    @Test
    fun `row older than the delete request is erased`() {
        assertFalse(SyncPolicy.survivesTombstone(localUpdatedAt = 900L, tombstoneAt = 1_000L))
    }

    @Test
    fun `row edited in the same millisecond as the delete survives`() {
        // Boundary tie goes to survival: the delete is drained only after the
        // local mutation was observed, so same-ms content is the newer state.
        assertTrue(SyncPolicy.survivesTombstone(localUpdatedAt = 1_000L, tombstoneAt = 1_000L))
    }

    // ---- cloud layout ----

    @Test
    fun `paths are uid-scoped islands`() {
        assertEquals("users/u1/slots", SyncPolicy.collectionPath("u1", "slots"))
        assertEquals("users/u1/students", SyncPolicy.collectionPath("u1", "students"))
        assertEquals("users/u1/sessions", SyncPolicy.collectionPath("u1", "sessions"))
        assertEquals("users/u1/tombstones/abc", SyncPolicy.tombstonePath("u1", "abc"))
    }
}

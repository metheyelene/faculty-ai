package com.bits.facultyai.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bits.facultyai.data.sync.SyncEngine
import com.bits.facultyai.data.sync.SyncStatus
import kotlinx.coroutines.launch

/**
 * Wraps a top-level screen with a "SYNC NOW" pull-to-refresh gesture so the
 * two-device demo reveal works from any screen, without opening Settings.
 *
 * Only signed-in users get the gesture: guests pull nothing (the indicator
 * snaps back immediately). The indicator stays up until the engine's run
 * actually finishes — [SyncEngine.syncNow] folds into any in-flight run, so
 * repeated pulls never queue duplicate syncs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPullToRefresh(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val syncEngine = SyncEngine.get(LocalContext.current)
    val status by syncEngine.status.collectAsStateWithLifecycle()
    val signedIn by syncEngine.signedIn.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = status is SyncStatus.Syncing,
        onRefresh = {
            if (signedIn) scope.launch { syncEngine.syncNow() }
        },
        state = state,
        modifier = modifier,
    ) {
        content()
    }
}

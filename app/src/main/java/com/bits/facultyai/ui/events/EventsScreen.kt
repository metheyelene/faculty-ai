package com.bits.facultyai.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.EventEntity
import com.bits.facultyai.ui.components.GlassChip
import com.bits.facultyai.ui.components.GlassDialogSurface
import com.bits.facultyai.ui.components.GlassEmptyState
import com.bits.facultyai.ui.components.GlassSurface
import com.bits.facultyai.ui.components.GlassTopBar
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.navigation.KineticBottomNavigation
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun EventsScreen(
    onBack: () -> Unit = {},
    onOpenEvent: (Long) -> Unit,
    vm: EventsViewModel = viewModel(),
) {
    val k = LocalKineticColors.current
    val events by vm.events.collectAsStateWithLifecycle()
    val search by vm.searchQuery.collectAsStateWithLifecycle()
    val filter by vm.selectedFilter.collectAsStateWithLifecycle()
    val spent by vm.spentByEvent.collectAsStateWithLifecycle()
    val collected by vm.collectedByEvent.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<EventEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        GlassTopBar(title = "EVENTS", onBack = onBack)
        Spacer(Modifier.height(KineticSpacing.md))

        KineticTextField(value = search, onValueChange = vm::setSearch, hint = "SEARCH NAME, VENUE, DEPARTMENT...")

        Spacer(Modifier.height(KineticSpacing.md))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            EventFilter.entries.forEach { f ->
                GlassChip(
                    label = when (f) {
                        EventFilter.ALL -> "ALL"
                        EventFilter.UPCOMING -> "UPCOMING"
                        EventFilter.PAST -> "PAST"
                        EventFilter.THIS_MONTH -> "THIS MONTH"
                        EventFilter.THIS_YEAR -> "THIS YEAR"
                    },
                    selected = filter == f,
                    onClick = { vm.selectedFilter.value = f },
                )
            }
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        when {
            events.isEmpty() && search.isBlank() && filter == EventFilter.ALL -> {
                GlassEmptyState(
                    title = "NO EVENTS",
                    message = "TRACK FESTS, WORKSHOPS AND SEMINARS — WITH BUDGETS AND PHOTOS",
                    actionText = "NEW EVENT",
                    onAction = { onOpenEvent(0L) },
                )
            }
            events.isEmpty() -> {
                GlassEmptyState(
                    title = "NOTHING FOUND",
                    message = "NO EVENTS MATCH YOUR SEARCH OR FILTER",
                )
            }
            else -> {
                events.forEach { event ->
                    EventRow(
                        event = event,
                        spentPaisa = spent[event.id] ?: 0L,
                        collectedPaisa = collected[event.id] ?: 0L,
                        onClick = { onOpenEvent(event.id) },
                        onLongPressDelete = { confirmDelete = event },
                    )
                    KineticDivider()
                }
            }
        }

        Spacer(Modifier.height(KineticSpacing.lg))
        KineticButton(text = "NEW EVENT", onClick = { onOpenEvent(0L) })
        // Clears the floating glass dock + system navigation area.
        Spacer(Modifier.height(KineticBottomNavigation.bottomClearance()))
        Spacer(Modifier.height(KineticSpacing.xl))
    }

    confirmDelete?.let { event ->
        Dialog(onDismissRequest = { confirmDelete = null }) {
            GlassDialogSurface {
                Text(
                    text = "DELETE EVENT",
                    style = KineticType.heading,
                    color = k.foreground,
                )
                Spacer(Modifier.height(KineticSpacing.sm))
                Text(
                    text = "\"${event.name}\" and all its photos, expenses and collections will be removed. This cannot be undone.",
                    style = KineticType.body,
                    color = k.mutedForeground,
                )
                Spacer(Modifier.height(KineticSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                    KineticGhostButton(
                        text = "CANCEL",
                        onClick = { confirmDelete = null },
                        modifier = Modifier.weight(1f),
                    )
                    KineticButton(
                        text = "DELETE",
                        onClick = {
                            vm.deleteEvent(event)
                            confirmDelete = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: EventEntity,
    spentPaisa: Long,
    collectedPaisa: Long,
    onClick: () -> Unit,
    onLongPressDelete: () -> Unit,
) {
    val k = LocalKineticColors.current
    val balance = collectedPaisa - spentPaisa
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KineticSpacing.sm),
        strength = GlassStrength.THIN,
        onClick = onClick,
    ) {
        Column(Modifier.padding(KineticSpacing.lg)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = event.name.uppercase(),
                        style = KineticType.headingSm,
                        color = k.foreground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(KineticSpacing.xs))
                    Text(
                        text = buildString {
                            append(EventFormat.fullDate(event.date))
                            append(" · ")
                            append(EventFormat.dayName(event.date))
                        },
                        style = KineticType.label,
                        color = k.accent,
                    )
                    if (event.venue.isNotBlank() || event.category != "OTHER") {
                        Spacer(Modifier.height(KineticSpacing.xs))
                        Text(
                            text = listOf(event.venue, event.category).filter { it.isNotBlank() }
                                .joinToString(" · ").uppercase(),
                            style = KineticType.label,
                            color = k.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Top)
                        .background(androidx.compose.ui.graphics.Color.Transparent),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(text = "→", style = KineticType.headingSm, color = k.accent)
                }
            }
            // Live finance strip — real totals from the database, zero when untouched.
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.lg)) {
                MiniStat("SPENT", EventFormat.money(spentPaisa), k.mutedForeground)
                MiniStat("RAISED", EventFormat.money(collectedPaisa), k.mutedForeground)
                MiniStat(
                    "BALANCE",
                    EventFormat.money(balance),
                    if (balance < 0) k.statusError else k.foreground,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column {
        Text(text = value, style = KineticType.bodyMedium, color = valueColor)
        Text(text = label, style = KineticType.label, fontSize = 9.sp, color = valueColor.copy(alpha = 0.7f))
    }
}

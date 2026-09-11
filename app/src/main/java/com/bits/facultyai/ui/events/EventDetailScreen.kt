package com.bits.facultyai.ui.events

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.bits.facultyai.data.local.EventCollectionEntity
import com.bits.facultyai.data.local.EventEntity
import com.bits.facultyai.data.local.EventExpenseEntity
import com.bits.facultyai.data.local.EventPhotoEntity
import com.bits.facultyai.ui.components.GlassChip
import com.bits.facultyai.ui.components.GlassDialogSurface
import com.bits.facultyai.ui.components.GlassEmptyState
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.GlassSurface
import com.bits.facultyai.ui.components.GlassTopBar
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.navigation.KineticBottomNavigation
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import java.io.File
import java.time.LocalDate

private val EXPENSE_CATEGORIES = listOf(
    "VENUE", "FOOD", "TRAVEL", "DECORATION", "PRINTING",
    "EQUIPMENT", "GIFTS", "LOGISTICS", "OTHER",
)
private val PAYMENT_METHODS = listOf("CASH", "UPI", "CARD", "BANK", "CHEQUE", "OTHER")

@Composable
fun EventDetailScreen(
    onBack: () -> Unit = {},
    onEditEvent: (Long) -> Unit,
    vm: EventDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                EventDetailViewModel(
                    createSavedStateHandle(),
                    checkNotNull(this[APPLICATION_KEY]),
                )
            }
        },
    ),
) {
    val k = LocalKineticColors.current
    val context = LocalContext.current
    val event by vm.event.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val finance by vm.finance.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var viewerPhoto by remember { mutableStateOf<EventPhotoEntity?>(null) }
    var confirmDeletePhoto by remember { mutableStateOf<EventPhotoEntity?>(null) }
    var confirmDeleteExpense by remember { mutableStateOf<EventExpenseEntity?>(null) }
    var confirmDeleteCollection by remember { mutableStateOf<EventCollectionEntity?>(null) }
    var editExpense by remember { mutableStateOf<EventExpenseEntity?>(null) }
    var showExpenseEditor by remember { mutableStateOf(false) }
    var editCollection by remember { mutableStateOf<EventCollectionEntity?>(null) }
    var showCollectionEditor by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingReceiptTargetId by remember { mutableStateOf<Long?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.attachPhotos(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCaptureUri
        if (ok && uri != null) vm.capturePhoto(uri)
        pendingCaptureUri = null
    }
    val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = pendingReceiptTargetId
        if (uri != null && targetId != null && targetId > 0) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.media.attachReceipt(targetId, uri)
        }
        pendingReceiptTargetId = null
    }

    val e = event
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        GlassTopBar(
            title = e?.name?.uppercase() ?: "EVENT",
            subtitle = e?.let { "${EventFormat.fullDate(it.date)} · ${EventFormat.dayName(it.date)}" },
            onBack = onBack,
            trailing = {
                if (e != null) {
                    Text(
                        text = "EDIT",
                        style = KineticType.labelBold,
                        color = k.accent,
                        modifier = Modifier
                            .padding(start = KineticSpacing.md)
                            .clickable(onClick = { onEditEvent(vm.eventId) }),
                    )
                }
            },
        )
        Spacer(Modifier.height(KineticSpacing.md))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            listOf("OVERVIEW", "GALLERY", "FINANCES").forEachIndexed { i, label ->
                GlassChip(label = label, selected = tab == i, onClick = { tab = i })
            }
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        if (e == null) {
            GlassEmptyState(title = "EVENT NOT FOUND", message = "THIS EVENT MAY HAVE BEEN DELETED")
        } else when (tab) {
            0 -> OverviewTab(event = e, photos = photos)
            1 -> GalleryTab(
                photos = photos,
                onAdd = { galleryLauncher.launch(arrayOf("image/*")) },
                onCapture = {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                    pendingCaptureUri = uri
                    cameraLauncher.launch(uri)
                },
                onOpen = { viewerPhoto = it },
                onRetry = vm::retryPhoto,
            )
            else -> FinanceTab(
                expenses = expenses,
                collections = collections,
                collectedPaisa = finance.first,
                spentPaisa = finance.second,
                onAddExpense = { editExpense = null; showExpenseEditor = true },
                onEditExpense = { editExpense = it; showExpenseEditor = true },
                onAttachReceipt = { row ->
                    pendingReceiptTargetId = row.id
                    receiptLauncher.launch(arrayOf("image/*", "application/pdf"))
                },
                onOpenReceipt = { row -> vm.media.openReceipt(row.id) {} },
                onRemoveReceipt = { row -> vm.media.removeReceipt(row.id) },
                onDeleteExpense = { confirmDeleteExpense = it },
                onAddCollection = { editCollection = null; showCollectionEditor = true },
                onEditCollection = { editCollection = it; showCollectionEditor = true },
                onDeleteCollection = { confirmDeleteCollection = it },
            )
        }

        // Clears the floating glass dock + system navigation area.
        Spacer(Modifier.height(KineticBottomNavigation.bottomClearance()))
        Spacer(Modifier.height(KineticSpacing.xl))
    }

    // ---------------- Fullscreen viewer with pinch zoom ----------------
    viewerPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { viewerPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            var scale by remember(photo.id) { mutableFloatStateOf(1f) }
            var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
            var caption by remember(photo.id) { mutableStateOf(photo.caption) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .pointerInput(photo.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(photo.id) {
                        detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
                    },
            ) {
                AsyncImage(
                    model = File(File(context.filesDir, "event_photos"), photo.fileName),
                    contentDescription = photo.caption.ifBlank { "Event photo" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        },
                )
                Text(
                    text = "CLOSE ✕",
                    style = KineticType.labelBold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(KineticSpacing.lg)
                        .clickable(onClick = { viewerPhoto = null }),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(KineticSpacing.lg),
                ) {
                    if (photo.isCover) {
                        Text(text = "COVER PHOTO", style = KineticType.labelBold, color = k.accent)
                        Spacer(Modifier.height(KineticSpacing.xs))
                    }
                    KineticTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        hint = "CAPTION",
                        modifier = Modifier.imePadding(),
                    )
                    Spacer(Modifier.height(KineticSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                        KineticGhostButton(
                            text = if (photo.isCover) "COVER ✓" else "SET COVER",
                            onClick = {
                                vm.setCover(photo.id)
                                viewerPhoto = photo.copy(isCover = true)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        KineticGhostButton(
                            text = "DELETE",
                            onClick = {
                                confirmDeletePhoto = photo
                                viewerPhoto = null
                            },
                            modifier = Modifier.weight(1f),
                        )
                        KineticButton(
                            text = "SAVE",
                            onClick = {
                                vm.setCaption(photo.id, caption)
                                viewerPhoto = null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    // ---------------- Photo remove confirmation ----------------
    confirmDeletePhoto?.let { photo ->
        Dialog(onDismissRequest = { confirmDeletePhoto = null }) {
            GlassDialogSurface {
                Text(text = "REMOVE PHOTO", style = KineticType.heading, color = k.foreground)
                Spacer(Modifier.height(KineticSpacing.sm))
                Text(text = "This photo will be removed from the event gallery.", style = KineticType.body, color = k.mutedForeground)
                Spacer(Modifier.height(KineticSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                    KineticGhostButton(text = "CANCEL", onClick = { confirmDeletePhoto = null }, modifier = Modifier.weight(1f))
                    KineticButton(
                        text = "REMOVE",
                        onClick = {
                            vm.removePhoto(photo.id)
                            confirmDeletePhoto = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ---------------- Expense delete confirmation (financial history) ----------------
    confirmDeleteExpense?.let { x ->
        Dialog(onDismissRequest = { confirmDeleteExpense = null }) {
            GlassDialogSurface {
                Text(text = "DELETE EXPENSE", style = KineticType.heading, color = k.foreground)
                Spacer(Modifier.height(KineticSpacing.sm))
                Text(
                    text = "\"${x.title}\" (−${EventFormat.money(x.amountPaisa)}) will be permanently removed from this event's finances.",
                    style = KineticType.body,
                    color = k.mutedForeground,
                )
                Spacer(Modifier.height(KineticSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                    KineticGhostButton(text = "CANCEL", onClick = { confirmDeleteExpense = null }, modifier = Modifier.weight(1f))
                    KineticButton(
                        text = "DELETE",
                        onClick = {
                            vm.deleteExpense(x)
                            confirmDeleteExpense = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ---------------- Collection delete confirmation ----------------
    confirmDeleteCollection?.let { c ->
        Dialog(onDismissRequest = { confirmDeleteCollection = null }) {
            GlassDialogSurface {
                Text(text = "DELETE COLLECTION", style = KineticType.heading, color = k.foreground)
                Spacer(Modifier.height(KineticSpacing.sm))
                Text(
                    text = "\"${c.source}\" (+${EventFormat.money(c.amountPaisa)}) will be permanently removed from this event's finances.",
                    style = KineticType.body,
                    color = k.mutedForeground,
                )
                Spacer(Modifier.height(KineticSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                    KineticGhostButton(text = "CANCEL", onClick = { confirmDeleteCollection = null }, modifier = Modifier.weight(1f))
                    KineticButton(
                        text = "DELETE",
                        onClick = {
                            vm.deleteCollection(c)
                            confirmDeleteCollection = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ---------------- Expense editor ----------------
    if (showExpenseEditor) {
        ExpenseEditorDialog(
            existing = editExpense,
            onDismiss = { showExpenseEditor = false },
            onSave = { row ->
                val isNew = editExpense == null
                if (isNew) {
                    vm.saveExpense(null, row) { newId ->
                        pendingReceiptTargetId = newId
                        receiptLauncher.launch(arrayOf("image/*", "application/pdf"))
                    }
                } else {
                    vm.saveExpense(editExpense, row) {}
                }
                showExpenseEditor = false
            },
            onAttachReceipt = { row ->
                pendingReceiptTargetId = row.id
                receiptLauncher.launch(arrayOf("image/*", "application/pdf"))
            },
            onRemoveReceipt = { row -> vm.media.removeReceipt(row.id) },
            onOpenReceipt = { row -> vm.media.openReceipt(row.id) {} },
        )
    }

    // ---------------- Collection editor ----------------
    if (showCollectionEditor) {
        CollectionEditorDialog(
            existing = editCollection,
            onDismiss = { showCollectionEditor = false },
            onSave = { row ->
                vm.saveCollection(editCollection, row)
                showCollectionEditor = false
            },
        )
    }
}

// ------------------------------------------------------------------ Tabs

@Composable
private fun OverviewTab(event: EventEntity, photos: List<EventPhotoEntity>) {
    val k = LocalKineticColors.current
    GlassSurface(modifier = Modifier.fillMaxWidth(), strength = GlassStrength.THIN) {
        Column(Modifier.padding(KineticSpacing.lg)) {
            InfoRow("WHEN", "${EventFormat.fullDate(event.date)} · ${EventFormat.dayName(event.date)}")
            InfoRow(
                "TIME",
                "${EventFormat.minutesToTime(event.startTimeMinutes)} — ${EventFormat.minutesToTime(event.endTimeMinutes)}",
            )
            if (event.venue.isNotBlank()) InfoRow("VENUE", event.venue)
            if (event.organizer.isNotBlank()) InfoRow("ORGANIZER", event.organizer)
            if (event.department.isNotBlank()) InfoRow("DEPARTMENT", event.department)
            if (event.participants.isNotBlank()) InfoRow("PARTICIPANTS", event.participants)
            InfoRow("CATEGORY", event.category)
        }
    }
    if (event.description.isNotBlank() || event.notes.isNotBlank()) {
        Spacer(Modifier.height(KineticSpacing.lg))
        if (event.description.isNotBlank()) {
            KineticSectionHeader(title = "DESCRIPTION")
            Text(text = event.description, style = KineticType.body, color = k.foreground)
            Spacer(Modifier.height(KineticSpacing.lg))
        }
        if (event.notes.isNotBlank()) {
            KineticSectionHeader(title = "NOTES")
            Text(text = event.notes, style = KineticType.body, color = k.mutedForeground)
        }
    }
    if (photos.isNotEmpty()) {
        Spacer(Modifier.height(KineticSpacing.lg))
        KineticSectionHeader(title = "PHOTOS (${photos.size})")
        Spacer(Modifier.height(KineticSpacing.sm))
        PhotoStrip(photos = photos.take(6))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val k = LocalKineticColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = KineticSpacing.xs)) {
        Text(
            text = label,
            style = KineticType.labelBold,
            color = k.mutedForeground,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = KineticType.bodyMedium,
            color = k.foreground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GalleryTab(
    photos: List<EventPhotoEntity>,
    onAdd: () -> Unit,
    onCapture: () -> Unit,
    onOpen: (EventPhotoEntity) -> Unit,
    onRetry: (Long) -> Unit,
) {
    if (photos.isEmpty()) {
        GlassEmptyState(
            title = "NO PHOTOS",
            message = "ADD PHOTOS FROM YOUR GALLERY OR CAPTURE WITH THE CAMERA",
            actionText = "ADD PHOTOS",
            onAction = onAdd,
        )
        Spacer(Modifier.height(KineticSpacing.md))
        KineticGhostButton(text = "CAPTURE PHOTO", onClick = onCapture)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
        KineticButton(text = "ADD PHOTOS", onClick = onAdd, modifier = Modifier.weight(1f))
        KineticGhostButton(text = "CAMERA", onClick = onCapture, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(KineticSpacing.md))
    photos.chunked(3).forEach { rowPhotos ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
            modifier = Modifier.padding(bottom = KineticSpacing.sm),
        ) {
            rowPhotos.forEach { photo ->
                PhotoTile(
                    photo = photo,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpen(photo) },
                    onRetry = { onRetry(photo.id) },
                )
            }
            repeat(3 - rowPhotos.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PhotoTile(
    photo: EventPhotoEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val k = LocalKineticColors.current
    val context = LocalContext.current
    GlassSurface(modifier = modifier, strength = GlassStrength.ULTRA_THIN, onClick = onClick) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (photo.state == "UPLOADED") {
                    AsyncImage(
                        model = File(File(context.filesDir, "event_photos"), photo.fileName),
                        contentDescription = photo.caption.ifBlank { "Event photo" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (photo.isCover) {
                        Text(
                            text = "COVER",
                            style = KineticType.labelBold,
                            color = k.accent,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Column(
                        Modifier.padding(KineticSpacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = when (photo.state) {
                                "UPLOADING" -> "UPLOADING"
                                "PROCESSING" -> "PROCESSING"
                                else -> "FAILED"
                            },
                            style = KineticType.labelBold,
                            color = if (photo.state == "FAILED") k.statusError else k.mutedForeground,
                        )
                        if (photo.state == "FAILED") {
                            Spacer(Modifier.height(KineticSpacing.xs))
                            Text(
                                text = "RETRY",
                                style = KineticType.labelBold,
                                color = k.accent,
                                modifier = Modifier.clickable(onClick = onRetry),
                            )
                        }
                    }
                }
            }
            if (photo.caption.isNotBlank()) {
                Text(
                    text = photo.caption,
                    style = KineticType.label,
                    color = k.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = KineticSpacing.sm, vertical = KineticSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun PhotoStrip(photos: List<EventPhotoEntity>) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
        photos.forEach { photo ->
            GlassSurface(modifier = Modifier.weight(1f), strength = GlassStrength.ULTRA_THIN) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo.state == "UPLOADED") {
                        AsyncImage(
                            model = File(File(context.filesDir, "event_photos"), photo.fileName),
                            contentDescription = photo.caption.ifBlank { "Event photo" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = "…",
                            style = KineticType.heading,
                            color = LocalKineticColors.current.mutedForeground,
                        )
                    }
                }
            }
        }
        repeat(6 - photos.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun FinanceTab(
    expenses: List<EventExpenseEntity>,
    collections: List<EventCollectionEntity>,
    collectedPaisa: Long,
    spentPaisa: Long,
    onAddExpense: () -> Unit,
    onEditExpense: (EventExpenseEntity) -> Unit,
    onAttachReceipt: (EventExpenseEntity) -> Unit,
    onOpenReceipt: (EventExpenseEntity) -> Unit,
    onRemoveReceipt: (EventExpenseEntity) -> Unit,
    onDeleteExpense: (EventExpenseEntity) -> Unit,
    onAddCollection: () -> Unit,
    onEditCollection: (EventCollectionEntity) -> Unit,
    onDeleteCollection: (EventCollectionEntity) -> Unit,
) {
    val k = LocalKineticColors.current
    val balance = collectedPaisa - spentPaisa

    GlassSurface(modifier = Modifier.fillMaxWidth(), strength = GlassStrength.REGULAR) {
        Column(Modifier.padding(KineticSpacing.lg)) {
            Text(text = "EVENT FINANCES", style = KineticType.labelBold, color = k.mutedForeground)
            Spacer(Modifier.height(KineticSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.lg)) {
                BigMoney(EventFormat.money(collectedPaisa), "COLLECTED", Modifier.weight(1f))
                BigMoney(EventFormat.money(spentPaisa), "SPENT", Modifier.weight(1f))
                BigMoney(
                    EventFormat.money(balance),
                    "BALANCE",
                    Modifier.weight(1f),
                    color = if (balance < 0) k.statusError else k.accent,
                )
            }
            Spacer(Modifier.height(KineticSpacing.sm))
            Text(
                text = "${expenses.size} EXPENSES · ${collections.size} COLLECTIONS · ${collections.map { it.source }.distinct().size} CONTRIBUTORS",
                style = KineticType.label,
                color = k.mutedForeground,
            )
        }
    }

    Spacer(Modifier.height(KineticSpacing.lg))
    KineticSectionHeader(title = "COLLECTIONS")
    if (collections.isEmpty()) {
        Text(text = "NO MONEY COLLECTED YET", style = KineticType.label, color = k.mutedForeground)
    } else {
        collections.forEach { c ->
            CollectionRow(c, onClick = { onEditCollection(c) }, onDelete = { onDeleteCollection(c) })
            KineticDivider()
        }
    }
    Spacer(Modifier.height(KineticSpacing.sm))
    KineticGhostButton(text = "ADD COLLECTION", onClick = onAddCollection)

    Spacer(Modifier.height(KineticSpacing.lg))
    KineticSectionHeader(title = "EXPENSES")
    if (expenses.isEmpty()) {
        Text(text = "NO EXPENSES RECORDED YET", style = KineticType.label, color = k.mutedForeground)
    } else {
        expenses.forEach { x ->
            ExpenseRow(
                x,
                onClick = { onEditExpense(x) },
                onAttachReceipt = { onAttachReceipt(x) },
                onOpenReceipt = { onOpenReceipt(x) },
                onRemoveReceipt = { onRemoveReceipt(x) },
                onDelete = { onDeleteExpense(x) },
            )
            KineticDivider()
        }
    }
    Spacer(Modifier.height(KineticSpacing.sm))
    KineticButton(text = "ADD EXPENSE", onClick = onAddExpense)
}

@Composable
private fun BigMoney(value: String, label: String, modifier: Modifier = Modifier, color: Color? = null) {
    val k = LocalKineticColors.current
    Column(modifier) {
        Text(
            text = value,
            style = KineticType.display.copy(fontSize = 22.sp),
            color = color ?: k.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = label, style = KineticType.label, color = k.mutedForeground)
    }
}

@Composable
private fun CollectionRow(c: EventCollectionEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val k = LocalKineticColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = KineticSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = c.source.uppercase(),
                style = KineticType.bodyMedium,
                color = k.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    EventFormat.parse(c.date)?.let { EventFormat.fullDate(c.date) },
                    c.paymentMethod,
                    c.purpose.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = KineticType.label,
                color = k.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = "+${EventFormat.money(c.amountPaisa)}", style = KineticType.headingSm, color = k.statusSuccess)
        Text(
            text = "✕",
            style = KineticType.labelBold,
            color = k.mutedForeground,
            modifier = Modifier
                .padding(start = KineticSpacing.md)
                .clickable(onClick = onDelete),
        )
    }
}

@Composable
private fun ExpenseRow(
    x: EventExpenseEntity,
    onClick: () -> Unit,
    onAttachReceipt: () -> Unit,
    onOpenReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit,
    onDelete: () -> Unit,
) {
    val k = LocalKineticColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = KineticSpacing.sm)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = x.title.uppercase(),
                    style = KineticType.bodyMedium,
                    color = k.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        x.category,
                        EventFormat.parse(x.date)?.let { EventFormat.fullDate(x.date) },
                        x.paymentMethod,
                        x.paidBy.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = KineticType.label,
                    color = k.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text = "−${EventFormat.money(x.amountPaisa)}", style = KineticType.headingSm, color = k.foreground)
            Text(
                text = "✕",
                style = KineticType.labelBold,
                color = k.mutedForeground,
                modifier = Modifier
                    .padding(start = KineticSpacing.md)
                    .clickable(onClick = onDelete),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.md), modifier = Modifier.padding(top = KineticSpacing.xs)) {
            Text(
                text = if (x.receiptFileName == null) "ATTACH RECEIPT" else "RECEIPT: ${x.receiptName}",
                style = KineticType.labelBold,
                color = if (x.receiptFileName == null) k.accent else k.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable {
                        if (x.receiptFileName == null) onAttachReceipt() else onOpenReceipt()
                    },
            )
            if (x.receiptFileName != null) {
                Text(
                    text = "REMOVE RECEIPT",
                    style = KineticType.labelBold,
                    color = k.mutedForeground,
                    modifier = Modifier.clickable(onClick = onRemoveReceipt),
                )
            }
        }
    }
}

// --------------------------------------------------------------- Editors

@Composable
private fun ExpenseEditorDialog(
    existing: EventExpenseEntity?,
    onDismiss: () -> Unit,
    onSave: (EventExpenseEntity) -> Unit,
    onAttachReceipt: (EventExpenseEntity) -> Unit,
    onRemoveReceipt: (EventExpenseEntity) -> Unit,
    onOpenReceipt: (EventExpenseEntity) -> Unit,
) {
    val k = LocalKineticColors.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "OTHER") }
    var customCategory by remember {
        mutableStateOf(if (existing != null && existing.category !in EXPENSE_CATEGORIES) existing.category else "")
    }
    var amountText by remember {
        mutableStateOf(existing?.let { EventFormat.money(it.amountPaisa).removePrefix("₹") } ?: "")
    }
    var dateText by remember { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var paidBy by remember { mutableStateOf(existing?.paidBy ?: "") }
    var method by remember { mutableStateOf(existing?.paymentMethod ?: "CASH") }
    var vendor by remember { mutableStateOf(existing?.vendor ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val amountPaisa = EventFormat.paisaFromRupees(amountText)
    val titleError = showErrors && title.isBlank()
    val amountError = showErrors && (amountPaisa == null || amountPaisa <= 0)
    val dateBad = EventFormat.parse(dateText) == null

    Dialog(onDismissRequest = onDismiss) {
        GlassDialogSurface(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Text(
                text = if (existing == null) "ADD EXPENSE" else "EDIT EXPENSE",
                style = KineticType.heading,
                color = k.foreground,
            )
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(
                value = title,
                onValueChange = { title = it },
                hint = "EXPENSE TITLE *",
                isError = titleError,
                errorMessage = if (titleError) "TITLE IS REQUIRED" else null,
            )
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                hint = "AMOUNT (₹) *",
                isError = amountError,
                errorMessage = if (amountError) "ENTER A VALID AMOUNT" else null,
            )
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(
                value = dateText,
                onValueChange = { dateText = it },
                hint = "DATE (YYYY-MM-DD) *",
                isError = showErrors && dateBad,
                errorMessage = if (showErrors && dateBad) "USE YYYY-MM-DD" else null,
            )
            Spacer(Modifier.height(KineticSpacing.md))
            Text(text = "CATEGORY", style = KineticType.labelBold, color = k.mutedForeground)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                val effective = if (customCategory.isNotBlank()) customCategory.uppercase() else category
                EXPENSE_CATEGORIES.forEach { c ->
                    GlassChip(label = c, selected = effective == c, onClick = { category = c; customCategory = "" })
                }
                GlassChip(
                    label = "CUSTOM",
                    selected = customCategory.isNotBlank(),
                    onClick = { customCategory = if (customCategory.isBlank()) " " else "" },
                )
            }
            if (customCategory.isNotBlank()) {
                Spacer(Modifier.height(KineticSpacing.sm))
                KineticTextField(value = customCategory.trim(), onValueChange = { customCategory = it }, hint = "CUSTOM CATEGORY")
            }
            Spacer(Modifier.height(KineticSpacing.md))
            Text(text = "PAID VIA", style = KineticType.labelBold, color = k.mutedForeground)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                PAYMENT_METHODS.forEach { m -> GlassChip(label = m, selected = method == m, onClick = { method = m }) }
            }
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = paidBy, onValueChange = { paidBy = it }, hint = "PAID BY")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = vendor, onValueChange = { vendor = it }, hint = "VENDOR / PERSON")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = description, onValueChange = { description = it }, hint = "DESCRIPTION", minLines = 2, maxLines = 4)
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = notes, onValueChange = { notes = it }, hint = "NOTES", minLines = 1, maxLines = 3)

            if (existing != null && existing.receiptFileName != null) {
                Spacer(Modifier.height(KineticSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.md)) {
                    Text(
                        text = "OPEN RECEIPT",
                        style = KineticType.labelBold,
                        color = k.accent,
                        modifier = Modifier.clickable(onClick = { onOpenReceipt(existing) }),
                    )
                    Text(
                        text = "REMOVE RECEIPT",
                        style = KineticType.labelBold,
                        color = k.statusError,
                        modifier = Modifier.clickable(onClick = { onRemoveReceipt(existing) }),
                    )
                }
            } else {
                Spacer(Modifier.height(KineticSpacing.md))
                Text(
                    text = "RECEIPT CAN BE ATTACHED AFTER SAVING",
                    style = KineticType.label,
                    color = k.mutedForeground,
                )
            }

            Spacer(Modifier.height(KineticSpacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticGhostButton(text = "CANCEL", onClick = onDismiss, modifier = Modifier.weight(1f))
                KineticButton(
                    text = "SAVE",
                    onClick = {
                        if (title.isBlank() || amountPaisa == null || amountPaisa <= 0 || dateBad) {
                            showErrors = true
                        } else {
                            onSave(
                                (existing ?: EventExpenseEntity(eventId = 0, title = "", amountPaisa = 0, date = "")).copy(
                                    title = title.trim(),
                                    category = if (customCategory.isNotBlank()) customCategory.trim().uppercase() else category,
                                    amountPaisa = amountPaisa,
                                    date = dateText,
                                    paidBy = paidBy.trim(),
                                    paymentMethod = method,
                                    vendor = vendor.trim(),
                                    description = description.trim(),
                                    notes = notes.trim(),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CollectionEditorDialog(
    existing: EventCollectionEntity?,
    onDismiss: () -> Unit,
    onSave: (EventCollectionEntity) -> Unit,
) {
    val k = LocalKineticColors.current
    var source by remember { mutableStateOf(existing?.source ?: "") }
    var amountText by remember {
        mutableStateOf(existing?.let { EventFormat.money(it.amountPaisa).removePrefix("₹") } ?: "")
    }
    var dateText by remember { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var method by remember { mutableStateOf(existing?.paymentMethod ?: "CASH") }
    var purpose by remember { mutableStateOf(existing?.purpose ?: "") }
    var reference by remember { mutableStateOf(existing?.reference ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val amountPaisa = EventFormat.paisaFromRupees(amountText)
    val sourceError = showErrors && source.isBlank()
    val amountError = showErrors && (amountPaisa == null || amountPaisa <= 0)
    val dateBad = EventFormat.parse(dateText) == null

    Dialog(onDismissRequest = onDismiss) {
        GlassDialogSurface(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Text(
                text = if (existing == null) "ADD COLLECTION" else "EDIT COLLECTION",
                style = KineticType.heading,
                color = k.foreground,
            )
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(
                value = source,
                onValueChange = { source = it },
                hint = "PERSON / SOURCE *",
                isError = sourceError,
                errorMessage = if (sourceError) "SOURCE IS REQUIRED" else null,
            )
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                hint = "AMOUNT (₹) *",
                isError = amountError,
                errorMessage = if (amountError) "ENTER A VALID AMOUNT" else null,
            )
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(
                value = dateText,
                onValueChange = { dateText = it },
                hint = "DATE (YYYY-MM-DD) *",
                isError = showErrors && dateBad,
                errorMessage = if (showErrors && dateBad) "USE YYYY-MM-DD" else null,
            )
            Spacer(Modifier.height(KineticSpacing.md))
            Text(text = "PAID VIA", style = KineticType.labelBold, color = k.mutedForeground)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                PAYMENT_METHODS.forEach { m -> GlassChip(label = m, selected = method == m, onClick = { method = m }) }
            }
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = purpose, onValueChange = { purpose = it }, hint = "PURPOSE (E.G. DEPARTMENT FUND)")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = reference, onValueChange = { reference = it }, hint = "REFERENCE / TXN ID")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = notes, onValueChange = { notes = it }, hint = "NOTES", minLines = 1, maxLines = 3)
            Spacer(Modifier.height(KineticSpacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticGhostButton(text = "CANCEL", onClick = onDismiss, modifier = Modifier.weight(1f))
                KineticButton(
                    text = "SAVE",
                    onClick = {
                        if (source.isBlank() || amountPaisa == null || amountPaisa <= 0 || dateBad) {
                            showErrors = true
                        } else {
                            onSave(
                                (existing ?: EventCollectionEntity(eventId = 0, source = "", amountPaisa = 0, date = "")).copy(
                                    source = source.trim(),
                                    amountPaisa = amountPaisa,
                                    date = dateText,
                                    paymentMethod = method,
                                    purpose = purpose.trim(),
                                    reference = reference.trim(),
                                    notes = notes.trim(),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.DecryptedLoveNote
import com.example.data.DecryptedMedia
import com.example.data.ImageBitmapCache
import com.example.data.MediaAttachmentHelper
import com.example.ui.ForeverUsSessionState
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EncryptedEmerald
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LoveNoteMoods = listOf(
    "adore" to "Adore",
    "grateful" to "Grateful",
    "miss_you" to "Miss You",
    "promise" to "Promise",
    "celebrate" to "Celebrate"
)

@Composable
fun MemoriesAndNotesScreen(
    session: ForeverUsSessionState,
    loveNotes: List<DecryptedLoveNote>,
    mediaItems: List<DecryptedMedia>,
    savedChatPhotoIds: Set<String>,
    onAddLoveNote: (String, String) -> Unit,
    onTogglePinNote: (DecryptedLoveNote) -> Unit,
    onDeleteNote: (String) -> Unit,
    onUploadMemoryPhoto: (ByteArray, String, String) -> Unit,
    onSaveChatPhotoToMemories: (DecryptedMedia, String?, String?) -> Unit,
    onDeleteMedia: (String) -> Unit,
    onOpenChat: () -> Unit,
    onGoToPairing: () -> Unit,
    onShowError: (String) -> Unit
) {
    val couple = session.activeCouple
    val myUid = session.userId

    if (couple == null || couple.status != "connected") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                Text("Shared Vault Locked", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Encrypted Love Notes and your Private Photo Gallery unlock once both partners connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onGoToPairing) {
                    Text("Go to Partner Pairing")
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    var selectedSegment by remember { mutableIntStateOf(0) } // 0 = Saved Chat Photos (Memories), 1 = Love Notes
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var pendingMemoryBytes by remember { mutableStateOf<ByteArray?>(null) }
    var viewingMedia by remember { mutableStateOf<DecryptedMedia?>(null) }

    val savedMemories = remember(mediaItems) {
        mediaItems.filter { it.raw.mediaType == "memory" }
    }
    val chatPhotos = remember(mediaItems) {
        mediaItems.filter { it.raw.mediaType == "photo" }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaAttachmentHelper.compressPickedImageAsync(context, uri)
                    .onSuccess { payload -> pendingMemoryBytes = payload.bytes }
                    .onFailure { onShowError(it.localizedMessage ?: "Unable to read photo.") }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Memories", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = EncryptedEmerald, modifier = Modifier.size(14.dp))
                    Text(
                        text = "Your saved chat photos & keepsake love notes, end-to-end encrypted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedSegment == 0,
                        onClick = { selectedSegment = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("memories_tab_gallery")
                    ) {
                        Text("Saved Photos (${savedMemories.size})")
                    }
                    SegmentedButton(
                        selected = selectedSegment == 1,
                        onClick = { selectedSegment = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("memories_tab_notes")
                    ) {
                        Text("Love Notes (${loveNotes.size})")
                    }
                }
            }

            if (selectedSegment == 0) {
                // Quick strip to save any existing chat photos directly into Memories
                if (chatPhotos.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Save Photos from Chat (${chatPhotos.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tap to save",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(chatPhotos, key = { it.raw.id }) { chatPhoto ->
                                    val isSaved = chatPhoto.raw.id in savedChatPhotoIds
                                    ChatPhotoSaveChipTile(
                                        media = chatPhoto,
                                        isSaved = isSaved,
                                        onSave = {
                                            if (!isSaved) {
                                                onSaveChatPhotoToMemories(chatPhoto, null, null)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (savedMemories.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                                Text("No Saved Chat Photos Yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Tap \"Save Memory\" on any photo in your private chat to keep it here forever—or tap + to add a photo directly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onOpenChat,
                                    modifier = Modifier.testTag("memories_open_chat_button")
                                ) {
                                    Icon(Icons.Filled.ChatBubble, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open Private Chat")
                                }
                            }
                        }
                    }
                } else {
                    items(savedMemories.chunked(2)) { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowPair.forEach { item ->
                                MemoryPhotoTile(
                                    media = item,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewingMedia = item }
                                )
                            }
                            if (rowPair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                if (loveNotes.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Text("No love notes yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Tap the + button to leave an encrypted keepsake note for ${couple.partnerNameFor(myUid)}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(loveNotes, key = { it.raw.id }) { note ->
                        val authorLabel = if (note.raw.authorId == myUid) "From You" else "From ${couple.partnerNameFor(myUid)}"

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (note.raw.pinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surfaceContainer
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = note.raw.mood.replace("_", " ").replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { onTogglePinNote(note) }) {
                                            Icon(
                                                imageVector = Icons.Filled.PushPin,
                                                contentDescription = if (note.raw.pinned) "Unpin love note" else "Pin love note",
                                                tint = if (note.raw.pinned) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (note.raw.authorId == myUid) {
                                            IconButton(onClick = { onDeleteNote(note.raw.id) }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Delete love note",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = "\"${note.text}\"",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$authorLabel • ${dateFormat.format(note.raw.createdAt?.toDate() ?: Date())}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (selectedSegment == 0) {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } else {
                    showNewNoteDialog = true
                }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("memories_add_fab")
        ) {
            Icon(
                imageVector = if (selectedSegment == 0) Icons.Filled.AddPhotoAlternate else Icons.Filled.Add,
                contentDescription = if (selectedSegment == 0) "Add encrypted memory photo" else "Write love note"
            )
        }
    }

    if (showNewNoteDialog) {
        ComposeLoveNoteDialog(
            onDismiss = { showNewNoteDialog = false },
            onSave = { text, mood ->
                onAddLoveNote(text, mood)
                showNewNoteDialog = false
            }
        )
    }

    pendingMemoryBytes?.let { bytes ->
        UploadMemoryDialog(
            onDismiss = { pendingMemoryBytes = null },
            onUpload = { title, caption ->
                onUploadMemoryPhoto(bytes, title, caption)
                pendingMemoryBytes = null
            }
        )
    }

    viewingMedia?.let { media ->
        AlertDialog(
            onDismissRequest = { viewingMedia = null },
            title = { Text(media.title.ifBlank { "Shared Memory" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    media.bytes?.let { bytes ->
                        val bitmap by produceState<ImageBitmap?>(
                            initialValue = ImageBitmapCache.get(media.raw.id),
                            key1 = media.raw.id
                        ) {
                            value = ImageBitmapCache.getOrDecodeBytes(media.raw.id, bytes)
                        }
                        val rendered = bitmap
                        if (rendered != null) {
                            Image(
                                bitmap = rendered,
                                contentDescription = media.caption.ifBlank { "Memory photo" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                        }
                    }
                    if (media.caption.isNotBlank()) {
                        Text(media.caption, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingMedia = null }) { Text("Close") }
            },
            dismissButton = {
                if (media.raw.uploaderId == myUid) {
                    TextButton(
                        onClick = {
                            onDeleteMedia(media.raw.id)
                            viewingMedia = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
}

@Composable
private fun ChatPhotoSaveChipTile(
    media: DecryptedMedia,
    isSaved: Boolean,
    onSave: () -> Unit
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = ImageBitmapCache.get(media.raw.id),
        key1 = media.raw.id,
        key2 = media.bytes != null
    ) {
        val bytes = media.bytes
        value = if (bytes != null) ImageBitmapCache.getOrDecodeBytes(media.raw.id, bytes) else null
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .width(108.dp)
            .clickable(onClick = onSave)
            .testTag("save_chat_photo_tile_${media.raw.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            val rendered = bitmap
            if (rendered != null) {
                Image(
                    bitmap = rendered,
                    contentDescription = "Chat photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isSaved) "Saved" else "Save",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryPhotoTile(
    media: DecryptedMedia,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = ImageBitmapCache.get(media.raw.id),
        key1 = media.raw.id,
        key2 = media.bytes != null
    ) {
        val bytes = media.bytes
        value = if (bytes != null) ImageBitmapCache.getOrDecodeBytes(media.raw.id, bytes) else null
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val rendered = bitmap
                if (rendered != null) {
                    Image(
                        bitmap = rendered,
                        contentDescription = media.title.ifBlank { "Encrypted photo" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (media.title.isNotBlank() || media.caption.isNotBlank()) {
                Text(
                    text = media.title.ifBlank { media.caption },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun ComposeLoveNoteDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("adore") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Encrypted Love Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LoveNoteMoods) { (key, label) ->
                        FilterChip(
                            selected = selectedMood == key,
                            onClick = { selectedMood = key },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(1500) },
                    label = { Text("Write from your heart...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("love_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(noteText, selectedMood) },
                enabled = noteText.trim().isNotEmpty(),
                modifier = Modifier.testTag("save_love_note_button")
            ) {
                Text("Encrypt & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun UploadMemoryDialog(
    onDismiss: () -> Unit,
    onUpload: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Encrypted Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Memory Title") },
                    placeholder = { Text("Sunset picnic by the lake") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(300) },
                    label = { Text("Private Caption (Encrypted)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onUpload(title.ifBlank { "Shared Moment" }, caption) }) {
                Text("Encrypt & Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

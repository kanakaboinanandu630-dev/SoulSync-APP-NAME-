package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.DecryptedMedia
import com.example.data.DecryptedMessage
import com.example.data.ImageBitmapCache
import com.example.data.MediaAttachmentHelper
import com.example.data.VoiceNotePlayer
import com.example.data.VoiceNoteRecorder
import com.example.ui.ForeverUsSessionState
import com.example.ui.ForeverUsViewModel
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.EncryptedEmerald
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RomanticStickers = listOf(
    "❤️", "🌹", "💍", "✨", "🥰", "😘", "🫶", "💌", "🌙", "🥂", "🧸", "Forever Yours 💕"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrivateChatScreen(
    session: ForeverUsSessionState,
    messages: List<DecryptedMessage>,
    mediaItems: List<DecryptedMedia>,
    savedChatPhotoIds: Set<String>,
    onSendText: (text: String, type: String, replyToId: String?, forwarded: Boolean) -> Unit,
    onSendMedia: (bytes: ByteArray, mediaType: String, mimeType: String, label: String, caption: String?, durationSec: Long?) -> Unit,
    onSavePhotoToMemories: (media: DecryptedMedia, title: String?, caption: String?) -> Unit,
    onMarkRead: (List<DecryptedMessage>) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onSetTyping: (Boolean) -> Unit,
    onSetDisappearingTimer: (Long) -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onGoToPairing: () -> Unit,
    onShowError: (String) -> Unit
) {
    val couple = session.activeCouple
    if (couple == null || couple.status != "connected") {
        LockedChatGate(
            status = couple?.status,
            onGoToPairing = onGoToPairing
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val myUid = session.userId
    val partnerName = couple.partnerNameFor(myUid)
    val partnerIsTyping = !couple.typingUid.isNullOrBlank() && couple.typingUid != myUid
    val mediaById = remember(mediaItems) { mediaItems.associateBy { it.raw.id } }
    val messageById = remember(messages) { messages.associateBy { it.raw.id } }

    var inputText by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<DecryptedMessage?>(null) }
    var selectedMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var previewPhotoMedia by remember { mutableStateOf<DecryptedMedia?>(null) }
    var showStickerTray by remember { mutableStateOf(false) }
    var showTimerMenu by remember { mutableStateOf(false) }
    var showSafetySheet by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var playingMediaId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val voicePlayer = remember(context) { VoiceNotePlayer(context) }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.stopAndDiscard()
            voicePlayer.stop()
            onSetTyping(false)
        }
    }

    // Auto-scroll and mark partner messages read
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
            onMarkRead(messages)
        }
    }

    // Zero-permission Android Photo Picker (Google Play compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaAttachmentHelper.compressPickedImageAsync(context, uri)
                    .onSuccess { payload ->
                        onSendMedia(payload.bytes, "photo", payload.mimeType, payload.displayName, "Encrypted Photo", null)
                    }
                    .onFailure { onShowError(it.localizedMessage ?: "Could not process selected photo.") }
            }
        }
    }

    // Zero-permission SAF File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaAttachmentHelper.readSmallDocumentAsync(context, uri)
                    .onSuccess { payload ->
                        onSendMedia(payload.bytes, "file", payload.mimeType, payload.displayName, payload.displayName, null)
                    }
                    .onFailure { onShowError(it.localizedMessage ?: "File could not be attached.") }
            }
        }
    }

    // Runtime RECORD_AUDIO permission for real voice notes
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.startRecording()
                .onSuccess { isRecordingVoice = true }
                .onFailure { onShowError("Microphone unavailable: ${it.localizedMessage}") }
        } else {
            onShowError("Microphone permission is needed to record encrypted voice notes.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // WhatsApp-style Romantic Encrypted Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { showSafetySheet = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = partnerName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showSafetySheet = true }
                ) {
                    Text(
                        text = partnerName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "End-to-end encrypted",
                            tint = EncryptedEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = when {
                                partnerIsTyping -> "typing..."
                                couple.disappearingSeconds > 0L -> "E2EE • Auto-delete ${ForeverUsViewModel.formatDurationLabel(couple.disappearingSeconds)}"
                                else -> "End-to-end encrypted"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (partnerIsTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showTimerMenu = true },
                        modifier = Modifier.testTag("chat_timer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "Disappearing messages timer",
                            tint = if (couple.disappearingSeconds > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = showTimerMenu, onDismissRequest = { showTimerMenu = false }) {
                        listOf(0L to "Off", 30L to "30 seconds", 300L to "5 minutes", 3600L to "1 hour", 86400L to "24 hours").forEach { (sec, label) ->
                            DropdownMenuItem(
                                text = { Text("Disappearing: $label") },
                                onClick = {
                                    showTimerMenu = false
                                    onSetDisappearingTimer(sec)
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onStartAudioCall,
                    modifier = Modifier.testTag("chat_audio_call_button")
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Start private audio call")
                }

                IconButton(
                    onClick = onStartVideoCall,
                    modifier = Modifier.testTag("chat_video_call_button")
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = "Start private video call")
                }
            }
        }

        // Message Stream
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = EncryptedEmerald,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Messages are end-to-end encrypted",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Only you and $partnerName hold the ECDH session keys to read messages, voice notes, photos, and files sent here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.raw.id }) { msg ->
                    val isMine = msg.raw.senderId == myUid
                    val repliedMsg = msg.raw.replyToId?.let { messageById[it] }
                    val attachedMedia = msg.raw.mediaId?.let { mediaById[it] }

                    ChatMessageBubble(
                        message = msg,
                        isMine = isMine,
                        repliedTo = repliedMsg,
                        media = attachedMedia,
                        isSavedToMemories = attachedMedia != null && attachedMedia.raw.id in savedChatPhotoIds,
                        timeFormat = timeFormat,
                        isPlayingAudio = playingMediaId != null && playingMediaId == msg.raw.mediaId,
                        onPlayVoice = { bytes, mediaId ->
                            if (playingMediaId == mediaId) {
                                voicePlayer.stop()
                                playingMediaId = null
                            } else {
                                voicePlayer.playBytes(bytes) {
                                    playingMediaId = null
                                }.onSuccess {
                                    playingMediaId = mediaId
                                }.onFailure {
                                    onShowError("Unable to play encrypted voice note.")
                                }
                            }
                        },
                        onSavePhotoToMemories = { mediaToSave ->
                            onSavePhotoToMemories(mediaToSave, null, null)
                        },
                        onPreviewPhoto = { mediaToView ->
                            previewPhotoMedia = mediaToView
                        },
                        onLongPress = { selectedMessage = msg }
                    )
                }
            }
        }

        // Sticker / Emoji Quick Tray
        AnimatedVisibility(visible = showStickerTray) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(RomanticStickers) { sticker ->
                        Surface(
                            onClick = {
                                onSendText(sticker, "emoji", replyTarget?.raw?.id, false)
                                replyTarget = null
                                showStickerTray = false
                            },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = sticker,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Reply Preview Bar
        AnimatedVisibility(visible = replyTarget != null) {
            replyTarget?.let { target ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (target.raw.senderId == myUid) "Replying to yourself" else "Replying to $partnerName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = target.plaintext,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { replyTarget = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel reply")
                        }
                    }
                }
            }
        }

        // Bottom Encrypted Composer
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { showStickerTray = !showStickerTray },
                    modifier = Modifier.testTag("chat_emoji_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEmotions,
                        contentDescription = "Romantic emojis and stickers",
                        tint = if (showStickerTray) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("chat_photo_button")
                ) {
                    Icon(Icons.Filled.Image, contentDescription = "Send encrypted photo")
                }

                IconButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.testTag("chat_file_button")
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Send encrypted file")
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        onSetTyping(it.isNotBlank())
                    },
                    placeholder = {
                        Text(
                            if (isRecordingVoice) "Recording encrypted voice..." else "Whisper something...",
                            maxLines = 1
                        )
                    },
                    enabled = !isRecordingVoice,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                if (inputText.isNotBlank()) {
                    Surface(
                        onClick = {
                            val textToSend = inputText
                            inputText = ""
                            onSetTyping(false)
                            onSendText(textToSend, "text", replyTarget?.raw?.id, false)
                            replyTarget = null
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send encrypted message",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = {
                            if (isRecordingVoice) {
                                isRecordingVoice = false
                                voiceRecorder.stopAndReadBytes()
                                    .onSuccess { (bytes, duration) ->
                                        onSendMedia(bytes, "voice", "audio/mp4", "Voice Note (${duration}s)", "Voice Note (${duration}s)", duration)
                                    }
                                    .onFailure { onShowError(it.localizedMessage ?: "Voice recording failed.") }
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    voiceRecorder.startRecording()
                                        .onSuccess { isRecordingVoice = true }
                                        .onFailure { onShowError("Microphone unavailable: ${it.localizedMessage}") }
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        shape = CircleShape,
                        color = if (isRecordingVoice) CallCrimson else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_voice_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isRecordingVoice) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isRecordingVoice) "Stop and send voice note" else "Record voice note",
                                tint = if (isRecordingVoice) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Message Action Dialog (Reply / Save Photo to Memories / Forward / Copy / Delete)
    selectedMessage?.let { msg ->
        val selectedPhotoMedia = if (msg.raw.messageType == "photo") {
            msg.raw.mediaId?.let { mediaById[it] }
        } else null

        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text("Message Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!msg.raw.deleted) {
                        if (selectedPhotoMedia != null && selectedPhotoMedia.bytes != null) {
                            val alreadySaved = selectedPhotoMedia.raw.id in savedChatPhotoIds
                            TextButton(
                                onClick = {
                                    onSavePhotoToMemories(selectedPhotoMedia, null, null)
                                    selectedMessage = null
                                },
                                modifier = Modifier.fillMaxWidth().testTag("message_option_save_memory")
                            ) {
                                Icon(
                                    imageVector = if (alreadySaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (alreadySaved) "Save Copy to Memories" else "Save Photo to Memories",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                replyTarget = msg
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reply", modifier = Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = {
                                onSendText(msg.plaintext, msg.raw.messageType, null, true)
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Forward in Chat", modifier = Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(msg.plaintext))
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy Decrypted Text", modifier = Modifier.weight(1f))
                        }
                    }
                    if (msg.raw.senderId == myUid && !msg.raw.deleted) {
                        TextButton(
                            onClick = {
                                onDeleteMessage(msg.raw.id)
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete for Everyone", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessage = null }) { Text("Close") }
            }
        )
    }

    // Full-screen Chat Photo Preview + Save to Memories
    previewPhotoMedia?.let { photoMedia ->
        val isSaved = photoMedia.raw.id in savedChatPhotoIds
        AlertDialog(
            onDismissRequest = { previewPhotoMedia = null },
            title = { Text("Encrypted Chat Photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    photoMedia.bytes?.let { bytes ->
                        val bitmap by produceState<ImageBitmap?>(
                            initialValue = ImageBitmapCache.get(photoMedia.raw.id),
                            key1 = photoMedia.raw.id
                        ) {
                            value = ImageBitmapCache.getOrDecodeBytes(photoMedia.raw.id, bytes)
                        }
                        val rendered = bitmap
                        if (rendered != null) {
                            Image(
                                bitmap = rendered,
                                contentDescription = "Decrypted chat photo preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                        }
                    }
                    Text(
                        text = if (isSaved) "Saved to your encrypted Memories vault." else "Save this chat photo to your permanent Memories gallery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePhotoToMemories(photoMedia, null, null)
                        previewPhotoMedia = null
                    },
                    modifier = Modifier.testTag("chat_preview_save_memory_button")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSaved) "Saved in Memories" else "Save to Memories")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewPhotoMedia = null }) { Text("Close") }
            }
        )
    }

    if (showSafetySheet) {
        SafetyNumberDialog(
            myFingerprint = session.myKeyFingerprint,
            safetyNumber = session.safetyNumber,
            isConnected = true,
            onDismiss = { showSafetySheet = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubble(
    message: DecryptedMessage,
    isMine: Boolean,
    repliedTo: DecryptedMessage?,
    media: DecryptedMedia?,
    isSavedToMemories: Boolean,
    timeFormat: SimpleDateFormat,
    isPlayingAudio: Boolean,
    onPlayVoice: (ByteArray, String) -> Unit,
    onSavePhotoToMemories: (DecryptedMedia) -> Unit,
    onPreviewPhoto: (DecryptedMedia) -> Unit,
    onLongPress: () -> Unit
) {
    val timeLabel = remember(message.raw.createdAt) {
        val date = message.raw.createdAt?.toDate() ?: Date()
        timeFormat.format(date)
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )
    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier
                .widthIn(min = 96.dp, max = 300.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (message.raw.forwarded && !message.raw.deleted) {
                    Text(
                        text = "Forwarded",
                        style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (repliedTo != null && !message.raw.deleted) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = repliedTo.plaintext,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                // Encrypted Photo Rendering (decoded off-main with LRU cache + Save to Memories button)
                if (!message.raw.deleted && message.raw.messageType == "photo" && media?.bytes != null) {
                    val bitmap by produceState<ImageBitmap?>(
                        initialValue = ImageBitmapCache.get(media.raw.id),
                        key1 = media.raw.id
                    ) {
                        value = ImageBitmapCache.getOrDecodeBytes(media.raw.id, media.bytes)
                    }
                    val rendered = bitmap
                    if (rendered != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPreviewPhoto(media) }
                        ) {
                            Image(
                                bitmap = rendered,
                                contentDescription = "Decrypted photo attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                onClick = { if (!isSavedToMemories) onSavePhotoToMemories(media) },
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .testTag("chat_photo_save_memory_${media.raw.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSavedToMemories) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = if (isSavedToMemories) "Saved to Memories" else "Save to Memories",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (isSavedToMemories) "Saved" else "Save Memory",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Encrypted Voice Note Player
                if (!message.raw.deleted && message.raw.messageType == "voice" && media?.bytes != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                            .clickable { onPlayVoice(media.bytes, media.raw.id) }
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlayingAudio) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = "Play encrypted voice note",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Voice (${media.raw.durationSec ?: 1}s)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Encrypted File Pill
                if (!message.raw.deleted && message.raw.messageType == "file") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.InsertDriveFile,
                            contentDescription = "Encrypted file",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = message.plaintext,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Text(
                        text = message.plaintext,
                        style = if (message.raw.messageType == "emoji") MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.bodyLarge.copy(
                            fontStyle = if (message.raw.deleted) FontStyle.Italic else FontStyle.Normal
                        ),
                        color = if (message.raw.deleted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isMine && !message.raw.deleted) {
                        val isRead = message.raw.deliveryStatus == "read"
                        Icon(
                            imageVector = if (isRead || message.raw.deliveryStatus == "delivered") Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = if (isRead) "Read by partner" else "Sent",
                            tint = if (isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedChatGate(
    status: String?,
    onGoToPairing: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Private chat locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                text = "Private Chat Locked",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = when (status) {
                    "pending_approval" -> "Your partner connection is waiting for final dual approval. Once both partners approve, your ECDH encrypted chat unlocks automatically."
                    "pending_partner" -> "Share your private invitation code from the Sanctuary tab so your partner can connect."
                    else -> "SoulSync has zero public rooms. Connect and mutually approve your one partner to open your end-to-end encrypted chat."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onGoToPairing,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("locked_chat_go_pairing_button")
            ) {
                Text("Go to Partner Pairing")
            }
        }
    }
}

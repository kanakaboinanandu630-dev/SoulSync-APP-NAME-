package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Couple
import com.example.data.CoupleNotification
import com.example.data.DecryptedLoveNote
import com.example.data.DecryptedMedia
import com.example.data.ImageBitmapCache
import com.example.ui.ForeverUsSessionState
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DancingScriptFamily
import com.example.ui.theme.EncryptedEmerald
import com.example.ui.theme.HeartCoralPink
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PacificoFamily
import com.example.ui.theme.PolaroidBlushEnd
import com.example.ui.theme.PolaroidSkyMid
import com.example.ui.theme.PolaroidSkyStart
import com.example.ui.theme.SparkleGold
import com.example.ui.theme.TileLavenderBg
import com.example.ui.theme.TileLavenderFg
import com.example.ui.theme.TileMintBg
import com.example.ui.theme.TileMintFg
import com.example.ui.theme.TilePeachBg
import com.example.ui.theme.TilePeachFg
import com.example.ui.theme.TilePinkBg
import com.example.ui.theme.TilePinkFg
import com.example.ui.theme.TileRoseBg
import com.example.ui.theme.TileRoseFg
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeSanctuaryScreen(
    session: ForeverUsSessionState,
    loveNotes: List<DecryptedLoveNote>,
    mediaItems: List<DecryptedMedia>,
    notifications: List<CoupleNotification>,
    isBusy: Boolean,
    onCreateInvite: () -> Unit,
    onJoinInvite: (String) -> Unit,
    onApproveConnection: () -> Unit,
    onDisconnect: () -> Unit,
    onSaveMilestones: (Long?, String?) -> Unit,
    onOpenChat: () -> Unit,
    onOpenCalls: () -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onOpenMemories: () -> Unit,
    onMarkNotificationRead: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val couple = session.activeCouple
    val myUid = session.userId
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f

    var inviteInput by remember { mutableStateOf("") }
    var showMilestoneDialog by remember { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showAlertsDialog by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf(false) }

    val savedMemories = remember(mediaItems) {
        mediaItems.filter { it.raw.mediaType == "memory" }
    }
    val unreadCount = remember(notifications) {
        notifications.count { !it.read }
    }
    val myAvatarKey = session.profile?.avatarData?.let { "avatar_${it.hashCode()}" }
    val myAvatarBitmap by produceState<ImageBitmap?>(
        initialValue = myAvatarKey?.let { ImageBitmapCache.get(it) },
        key1 = myAvatarKey
    ) {
        val b64 = session.profile?.avatarData
        value = if (!b64.isNullOrBlank() && myAvatarKey != null) {
            ImageBitmapCache.getOrDecodeBase64(myAvatarKey, b64)
        } else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Clean Airy Top Header Bar (Key/E2EE | Script SoulSync ♡ | Alerts Bell)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showSafetyDialog = true },
                    modifier = Modifier.testTag("safety_number_button")
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "View E2EE encryption keys",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(if (session.isE2eeReady) EncryptedEmerald else SparkleGold)
                                .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = PacificoFamily,
                            fontSize = 27.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(25.dp)
                    )
                }

                IconButton(
                    onClick = { showAlertsDialog = true },
                    modifier = Modifier.testTag("home_notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge(
                                    containerColor = HeartCoralPink,
                                    contentColor = Color.White
                                ) {
                                    Text(unreadCount.coerceAtMost(9).toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Sanctuary alerts",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // 2. Hero Couple Card (Connected) OR Pairing Hub Card
        item {
            if (couple?.status == "connected") {
                ConnectedCoupleHeroCard(
                    couple = couple,
                    myUid = myUid,
                    myName = session.profile?.username?.ifBlank { "You" } ?: "You",
                    myAvatar = myAvatarBitmap,
                    isE2eeReady = session.isE2eeReady,
                    onEditMilestones = { showMilestoneDialog = true }
                )
            } else {
                PairingHubCard(
                    couple = couple,
                    myUid = myUid,
                    myName = session.profile?.username?.ifBlank { "You" } ?: "You",
                    inviteInput = inviteInput,
                    onInviteInputChange = { inviteInput = it.uppercase().take(16) },
                    isBusy = isBusy,
                    copiedNotice = copiedNotice,
                    onCreateInvite = onCreateInvite,
                    onCopyCode = { code ->
                        clipboard.setText(AnnotatedString(code))
                        copiedNotice = true
                    },
                    onShareCode = { code ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join me in our private SoulSync sanctuary! Use my secret invite code: $code"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share SoulSync Invite Code"))
                    },
                    onJoinWithCode = { onJoinInvite(inviteInput) },
                    onApprove = onApproveConnection,
                    onCancelPending = onDisconnect
                )
            }
        }

        // 3. 5 Pastel Rounded Quick-Action Tiles (Chat | Memories | Dates | Notes | Calls)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PastelFeatureTile(
                    modifier = Modifier.weight(1f).testTag("home_open_chat_button"),
                    icon = Icons.Filled.ChatBubble,
                    label = "Chat",
                    tileBg = if (isDark) TilePinkFg.copy(alpha = 0.22f) else TilePinkBg,
                    iconTint = TilePinkFg,
                    onClick = onOpenChat
                )
                PastelFeatureTile(
                    modifier = Modifier.weight(1f).testTag("home_memories_button"),
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Memories",
                    tileBg = if (isDark) TileLavenderFg.copy(alpha = 0.22f) else TileLavenderBg,
                    iconTint = TileLavenderFg,
                    onClick = onOpenMemories
                )
                PastelFeatureTile(
                    modifier = Modifier.weight(1f).testTag("edit_milestones_button"),
                    icon = Icons.Filled.DateRange,
                    label = "Dates",
                    tileBg = if (isDark) TileRoseFg.copy(alpha = 0.22f) else TileRoseBg,
                    iconTint = TileRoseFg,
                    onClick = { showMilestoneDialog = true }
                )
                PastelFeatureTile(
                    modifier = Modifier.weight(1f).testTag("home_notes_button"),
                    icon = Icons.Filled.EditNote,
                    label = "Notes",
                    tileBg = if (isDark) TileMintFg.copy(alpha = 0.22f) else TileMintBg,
                    iconTint = TileMintFg,
                    onClick = onOpenMemories
                )
                PastelFeatureTile(
                    modifier = Modifier.weight(1f).testTag("home_calls_button"),
                    icon = Icons.Filled.Call,
                    label = "Calls",
                    tileBg = if (isDark) TilePeachFg.copy(alpha = 0.22f) else TilePeachBg,
                    iconTint = TilePeachFg,
                    onClick = onOpenCalls
                )
            }
        }

        // 4. "✨ Today's Moment" Card (Latest Encrypted Love Note + Polaroid Memory Thumbnail)
        item {
            TodaysMomentCard(
                latestNote = loveNotes.firstOrNull(),
                latestMemory = savedMemories.firstOrNull(),
                isConnected = couple?.status == "connected",
                onOpenMemories = onOpenMemories
            )
        }

        // 5. "Our Memories" Horizontal Photo Strip (Saved Encrypted Chat Photos)
        if (couple?.status == "connected") {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Our Memories",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenMemories)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "See All",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (savedMemories.isEmpty()) {
                        Card(
                            onClick = onOpenChat,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), MaterialTheme.shapes.large)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Save Your Favorite Chat Photos",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Photos saved from your private chat appear here forever.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(savedMemories.take(10), key = { it.raw.id }) { memory ->
                                HomeMemoryPosterCard(
                                    media = memory,
                                    onClick = onOpenMemories
                                )
                            }
                        }
                    }
                }
            }

            // 6. Quick Private Calls Strip (Audio + Video)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = EncryptedEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Quick Private Calls",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = onStartAudioCall,
                                color = if (isDark) TileMintFg.copy(alpha = 0.2f) else TileMintBg,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_audio_call_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Call,
                                        contentDescription = "Start voice call",
                                        tint = TileMintFg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Voice Call",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Surface(
                                onClick = onStartVideoCall,
                                color = if (isDark) TileLavenderFg.copy(alpha = 0.2f) else TileLavenderBg,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_video_call_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Videocam,
                                        contentDescription = "Start video call",
                                        tint = TileLavenderFg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Video Call",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Recent Sanctuary Alerts Card (if notifications exist)
        if (notifications.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), MaterialTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Sanctuary Alerts",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        notifications.take(3).forEach { notif ->
                            Surface(
                                onClick = { if (!notif.read) onMarkNotificationRead(notif.id) },
                                color = if (notif.read) MaterialTheme.colorScheme.surfaceContainerLow
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = notif.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!notif.read) {
                                            Text(
                                                text = "NEW",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HeartCoralPink
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = notif.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Developer Attribution Pill
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .testTag("home_developer_credit")
                ) {
                    Text(
                        text = stringResource(R.string.developed_by_credit),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (showMilestoneDialog) {
        MilestonesDialog(
            initialAnniversaryMs = couple?.anniversaryEpochMs,
            initialBirthday = session.profile?.birthday.orEmpty(),
            onDismiss = { showMilestoneDialog = false },
            onSave = { annivMs, bday ->
                onSaveMilestones(annivMs, bday)
                showMilestoneDialog = false
            }
        )
    }

    if (showSafetyDialog) {
        SafetyNumberDialog(
            myFingerprint = session.myKeyFingerprint,
            safetyNumber = session.safetyNumber,
            isConnected = couple?.status == "connected",
            onDismiss = { showSafetyDialog = false }
        )
    }

    if (showAlertsDialog) {
        AlertDialog(
            onDismissRequest = { showAlertsDialog = false },
            title = { Text("Sanctuary Alerts") },
            text = {
                if (notifications.isEmpty()) {
                    Text(
                        text = "No new alerts. Invitations, approvals, calls, and love notes appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notifications.take(6).forEach { notif ->
                            Surface(
                                onClick = { if (!notif.read) onMarkNotificationRead(notif.id) },
                                color = if (notif.read) MaterialTheme.colorScheme.surfaceContainerLow
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(notif.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        notif.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlertsDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ConnectedCoupleHeroCard(
    couple: Couple,
    myUid: String,
    myName: String,
    myAvatar: ImageBitmap?,
    isE2eeReady: Boolean,
    onEditMilestones: () -> Unit
) {
    val partnerName = couple.partnerNameFor(myUid)
    val annivMs = couple.anniversaryEpochMs

    Card(
        onClick = onEditMilestones,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dual Overlapping Circular Avatars with Floating Heart Badge
                Box(
                    modifier = Modifier.width(114.dp).height(62.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // My Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (myAvatar != null) {
                            Image(
                                bitmap = myAvatar,
                                contentDescription = myName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = myName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Partner Avatar Circle
                    Box(
                        modifier = Modifier
                            .offset(x = 52.dp)
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = partnerName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = HeartCoralPink
                        )
                    }

                    // Centered Floating Coral-Pink Heart Badge
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.Center)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = HeartCoralPink,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Right: Better Together + Days Counter
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Better Together",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (annivMs != null && annivMs > 0L) {
                        val days = TimeUnit.MILLISECONDS.toDays((System.currentTimeMillis() - annivMs).coerceAtLeast(0L))
                        Text(
                            text = "$days Days",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Since We Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "$myName & $partnerName",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tap to set anniversary date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Edit milestones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Live ticking HRS / MIN / SEC bar (isolated leaf composable!)
            if (annivMs != null && annivMs > 0L) {
                LiveCountdownTicker(annivMs = annivMs)
            }

            // Subtle E2EE + Partner Birthday Footer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = EncryptedEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isE2eeReady) "E2EE Sanctuary Active" else "Keystore Ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val partnerBday = couple.partnerBirthdayFor(myUid)
                if (!partnerBday.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cake,
                            contentDescription = null,
                            tint = HeartCoralPink,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = partnerBday,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCountdownTicker(annivMs: Long) {
    var nowMs by remember(annivMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(annivMs) {
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val elapsedMs = (nowMs - annivMs).coerceAtLeast(0L)
    val days = TimeUnit.MILLISECONDS.toDays(elapsedMs)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60
    val nextDays = remember(annivMs, days) { daysUntilNextOccurrence(annivMs, nowMs) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CountdownUnitBox(modifier = Modifier.weight(1f), value = days.toString(), label = "DAYS")
            CountdownUnitBox(modifier = Modifier.weight(1f), value = hours.toString().padStart(2, '0'), label = "HRS")
            CountdownUnitBox(modifier = Modifier.weight(1f), value = minutes.toString().padStart(2, '0'), label = "MIN")
            CountdownUnitBox(modifier = Modifier.weight(1f), value = seconds.toString().padStart(2, '0'), label = "SEC")
        }
        Text(
            text = "Next anniversary in $nextDays days",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CountdownUnitBox(modifier: Modifier, value: String, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PastelFeatureTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    tileBg: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = tileBg,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .border(1.dp, iconTint.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 13.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TodaysMomentCard(
    latestNote: DecryptedLoveNote?,
    latestMemory: DecryptedMedia?,
    isConnected: Boolean,
    onOpenMemories: () -> Unit
) {
    Card(
        onClick = onOpenMemories,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), MaterialTheme.shapes.large)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = SparkleGold,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "Today's Moment",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (isConnected) "Open" else "Locked",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (latestNote != null) "\"${latestNote.text}\"" else "\"Good things are better together ❤️\"",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = DancingScriptFamily,
                            fontSize = 23.sp,
                            lineHeight = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (latestNote != null) {
                            "Encrypted Note • ${latestNote.raw.mood.replace("_", " ").replaceFirstChar { it.uppercase() }}"
                        } else {
                            "Tap to write your first encrypted love note"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Slightly tilted polaroid memory preview
                val polaroidBitmap by produceState<ImageBitmap?>(
                    initialValue = latestMemory?.raw?.id?.let { ImageBitmapCache.get(it) },
                    key1 = latestMemory?.raw?.id,
                    key2 = latestMemory?.bytes != null
                ) {
                    val m = latestMemory
                    value = if (m != null && m.bytes != null) {
                        ImageBitmapCache.getOrDecodeBytes(m.raw.id, m.bytes)
                    } else null
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .width(104.dp)
                        .height(80.dp)
                        .rotate(3.5f)
                        .border(2.dp, Color.White, RoundedCornerShape(14.dp))
                ) {
                    val bmp = polaroidBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Latest saved memory",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(PolaroidSkyStart, PolaroidSkyMid, PolaroidBlushEnd)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = HeartCoralPink,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMemoryPosterCard(
    media: DecryptedMedia,
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
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(102.dp)
            .height(134.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val rendered = bitmap
            if (rendered != null) {
                Image(
                    bitmap = rendered,
                    contentDescription = media.title.ifBlank { "Saved memory photo" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PairingHubCard(
    couple: Couple?,
    myUid: String,
    myName: String,
    inviteInput: String,
    onInviteInputChange: (String) -> Unit,
    isBusy: Boolean,
    copiedNotice: Boolean,
    onCreateInvite: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit,
    onJoinWithCode: () -> Unit,
    onApprove: () -> Unit,
    onCancelPending: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (couple?.status) {
                "pending_partner" -> {
                    Text(
                        text = "Your Private Invitation Code",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Share this one-time code with your partner. Once they enter it, you will receive a dual-approval prompt to unlock your encrypted sanctuary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = couple.inviteCode,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                letterSpacing = TextUnit(4f, TextUnitType.Sp)
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onCopyCode(couple.inviteCode) },
                            modifier = Modifier.weight(1f).testTag("copy_invite_code_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedNotice) "Copied" else "Copy")
                        }
                        Button(
                            onClick = { onShareCode(couple.inviteCode) },
                            modifier = Modifier.weight(1f).testTag("share_invite_code_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                    TextButton(
                        onClick = onCancelPending,
                        enabled = !isBusy,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Revoke Invitation", color = MaterialTheme.colorScheme.error)
                    }
                }

                "pending_approval" -> {
                    val isCreator = couple.partnerAId == myUid
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = ChampagneGold)
                        Text(
                            text = if (isCreator) "Approve Partner Connection" else "Awaiting Partner Approval",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    if (isCreator) {
                        Text(
                            text = "${couple.partnerBName ?: "Your partner"} redeemed your code and exchanged their ECDH public key. Approve below to unlock your encrypted sanctuary.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancelPending,
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).testTag("decline_partner_button")
                            ) {
                                Text("Decline")
                            }
                            Button(
                                onClick = onApprove,
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).testTag("approve_partner_button")
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve")
                            }
                        }
                    } else {
                        Text(
                            text = "You approved joining ${couple.partnerAName}'s sanctuary. Waiting for ${couple.partnerAName} to finalize dual approval.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onCancelPending,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel Join Request")
                        }
                    }
                }

                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfinityHeartEmblem(modifier = Modifier.size(width = 58.dp, height = 40.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Welcome, $myName",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Connect with your one special partner to unlock encrypted chat, calls & memories.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onCreateInvite,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_invite_button")
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Private Invite Code")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Text("OR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }

                    OutlinedTextField(
                        value = inviteInput,
                        onValueChange = onInviteInputChange,
                        label = { Text("Enter Partner's Invite Code") },
                        placeholder = { Text("e.g. ROSE8821") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enter_invite_code_field")
                    )

                    FilledTonalButton(
                        onClick = onJoinWithCode,
                        enabled = !isBusy && inviteInput.trim().length >= 6,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("join_invite_button")
                    ) {
                        Text("Request Private Connection")
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestonesDialog(
    initialAnniversaryMs: Long?,
    initialBirthday: String,
    onDismiss: () -> Unit,
    onSave: (Long?, String?) -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    var anniversaryText by remember {
        mutableStateOf(
            if (initialAnniversaryMs != null && initialAnniversaryMs > 0L) sdf.format(Date(initialAnniversaryMs))
            else ""
        )
    }
    var birthdayText by remember { mutableStateOf(initialBirthday) }
    var parseError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Special Dates & Milestones") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = anniversaryText,
                    onValueChange = {
                        anniversaryText = it
                        parseError = null
                    },
                    label = { Text("Anniversary Date (YYYY-MM-DD)") },
                    placeholder = { Text("2024-02-14") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = birthdayText,
                    onValueChange = { birthdayText = it },
                    label = { Text("Your Birthday (YYYY-MM-DD)") },
                    placeholder = { Text("1999-08-22") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (parseError != null) {
                    Text(parseError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedAnniv = anniversaryText.trim()
                    val parsedMs = if (trimmedAnniv.isEmpty()) {
                        null
                    } else {
                        runCatching { sdf.parse(trimmedAnniv)?.time }.getOrNull() ?: run {
                            parseError = "Use format YYYY-MM-DD for anniversary date."
                            return@Button
                        }
                    }
                    onSave(parsedMs, birthdayText.trim().ifBlank { null })
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SafetyNumberDialog(
    myFingerprint: String,
    safetyNumber: String?,
    isConnected: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End-to-End Encryption Keys") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Protocol: ECDH P-256 + HKDF-SHA256 + AES-256-GCM (Android Keystore Hardware Master Key).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Your Identity Key Fingerprint", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = myFingerprint.ifBlank { "Generating..." },
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                        )
                    }
                }
                if (isConnected && !safetyNumber.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("60-Digit Couple Safety Number", style = MaterialTheme.typography.labelSmall, color = EncryptedEmerald)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = safetyNumber,
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFamily),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Compare these digits with your partner in person to confirm zero interception.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

private fun daysUntilNextOccurrence(anniversaryMs: Long, nowMs: Long): Long {
    val annivCal = Calendar.getInstance().apply { timeInMillis = anniversaryMs }
    val targetCal = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.MONTH, annivCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, annivCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (targetCal.timeInMillis < nowMs) {
        targetCal.add(Calendar.YEAR, 1)
    }
    val diff = (targetCal.timeInMillis - nowMs).coerceAtLeast(0L)
    return TimeUnit.MILLISECONDS.toDays(diff) + 1L
}

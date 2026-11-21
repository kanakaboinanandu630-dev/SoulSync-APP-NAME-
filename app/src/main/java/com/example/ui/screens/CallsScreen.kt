package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.CallSession
import com.example.ui.ForeverUsSessionState
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.EncryptedEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallsScreen(
    session: ForeverUsSessionState,
    calls: List<CallSession>,
    onStartCall: (String) -> Unit,
    onAnswerCall: (CallSession) -> Unit,
    onEndOrDeclineCall: (String, String) -> Unit,
    onToggleMute: (CallSession) -> Unit,
    onGoToPairing: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
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
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Calls locked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Text("Private Calls Locked", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "End-to-end encrypted audio & video calls unlock once both partners approve the SoulSync connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onGoToPairing) {
                    Text("Go to Sanctuary Pairing")
                }
            }
        }
        return
    }

    var pendingCallType by remember { mutableStateOf<String?>(null) }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micOk = results[Manifest.permission.RECORD_AUDIO] == true
        val type = pendingCallType
        pendingCallType = null
        if (type != null) {
            if (micOk) {
                onStartCall(type)
            } else {
                onShowMessage("Microphone permission is required for private calls.")
            }
        }
    }

    fun requestAndStartCall(type: String) {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (type == "video" && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.CAMERA)
            }
        }
        if (needed.isEmpty()) {
            onStartCall(type)
        } else {
            pendingCallType = type
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    val activeCall = calls.firstOrNull { it.status == "ringing" || it.status == "active" }
    val partnerName = couple.partnerNameFor(myUid)

    if (activeCall != null) {
        ActiveCallOverlay(
            call = activeCall,
            myUid = myUid,
            partnerName = partnerName,
            onAnswer = { onAnswerCall(activeCall) },
            onEndOrDecline = {
                val terminal = if (activeCall.status == "ringing" && activeCall.calleeId == myUid) "declined" else "ended"
                onEndOrDeclineCall(activeCall.id, terminal)
            },
            onToggleMute = { onToggleMute(activeCall) }
        )
        return
    }

    val callDateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Private Calls", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Peer-to-peer WebRTC signaling secured exclusively for you and $partnerName.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    onClick = { requestAndStartCall("audio") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_audio_call_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Call, contentDescription = "Audio call", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("Audio Call", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "DTLS-SRTP Voice",
                            style = MaterialTheme.typography.labelSmall,
                            color = EncryptedEmerald
                        )
                    }
                }

                Card(
                    onClick = { requestAndStartCall("video") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_video_call_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Videocam, contentDescription = "Video call", tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Text("Video Call", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Encrypted HD",
                            style = MaterialTheme.typography.labelSmall,
                            color = EncryptedEmerald
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Recent Call History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (calls.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No call history yet. Tap Audio Call or Video Call above to ring $partnerName.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(calls, key = { it.id }) { call ->
                val outgoing = call.callerId == myUid
                val dateLabel = callDateFormat.format(call.createdAt?.toDate() ?: Date())
                val durationLabel = if (call.startedAtEpochMs != null && call.endedAtEpochMs != null) {
                    val secs = ((call.endedAtEpochMs - call.startedAtEpochMs) / 1000L).coerceAtLeast(0L)
                    "${secs / 60}m ${secs % 60}s"
                } else {
                    call.status.replaceFirstChar { it.uppercase() }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (outgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                            contentDescription = if (outgoing) "Outgoing" else "Incoming",
                            tint = if (call.status == "declined" || call.status == "missed") CallCrimson else EncryptedEmerald
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (call.callType == "video") "Video" else "Audio"} Call with $partnerName",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "$dateLabel • $durationLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (call.callType == "video") Icons.Filled.Videocam else Icons.Filled.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveCallOverlay(
    call: CallSession,
    myUid: String,
    partnerName: String,
    onAnswer: () -> Unit,
    onEndOrDecline: () -> Unit,
    onToggleMute: () -> Unit
) {
    val context = LocalContext.current
    val isIncoming = call.calleeId == myUid && call.status == "ringing"
    val isMuted = if (call.callerId == myUid) call.mutedByCaller else call.mutedByCallee
    var speakerOn by remember { mutableStateOf(call.callType == "video") }
    var cameraEnabled by remember { mutableStateOf(call.callType == "video") }

    var elapsedSeconds by remember(call.id, call.status) { mutableLongStateOf(0L) }
    LaunchedEffect(call.id, call.status) {
        if (call.status == "active") {
            while (true) {
                val start = call.startedAtEpochMs ?: System.currentTimeMillis()
                elapsedSeconds = ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    val pulse = rememberInfiniteTransition(label = "callPulse").animateFloat(
        initialValue = 1.0f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = EncryptedEmerald, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (call.status == "active") "WebRTC DTLS-SRTP Connected" else "WebRTC SDP Offer Sent",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(142.dp)
                            .scale(pulse.value)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(104.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = partnerName.take(1).uppercase(),
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(22.dp))
                Text(partnerName, style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(6.dp))
                val statusLabel = when {
                    isIncoming -> "Incoming ${call.callType} call..."
                    call.status == "ringing" -> "Ringing partner..."
                    else -> "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Call Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(bottom = 18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(58.dp)
                            .testTag("call_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone",
                            tint = if (isMuted) CallCrimson else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            speakerOn = !speakerOn
                            runCatching {
                                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                @Suppress("DEPRECATION")
                                audio?.isSpeakerphoneOn = speakerOn
                            }
                        },
                        modifier = Modifier
                            .size(58.dp)
                            .testTag("call_speaker_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Toggle speakerphone",
                            tint = if (speakerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (call.callType == "video") {
                        FilledTonalIconButton(
                            onClick = { cameraEnabled = !cameraEnabled },
                            modifier = Modifier
                                .size(58.dp)
                                .testTag("call_camera_button")
                        ) {
                            Icon(
                                imageVector = if (cameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                contentDescription = "Toggle camera"
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    if (isIncoming) {
                        Button(
                            onClick = onAnswer,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = EncryptedEmerald, contentColor = Color.Black),
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("call_answer_button")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Answer call", modifier = Modifier.size(28.dp))
                        }
                    }
                    Button(
                        onClick = onEndOrDecline,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = CallCrimson, contentColor = Color.White),
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("call_end_button")
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "End call", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

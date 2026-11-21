package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoVault
import com.example.crypto.E2EECryptoEngine
import com.example.crypto.IdentityKeyMaterial
import com.example.crypto.LocalSecurityPreferences
import com.example.data.AbuseReport
import com.example.data.CallSession
import com.example.data.Couple
import com.example.data.CoupleNotification
import com.example.data.DecryptedLoveNote
import com.example.data.DecryptedMedia
import com.example.data.DecryptedMessage
import com.example.data.ForeverUsRepository
import com.example.data.UiState
import com.example.data.UserPrivateAccount
import com.example.data.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

data class ForeverUsSessionState(
    val userId: String = "",
    val profile: UserProfile? = null,
    val privateAccount: UserPrivateAccount? = null,
    val activeCouple: Couple? = null,
    val identityPublicKey: String = "",
    val myKeyFingerprint: String = "",
    val safetyNumber: String? = null,
    val isE2eeReady: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ForeverUsViewModel(
    private val repository: ForeverUsRepository,
    private val cryptoVault: CryptoVault,
    val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String?
) : ViewModel() {

    private val _identityKeys = MutableStateFlow<IdentityKeyMaterial?>(null)
    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _isActionBusy = MutableStateFlow(false)
    val isActionBusy: StateFlow<Boolean> = _isActionBusy.asStateFlow()

    private val _exportedJson = MutableStateFlow<String?>(null)
    val exportedJson: StateFlow<String?> = _exportedJson.asStateFlow()

    private val _savedChatPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    val savedChatPhotoIds: StateFlow<Set<String>> = _savedChatPhotoIds.asStateFlow()

    // In-memory decryption caches so already-decrypted messages/media/notes skip redundant AES-GCM work
    private val messagePlaintextCache = ConcurrentHashMap<String, Pair<String, Boolean>>()
    private val mediaPayloadCache = ConcurrentHashMap<String, DecryptedMedia>()
    private val loveNoteTextCache = ConcurrentHashMap<String, Pair<String, Boolean>>()

    // Deduplicate read-receipt writes and throttle typing indicator writes
    private val markedReadIds: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private var lastTypingSentAtMs: Long = 0L
    private var currentlyTypingSent: Boolean = false
    private var typingClearJob: Job? = null

    val localPreferences: StateFlow<LocalSecurityPreferences> =
        cryptoVault.observeLocalSecurityPreferences()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = LocalSecurityPreferences()
            )

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "profile") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val privateAccountState: StateFlow<UiState<UserPrivateAccount?>> =
        repository.observePrivateAccount(currentUserId)
            .map<UserPrivateAccount?, UiState<UserPrivateAccount?>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "private account") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val couplesState: StateFlow<UiState<List<Couple>>> =
        repository.observeUserCouples(currentUserId)
            .map<List<Couple>, UiState<List<Couple>>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "couples") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val notificationsState: StateFlow<UiState<List<CoupleNotification>>> =
        repository.observeNotifications(currentUserId)
            .map<List<CoupleNotification>, UiState<List<CoupleNotification>>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "notifications") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val reportsState: StateFlow<UiState<List<AbuseReport>>> =
        repository.observeMyReports(currentUserId)
            .map<List<AbuseReport>, UiState<List<AbuseReport>>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "reports") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val activeCouple: StateFlow<Couple?> = couplesState
        .map { state ->
            val list = (state as? UiState.Success)?.data ?: emptyList()
            list.firstOrNull { it.status == "connected" }
                ?: list.firstOrNull { it.status == "pending_approval" }
                ?: list.firstOrNull { it.status == "pending_partner" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val sharedSessionKey: StateFlow<SecretKey?> = combine(activeCouple, _identityKeys) { couple, keys ->
        if (couple == null || keys == null || couple.status != "connected") return@combine null
        val partnerPubKey = couple.partnerPublicKeyFor(currentUserId) ?: return@combine null
        runCatching {
            E2EECryptoEngine.deriveSharedSessionKey(keys.privateKey, partnerPubKey, couple.id)
        }.onFailure {
            Log.e(TAG, "Failed deriving ECDH session key", it)
        }.getOrNull()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val sessionState: StateFlow<ForeverUsSessionState> = combine(
        profileState,
        privateAccountState,
        activeCouple,
        _identityKeys,
        sharedSessionKey
    ) { profileUi, privUi, couple, keys, sessionKey ->
        val profile = (profileUi as? UiState.Success)?.data
        val priv = (privUi as? UiState.Success)?.data
        val myPub = keys?.publicKeyBase64 ?: profile?.publicKeyBase64.orEmpty()
        val partnerPub = couple?.partnerPublicKeyFor(currentUserId)
        val safety = if (myPub.isNotBlank() && !partnerPub.isNullOrBlank()) {
            runCatching { E2EECryptoEngine.computeSafetyNumber(myPub, partnerPub) }.getOrNull()
        } else {
            null
        }
        ForeverUsSessionState(
            userId = currentUserId,
            profile = profile,
            privateAccount = priv,
            activeCouple = couple,
            identityPublicKey = myPub,
            myKeyFingerprint = if (myPub.isNotBlank()) E2EECryptoEngine.shortFingerprint(myPub) else "",
            safetyNumber = safety,
            isE2eeReady = sessionKey != null
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ForeverUsSessionState(userId = currentUserId)
        )

    val messagesState: StateFlow<UiState<List<DecryptedMessage>>> = activeCouple
        .flatMapLatest { couple ->
            if (couple == null || couple.status != "connected") {
                flowOf(UiState.Success(emptyList()))
            } else {
                combine(
                    repository.observeEncryptedMessages(couple.id, currentUserId),
                    sharedSessionKey
                ) { encryptedList, key ->
                    val now = System.currentTimeMillis()
                    val keyTag = if (key == null) 0 else System.identityHashCode(key)
                    val visible = encryptedList.filter { msg ->
                        val exp = msg.expiresAtEpochMs
                        exp == null || exp > now
                    }
                    val decrypted = visible.map { msg ->
                        if (msg.deleted) {
                            DecryptedMessage(msg, "This message was deleted", true)
                        } else if (key == null) {
                            DecryptedMessage(msg, "Encrypted message (awaiting key exchange)", false)
                        } else {
                            val cacheKey = "$keyTag:${msg.id}:${msg.ciphertextBase64.hashCode()}"
                            val cached = messagePlaintextCache[cacheKey]
                            if (cached != null) {
                                DecryptedMessage(msg, cached.first, cached.second)
                            } else {
                                val aad = "${msg.coupleId}|${msg.senderId}"
                                val result = runCatching {
                                    E2EECryptoEngine.decryptText(msg.ciphertextBase64, msg.ivBase64, key, aad) to true
                                }.getOrElse {
                                    "Unable to decrypt with current device key" to false
                                }
                                if (result.second) {
                                    messagePlaintextCache[cacheKey] = result
                                }
                                DecryptedMessage(msg, result.first, result.second)
                            }
                        }
                    }
                    UiState.Success(decrypted) as UiState<List<DecryptedMessage>>
                }.catch { error -> emitStreamError(error, "messages") }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val mediaState: StateFlow<UiState<List<DecryptedMedia>>> = activeCouple
        .flatMapLatest { couple ->
            if (couple == null || couple.status != "connected") {
                flowOf(UiState.Success(emptyList()))
            } else {
                combine(
                    repository.observeEncryptedMedia(couple.id, currentUserId),
                    sharedSessionKey
                ) { encryptedMedia, key ->
                    val keyTag = if (key == null) 0 else System.identityHashCode(key)
                    val decrypted = encryptedMedia.map { item ->
                        if (key == null) {
                            DecryptedMedia(item, null, "", "", false)
                        } else {
                            val cacheKey = "$keyTag:${item.id}:${item.ciphertextBase64.hashCode()}"
                            val cached = mediaPayloadCache[cacheKey]
                            if (cached != null) {
                                cached.copy(raw = item)
                            } else {
                                val aad = "${item.coupleId}|${item.uploaderId}|media"
                                val fresh = runCatching {
                                    val bytes = E2EECryptoEngine.decryptBytes(item.ciphertextBase64, item.ivBase64, key, aad)
                                    val caption = if (!item.captionCiphertext.isNullOrBlank() && !item.captionIv.isNullOrBlank()) {
                                        E2EECryptoEngine.decryptText(item.captionCiphertext, item.captionIv, key, aad)
                                    } else ""
                                    val title = if (!item.titleCiphertext.isNullOrBlank() && !item.titleIv.isNullOrBlank()) {
                                        E2EECryptoEngine.decryptText(item.titleCiphertext, item.titleIv, key, aad)
                                    } else ""
                                    DecryptedMedia(item, bytes, caption, title, true)
                                }.getOrElse {
                                    DecryptedMedia(item, null, "", "", false)
                                }
                                if (fresh.decryptionSucceeded) {
                                    mediaPayloadCache[cacheKey] = fresh
                                }
                                fresh
                            }
                        }
                    }
                    UiState.Success(decrypted) as UiState<List<DecryptedMedia>>
                }.catch { error -> emitStreamError(error, "media") }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val loveNotesState: StateFlow<UiState<List<DecryptedLoveNote>>> = activeCouple
        .flatMapLatest { couple ->
            if (couple == null || couple.status != "connected") {
                flowOf(UiState.Success(emptyList()))
            } else {
                combine(
                    repository.observeLoveNotes(couple.id, currentUserId),
                    sharedSessionKey
                ) { notes, key ->
                    val keyTag = if (key == null) 0 else System.identityHashCode(key)
                    val decrypted = notes.map { note ->
                        if (key == null) {
                            DecryptedLoveNote(note, "Encrypted love note", false)
                        } else {
                            val cacheKey = "$keyTag:${note.id}:${note.ciphertextBase64.hashCode()}"
                            val cached = loveNoteTextCache[cacheKey]
                            if (cached != null) {
                                DecryptedLoveNote(note, cached.first, cached.second)
                            } else {
                                val aad = "${note.coupleId}|${note.authorId}|lovenote"
                                val result = runCatching {
                                    E2EECryptoEngine.decryptText(note.ciphertextBase64, note.ivBase64, key, aad) to true
                                }.getOrElse {
                                    "Unable to decrypt note" to false
                                }
                                if (result.second) {
                                    loveNoteTextCache[cacheKey] = result
                                }
                                DecryptedLoveNote(note, result.first, result.second)
                            }
                        }
                    }
                    UiState.Success(decrypted) as UiState<List<DecryptedLoveNote>>
                }.catch { error -> emitStreamError(error, "love notes") }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val callsState: StateFlow<UiState<List<CallSession>>> = activeCouple
        .flatMapLatest { couple ->
            if (couple == null || couple.status != "connected") {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeCalls(couple.id, currentUserId)
                    .map<List<CallSession>, UiState<List<CallSession>>> { UiState.Success(it) }
                    .catch { error -> emitStreamError(error, "calls") }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    init {
        bootstrapIdentityAndProfile()
    }

    private fun bootstrapIdentityAndProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val keys = cryptoVault.getOrCreateIdentityKeys(currentUserId)
                _identityKeys.value = keys
                val result = repository.ensureUserBootstrapped(
                    displayName = initialDisplayName,
                    email = initialEmail,
                    publicKeyBase64 = keys.publicKeyBase64
                )
                result.onFailure {
                    _bannerMessage.value = "Sync warning: ${it.localizedMessage ?: "Check connection"}"
                }
            }.onFailure { e ->
                Log.e(TAG, "Identity bootstrap failed", e)
                _bannerMessage.value = "Could not initialize local encryption keys."
            }
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun clearExportedJson() {
        _exportedJson.value = null
    }

    fun isBiometricHardwareAvailable(): Boolean = cryptoVault.isBiometricHardwareAvailable()

    // -------------------------------------------------------------------------
    // Pairing & Couple Connection Actions
    // -------------------------------------------------------------------------
    fun createInviteCode() {
        val keys = _identityKeys.value ?: run {
            _bannerMessage.value = "Encryption keys are still initializing. Please try again."
            return
        }
        val profile = sessionState.value.profile
        val name = profile?.username?.ifBlank { initialDisplayName } ?: initialDisplayName
        runAction {
            repository.createCoupleInvitation(name, keys.publicKeyBase64, profile?.birthday)
                .onSuccess { code -> _bannerMessage.value = "Private invite code $code created. Share it securely with your partner." }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Could not create invite code." }
        }
    }

    fun joinWithInviteCode(code: String) {
        val keys = _identityKeys.value ?: run {
            _bannerMessage.value = "Encryption keys are still initializing."
            return
        }
        val name = sessionState.value.profile?.username?.ifBlank { initialDisplayName } ?: initialDisplayName
        runAction {
            repository.redeemInviteCode(code, name, keys.publicKeyBase64)
                .onSuccess { _bannerMessage.value = "Request sent! Waiting for your partner to approve the connection." }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Invalid or already redeemed invitation code." }
        }
    }

    fun approvePartnerConnection() {
        val couple = activeCouple.value ?: return
        runAction {
            repository.approveCoupleConnection(couple)
                .onSuccess { _bannerMessage.value = "SoulSync sanctuary unlocked with end-to-end encryption!" }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Could not approve connection." }
        }
    }

    fun disconnectPartner() {
        val couple = activeCouple.value ?: return
        runAction {
            repository.disconnectCouple(couple)
                .onSuccess { _bannerMessage.value = "Disconnected securely. Private chat access has been revoked." }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Disconnection failed." }
        }
    }

    // -------------------------------------------------------------------------
    // Messaging Actions
    // -------------------------------------------------------------------------
    fun sendTextMessage(
        text: String,
        messageType: String = "text",
        replyToId: String? = null,
        forwarded: Boolean = false
    ) {
        val couple = activeCouple.value ?: return
        val key = sharedSessionKey.value ?: run {
            _bannerMessage.value = "End-to-end encryption session is not active yet."
            return
        }
        setTyping(false)
        viewModelScope.launch(Dispatchers.Default) {
            repository.sendEncryptedMessage(
                couple = couple,
                sessionKey = key,
                plaintext = text,
                messageType = messageType,
                replyToId = replyToId,
                forwarded = forwarded,
                keyFingerprint = sessionState.value.myKeyFingerprint
            ).onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Failed to send encrypted message."
            }
        }
    }

    fun sendEncryptedAttachmentMessage(
        bytes: ByteArray,
        mediaType: String,
        mimeType: String,
        displayLabel: String,
        caption: String? = null,
        durationSec: Long? = null
    ) {
        val couple = activeCouple.value ?: return
        val key = sharedSessionKey.value ?: run {
            _bannerMessage.value = "Encryption key unavailable."
            return
        }
        runAction {
            val uploadResult = repository.uploadEncryptedMedia(
                couple = couple,
                sessionKey = key,
                rawBytes = bytes,
                mediaType = mediaType,
                mimeType = mimeType,
                caption = caption,
                title = displayLabel,
                durationSec = durationSec
            )
            uploadResult.onSuccess { mediaId ->
                val msgType = when (mediaType) {
                    "voice" -> "voice"
                    "file" -> "file"
                    else -> "photo"
                }
                val messageBody = caption?.trim()?.takeIf { it.isNotEmpty() } ?: displayLabel
                repository.sendEncryptedMessage(
                    couple = couple,
                    sessionKey = key,
                    plaintext = messageBody,
                    messageType = msgType,
                    mediaId = mediaId,
                    keyFingerprint = sessionState.value.myKeyFingerprint
                )
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Could not encrypt & upload media."
            }
        }
    }

    fun markPartnerMessagesRead(messages: List<DecryptedMessage>) {
        val readReceiptsOn = sessionState.value.privateAccount?.readReceiptsEnabled ?: true
        if (!readReceiptsOn) return
        val unreadPartnerMsgs = messages.filter {
            it.raw.senderId != currentUserId &&
                it.raw.deliveryStatus != "read" &&
                !it.raw.deleted &&
                markedReadIds.add(it.raw.id)
        }
        if (unreadPartnerMsgs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            unreadPartnerMsgs.take(15).forEach { msg ->
                repository.markMessageRead(msg.raw.id)
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessageForEveryone(messageId)
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to delete message." }
        }
    }

    fun setTyping(isTyping: Boolean) {
        val couple = activeCouple.value ?: return
        if (couple.status != "connected") return
        val enabled = sessionState.value.privateAccount?.typingIndicatorsEnabled ?: true
        if (!enabled && isTyping) return

        typingClearJob?.cancel()
        if (isTyping) {
            val now = System.currentTimeMillis()
            if (!currentlyTypingSent || (now - lastTypingSentAtMs) >= TYPING_THROTTLE_MS) {
                currentlyTypingSent = true
                lastTypingSentAtMs = now
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updateTypingIndicator(couple.id, true)
                }
            }
            typingClearJob = viewModelScope.launch(Dispatchers.IO) {
                delay(TYPING_IDLE_CLEAR_MS)
                if (currentlyTypingSent) {
                    currentlyTypingSent = false
                    repository.updateTypingIndicator(couple.id, false)
                }
            }
        } else if (currentlyTypingSent) {
            currentlyTypingSent = false
            lastTypingSentAtMs = 0L
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateTypingIndicator(couple.id, false)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Couple Features: Milestones, Love Notes, Shared Memories
    // -------------------------------------------------------------------------
    fun saveAnniversaryAndBirthday(anniversaryEpochMs: Long?, myBirthday: String?) {
        val couple = activeCouple.value
        runAction {
            if (couple != null && couple.status != "disconnected") {
                repository.updateCoupleMilestones(
                    couple = couple,
                    anniversaryEpochMs = anniversaryEpochMs,
                    myBirthday = myBirthday
                ).onFailure { _bannerMessage.value = it.localizedMessage ?: "Could not update milestones." }
            }
            if (!myBirthday.isNullOrBlank()) {
                val profile = sessionState.value.profile
                repository.updateUserProfile(
                    username = profile?.username?.ifBlank { initialDisplayName } ?: initialDisplayName,
                    bio = profile?.bio,
                    birthday = myBirthday
                )
            }
            _bannerMessage.value = "Milestones saved."
        }
    }

    fun setDisappearingTimer(seconds: Long) {
        val couple = activeCouple.value
        runAction {
            repository.updatePrivateSettings(disappearingSeconds = seconds)
            if (couple != null && couple.status != "disconnected") {
                repository.updateCoupleMilestones(couple = couple, disappearingSeconds = seconds)
            }
            _bannerMessage.value = if (seconds == 0L) "Disappearing messages turned off."
            else "Disappearing messages set to ${formatDurationLabel(seconds)}."
        }
    }

    fun addLoveNote(text: String, mood: String) {
        val couple = activeCouple.value ?: return
        val key = sharedSessionKey.value ?: run {
            _bannerMessage.value = "Connect with your partner to create encrypted love notes."
            return
        }
        runAction {
            repository.createLoveNote(couple, key, text, mood)
                .onSuccess { _bannerMessage.value = "Encrypted love note saved to your Sanctuary." }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Could not save love note." }
        }
    }

    fun togglePinNote(note: DecryptedLoveNote) {
        viewModelScope.launch {
            repository.togglePinLoveNote(note.raw.id, !note.raw.pinned)
        }
    }

    fun deleteLoveNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteLoveNote(noteId)
        }
    }

    fun uploadMemoryPhoto(bytes: ByteArray, title: String, caption: String) {
        val couple = activeCouple.value ?: return
        val key = sharedSessionKey.value ?: run {
            _bannerMessage.value = "Connect with your partner to save encrypted memories."
            return
        }
        runAction {
            repository.uploadEncryptedMedia(
                couple = couple,
                sessionKey = key,
                rawBytes = bytes,
                mediaType = "memory",
                mimeType = "image/jpeg",
                caption = caption,
                title = title
            ).onSuccess {
                _bannerMessage.value = "Memory encrypted & added to your Memories vault."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Could not save memory."
            }
        }
    }

    fun saveChatPhotoToMemories(
        media: DecryptedMedia,
        customTitle: String? = null,
        customCaption: String? = null
    ) {
        val couple = activeCouple.value ?: return
        val key = sharedSessionKey.value ?: run {
            _bannerMessage.value = "Connect with your partner to save chat photos to Memories."
            return
        }
        val bytes = media.bytes ?: run {
            _bannerMessage.value = "Photo is still decrypting. Please try again in a moment."
            return
        }
        val title = customTitle?.trim()?.ifBlank { null }
            ?: media.title.ifBlank { "Saved Chat Photo" }
        val caption = customCaption?.trim()?.ifBlank { null }
            ?: media.caption.ifBlank { "Saved from private chat" }

        runAction {
            repository.uploadEncryptedMedia(
                couple = couple,
                sessionKey = key,
                rawBytes = bytes,
                mediaType = "memory",
                mimeType = media.raw.mimeType.ifBlank { "image/jpeg" },
                caption = caption,
                title = title
            ).onSuccess {
                _savedChatPhotoIds.value = _savedChatPhotoIds.value + media.raw.id
                _bannerMessage.value = "Chat photo saved to Memories!"
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Could not save photo to Memories."
            }
        }
    }

    fun deleteMediaItem(mediaId: String) {
        viewModelScope.launch {
            repository.deleteMedia(mediaId)
        }
    }

    // -------------------------------------------------------------------------
    // Calls (WebRTC Signaling)
    // -------------------------------------------------------------------------
    fun startCall(callType: String) {
        val couple = activeCouple.value ?: return
        runAction {
            repository.startCall(couple, callType)
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Could not initiate call." }
        }
    }

    fun answerCall(call: CallSession) {
        viewModelScope.launch {
            repository.answerCall(call)
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Could not answer call." }
        }
    }

    fun endOrDeclineCall(callId: String, status: String) {
        viewModelScope.launch {
            repository.endOrDeclineCall(callId, status)
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Could not update call status." }
        }
    }

    fun toggleCallMute(call: CallSession) {
        val currentlyMuted = if (call.callerId == currentUserId) call.mutedByCaller else call.mutedByCallee
        viewModelScope.launch {
            repository.toggleCallMute(call, !currentlyMuted)
        }
    }

    // -------------------------------------------------------------------------
    // Notifications, Settings, App Lock, Reports, GDPR Export & Deletion
    // -------------------------------------------------------------------------
    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    fun updateProfile(username: String, bio: String, birthday: String, avatarBase64: String? = null) {
        runAction {
            repository.updateUserProfile(username, bio, birthday, avatarBase64)
                .onSuccess { _bannerMessage.value = "Profile updated." }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to update profile." }
        }
    }

    fun updateTheme(paletteKey: String, darkMode: Boolean) {
        viewModelScope.launch {
            cryptoVault.saveTheme(paletteKey, darkMode)
            repository.updatePrivateSettings(themePalette = paletteKey, darkMode = darkMode)
        }
    }

    fun updatePrivacyToggle(
        notificationsEnabled: Boolean? = null,
        readReceiptsEnabled: Boolean? = null,
        typingIndicatorsEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            repository.updatePrivateSettings(
                notificationsEnabled = notificationsEnabled,
                readReceiptsEnabled = readReceiptsEnabled,
                typingIndicatorsEnabled = typingIndicatorsEnabled
            )
        }
    }

    fun enablePinLock(pin: String) {
        runAction {
            runCatching {
                cryptoVault.setPin(pin)
                repository.updatePrivateSettings(pinEnabled = true)
            }.onSuccess {
                _bannerMessage.value = "PIN App Lock enabled."
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Invalid PIN."
            }
        }
    }

    fun disablePinLock() {
        runAction {
            cryptoVault.disablePin()
            repository.updatePrivateSettings(pinEnabled = false, biometricEnabled = false)
            _bannerMessage.value = "PIN & Biometric Lock disabled."
        }
    }

    fun toggleBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            cryptoVault.setBiometricEnabled(enabled)
            repository.updatePrivateSettings(biometricEnabled = enabled)
        }
    }

    fun submitAbuseReport(category: String, details: String) {
        val couple = activeCouple.value
        val reportedUid = couple?.partnerUidFor(currentUserId) ?: "unspecified_user"
        runAction {
            repository.submitAbuseReport(reportedUid, couple?.id, category, details)
                .onSuccess { _bannerMessage.value = "Safety report submitted securely to moderation." }
                .onFailure { _bannerMessage.value = it.localizedMessage ?: "Failed to submit report." }
        }
    }

    fun generateDataExport() {
        viewModelScope.launch {
            val state = sessionState.value
            val notifications = (notificationsState.value as? UiState.Success)?.data ?: emptyList()
            _exportedJson.value = withContext(Dispatchers.Default) {
                repository.exportPersonalDataJson(
                    profile = state.profile,
                    privateAccount = state.privateAccount,
                    couple = state.activeCouple,
                    notifications = notifications
                )
            }
        }
    }

    suspend fun deleteAccountCompletely(): Result<Unit> {
        val result = repository.deleteAccountData(activeCouple.value)
        if (result.isSuccess) {
            cryptoVault.clearIdentityKeys(currentUserId)
            cryptoVault.disablePin()
        }
        return result
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            _isActionBusy.value = true
            try {
                block()
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<UiState<T>>.emitStreamError(
        error: Throwable,
        label: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Unable to sync $label"))
    }

    companion object {
        private const val TAG = "ForeverUsVM"
        private const val STOP_TIMEOUT_MS = 5000L
        private const val TYPING_THROTTLE_MS = 4000L
        private const val TYPING_IDLE_CLEAR_MS = 3000L

        fun formatDurationLabel(seconds: Long): String = when (seconds) {
            0L -> "Off"
            30L -> "30 seconds"
            300L -> "5 minutes"
            3600L -> "1 hour"
            86400L -> "24 hours"
            else -> "${seconds}s"
        }
    }
}

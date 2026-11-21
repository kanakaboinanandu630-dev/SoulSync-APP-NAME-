package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val bio: String? = null,
    val avatarData: String? = null,
    val birthday: String? = null,
    val publicKeyBase64: String = "",
    val activeCoupleId: String? = null,
    val partnerUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPrivateAccount(
    @DocumentId val id: String = "",
    val email: String? = null,
    val themePalette: String = "rose_quartz",
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val typingIndicatorsEnabled: Boolean = true,
    val disappearingSeconds: Long = 0L,
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InviteCode(
    @DocumentId val id: String = "",
    val code: String = "",
    val coupleId: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val creatorPublicKey: String = "",
    val redeemed: Boolean = false,
    val redeemedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Couple(
    @DocumentId val id: String = "",
    val partnerAId: String = "",
    val partnerBId: String? = null,
    val memberUids: List<String> = emptyList(),
    val inviteCode: String = "",
    val partnerAApproved: Boolean = false,
    val partnerBApproved: Boolean = false,
    val partnerAPublicKey: String = "",
    val partnerBPublicKey: String? = null,
    val partnerAName: String = "",
    val partnerBName: String? = null,
    val status: String = "pending_partner",
    val anniversaryEpochMs: Long? = null,
    val partnerABirthday: String? = null,
    val partnerBBirthday: String? = null,
    val disappearingSeconds: Long = 0L,
    val typingUid: String? = null,
    val disconnectedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun partnerUidFor(myUid: String): String? =
        if (partnerAId == myUid) partnerBId else partnerAId

    fun partnerNameFor(myUid: String): String =
        if (partnerAId == myUid) partnerBName ?: "Your Partner" else partnerAName.ifBlank { "Your Partner" }

    fun partnerPublicKeyFor(myUid: String): String? =
        if (partnerAId == myUid) partnerBPublicKey else partnerAPublicKey.ifBlank { null }

    fun partnerBirthdayFor(myUid: String): String? =
        if (partnerAId == myUid) partnerBBirthday else partnerABirthday
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val lastCiphertext: String? = null,
    val lastIv: String? = null,
    val lastMessageType: String? = null,
    val lastSenderId: String? = null,
    val typingUid: String? = null,
    val disappearingSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EncryptedMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val senderId: String = "",
    val ciphertextBase64: String = "",
    val ivBase64: String = "",
    val messageType: String = "text",
    val replyToId: String? = null,
    val mediaId: String? = null,
    val forwarded: Boolean = false,
    val deliveryStatus: String = "sent",
    val readAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val deleted: Boolean = false,
    val keyFingerprint: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedMessage(
    val raw: EncryptedMessage,
    val plaintext: String,
    val decryptionSucceeded: Boolean
)

@Keep
@IgnoreExtraProperties
data class EncryptedMedia(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val uploaderId: String = "",
    val mediaType: String = "photo",
    val ciphertextBase64: String = "",
    val ivBase64: String = "",
    val captionCiphertext: String? = null,
    val captionIv: String? = null,
    val titleCiphertext: String? = null,
    val titleIv: String? = null,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val durationSec: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedMedia(
    val raw: EncryptedMedia,
    val bytes: ByteArray?,
    val caption: String,
    val title: String,
    val decryptionSucceeded: Boolean
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val callerId: String = "",
    val calleeId: String = "",
    val callType: String = "audio",
    val status: String = "ringing",
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val mutedByCaller: Boolean = false,
    val mutedByCallee: Boolean = false,
    val startedAtEpochMs: Long? = null,
    val endedAtEpochMs: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LoveNote(
    @DocumentId val id: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val authorId: String = "",
    val ciphertextBase64: String = "",
    val ivBase64: String = "",
    val mood: String = "adore",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DecryptedLoveNote(
    val raw: LoveNote,
    val text: String,
    val decryptionSucceeded: Boolean
)

@Keep
@IgnoreExtraProperties
data class CoupleNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderId: String = "",
    val coupleId: String = "",
    val memberUids: List<String> = emptyList(),
    val type: String = "system",
    val title: String = "",
    val body: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AbuseReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reportedUserId: String = "",
    val coupleId: String? = null,
    val category: String = "safety",
    val details: String = "",
    val status: String = "submitted",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

package com.example.data

import android.content.Context
import com.example.R
import com.example.crypto.E2EECryptoEngine
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey

class ForeverUsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val codeRandom = SecureRandom()
    private val codeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun generateInviteCode(): String =
        (1..8).map { codeAlphabet[codeRandom.nextInt(codeAlphabet.length)] }.joinToString("")

    private fun cleanDocId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(18)}"

    // -------------------------------------------------------------------------
    // Real-time Observation Flows (Query.snapshots() + ESTIMATE + Two-Tier Catch)
    // -------------------------------------------------------------------------
    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observePrivateAccount(userId: String): Flow<UserPrivateAccount?> = flow {
        val path = "users/$userId/private/account"
        emitAll(
            db.collection("users").document(userId).collection("private").document("account")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(UserPrivateAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeUserCouples(userId: String): Flow<List<Couple>> = flow {
        val path = "couples"
        emitAll(
            db.collection("couples")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Couple::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeConversation(coupleId: String, userId: String): Flow<Conversation?> = flow {
        val path = "conversations/$coupleId"
        emitAll(
            db.collection("conversations").document(coupleId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeEncryptedMessages(coupleId: String, userId: String): Flow<List<EncryptedMessage>> = flow {
        val path = "encryptedMessages"
        emitAll(
            db.collection("encryptedMessages")
                .whereEqualTo("coupleId", coupleId)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(EncryptedMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(Long.MAX_VALUE / 2, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeEncryptedMedia(coupleId: String, userId: String): Flow<List<EncryptedMedia>> = flow {
        val path = "media"
        emitAll(
            db.collection("media")
                .whereEqualTo("coupleId", coupleId)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(EncryptedMedia::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeCalls(coupleId: String, userId: String): Flow<List<CallSession>> = flow {
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereEqualTo("coupleId", coupleId)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeLoveNotes(coupleId: String, userId: String): Flow<List<LoveNote>> = flow {
        val path = "loveNotes"
        emitAll(
            db.collection("loveNotes")
                .whereEqualTo("coupleId", coupleId)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(LoveNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<LoveNote> { it.pinned }
                                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeNotifications(userId: String): Flow<List<CoupleNotification>> = flow {
        val path = "notifications"
        emitAll(
            db.collection("notifications")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CoupleNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeMyReports(userId: String): Flow<List<AbuseReport>> = flow {
        val path = "reports"
        emitAll(
            db.collection("reports")
                .whereEqualTo("reporterId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AbuseReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    // -------------------------------------------------------------------------
    // User Bootstrap & Profile / Private Preferences
    // -------------------------------------------------------------------------
    suspend fun ensureUserBootstrapped(
        displayName: String,
        email: String?,
        publicKeyBase64: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val userPath = "users/${uid ?: "unauthenticated"}"
        return try {
            val safeUid = requireUserId()
            val safeName = displayName.trim().ifBlank { "Forever Partner" }.take(40)
            val userRef = db.collection("users").document(safeUid)
            val userSnap = userRef.get().await()

            if (!userSnap.exists()) {
                val createMap = mapOf(
                    "userId" to safeUid,
                    "username" to safeName,
                    "bio" to "Waiting for my favorite person.",
                    "publicKeyBase64" to publicKeyBase64,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(createMap).await()
            } else {
                val existingKey = userSnap.getString("publicKeyBase64")
                if (existingKey != publicKeyBase64) {
                    userRef.update(
                        mapOf(
                            "publicKeyBase64" to publicKeyBase64,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val privateRef = userRef.collection("private").document("account")
            val privateSnap = privateRef.get().await()
            if (!privateSnap.exists()) {
                val privateMap = mapOf<String, Any?>(
                    "email" to email?.take(254),
                    "themePalette" to "rose_quartz",
                    "darkMode" to false,
                    "notificationsEnabled" to true,
                    "readReceiptsEnabled" to true,
                    "typingIndicatorsEnabled" to true,
                    "disappearingSeconds" to 0L,
                    "pinEnabled" to false,
                    "biometricEnabled" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                privateRef.set(privateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        username: String,
        bio: String?,
        birthday: String?,
        avatarData: String? = null
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val trimmedName = username.trim()
            require(trimmedName.length in 1..40) { "Username must be between 1 and 40 characters." }
            val trimmedBio = bio?.trim()?.take(200)
            val trimmedBirthday = birthday?.trim()?.take(32)
            val updateMap = mutableMapOf<String, Any>(
                "username" to trimmedName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (trimmedBio != null) updateMap["bio"] = trimmedBio
            if (trimmedBirthday != null) updateMap["birthday"] = trimmedBirthday
            if (avatarData != null) {
                require(avatarData.length <= 150000) { "Avatar image is too large." }
                updateMap["avatarData"] = avatarData
            }
            db.collection("users").document(uid).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivateSettings(
        themePalette: String? = null,
        darkMode: Boolean? = null,
        notificationsEnabled: Boolean? = null,
        readReceiptsEnabled: Boolean? = null,
        typingIndicatorsEnabled: Boolean? = null,
        disappearingSeconds: Long? = null,
        pinEnabled: Boolean? = null,
        biometricEnabled: Boolean? = null
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/private/account"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (themePalette != null) updates["themePalette"] = themePalette
            if (darkMode != null) updates["darkMode"] = darkMode
            if (notificationsEnabled != null) updates["notificationsEnabled"] = notificationsEnabled
            if (readReceiptsEnabled != null) updates["readReceiptsEnabled"] = readReceiptsEnabled
            if (typingIndicatorsEnabled != null) updates["typingIndicatorsEnabled"] = typingIndicatorsEnabled
            if (disappearingSeconds != null) {
                require(disappearingSeconds in 0L..604800L) { "Invalid disappearing timer." }
                updates["disappearingSeconds"] = disappearingSeconds
            }
            if (pinEnabled != null) updates["pinEnabled"] = pinEnabled
            if (biometricEnabled != null) updates["biometricEnabled"] = biometricEnabled

            db.collection("users").document(uid)
                .collection("private").document("account")
                .update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Private Couple Connection (Unique Invite Code + Dual Approval + Disconnect)
    // -------------------------------------------------------------------------
    suspend fun createCoupleInvitation(
        myUsername: String,
        myPublicKey: String,
        myBirthday: String?
    ): Result<String> {
        var path = "couples"
        return try {
            val uid = requireUserId()
            val coupleId = cleanDocId("couple")
            val code = generateInviteCode()
            val safeName = myUsername.trim().ifBlank { "Partner" }.take(40)

            path = "couples/$coupleId"
            val coupleMap = mapOf<String, Any?>(
                "partnerAId" to uid,
                "memberUids" to listOf(uid),
                "inviteCode" to code,
                "partnerAApproved" to false,
                "partnerBApproved" to false,
                "partnerAPublicKey" to myPublicKey,
                "partnerAName" to safeName,
                "status" to "pending_partner",
                "partnerABirthday" to myBirthday?.take(32),
                "disappearingSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("couples").document(coupleId).set(coupleMap).await()

            path = "inviteCodes/$code"
            val inviteMap = mapOf(
                "code" to code,
                "coupleId" to coupleId,
                "creatorId" to uid,
                "creatorName" to safeName,
                "creatorPublicKey" to myPublicKey,
                "redeemed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("inviteCodes").document(code).set(inviteMap).await()

            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "activeCoupleId" to coupleId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun redeemInviteCode(
        rawCode: String,
        myUsername: String,
        myPublicKey: String
    ): Result<String> {
        val code = rawCode.trim().uppercase()
        var path = "inviteCodes/$code"
        return try {
            val uid = requireUserId()
            require(code.length in 6..16 && code.matches(Regex("^[A-Z0-9_\\-]+$"))) {
                "Enter a valid invitation code (6–16 letters or digits)."
            }
            val inviteRef = db.collection("inviteCodes").document(code)
            val snap = inviteRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("Invitation code not found. Double-check the code with your partner."))
            }
            val invite = snap.toObject(InviteCode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Invalid invitation record."))

            if (invite.redeemed) {
                return Result.failure(IllegalStateException("This invitation code has already been redeemed."))
            }
            if (invite.creatorId == uid) {
                return Result.failure(IllegalArgumentException("You cannot redeem your own invitation code. Share it with your partner."))
            }

            // 1. Mark invite redeemed
            inviteRef.update(
                mapOf(
                    "redeemed" to true,
                    "redeemedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. Join couple in pending_approval state (Partner B approves; Partner A must now confirm)
            path = "couples/${invite.coupleId}"
            val safeName = myUsername.trim().ifBlank { "Partner" }.take(40)
            val members = listOf(invite.creatorId, uid)
            db.collection("couples").document(invite.coupleId).update(
                mapOf(
                    "partnerBId" to uid,
                    "partnerBName" to safeName,
                    "partnerBPublicKey" to myPublicKey,
                    "memberUids" to members,
                    "partnerBApproved" to true,
                    "status" to "pending_approval",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 3. Link activeCoupleId on user profile
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "activeCoupleId" to invite.coupleId,
                    "partnerUid" to invite.creatorId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 4. Notify Partner A to approve the bond
            sendNotificationInternal(
                recipientUid = invite.creatorId,
                senderUid = uid,
                coupleId = invite.coupleId,
                memberUids = members,
                type = "invite",
                title = "Partner Join Request",
                body = "$safeName accepted your SoulSync invite. Tap to approve your encrypted bond."
            )

            Result.success(invite.coupleId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun approveCoupleConnection(couple: Couple): Result<Unit> {
        var path = "couples/${couple.id}"
        return try {
            val uid = requireUserId()
            require(couple.partnerAId == uid && couple.status == "pending_approval") {
                "Only the inviting partner can finalize this connection."
            }
            val partnerBUid = couple.partnerBId
                ?: throw IllegalStateException("Partner B has not joined yet.")

            // 1. Finalize dual approval -> connected
            db.collection("couples").document(couple.id).update(
                mapOf(
                    "partnerAApproved" to true,
                    "status" to "connected",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. Initialize private 1-to-1 conversation
            path = "conversations/${couple.id}"
            val convRef = db.collection("conversations").document(couple.id)
            if (!convRef.get().await().exists()) {
                convRef.set(
                    mapOf(
                        "coupleId" to couple.id,
                        "memberUids" to couple.memberUids,
                        "disappearingSeconds" to couple.disappearingSeconds,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // 3. Record mutual partnerUid on Partner A profile
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "partnerUid" to partnerBUid,
                    "activeCoupleId" to couple.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 4. Notify Partner B
            sendNotificationInternal(
                recipientUid = partnerBUid,
                senderUid = uid,
                coupleId = couple.id,
                memberUids = couple.memberUids,
                type = "approval",
                title = "Connection Approved",
                body = "${couple.partnerAName} approved your bond! Your end-to-end encrypted sanctuary is now unlocked."
            )

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun disconnectCouple(couple: Couple): Result<Unit> {
        var path = "couples/${couple.id}"
        return try {
            val uid = requireUserId()
            db.collection("couples").document(couple.id).update(
                mapOf(
                    "status" to "disconnected",
                    "disconnectedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Clear activeCoupleId and partnerUid on current user's profile
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "activeCoupleId" to null,
                    "partnerUid" to null,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Notify partner if present
            val partnerUid = couple.partnerUidFor(uid)
            if (!partnerUid.isNullOrBlank() && couple.memberUids.size == 2) {
                sendNotificationInternal(
                    recipientUid = partnerUid,
                    senderUid = uid,
                    coupleId = couple.id,
                    memberUids = couple.memberUids,
                    type = "system",
                    title = "Sanctuary Disconnected",
                    body = "Your partner securely disconnected the shared sanctuary."
                )
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCoupleMilestones(
        couple: Couple,
        anniversaryEpochMs: Long? = null,
        myBirthday: String? = null,
        disappearingSeconds: Long? = null
    ): Result<Unit> {
        val path = "couples/${couple.id}"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (anniversaryEpochMs != null && anniversaryEpochMs >= 0L) {
                updates["anniversaryEpochMs"] = anniversaryEpochMs
            }
            if (myBirthday != null) {
                val field = if (couple.partnerAId == uid) "partnerABirthday" else "partnerBBirthday"
                updates[field] = myBirthday.trim().take(32)
            }
            if (disappearingSeconds != null && disappearingSeconds in 0L..604800L) {
                updates["disappearingSeconds"] = disappearingSeconds
            }
            db.collection("couples").document(couple.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // End-to-End Encrypted Messaging (Never Stores Plaintext!)
    // -------------------------------------------------------------------------
    suspend fun sendEncryptedMessage(
        couple: Couple,
        sessionKey: SecretKey,
        plaintext: String,
        messageType: String = "text",
        replyToId: String? = null,
        mediaId: String? = null,
        forwarded: Boolean = false,
        keyFingerprint: String? = null
    ): Result<String> {
        var path = "encryptedMessages"
        return try {
            val uid = requireUserId()
            require(couple.status == "connected" && couple.memberUids.size == 2) {
                "Only dual-approved connected partners can send encrypted messages."
            }
            val cleanText = plaintext.trim()
            require(cleanText.isNotEmpty() && cleanText.length <= 4000) {
                "Message must be between 1 and 4000 characters."
            }

            val aad = "${couple.id}|$uid"
            val encrypted = E2EECryptoEngine.encryptText(cleanText, sessionKey, aad)
            val messageId = cleanDocId("msg")
            path = "encryptedMessages/$messageId"

            val expiresAt = if (couple.disappearingSeconds > 0L) {
                System.currentTimeMillis() + (couple.disappearingSeconds * 1000L)
            } else {
                null
            }

            val msgMap = mapOf<String, Any?>(
                "conversationId" to couple.id,
                "coupleId" to couple.id,
                "memberUids" to couple.memberUids,
                "senderId" to uid,
                "ciphertextBase64" to encrypted.ciphertextBase64,
                "ivBase64" to encrypted.ivBase64,
                "messageType" to messageType,
                "replyToId" to replyToId,
                "mediaId" to mediaId,
                "forwarded" to forwarded,
                "deliveryStatus" to "sent",
                "expiresAtEpochMs" to expiresAt,
                "deleted" to false,
                "keyFingerprint" to keyFingerprint?.take(128),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("encryptedMessages").document(messageId).set(msgMap).await()

            // Update conversation preview (still encrypted!)
            val convRef = db.collection("conversations").document(couple.id)
            runCatching {
                convRef.update(
                    mapOf(
                        "lastCiphertext" to encrypted.ciphertextBase64.take(8000),
                        "lastIv" to encrypted.ivBase64,
                        "lastMessageType" to messageType,
                        "lastSenderId" to uid,
                        "typingUid" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markMessageRead(messageId: String): Result<Unit> {
        val path = "encryptedMessages/$messageId"
        return try {
            requireUserId()
            db.collection("encryptedMessages").document(messageId).update(
                mapOf(
                    "deliveryStatus" to "read",
                    "readAtEpochMs" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String): Result<Unit> {
        val path = "encryptedMessages/$messageId"
        return try {
            requireUserId()
            db.collection("encryptedMessages").document(messageId).update(
                mapOf(
                    "deleted" to true,
                    "ciphertextBase64" to "REVMRVRFRA==",
                    "ivBase64" to "MDAwMDAwMDAwMDAw",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTypingIndicator(coupleId: String, typing: Boolean): Result<Unit> {
        val path = "couples/$coupleId"
        return try {
            val uid = requireUserId()
            db.collection("couples").document(coupleId).update(
                mapOf(
                    "typingUid" to if (typing) uid else "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Encrypted Media & Private Photo Gallery / Voice Notes / Files
    // -------------------------------------------------------------------------
    suspend fun uploadEncryptedMedia(
        couple: Couple,
        sessionKey: SecretKey,
        rawBytes: ByteArray,
        mediaType: String,
        mimeType: String,
        caption: String? = null,
        title: String? = null,
        durationSec: Long? = null
    ): Result<String> {
        var path = "media"
        return try {
            val uid = requireUserId()
            require(couple.status == "connected" && couple.memberUids.size == 2) {
                "Connect with your partner to share encrypted media."
            }
            require(rawBytes.isNotEmpty() && rawBytes.size <= 260_000) {
                "Media attachment exceeds encrypted Firestore packet limit (260 KB)."
            }

            val aad = "${couple.id}|$uid|media"
            val encryptedBlob = E2EECryptoEngine.encryptBytes(rawBytes, sessionKey, aad)
            val encryptedCaption = caption?.trim()?.takeIf { it.isNotEmpty() }?.let {
                E2EECryptoEngine.encryptText(it.take(500), sessionKey, aad)
            }
            val encryptedTitle = title?.trim()?.takeIf { it.isNotEmpty() }?.let {
                E2EECryptoEngine.encryptText(it.take(120), sessionKey, aad)
            }

            val mediaId = cleanDocId("media")
            path = "media/$mediaId"
            val map = mapOf<String, Any?>(
                "coupleId" to couple.id,
                "memberUids" to couple.memberUids,
                "uploaderId" to uid,
                "mediaType" to mediaType,
                "ciphertextBase64" to encryptedBlob.ciphertextBase64,
                "ivBase64" to encryptedBlob.ivBase64,
                "captionCiphertext" to encryptedCaption?.ciphertextBase64,
                "captionIv" to encryptedCaption?.ivBase64,
                "titleCiphertext" to encryptedTitle?.ciphertextBase64,
                "titleIv" to encryptedTitle?.ivBase64,
                "mimeType" to mimeType.take(100),
                "sizeBytes" to rawBytes.size.toLong(),
                "durationSec" to durationSec,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("media").document(mediaId).set(map).await()
            Result.success(mediaId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMedia(mediaId: String): Result<Unit> {
        val path = "media/$mediaId"
        return try {
            requireUserId()
            db.collection("media").document(mediaId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // WebRTC Private Audio & Video Call Signaling
    // -------------------------------------------------------------------------
    suspend fun startCall(couple: Couple, callType: String): Result<String> {
        var path = "calls"
        return try {
            val uid = requireUserId()
            require(couple.status == "connected" && couple.memberUids.size == 2) {
                "Connect with your partner before placing private calls."
            }
            val calleeUid = couple.partnerUidFor(uid)
                ?: throw IllegalStateException("Partner UID unavailable.")
            val callId = cleanDocId("call")
            path = "calls/$callId"

            val offerSdp = buildLocalWebRtcOfferSdp(callId, callType, uid)
            val payload = mapOf(
                "coupleId" to couple.id,
                "memberUids" to couple.memberUids,
                "callerId" to uid,
                "calleeId" to calleeUid,
                "callType" to callType,
                "status" to "ringing",
                "offerSdp" to offerSdp,
                "mutedByCaller" to false,
                "mutedByCallee" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("calls").document(callId).set(payload).await()

            sendNotificationInternal(
                recipientUid = calleeUid,
                senderUid = uid,
                coupleId = couple.id,
                memberUids = couple.memberUids,
                type = "call",
                title = if (callType == "video") "Incoming Private Video Call" else "Incoming Private Audio Call",
                body = "Your partner is calling you on an encrypted channel."
            )

            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun answerCall(call: CallSession): Result<Unit> {
        val path = "calls/${call.id}"
        return try {
            val uid = requireUserId()
            val answerSdp = buildLocalWebRtcAnswerSdp(call.id, call.callType, uid)
            db.collection("calls").document(call.id).update(
                mapOf(
                    "status" to "active",
                    "answerSdp" to answerSdp,
                    "startedAtEpochMs" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun endOrDeclineCall(callId: String, terminalStatus: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            require(terminalStatus in setOf("ended", "declined", "missed"))
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to terminalStatus,
                    "endedAtEpochMs" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleCallMute(call: CallSession, muted: Boolean): Result<Unit> {
        val path = "calls/${call.id}"
        return try {
            val uid = requireUserId()
            val field = if (call.callerId == uid) "mutedByCaller" else "mutedByCallee"
            db.collection("calls").document(call.id).update(
                mapOf(
                    field to muted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun buildLocalWebRtcOfferSdp(callId: String, callType: String, uid: String): String {
        val sessionId = System.currentTimeMillis()
        val mediaBlock = if (callType == "video") {
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"
        } else {
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"
        }
        return "v=0\r\no=foreverus-$uid $sessionId 2 IN IP4 0.0.0.0\r\ns=ForeverUs-$callId\r\nt=0 0\r\na=setup:actpass\r\n$mediaBlock"
    }

    private fun buildLocalWebRtcAnswerSdp(callId: String, callType: String, uid: String): String {
        val sessionId = System.currentTimeMillis()
        val mediaBlock = if (callType == "video") {
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"
        } else {
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"
        }
        return "v=0\r\no=foreverus-$uid $sessionId 2 IN IP4 0.0.0.0\r\ns=ForeverUs-$callId\r\nt=0 0\r\na=setup:active\r\n$mediaBlock"
    }

    // -------------------------------------------------------------------------
    // Encrypted Love Notes & Notifications
    // -------------------------------------------------------------------------
    suspend fun createLoveNote(
        couple: Couple,
        sessionKey: SecretKey,
        noteText: String,
        mood: String
    ): Result<String> {
        var path = "loveNotes"
        return try {
            val uid = requireUserId()
            require(couple.status == "connected" && couple.memberUids.size == 2) {
                "Connect with your partner to leave encrypted love notes."
            }
            val clean = noteText.trim()
            require(clean.length in 1..1500) { "Love note must be between 1 and 1500 characters." }

            val aad = "${couple.id}|$uid|lovenote"
            val encrypted = E2EECryptoEngine.encryptText(clean, sessionKey, aad)
            val noteId = cleanDocId("note")
            path = "loveNotes/$noteId"

            db.collection("loveNotes").document(noteId).set(
                mapOf(
                    "coupleId" to couple.id,
                    "memberUids" to couple.memberUids,
                    "authorId" to uid,
                    "ciphertextBase64" to encrypted.ciphertextBase64,
                    "ivBase64" to encrypted.ivBase64,
                    "mood" to mood,
                    "pinned" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val partnerUid = couple.partnerUidFor(uid)
            if (!partnerUid.isNullOrBlank()) {
                sendNotificationInternal(
                    recipientUid = partnerUid,
                    senderUid = uid,
                    coupleId = couple.id,
                    memberUids = couple.memberUids,
                    type = "love_note",
                    title = "New Encrypted Love Note",
                    body = "Your partner left you a sweet note in your SoulSync Sanctuary."
                )
            }
            Result.success(noteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun togglePinLoveNote(noteId: String, pinned: Boolean): Result<Unit> {
        val path = "loveNotes/$noteId"
        return try {
            requireUserId()
            db.collection("loveNotes").document(noteId).update(
                mapOf(
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLoveNote(noteId: String): Result<Unit> {
        val path = "loveNotes/$noteId"
        return try {
            requireUserId()
            db.collection("loveNotes").document(noteId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun sendNotificationInternal(
        recipientUid: String,
        senderUid: String,
        coupleId: String,
        memberUids: List<String>,
        type: String,
        title: String,
        body: String
    ) {
        val notifId = cleanDocId("notif")
        runCatching {
            db.collection("notifications").document(notifId).set(
                mapOf(
                    "userId" to recipientUid,
                    "senderId" to senderUid,
                    "coupleId" to coupleId,
                    "memberUids" to memberUids,
                    "type" to type,
                    "title" to title.take(120),
                    "body" to body.take(300),
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        val path = "notifications/$notificationId"
        return try {
            requireUserId()
            db.collection("notifications").document(notificationId).update(
                mapOf(
                    "read" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Abuse Reporting, GDPR Export & Account Deletion
    // -------------------------------------------------------------------------
    suspend fun submitAbuseReport(
        reportedUserId: String,
        coupleId: String?,
        category: String,
        details: String
    ): Result<String> {
        var path = "reports"
        return try {
            val uid = requireUserId()
            val cleanDetails = details.trim()
            require(cleanDetails.length in 1..2000) { "Please provide details (1–2000 characters)." }
            val reportId = cleanDocId("rep")
            path = "reports/$reportId"
            val payload = mapOf<String, Any?>(
                "reporterId" to uid,
                "reportedUserId" to reportedUserId.ifBlank { "unknown_partner" }.take(128),
                "coupleId" to coupleId,
                "category" to category,
                "details" to cleanDetails,
                "status" to "submitted",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("reports").document(reportId).set(payload).await()
            Result.success(reportId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun exportPersonalDataJson(
        profile: UserProfile?,
        privateAccount: UserPrivateAccount?,
        couple: Couple?,
        notifications: List<CoupleNotification>
    ): String {
        return JSONObject().apply {
            put("app", "SoulSync")
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("profile", JSONObject().apply {
                put("userId", profile?.userId ?: "")
                put("username", profile?.username ?: "")
                put("bio", profile?.bio ?: "")
                put("birthday", profile?.birthday ?: "")
                put("publicKeyFingerprint", profile?.publicKeyBase64?.let { E2EECryptoEngine.shortFingerprint(it) } ?: "")
            })
            put("privateSettings", JSONObject().apply {
                put("email", privateAccount?.email ?: "")
                put("themePalette", privateAccount?.themePalette ?: "rose_quartz")
                put("darkMode", privateAccount?.darkMode ?: true)
                put("readReceiptsEnabled", privateAccount?.readReceiptsEnabled ?: true)
                put("typingIndicatorsEnabled", privateAccount?.typingIndicatorsEnabled ?: true)
                put("disappearingSeconds", privateAccount?.disappearingSeconds ?: 0L)
            })
            put("coupleStatus", couple?.status ?: "unpaired")
            put("notificationsCount", notifications.size)
            put("note", "Private chat messages and media are end-to-end encrypted and stored only as ciphertext on the server.")
        }.toString(2)
    }

    suspend fun deleteAccountData(activeCouple: Couple?): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            if (activeCouple != null && activeCouple.status != "disconnected") {
                runCatching { disconnectCouple(activeCouple) }
            }
            path = "users/$uid/private/account"
            runCatching {
                db.collection("users").document(uid).collection("private").document("account").delete().await()
            }
            path = "users/$uid"
            db.collection("users").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}

# Forever Us — Architecture, Security & Deployment Guide

## Overview
**Forever Us** is a native Android private couples messaging application built with **Kotlin**, **Jetpack Compose (Material Design 3)**, **Firebase Authentication (Google Sign-In via Jetpack Credential Manager)**, **Firebase Cloud Firestore**, and **real on-device End-to-End Encryption (ECDH P-256 + HKDF-SHA256 + AES-256-GCM + Android Keystore)**.

---

## Security & Cryptography Architecture

1. **Identity Key Generation & Hardware Keystore Wrapping (`E2EECryptoEngine.kt`)**
   - Each signed-in account generates a unique **ECDH `secp256r1` (P-256)** key pair on-device.
   - The private key is encrypted at rest using an **Android Keystore AES-256-GCM Master Key** (`forever_us_master_wrap_key_v1`) before persisting to local `DataStore`. Private keys never leave the device and are never uploaded to Firestore.
   - Only the X.509 public key (`publicKeyBase64`) is published to `users/{userId}` and exchanged during couple pairing.

2. **Shared Session Key Derivation & Message Encryption**
   - Once both partners approve the couple bond, each device independently computes `ECDH(myPrivateKey, partnerPublicKey)` and derives a 256-bit AES session key via **RFC 5869 HKDF-SHA256** bound to `coupleId`.
   - Every text message, love note, memory caption, photo, voice clip, and file attachment is encrypted on-device with **AES-256-GCM**, a fresh 96-bit `SecureRandom` IV, and Additional Authenticated Data (`coupleId|senderId`).
   - **Zero plaintext private content is ever written to Firestore.**
   - Both partners can compare their deterministic **60-digit Safety Number** (SHA-256 over sorted public keys) inside the app to detect MITM tampering.

3. **App Lock (`AppLockManager.kt`)**
   - Optional 4–8 digit PIN is hashed using **PBKDF2-HMAC-SHA256** (65,536 iterations, random 128-bit salt) and verified in constant time.
   - Supports Android **BiometricPrompt** (fingerprint/face) on Android 9+ devices.

4. **Zero-Trust Firestore Collections & Security Rules (`firestore.rules`)**
   - `users/{userId}` & `users/{userId}/private/account` (Split-collection PII isolation)
   - `inviteCodes/{code}` (Unlistable single-use secret code lookup)
   - `couples/{coupleId}` (Dual-approval state machine + terminal disconnection lock)
   - `conversations/{conversationId}` (Connected couple members only)
   - `encryptedMessages/{messageId}` (Ciphertext + IV only; strict action-based receipt & delete rules)
   - `media/{mediaId}` (Encrypted shared photos, voice notes, memories, and files)
   - `calls/{callId}` (WebRTC SDP signaling with terminal state locking once ended/declined)
   - `loveNotes/{noteId}` (Encrypted romantic keepsake notes)
   - `notifications/{notificationId}` (Recipient-scoped alerts)
   - `reports/{reportId}` (Abuse reporting with verified admin RBAC)

---

## Local Verification & Security Testing

1. **Firestore Security Rules Unit Tests (Local Emulator)**
   ```bash
   FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
   ```
   Verifies default-deny unauthenticated access, PII split-collection isolation, invite code anti-scraping, dual-approval anti-shortcutting, connected-only messaging, terminal call & disconnect locks, shadow field rejection, timestamp bounds, and email-verified admin RBAC.

2. **On-Device Cryptography & PIN Unit Tests**
   ```bash
   gradle :app:testDebugUnitTest
   ```
   Verifies ECDH key agreement, HKDF-SHA256 session key symmetry, AES-256-GCM round-trip & AAD tamper rejection, 60-digit Safety Number symmetry, and PBKDF2 PIN hashing.

---

## Extending Optional Firebase Services in the Firebase Console

This AI Studio Android project automatically provisions **Firebase Auth (Google Sign-In)** and **Cloud Firestore Enterprise**. To add optional capabilities in production:
- **Email/Password or Phone OTP Sign-In**: Enable *Email/Password* and *Phone* providers under **Firebase Console -> Authentication -> Sign-in method**.
- **Background Push Notifications (FCM)**: Add `firebase-messaging` and a `FirebaseMessagingService` subclass, then trigger notifications via Cloud Functions when documents are created in `notifications/` or `calls/`.
- **TURN/STUN Relay for WebRTC Media**: Pair the Firestore `calls/{callId}` SDP signaling layer with a managed TURN server (e.g., Coturn / Twilio NTS) and `io.getstream:stream-webrtc-android` for live cross-NAT media streams.

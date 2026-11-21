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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.crypto.LocalSecurityPreferences
import com.example.data.ImageBitmapCache
import com.example.data.MediaAttachmentHelper
import com.example.ui.ForeverUsSessionState
import com.example.ui.theme.AppThemePalette
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    session: ForeverUsSessionState,
    localPrefs: LocalSecurityPreferences,
    biometricAvailable: Boolean,
    isBusy: Boolean,
    onUpdateProfile: (username: String, bio: String, birthday: String, avatarBase64: String?) -> Unit,
    onUpdateTheme: (paletteKey: String, darkMode: Boolean) -> Unit,
    onUpdatePrivacyToggle: (notifications: Boolean?, readReceipts: Boolean?, typing: Boolean?) -> Unit,
    onEnablePin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onDisconnectPartner: () -> Unit,
    onSubmitReport: (category: String, details: String) -> Unit,
    onExportPersonalInfo: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
    onShowError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = session.profile
    val priv = session.privateAccount
    val couple = session.activeCouple

    var usernameInput by remember(profile?.username) { mutableStateOf(profile?.username.orEmpty()) }
    var bioInput by remember(profile?.bio) { mutableStateOf(profile?.bio.orEmpty()) }
    var birthdayInput by remember(profile?.birthday) { mutableStateOf(profile?.birthday.orEmpty()) }

    var showPinDialog by remember { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var legalDialogTitle by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaAttachmentHelper.compressAvatarToBase64Async(context, uri)
                    .onSuccess { b64 ->
                        onUpdateProfile(
                            usernameInput.ifBlank { profile?.username ?: "Partner" },
                            bioInput,
                            birthdayInput,
                            b64
                        )
                    }
                    .onFailure { onShowError(it.localizedMessage ?: "Failed to update avatar.") }
            }
        }
    }

    val avatarKey = profile?.avatarData?.let { "avatar_${it.hashCode()}" }
    val avatarBitmap by produceState<ImageBitmap?>(
        initialValue = avatarKey?.let { ImageBitmapCache.get(it) },
        key1 = avatarKey
    ) {
        val b64 = profile?.avatarData
        value = if (!b64.isNullOrBlank() && avatarKey != null) {
            ImageBitmapCache.getOrDecodeBase64(avatarKey, b64)
        } else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings & Security", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = priv?.email ?: "Signed in with Google",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Profile Editor Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    avatarPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("change_avatar_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            val renderedAvatar = avatarBitmap
                            if (renderedAvatar != null) {
                                Image(
                                    bitmap = renderedAvatar,
                                    contentDescription = "Your profile avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = "Upload profile photo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your Profile", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Tap avatar to pick a photo via Android Photo Picker.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it.take(40) },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_username_input")
                    )
                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it.take(200) },
                        label = { Text("Romantic Bio / Status") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = birthdayInput,
                        onValueChange = { birthdayInput = it.take(32) },
                        label = { Text("Birthday (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onUpdateProfile(usernameInput, bioInput, birthdayInput, null) },
                        enabled = !isBusy && usernameInput.trim().isNotEmpty(),
                        modifier = Modifier.align(Alignment.End).testTag("settings_save_profile_button")
                    ) {
                        Text("Save Profile")
                    }
                }
            }
        }

        // 2. Theme Studio Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Theme Studio", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemePalette.entries.forEach { palette ->
                            FilterChip(
                                selected = localPrefs.themePalette == palette.key,
                                onClick = { onUpdateTheme(palette.key, localPrefs.darkMode) },
                                label = { Text(palette.label.substringBefore(" ")) },
                                modifier = Modifier.weight(1f).testTag("theme_chip_${palette.key}")
                            )
                        }
                    }
                    SettingsToggleRow(
                        title = "Dark Mode",
                        subtitle = "Switch between luminous light and velvet dark ambiance.",
                        checked = localPrefs.darkMode,
                        testTag = "settings_dark_mode_switch",
                        onCheckedChange = { onUpdateTheme(localPrefs.themePalette, it) }
                    )
                }
            }
        }

        // 3. Security Vault (PIN, Biometric, E2EE Keys)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Security Vault", style = MaterialTheme.typography.titleMedium)
                    }

                    SettingsToggleRow(
                        title = "PIN App Lock",
                        subtitle = if (localPrefs.pinEnabled) "Protected with salted PBKDF2-SHA256 PIN hash."
                        else "Require a private PIN whenever SoulSync opens.",
                        checked = localPrefs.pinEnabled,
                        testTag = "settings_pin_lock_switch",
                        onCheckedChange = { enable ->
                            if (enable) showPinDialog = true else onDisablePin()
                        }
                    )

                    if (localPrefs.pinEnabled) {
                        SettingsToggleRow(
                            title = "Biometric Unlock",
                            subtitle = if (biometricAvailable) "Use fingerprint or face unlock."
                            else "Biometric hardware not enrolled on this device.",
                            checked = localPrefs.biometricEnabled,
                            enabled = biometricAvailable,
                            testTag = "settings_biometric_switch",
                            onCheckedChange = onToggleBiometric
                        )
                    }

                    OutlinedButton(
                        onClick = { showSafetyDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("settings_view_keys_button")
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Verify E2EE Keys & Safety Number")
                    }
                }
            }
        }

        // 4. Privacy & Notifications
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Privacy & Notifications", style = MaterialTheme.typography.titleMedium)
                    SettingsToggleRow(
                        title = "Sanctuary Notifications",
                        subtitle = "Show couple alerts for love notes, invites, and calls.",
                        checked = priv?.notificationsEnabled ?: true,
                        testTag = "settings_notif_switch",
                        onCheckedChange = { onUpdatePrivacyToggle(it, null, null) }
                    )
                    SettingsToggleRow(
                        title = "Read Receipts",
                        subtitle = "Let your partner see when you have read encrypted messages.",
                        checked = priv?.readReceiptsEnabled ?: true,
                        testTag = "settings_receipts_switch",
                        onCheckedChange = { onUpdatePrivacyToggle(null, it, null) }
                    )
                    SettingsToggleRow(
                        title = "Typing Indicators",
                        subtitle = "Show real-time typing status inside your private chat.",
                        checked = priv?.typingIndicatorsEnabled ?: true,
                        testTag = "settings_typing_switch",
                        onCheckedChange = { onUpdatePrivacyToggle(null, null, it) }
                    )
                }
            }
        }

        // 5. Safety, Legal, Data Export & Account Management
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Account, Safety & Legal", style = MaterialTheme.typography.titleMedium)

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "100% Free Forever — All features unlocked with zero ads, paywalls, or subscriptions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (couple != null && couple.status != "disconnected") {
                        OutlinedButton(
                            onClick = { showDisconnectDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("settings_disconnect_button")
                        ) {
                            Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Securely Disconnect Partner")
                        }
                    }

                    OutlinedButton(
                        onClick = { showReportDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("settings_report_button")
                    ) {
                        Icon(Icons.Filled.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Block & Report Abuse")
                    }

                    OutlinedButton(
                        onClick = onExportPersonalInfo,
                        modifier = Modifier.fillMaxWidth().testTag("settings_export_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export Personal Information (JSON)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { legalDialogTitle = "Privacy Policy" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Policy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Privacy Policy")
                        }
                        TextButton(
                            onClick = { legalDialogTitle = "Terms & Conditions" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Terms")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.weight(1f).testTag("settings_sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sign Out")
                        }
                        Button(
                            onClick = { showDeleteAccountDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.weight(1f).testTag("settings_delete_account_button")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }

        // 6. About & Developer Credit Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_developer_credit_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.developed_by_credit),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Version 1.0 • End-to-End Encrypted • 100% Free & Ad-Free",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        SetupPinDialog(
            onDismiss = { showPinDialog = false },
            onSavePin = { pin ->
                onEnablePin(pin)
                showPinDialog = false
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

    if (showReportDialog) {
        AbuseReportDialog(
            onDismiss = { showReportDialog = false },
            onSubmit = { cat, details ->
                onSubmitReport(cat, details)
                showReportDialog = false
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Securely Disconnect Partner?") },
            text = {
                Text("This permanently locks the current shared conversation and revokes future chat, media, and call access between both accounts.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        onDisconnectPartner()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account & Wipe Keys?") },
            text = {
                Text("This disconnects any active sanctuary, deletes your user profile and private account settings from Firestore, wipes your local E2EE private key from Android Keystore, and signs you out.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
            }
        )
    }

    legalDialogTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { legalDialogTitle = null },
            title = { Text(title) },
            text = {
                val body = if (title == "Privacy Policy") {
                    "SoulSync follows a zero-knowledge architecture for private content. All chat messages, love notes, voice clips, and gallery photos are encrypted on-device with ECDH P-256 + HKDF-SHA256 + AES-256-GCM before transmission. Private keys remain hardware-wrapped inside Android Keystore and are never uploaded. Personal account metadata (email & preferences) is stored in a strictly owner-isolated subcollection. You can export your personal data or delete your account at any time."
                } else {
                    "SoulSync is 100% free with no ads, in-app purchases, or paid subscriptions. It is designed strictly for consenting private 1-to-1 couples. Both partners must explicitly approve the connection before private messaging or calls unlock. Harassment, impersonation, or abuse is prohibited and can be reported directly from Settings -> Block & Report Abuse."
                }
                Text(body, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { legalDialogTitle = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SetupPinDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create App Lock PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; error = null } },
                    label = { Text("Enter 4–8 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { confirmPin = it; error = null } },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length < 4 -> error = "PIN must be at least 4 digits."
                        pin != confirmPin -> error = "PINs do not match."
                        else -> onSavePin(pin)
                    }
                }
            ) {
                Text("Enable Lock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AbuseReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    val categories = listOf("safety" to "Safety", "harassment" to "Harassment", "impersonation" to "Impersonation", "spam" to "Spam", "other" to "Other")
    var selectedCategory by remember { mutableStateOf("safety") }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Abuse or Safety Issue") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    categories.take(3).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(2000) },
                    label = { Text("Describe the concern") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedCategory, details) },
                enabled = details.trim().isNotEmpty()
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

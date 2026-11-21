package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.crypto.AppLockManager
import com.example.data.attemptAutoSignIn
import com.example.data.onGoogleSignInClicked
import com.example.ui.theme.DancingScriptFamily
import com.example.ui.theme.EncryptedEmerald
import com.example.ui.theme.HeartCoralPink
import com.example.ui.theme.InfinityViolet
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PacificoFamily
import com.example.ui.theme.SkyAzureGlow
import com.example.ui.theme.SkyAzurePrimary
import com.example.ui.theme.SkyEmblemBlue
import com.example.ui.theme.SkyGradientMid
import com.example.ui.theme.SkyGradientTop
import com.example.ui.theme.SkyMistLight
import com.example.ui.theme.SkyTwilightMid
import com.example.ui.theme.SkyTwilightTop
import com.example.ui.theme.SkyNavyInk
import kotlinx.coroutines.launch

@Composable
fun InfinityHeartEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.105f
        val cy = h * 0.60f
        val rx = w * 0.36f
        val ry = h * 0.28f

        val gradientBrush = Brush.linearGradient(
            colors = listOf(SkyAzurePrimary, SkyAzureGlow, InfinityViolet),
            start = Offset(0f, cy),
            end = Offset(w, cy)
        )

        // Smooth figure-8 infinity loop
        val infinityPath = Path().apply {
            moveTo(w * 0.50f, cy)
            cubicTo(
                w * 0.50f - rx * 0.55f, cy - ry * 1.45f,
                w * 0.50f - rx * 1.28f, cy - ry * 1.15f,
                w * 0.50f - rx * 1.15f, cy
            )
            cubicTo(
                w * 0.50f - rx * 1.05f, cy + ry * 1.15f,
                w * 0.50f - rx * 0.45f, cy + ry * 1.35f,
                w * 0.50f, cy
            )
            cubicTo(
                w * 0.50f + rx * 0.45f, cy - ry * 1.35f,
                w * 0.50f + rx * 1.05f, cy - ry * 1.15f,
                w * 0.50f + rx * 1.15f, cy
            )
            cubicTo(
                w * 0.50f + rx * 1.28f, cy + ry * 1.15f,
                w * 0.50f + rx * 0.55f, cy + ry * 1.45f,
                w * 0.50f, cy
            )
        }
        drawPath(
            path = infinityPath,
            brush = gradientBrush,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Floating gradient heart nestled above the center dip
        val heartCx = w * 0.50f
        val heartBottomY = h * 0.40f
        val heartSize = w * 0.145f
        val heartPath = Path().apply {
            moveTo(heartCx, heartBottomY)
            cubicTo(
                heartCx - heartSize * 1.35f, heartBottomY - heartSize * 0.85f,
                heartCx - heartSize * 1.05f, heartBottomY - heartSize * 2.05f,
                heartCx, heartBottomY - heartSize * 1.15f
            )
            cubicTo(
                heartCx + heartSize * 1.05f, heartBottomY - heartSize * 2.05f,
                heartCx + heartSize * 1.35f, heartBottomY - heartSize * 0.85f,
                heartCx, heartBottomY
            )
            close()
        }
        drawPath(
            path = heartPath,
            brush = Brush.linearGradient(
                colors = listOf(SkyEmblemBlue, InfinityViolet, HeartCoralPink),
                start = Offset(heartCx - heartSize, heartBottomY - heartSize * 1.6f),
                end = Offset(heartCx + heartSize, heartBottomY)
            )
        )
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { onAuthSuccess() },
            onUnauthenticated = { },
            scope = scope
        )
    }

    val skyColors = if (isDark) {
        listOf(
            SkyTwilightTop,
            SkyTwilightMid,
            MaterialTheme.colorScheme.background
        )
    } else {
        listOf(
            SkyGradientTop,
            SkyGradientMid,
            SkyMistLight,
            MaterialTheme.colorScheme.background
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = skyColors))
    ) {
        // Soft sunlit sky clouds & horizon glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cloudColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.45f)
            drawCircle(
                color = cloudColor,
                radius = size.width * 0.42f,
                center = Offset(size.width * 0.14f, size.height * 0.17f)
            )
            drawCircle(
                color = cloudColor,
                radius = size.width * 0.50f,
                center = Offset(size.width * 0.88f, size.height * 0.22f)
            )
            drawCircle(
                color = if (isDark) SkyAzureGlow.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.65f),
                radius = size.width * 0.65f,
                center = Offset(size.width * 0.50f, size.height * 0.38f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Official SoulSync App Logo
            Image(
                painter = painterResource(id = R.drawable.ic_soulsync_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(132.dp)
                    .shadow(10.dp, RoundedCornerShape(30.dp))
                    .clip(RoundedCornerShape(30.dp))
                    .border(2.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(30.dp))
                    .testTag("auth_app_logo")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = PacificoFamily,
                    fontSize = 42.sp
                ),
                color = if (isDark) Color.White else SkyNavyInk,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Connect  •  Share  •  Cherish",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = DancingScriptFamily,
                    fontSize = 23.sp
                ),
                color = if (isDark) Color.White.copy(alpha = 0.9f) else SkyNavyInk.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Clean crisp frosted-white pillars card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
                ),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.large
                    )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SecurityPillarRow(
                        icon = Icons.Filled.Lock,
                        tint = MaterialTheme.colorScheme.primary,
                        title = "True End-to-End Encryption",
                        subtitle = "ECDH P-256 + HKDF + AES-256-GCM. Private keys stay in Android Keystore."
                    )
                    SecurityPillarRow(
                        icon = Icons.Filled.VerifiedUser,
                        tint = InfinityViolet,
                        title = "Dual-Approved 1-to-1 Bond",
                        subtitle = "No public rooms. Both partners must explicitly approve before chat unlocks."
                    )
                    SecurityPillarRow(
                        icon = Icons.Filled.Shield,
                        tint = EncryptedEmerald,
                        title = "Zero Plaintext Server Storage",
                        subtitle = "Messages, love notes, voice clips, and saved chat memories stay private."
                    )
                    SecurityPillarRow(
                        icon = Icons.Filled.Favorite,
                        tint = HeartCoralPink,
                        title = "100% Free & Ad-Free Forever",
                        subtitle = "Every feature—chat, memories, calls, notes, and themes—is unlocked with zero ads."
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Signing in...",
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Together Always ♡",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = DancingScriptFamily,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(50)
                    )
                    .testTag("auth_developer_credit")
            ) {
                Text(
                    text = stringResource(R.string.developed_by_credit),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SecurityPillarRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.13f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AppLockScreen(
    biometricEnabled: Boolean,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enteredPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && AppLockManager.isBiometricHardwareAvailable(context)) {
            AppLockManager.promptBiometricUnlock(
                context = context,
                onSuccess = onUnlocked,
                onError = { errorText = it }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_soulsync_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .shadow(6.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            )

            Text(
                text = "SoulSync Vault",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Enter your private PIN to unlock your encrypted sanctuary.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // PIN dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                repeat(4.coerceAtLeast(enteredPin.length.coerceAtMost(8))) { idx ->
                    val filled = idx < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .border(
                                1.5.dp,
                                if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                    )
                }
            }

            if (errorText != null) {
                Text(
                    text = errorText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            // Keypad
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        row.forEach { digit ->
                            PinDigitButton(digit = digit) {
                                if (enteredPin.length < 8) {
                                    errorText = null
                                    enteredPin += digit
                                }
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    if (biometricEnabled && AppLockManager.isBiometricHardwareAvailable(context)) {
                        FilledTonalIconButton(
                            onClick = {
                                AppLockManager.promptBiometricUnlock(
                                    context = context,
                                    onSuccess = onUnlocked,
                                    onError = { errorText = it }
                                )
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("biometric_unlock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = "Unlock with biometrics",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(68.dp))
                    }

                    PinDigitButton(digit = "0") {
                        if (enteredPin.length < 8) {
                            errorText = null
                            enteredPin += "0"
                        }
                    }

                    FilledTonalIconButton(
                        onClick = {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                            }
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("pin_backspace_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Delete digit"
                        )
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        val valid = onVerifyPin(enteredPin)
                        if (valid) {
                            onUnlocked()
                        } else {
                            errorText = "Incorrect PIN. Please try again."
                            enteredPin = ""
                        }
                    }
                },
                enabled = enteredPin.length >= 4,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("pin_unlock_submit_button")
            ) {
                Text("Unlock Sanctuary", style = MaterialTheme.typography.titleMedium)
            }

            if (biometricEnabled) {
                TextButton(
                    onClick = {
                        AppLockManager.promptBiometricUnlock(
                            context = context,
                            onSuccess = onUnlocked,
                            onError = { errorText = it }
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = EncryptedEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use Biometrics")
                }
            }
        }
    }
}

@Composable
private fun PinDigitButton(
    digit: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 1.dp,
        modifier = Modifier
            .size(68.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .testTag("pin_digit_$digit")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

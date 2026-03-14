package com.adaptix.client.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.adaptix.client.R
import com.adaptix.client.models.ServerProfile
import com.adaptix.client.ui.components.GlassCard
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.theme.*
import com.adaptix.client.viewmodel.AppState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    state: AppState,
    savedProfiles: List<ServerProfile>,
    autoLogin: Boolean,
    onAutoLoginChange: (Boolean) -> Unit,
    onLogin: (ServerProfile) -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("4321") }
    var endpoint by remember { mutableStateOf("/endpoint") }
    var username by remember { mutableStateOf("operator") }
    var password by remember { mutableStateOf("") }
    var ssl by remember { mutableStateOf(true) }
    var profileName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Crimson.copy(alpha = 0.03f),
                        SurfaceBlack
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // App icon with glow ring
        val glowColor = Crimson.copy(alpha = 0.08f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor,
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.8f
                        )
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF282832))
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "Adaptix",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "ADAPTIX",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Crimson,
            letterSpacing = 6.sp
        )

        Text(
            text = "C2 FRAMEWORK",
            fontSize = 9.sp,
            color = TextMuted,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Version
        val context = LocalContext.current
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) { "1.0.0" }
        Text(
            text = "v$versionName",
            fontSize = 9.sp,
            color = TextMuted.copy(alpha = 0.5f),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Error card
        state.connectionError?.let { error ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RedError.copy(alpha = 0.10f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = RedError, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(error, color = RedError, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Saved profiles
        if (savedProfiles.isNotEmpty()) {
            TextButton(
                onClick = { showSaved = !showSaved },
                colors = ButtonDefaults.textButtonColors(contentColor = BlueInfo),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(
                    if (showSaved) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Saved Profiles (${savedProfiles.size})", fontSize = 12.sp)
            }

            if (showSaved) {
                Column(modifier = Modifier.heightIn(max = 150.dp)) {
                    savedProfiles.forEach { profile ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            cornerRadius = 10.dp,
                            onClick = {
                                host = profile.host
                                port = profile.port.toString()
                                endpoint = profile.endpoint
                                username = profile.username
                                password = profile.password
                                ssl = profile.ssl
                                profileName = profile.name
                                showSaved = false
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${if (profile.ssl) "https" else "http"}://${profile.host}:${profile.port}",
                                        color = TextMuted, fontSize = 10.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteProfile(profile.name) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Delete", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Form card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 14.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                        colors = fieldColors()
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = fieldColors()
                    )
                }

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Endpoint", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = TextMuted, modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )

                GlassDivider(modifier = Modifier.padding(vertical = 2.dp))

                // SSL row with glass treatment
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceCard.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SSL/TLS", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = ssl,
                            onCheckedChange = { ssl = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Crimson,
                                checkedTrackColor = CrimsonDark
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Connect button with gradient background
        val buttonEnabled = host.isNotBlank() && password.isNotBlank() && !state.isConnecting
        Button(
            onClick = {
                val name = profileName.ifBlank { "$host:$port" }
                onLogin(
                    ServerProfile(
                        name = name,
                        host = host,
                        port = port.toIntOrNull() ?: 4321,
                        endpoint = endpoint,
                        ssl = ssl,
                        username = username,
                        password = password
                    )
                )
            },
            enabled = buttonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (buttonEnabled) {
                            Brush.horizontalGradient(
                                colors = listOf(Crimson, CrimsonLight)
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    CrimsonDark.copy(alpha = 0.4f),
                                    CrimsonDark.copy(alpha = 0.4f)
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (state.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TextPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "CONNECT",
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (buttonEnabled) Color.White else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Auto-login toggle
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SurfaceCard.copy(alpha = 0.45f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Login, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Auto-login on launch", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = autoLogin,
                    onCheckedChange = onAutoLoginChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Crimson,
                        checkedTrackColor = CrimsonDark
                    )
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Crimson,
    unfocusedBorderColor = SurfaceElevated,
    cursorColor = Crimson,
    focusedLabelColor = Crimson,
    unfocusedLabelColor = TextMuted,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = SurfaceDark.copy(alpha = 0.5f),
    unfocusedContainerColor = SurfaceDark.copy(alpha = 0.5f)
)

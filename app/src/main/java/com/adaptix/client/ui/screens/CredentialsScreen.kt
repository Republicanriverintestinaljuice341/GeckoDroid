package com.adaptix.client.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptix.client.models.Credential
import com.adaptix.client.ui.components.GlassCard
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.components.GlowBadge
import com.adaptix.client.ui.theme.*
import com.adaptix.client.util.copyToClipboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(credentials: List<Credential>, onRefresh: () -> Unit = {}) {
    var showPasswords by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered by remember(credentials, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) credentials
            else credentials.filter { cred ->
                cred.username.contains(searchQuery, ignoreCase = true) ||
                    cred.realm.contains(searchQuery, ignoreCase = true) ||
                    cred.host.contains(searchQuery, ignoreCase = true) ||
                    cred.type.contains(searchQuery, ignoreCase = true) ||
                    cred.tag.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
    ) {
        // Search bar with show/hide toggle
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search credentials...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
            trailingIcon = {
                Surface(
                    onClick = { showPasswords = !showPasswords },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        0.5.dp,
                        if (showPasswords) Crimson.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f)
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (showPasswords)
                                        listOf(Crimson.copy(alpha = 0.15f), CrimsonDark.copy(alpha = 0.10f))
                                    else
                                        listOf(Color.White.copy(alpha = 0.04f), SurfaceElevated.copy(alpha = 0.5f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            if (showPasswords) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null,
                            tint = if (showPasswords) Crimson else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (showPasswords) "Hide" else "Show",
                            color = if (showPasswords) Crimson else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Crimson,
                unfocusedBorderColor = Color.White.copy(alpha = 0.06f),
                cursorColor = Crimson,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = SurfaceDark.copy(alpha = 0.5f),
                unfocusedContainerColor = SurfaceDark.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Count with divider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                "${filtered.size} credential${if (filtered.size != 1) "s" else ""}",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
        GlassDivider(modifier = Modifier.padding(horizontal = 12.dp))

        Spacer(modifier = Modifier.height(4.dp))

        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(credentials) { isRefreshing = false }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; onRefresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Key,
                            null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (credentials.isEmpty()) "No credentials" else "No matching credentials",
                            color = TextMuted.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.credId }) { cred ->
                        CredentialCard(cred, showPasswords)
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialCard(cred: Credential, showPassword: Boolean) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Username row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = BlueInfo.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            tint = BlueInfo,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (cred.realm.isNotBlank()) "${cred.realm}\\${cred.username}" else cred.username,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val fullUsername = if (cred.realm.isNotBlank()) "${cred.realm}\\${cred.username}" else cred.username
                            copyToClipboard(context, "Username", fullUsername)
                        }
                )
                if (cred.type.isNotBlank()) {
                    GlowBadge(
                        text = cred.type,
                        color = PurpleAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Password row
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    0.5.dp,
                    if (showPassword) YellowWarning.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { copyToClipboard(context, "Password", cred.password) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = if (showPassword)
                                    listOf(YellowWarning.copy(alpha = 0.06f), Color.Transparent)
                                else
                                    listOf(SurfaceElevated.copy(alpha = 0.4f), SurfaceElevated.copy(alpha = 0.4f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Key,
                        null,
                        tint = if (showPassword) YellowWarning else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (showPassword) cred.password else "\u2022".repeat(minOf(cred.password.length, 16)),
                        color = if (showPassword) YellowWarning else TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ContentCopy,
                        null,
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Info row
            if (cred.host.isNotBlank() || cred.storage.isNotBlank() || cred.tag.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cred.host.isNotBlank()) {
                        Icon(
                            Icons.Default.Computer,
                            null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            cred.host,
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { copyToClipboard(context, "Host", cred.host) }
                        )
                    }
                    if (cred.host.isNotBlank() && (cred.storage.isNotBlank() || cred.tag.isNotBlank())) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .width(1.dp)
                                .height(12.dp)
                                .background(DividerColor)
                        )
                    }
                    if (cred.storage.isNotBlank()) {
                        Icon(
                            Icons.Default.Storage,
                            null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(cred.storage, color = TextMuted, fontSize = 11.sp)
                    }
                    if (cred.storage.isNotBlank() && cred.tag.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .width(1.dp)
                                .height(12.dp)
                                .background(DividerColor)
                        )
                    }
                    if (cred.tag.isNotBlank()) {
                        Text("#${cred.tag}", color = Crimson, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

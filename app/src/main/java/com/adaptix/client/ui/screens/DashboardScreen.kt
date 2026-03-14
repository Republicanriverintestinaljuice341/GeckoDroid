package com.adaptix.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptix.client.ui.components.GlassCard
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.navigation.Screen
import com.adaptix.client.util.copyToClipboard
import com.adaptix.client.ui.theme.*
import com.adaptix.client.viewmodel.AppState

@Composable
fun DashboardScreen(state: AppState, onNavigate: (Screen) -> Unit, onAgentClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Crimson.copy(alpha = 0.02f),
                        SurfaceBlack
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Connection status bar
        val statusColor = if (state.wsConnected) GreenOnline else RedError
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient highlight strip at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                statusColor.copy(alpha = 0.4f),
                                statusColor.copy(alpha = 0.6f),
                                statusColor.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Status dot with outer glow
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .drawBehind {
                                drawCircle(
                                    color = statusColor.copy(alpha = 0.2f),
                                    radius = 20f
                                )
                            }
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (state.wsConnected) "Connected" else "Disconnected",
                        color = statusColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    state.serverProfile?.let {
                        Text(
                            text = "${it.host}:${it.port}",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { copyToClipboard(context, "Server", "${it.host}:${it.port}") }
                        )
                    }
                }
                if (state.serverProfile != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "User: ${state.serverProfile.username}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(10.dp)
                                .background(DividerColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Endpoint: ${state.serverProfile.endpoint}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats overview — 2x2 grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Agents",
                value = state.agents.size.toString(),
                subtitle = "${state.agents.count { it.isAlive }} active",
                icon = Icons.Default.Devices,
                color = Crimson,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Agents) }
            )
            StatCard(
                title = "Listeners",
                value = state.listeners.size.toString(),
                subtitle = "${state.listeners.count { it.isRunning }} running",
                icon = Icons.Default.Sensors,
                color = BlueInfo,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Listeners) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Downloads",
                value = state.downloads.size.toString(),
                subtitle = "${state.downloads.count { it.state == 1 }} active",
                icon = Icons.Default.CloudDownload,
                color = GreenOnline,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Downloads) }
            )
            StatCard(
                title = "Screenshots",
                value = state.screenshots.size.toString(),
                subtitle = "${state.screenshots.size} captured",
                icon = Icons.Default.Screenshot,
                color = CrimsonLight,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Screenshots) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary nav
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryNavButton(
                label = "Credentials",
                icon = Icons.Default.Key,
                count = state.credentials.size,
                color = YellowWarning,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Credentials) }
            )
            SecondaryNavButton(
                label = "Tunnels",
                icon = Icons.Default.SwapHoriz,
                count = state.tunnels.size,
                color = PurpleAccent,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Tunnels) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Chat card
            SecondaryNavButton(
                label = "Chat",
                icon = Icons.AutoMirrored.Filled.Chat,
                count = state.chatMessages.size,
                color = BlueInfo,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Chat) }
            )
            // Generate card
            SecondaryNavButton(
                label = "Generate",
                icon = Icons.Default.Build,
                count = 0,
                color = Crimson,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(Screen.Generate) },
                showCount = false
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Recent Agents section
        Text(
            "Recent Agents",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        GlassDivider()

        Spacer(modifier = Modifier.height(12.dp))

        if (state.agents.isEmpty()) {
            // Empty state
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No agents connected",
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            state.agents.take(5).forEach { agent ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = { onAgentClick(agent.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status dot with colored background
                        val dotColor = if (agent.isAlive) GreenOnline else TextMuted
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            dotColor.copy(alpha = 0.2f),
                                            dotColor.copy(alpha = 0.05f)
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (agent.elevated) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = "Elevated",
                                        tint = Crimson,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    agent.displayName,
                                    color = if (agent.elevated) Crimson else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${agent.osName} | ${agent.process} (${agent.pid})",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            agent.internalIp,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color.copy(alpha = 0.2f),
                                    color.copy(alpha = 0.05f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    value,
                    color = color,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SecondaryNavButton(
    label: String,
    icon: ImageVector,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
    showCount: Boolean = true,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Box {
            // Left accent line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .matchParentSize()
                    .background(
                        color = color.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .align(Alignment.CenterStart)
            )
            // Horizontal gradient overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color.copy(alpha = 0.2f),
                                    color.copy(alpha = 0.05f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    label,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                if (showCount) {
                    Text(
                        count.toString(),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

package com.adaptix.client.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptix.client.models.Tunnel
import com.adaptix.client.ui.components.ConfirmDialog
import com.adaptix.client.ui.components.GlassCard
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.components.GlowBadge
import com.adaptix.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelsScreen(
    tunnels: List<Tunnel>,
    onStopTunnel: (String) -> Unit,
    onRefresh: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
    ) {
        // Header with count and divider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "${tunnels.size} tunnel${if (tunnels.size != 1) "s" else ""}",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
        GlassDivider(modifier = Modifier.padding(horizontal = 12.dp))

        Spacer(modifier = Modifier.height(4.dp))

        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(tunnels) { isRefreshing = false }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; onRefresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (tunnels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No active tunnels",
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
                    items(tunnels, key = { it.tunnelId }) { tunnel ->
                        TunnelCard(tunnel) { onStopTunnel(tunnel.tunnelId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TunnelCard(tunnel: Tunnel, onStop: () -> Unit) {
    var showConfirmStop by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Type icon with radial gradient background
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    PurpleAccent.copy(alpha = 0.25f),
                                    PurpleAccent.copy(alpha = 0.05f)
                                )
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        null,
                        tint = PurpleAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    tunnel.type,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                // Stop button — glass with subtle red glow
                val stopGlow = RedError.copy(alpha = 0.10f)
                val stopBorder = RedError.copy(alpha = 0.20f)
                Surface(
                    onClick = { showConfirmStop = true },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, stopBorder),
                    modifier = Modifier
                        .size(32.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = stopGlow,
                                cornerRadius = CornerRadius(8.dp.toPx()),
                                size = size.copy(
                                    width = size.width + 2.dp.toPx(),
                                    height = size.height + 2.dp.toPx()
                                ),
                                topLeft = Offset(-1.dp.toPx(), -1.dp.toPx())
                            )
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        RedError.copy(alpha = 0.15f),
                                        RedError.copy(alpha = 0.05f)
                                    )
                                )
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Stop tunnel",
                            tint = RedError,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bind / Forward badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlowBadge(
                    text = "Bind  ${tunnel.iface}:${tunnel.port}",
                    color = BlueInfo
                )
                if (tunnel.fhost.isNotBlank()) {
                    GlowBadge(
                        text = "Fwd  ${tunnel.fhost}:${tunnel.fport}",
                        color = GreenOnline
                    )
                }
            }

            if (tunnel.info.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(tunnel.info, color = TextSecondary, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User/computer info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    tunnel.username,
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .height(12.dp)
                        .background(DividerColor)
                )
                Icon(
                    Icons.Default.Computer,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    tunnel.computer,
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .height(12.dp)
                        .background(DividerColor)
                )
                Text(
                    tunnel.process,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (showConfirmStop) {
            ConfirmDialog(
                title = "Stop Tunnel",
                message = "Stop this ${tunnel.type} tunnel?",
                confirmText = "Stop",
                onConfirm = { showConfirmStop = false; onStop() },
                onDismiss = { showConfirmStop = false }
            )
        }
    }
}

package com.adaptix.client.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptix.client.models.Agent
import com.adaptix.client.ui.components.ConfirmDialog
import com.adaptix.client.ui.components.GlassCard
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.components.GlowBadge
import com.adaptix.client.ui.theme.*
import com.adaptix.client.util.copyToClipboard
import com.adaptix.client.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    agents: List<Agent>,
    onAgentClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onRemove: (List<String>) -> Unit,
    sortBy: String = "last_seen",
    onSortChange: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterAliveOnly by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val filtered by remember(agents, searchQuery, filterAliveOnly, sortBy) {
        derivedStateOf {
            val list = agents.filter { agent ->
                val matchesSearch = searchQuery.isBlank() ||
                    agent.displayName.contains(searchQuery, ignoreCase = true) ||
                    agent.internalIp.contains(searchQuery) ||
                    agent.externalIp.contains(searchQuery) ||
                    agent.computer.contains(searchQuery, ignoreCase = true)
                val matchesFilter = !filterAliveOnly || agent.isAlive
                matchesSearch && matchesFilter
            }
            when (sortBy) {
                "name" -> list.sortedBy { it.displayName.lowercase() }
                "os" -> list.sortedBy { it.osName.lowercase() }
                "elevated" -> list.sortedByDescending { it.elevated }
                else -> list.sortedByDescending { it.lastTick } // last_seen
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
    ) {
        // Search bar — glass treatment
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search agents...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
            trailingIcon = {
                Row {
                    IconButton(onClick = { filterAliveOnly = !filterAliveOnly }) {
                        Icon(
                            Icons.Default.FilterList,
                            null,
                            tint = if (filterAliveOnly) Crimson else TextMuted
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, null, tint = if (sortBy != "last_seen") Crimson else TextMuted)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(SurfaceCard, RoundedCornerShape(12.dp))
                        ) {
                            listOf(
                                "last_seen" to "Last Seen",
                                "name" to "Name",
                                "os" to "OS",
                                "elevated" to "Elevated"
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label,
                                            color = if (sortBy == key) Crimson else TextPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = { onSortChange(key); showSortMenu = false }
                                )
                            }
                        }
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

        // Count with glass divider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                "${filtered.size} agent${if (filtered.size != 1) "s" else ""}",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
        GlassDivider(modifier = Modifier.padding(horizontal = 12.dp))

        Spacer(modifier = Modifier.height(4.dp))

        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(agents) { isRefreshing = false }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; onRefresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Devices,
                            null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (agents.isEmpty()) "No agents connected" else "No matching agents",
                            color = TextMuted.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { agent ->
                        AgentCard(
                            agent = agent,
                            onClick = { onAgentClick(agent.id) },
                            onRemove = { onRemove(listOf(agent.id)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentCard(agent: Agent, onClick: () -> Unit, onRemove: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmRemove by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val statusColor = if (agent.isAlive) GreenOnline else TextMuted

    Box {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            cornerRadius = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Status indicator with outer glow
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 10.dp, top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .drawBehind {
                                // Outer glow
                                drawCircle(
                                    color = statusColor.copy(alpha = 0.3f),
                                    radius = size.minDimension / 2f + 4.dp.toPx(),
                                    center = Offset(size.width / 2f, size.height / 2f)
                                )
                                // Inner dot
                                drawCircle(
                                    color = statusColor,
                                    radius = size.minDimension / 2f,
                                    center = Offset(size.width / 2f, size.height / 2f)
                                )
                            }
                    )
                    if (agent.elevated) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Elevated",
                            tint = YellowWarning,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            agent.displayName,
                            color = if (agent.elevated) Crimson else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (agent.tags.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            GlowBadge(
                                text = agent.tags,
                                color = Crimson
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "${agent.osName} | ${agent.process} (${agent.pid})",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // IP badge
                        Box(modifier = Modifier.clickable { copyToClipboard(context, "IP", agent.internalIp) }) {
                            GlowBadge(
                                text = agent.internalIp,
                                color = BlueInfo
                            )
                        }
                        // Sleep badge
                        GlowBadge(
                            text = "Sleep: ${agent.sleepDisplay}",
                            color = TextMuted
                        )
                        // Listener badge
                        GlowBadge(
                            text = agent.listener,
                            color = TextMuted
                        )
                    }
                }

                // Last seen
                Text(
                    formatRelativeTime(agent.lastTick),
                    color = if (agent.isAlive) GreenOnline else TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = DpOffset(12.dp, 0.dp),
            modifier = Modifier.background(SurfaceCard.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
        ) {
            DropdownMenuItem(
                text = { Text("Remove Agent", color = RedError, fontSize = 13.sp) },
                onClick = {
                    showMenu = false
                    showConfirmRemove = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(18.dp))
                }
            )
        }

        if (showConfirmRemove) {
            ConfirmDialog(
                title = "Remove Agent",
                message = "Remove ${agent.displayName}? This cannot be undone.",
                confirmText = "Remove",
                onConfirm = { showConfirmRemove = false; onRemove() },
                onDismiss = { showConfirmRemove = false }
            )
        }
    }
}

package com.adaptix.client.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptix.client.models.Download
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.theme.*
import com.adaptix.client.util.formatBytes
import com.adaptix.client.util.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(downloads: List<Download>, onRefresh: () -> Unit = {}) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered by remember(downloads, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) downloads
            else downloads.filter { dl ->
                dl.fileName.contains(searchQuery, ignoreCase = true) ||
                    dl.file.contains(searchQuery, ignoreCase = true) ||
                    dl.computer.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search downloads...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Crimson,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f),
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
                "${filtered.size} download${if (filtered.size != 1) "s" else ""}",
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            val active = filtered.count { it.state == 1 }
            if (active > 0) {
                Text(
                    "$active active",
                    color = BlueInfo,
                    fontSize = 12.sp
                )
            }
        }
        GlassDivider(modifier = Modifier.padding(horizontal = 12.dp))

        Spacer(modifier = Modifier.height(4.dp))

        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(downloads) { isRefreshing = false }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; onRefresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudDownload,
                            null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (downloads.isEmpty()) "No downloads" else "No matching downloads",
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
                    items(filtered, key = { it.fileId }) { dl ->
                        DownloadCard(dl)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(dl: Download) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (dl.state) {
                        1 -> Icons.Default.CloudDownload
                        3 -> Icons.Default.CheckCircle
                        else -> Icons.Default.Pause
                    },
                    null,
                    tint = when (dl.state) {
                        1 -> BlueInfo
                        3 -> GreenOnline
                        else -> YellowWarning
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    dl.fileName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = when (dl.state) {
                        1 -> BlueInfo.copy(alpha = 0.15f)
                        3 -> GreenOnline.copy(alpha = 0.15f)
                        else -> YellowWarning.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        dl.stateName,
                        color = when (dl.state) {
                            1 -> BlueInfo
                            3 -> GreenOnline
                            else -> YellowWarning
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                dl.file,
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar
            if (dl.totalSize > 0) {
                LinearProgressIndicator(
                    progress = { dl.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (dl.state == 3) GreenOnline else BlueInfo,
                    trackColor = SurfaceElevated
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Info row with dividers
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${formatBytes(dl.recvSize)} / ${formatBytes(dl.totalSize)}",
                    color = TextSecondary,
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
                    dl.computer,
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
                    formatTimestamp(dl.date),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

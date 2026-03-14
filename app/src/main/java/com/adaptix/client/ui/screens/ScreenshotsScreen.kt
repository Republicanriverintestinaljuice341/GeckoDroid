package com.adaptix.client.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.adaptix.client.models.Screenshot
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.theme.*
import com.adaptix.client.util.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotsScreen(
    screenshots: List<Screenshot>,
    onFetchImage: (String, (ByteArray?) -> Unit) -> Unit,
    onRemove: (List<String>) -> Unit,
    onRefresh: () -> Unit = {}
) {
    var selectedScreenshot by remember { mutableStateOf<Screenshot?>(null) }
    val imageCache = remember { mutableStateMapOf<String, Bitmap?>() }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    // Exit selection mode if screenshots list changes and selection is empty
    LaunchedEffect(screenshots.size) {
        selectedIds.removeAll { id -> screenshots.none { it.screenId == id } }
        if (selectedIds.isEmpty()) selectionMode = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
    ) {
        // Header row
        if (selectionMode) {
            // Selection toolbar
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(onClick = {
                        selectionMode = false
                        selectedIds.clear()
                    }) {
                        Icon(Icons.Default.Close, "Cancel", tint = TextSecondary)
                    }
                    Text(
                        "${selectedIds.size} selected",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    // Select all
                    Surface(
                        onClick = {
                            if (selectedIds.size == screenshots.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(screenshots.map { it.screenId })
                            }
                        },
                        color = Crimson.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (selectedIds.size == screenshots.size) "Deselect All" else "Select All",
                            color = Crimson,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Delete selected
                    Surface(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                val ids = selectedIds.toList()
                                ids.forEach { imageCache.remove(it)?.recycle() }
                                onRemove(ids)
                                selectedIds.clear()
                                selectionMode = false
                            }
                        },
                        color = if (selectedIds.isNotEmpty()) RedError.copy(alpha = 0.12f) else SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = if (selectedIds.isNotEmpty()) RedError else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "${screenshots.size} screenshot${if (screenshots.size != 1) "s" else ""}",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
        GlassDivider(modifier = Modifier.padding(horizontal = 12.dp))

        Spacer(modifier = Modifier.height(4.dp))

        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(screenshots) { isRefreshing = false }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; onRefresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (screenshots.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Screenshot,
                            null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No screenshots",
                            color = TextMuted.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(screenshots, key = { it.screenId }) { ss ->
                        val isSelected = ss.screenId in selectedIds
                        ScreenshotCard(
                            ss = ss,
                            bitmap = imageCache[ss.screenId],
                            onFetchImage = onFetchImage,
                            onBitmapLoaded = { imageCache[ss.screenId] = it },
                            isSelected = isSelected,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    if (isSelected) selectedIds.remove(ss.screenId)
                                    else selectedIds.add(ss.screenId)
                                    if (selectedIds.isEmpty()) selectionMode = false
                                } else {
                                    selectedScreenshot = ss
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds.add(ss.screenId)
                                }
                            },
                            onRemove = {
                                imageCache.remove(ss.screenId)?.recycle()
                                onRemove(listOf(ss.screenId))
                            }
                        )
                    }
                }
            }
        }
    }

    // Fullscreen viewer
    selectedScreenshot?.let { ss ->
        ScreenshotDialog(
            ss = ss,
            bitmap = imageCache[ss.screenId],
            onFetchImage = onFetchImage,
            onBitmapLoaded = { imageCache[ss.screenId] = it },
            onDismiss = { selectedScreenshot = null },
            onRemove = {
                imageCache.remove(ss.screenId)?.recycle()
                onRemove(listOf(ss.screenId))
                selectedScreenshot = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenshotCard(
    ss: Screenshot,
    bitmap: Bitmap?,
    onFetchImage: (String, (ByteArray?) -> Unit) -> Unit,
    onBitmapLoaded: (Bitmap?) -> Unit,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit
) {
    var fetchStarted by remember(ss.screenId) { mutableStateOf(bitmap != null) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(ss.screenId) {
        if (!fetchStarted) {
            fetchStarted = true
            onFetchImage(ss.screenId) { bytes ->
                if (bytes != null && bytes.isNotEmpty()) {
                    try {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        onBitmapLoaded(bmp)
                    } catch (_: Exception) {
                        onBitmapLoaded(null)
                    }
                } else {
                    onBitmapLoaded(null)
                }
            }
        }
    }

    val borderMod = if (isSelected) {
        Modifier.border(2.dp, Crimson, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(borderMod)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (!selectionMode) onLongClick()
                        else showMenu = true
                    }
                )
        ) {
            Box {
                Column {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Screenshot",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!fetchStarted) {
                                CircularProgressIndicator(
                                    color = Crimson,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.BrokenImage, null, tint = TextMuted)
                            }
                        }
                    }
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "${ss.user.orEmpty()}@${ss.computer.orEmpty()}",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            formatTimestamp(ss.date),
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                // Selection checkbox overlay
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Icon(
                            if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (isSelected) Crimson else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Context menu (only in non-selection mode, via long press handled above)
        if (!selectionMode) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset(12.dp, 0.dp),
                modifier = Modifier.background(SurfaceDark)
            ) {
                DropdownMenuItem(
                    text = { Text("Remove", color = RedError, fontSize = 13.sp) },
                    onClick = {
                        showMenu = false
                        onRemove()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun ScreenshotDialog(
    ss: Screenshot,
    bitmap: Bitmap?,
    onFetchImage: (String, (ByteArray?) -> Unit) -> Unit,
    onBitmapLoaded: (Bitmap?) -> Unit,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    var fetchStarted by remember(ss.screenId) { mutableStateOf(bitmap != null) }
    LaunchedEffect(ss.screenId) {
        if (!fetchStarted) {
            fetchStarted = true
            onFetchImage(ss.screenId) { bytes ->
                if (bytes != null && bytes.isNotEmpty()) {
                    try {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        onBitmapLoaded(bmp)
                    } catch (_: Exception) {
                        onBitmapLoaded(null)
                    }
                } else {
                    onBitmapLoaded(null)
                }
            }
        }
    }

    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SurfaceBlack,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${ss.user.orEmpty()}@${ss.computer.orEmpty()}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    // Save to gallery
                    Surface(
                        onClick = {
                            bitmap?.let { bmp ->
                                try {
                                    val values = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, "adaptix_${ss.screenId}.png")
                                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Adaptix")
                                        put(MediaStore.Images.Media.IS_PENDING, 1)
                                    }
                                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                    if (uri != null) {
                                        context.contentResolver.openOutputStream(uri)?.use { out ->
                                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }
                                        values.clear()
                                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                                        context.contentResolver.update(uri, values, null, null)
                                        Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        color = GreenOnline.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.SaveAlt, null, tint = GreenOnline, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    // Share
                    Surface(
                        onClick = {
                            bitmap?.let { bmp ->
                                try {
                                    val values = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, "adaptix_share_${ss.screenId}.png")
                                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Adaptix")
                                        put(MediaStore.Images.Media.IS_PENDING, 1)
                                    }
                                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                    if (uri != null) {
                                        context.contentResolver.openOutputStream(uri)?.use { out ->
                                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }
                                        values.clear()
                                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                                        context.contentResolver.update(uri, values, null, null)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share screenshot"))
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        color = BlueInfo.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Share, null, tint = BlueInfo, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        onClick = onRemove,
                        color = RedError.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        onClick = onDismiss,
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(SurfaceElevated, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Crimson, modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!ss.note.isNullOrBlank()) {
                    Text(
                        ss.note.orEmpty(),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    formatTimestamp(ss.date),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

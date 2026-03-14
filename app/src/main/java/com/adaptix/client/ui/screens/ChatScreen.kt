package com.adaptix.client.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptix.client.models.ChatMessage
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.components.GlassSurface
import com.adaptix.client.ui.theme.*
import com.adaptix.client.util.formatTimestamp

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    currentUser: String,
    onSend: (String) -> Unit,
    onClear: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var cleared by remember { mutableStateOf(false) }
    val con = LocalConsoleColors

    val displayMessages = if (cleared && messages.isEmpty()) emptyList() else {
        if (messages.isNotEmpty()) { cleared = false }
        messages
    }

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(con.background)
            .imePadding()
    ) {
        // Header
        GlassSurface(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = con.inputColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Operator Chat",
                        color = con.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "${displayMessages.size} messages",
                        color = con.taskColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (displayMessages.isNotEmpty()) {
                    IconButton(
                        onClick = { cleared = true; onClear() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, "Clear", tint = con.operatorColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = con.debugColor)

        // Messages area with background image
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Background image with dimming overlay
            con.backgroundImage?.let { bgRes ->
                Image(
                    painter = painterResource(id = bgRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = con.backgroundDimming))
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                if (displayMessages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = con.taskColor.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "No messages yet",
                                    color = con.taskColor.copy(alpha = 0.5f),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                itemsIndexed(displayMessages) { _, msg ->
                    val isMe = msg.username == currentUser
                    // Desktop format: timestamp [username] :: message
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 1.dp)
                    ) {
                        // Timestamp
                        Text(
                            formatTimestamp(msg.date),
                            color = con.operatorColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            " [",
                            color = con.text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        // Username — green for self, different for others (matches desktop)
                        Text(
                            msg.username,
                            color = if (isMe) con.successColor else con.agentMarkerColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "] :: ",
                            color = con.text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        // Message
                        Text(
                            msg.message,
                            color = con.text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Input area
        HorizontalDivider(thickness = 1.dp, color = con.debugColor)

        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    currentUser,
                    color = con.agentMarkerColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp)
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            "Message...",
                            color = con.taskColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = con.inputColor,
                        unfocusedBorderColor = con.debugColor,
                        cursorColor = con.inputColor,
                        focusedTextColor = con.text,
                        unfocusedTextColor = con.text,
                        focusedContainerColor = con.background,
                        unfocusedContainerColor = con.background
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) { onSend(input.trim()); input = "" }
                    }),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { if (input.isNotBlank()) { onSend(input.trim()); input = "" } },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = con.inputColor)
                }
            }
        }
    }
}

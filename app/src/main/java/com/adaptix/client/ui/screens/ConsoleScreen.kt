package com.adaptix.client.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptix.client.models.Agent
import com.adaptix.client.models.AgentTask
import com.adaptix.client.ui.theme.*
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.components.GlassSurface
import com.adaptix.client.util.copyToClipboard
import com.adaptix.client.util.formatTimestamp
import com.adaptix.client.viewmodel.RegisteredCommand

private data class CommandDef(
    val command: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    agent: Agent?,
    tasks: List<AgentTask>,
    onExecute: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit = {},
    registeredCommands: List<RegisteredCommand> = emptyList(),
    favoriteCommands: List<String> = emptyList(),
    onAddFavorite: (String) -> Unit = {},
    onRemoveFavorite: (String) -> Unit = {}
) {
    var command by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    var showDetails by remember { mutableStateOf(false) }

    // Build flat command list from dynamic server commands
    val dynamicCommands by remember(registeredCommands) {
        derivedStateOf {
            val flat = mutableListOf<CommandDef>()
            fun flatten(cmds: List<RegisteredCommand>, prefix: String = "") {
                for (cmd in cmds) {
                    val fullName = if (prefix.isEmpty()) cmd.name else "$prefix ${cmd.name}"
                    if (cmd.subcommands.isNotEmpty()) {
                        flatten(cmd.subcommands, fullName)
                    } else {
                        flat.add(CommandDef(fullName, cmd.description))
                    }
                    if (cmd.subcommands.isNotEmpty() && cmd.description.isNotBlank()) {
                        flat.add(CommandDef(fullName, cmd.description))
                    }
                }
            }
            flatten(registeredCommands)
            flat.add(CommandDef("help", "Show all available commands"))
            flat.distinctBy { it.command }
        }
    }

    val dynamicChips by remember(registeredCommands) {
        derivedStateOf {
            registeredCommands.map { it.name }.sorted()
        }
    }

    // Local help pseudo-tasks
    var helpItems by remember { mutableStateOf(listOf<AgentTask>()) }
    val displayTasks by remember(tasks, helpItems) {
        derivedStateOf { tasks + helpItems }
    }

    // Auto-scroll to bottom on any task change
    LaunchedEffect(tasks, helpItems) {
        if (displayTasks.isNotEmpty()) {
            listState.animateScrollToItem(displayTasks.size - 1)
        }
    }

    // Filter suggestions based on current input
    val suggestions by remember(command, dynamicCommands, dynamicChips) {
        derivedStateOf {
            val input = command.text.trim().lowercase()
            if (input.isEmpty()) {
                dynamicChips
            } else {
                val matched = dynamicCommands
                    .filter { it.command.lowercase().startsWith(input) }
                    .map { it.command }
                if (matched.isEmpty()) {
                    dynamicChips.filter { it.startsWith(input) }
                } else {
                    matched
                }
            }
        }
    }

    fun buildDynamicHelpText(): String {
        if (registeredCommands.isEmpty()) return "  No commands registered. Waiting for server..."
        val sb = StringBuilder()
        sb.appendLine("  Available Commands")
        sb.appendLine("  ==================")
        sb.appendLine()
        for (cmd in registeredCommands) {
            if (cmd.subcommands.isEmpty()) {
                sb.appendLine("    ${cmd.name.padEnd(30)} ${cmd.description}")
            } else {
                sb.appendLine("  ${cmd.name}: ${cmd.description}")
                for (sub in cmd.subcommands) {
                    sb.appendLine("    ${cmd.name} ${sub.name.padEnd(22)} ${sub.description}")
                }
            }
        }
        sb.appendLine()
        sb.appendLine("    help                          Show this help")
        return sb.toString()
    }

    fun handleSubmit() {
        val cmd = command.text.trim()
        if (cmd.isBlank()) return

        if (cmd.lowercase() == "help") {
            val helpTask = AgentTask(
                taskId = "help-${System.currentTimeMillis()}",
                commandLine = "help",
                client = "local",
                completed = true,
                clearText = buildDynamicHelpText(),
                message = "",
                messageType = 5
            )
            helpItems = helpItems + helpTask
        } else {
            onExecute(cmd)
        }
        command = TextFieldValue("")
    }

    val con = LocalConsoleColors

    Scaffold(
        containerColor = con.background,
        topBar = {
            Column {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = con.operatorColor)
                        }
                        if (agent != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (agent.isAlive) Color(0xFF40C070) else Color(0xFFF05560)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showDetails = !showDetails }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (agent.elevated) {
                                        Icon(
                                            Icons.Default.Shield,
                                            contentDescription = "Elevated",
                                            tint = Color(0xFFB82838),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        agent.displayName,
                                        color = if (agent.elevated) Color(0xFFB82838) else con.agentMarkerColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        null,
                                        tint = con.operatorColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    "${agent.osName} | ${agent.internalIp} | PID ${agent.pid}",
                                    color = con.operatorColor,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                agent.sleepDisplay,
                                color = con.taskColor,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    onClear()
                                    helpItems = emptyList()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, "Clear console", tint = con.operatorColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                // Expandable agent details panel — outside GlassSurface, tappable to collapse
                if (agent != null) {
                    AnimatedVisibility(
                        visible = showDetails,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        AgentDetailsPanel(agent, con, onDismiss = { showDetails = false })
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = con.debugColor)
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = con.debugColor)
                GlassSurface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        // Command suggestion chips
                        if (suggestions.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                suggestions.forEach { suggestion ->
                                    SuggestionChip(
                                        onClick = {
                                            val text = "$suggestion "
                                            command = TextFieldValue(text, TextRange(text.length))
                                        },
                                        label = {
                                            Text(
                                                suggestion,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = con.text
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = con.background.copy(alpha = 0.8f),
                                            labelColor = con.text
                                        ),
                                        border = BorderStroke(1.dp, con.inputColor.copy(alpha = 0.4f)),
                                        modifier = Modifier.height(30.dp)
                                    )
                                }
                            }
                        }

                        // Favorite commands row
                        if (favoriteCommands.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    null,
                                    tint = YellowWarning.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .align(Alignment.CenterVertically)
                                )
                                favoriteCommands.forEach { fav ->
                                    SuggestionChip(
                                        onClick = {
                                            val text = "$fav "
                                            command = TextFieldValue(text, TextRange(text.length))
                                        },
                                        label = {
                                            Text(
                                                fav,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = YellowWarning
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = YellowWarning.copy(alpha = 0.08f),
                                            labelColor = YellowWarning
                                        ),
                                        border = BorderStroke(1.dp, YellowWarning.copy(alpha = 0.3f)),
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }

                        // Input row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Favorite toggle button
                            val cmdText = command.text.trim()
                            val isFav = cmdText.isNotBlank() && cmdText in favoriteCommands
                            IconButton(
                                onClick = {
                                    if (cmdText.isNotBlank()) {
                                        if (isFav) onRemoveFavorite(cmdText) else onAddFavorite(cmdText)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                    "Favorite",
                                    tint = if (isFav) YellowWarning else con.taskColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                con.inputSymbol,
                                color = con.inputColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 4.dp, end = 6.dp)
                            )
                            OutlinedTextField(
                                value = command,
                                onValueChange = { command = it },
                                placeholder = {
                                    Text(
                                        "Enter command...",
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
                                    focusedTextColor = con.commandColor,
                                    unfocusedTextColor = con.commandColor,
                                    focusedContainerColor = con.background,
                                    unfocusedContainerColor = con.background
                                ),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { handleSubmit() }),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { handleSubmit() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = con.inputColor)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Background image with dimming overlay
            con.backgroundImage?.let { bgRes ->
                Image(
                    painter = painterResource(id = bgRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Dimming overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = con.backgroundDimming))
                )
            }

            if (displayTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Terminal,
                            null,
                            tint = con.taskColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No commands yet",
                            color = con.taskColor.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Type a command below to get started",
                            color = con.taskColor.copy(alpha = 0.3f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(displayTasks) { task ->
                        TaskItem(task, con)
                        HorizontalDivider(thickness = 0.5.dp, color = con.debugColor.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentDetailsPanel(agent: Agent, con: ConsoleColors, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .clickable { onDismiss() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        @Composable
        fun DetailRow(label: String, value: String) {
            if (value.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { copyToClipboard(context, label, value) }
                        )
                        .padding(vertical = 1.dp)
                ) {
                    Text(
                        label,
                        color = con.operatorColor.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(110.dp)
                    )
                    Text(
                        value,
                        color = con.text,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section: Identity
        DetailRow("Agent ID", agent.id)
        DetailRow("Agent Type", agent.name)
        DetailRow("Listener", agent.listener)

        HorizontalDivider(thickness = 0.5.dp, color = con.debugColor.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))

        // Section: Host
        DetailRow("Username", agent.username)
        DetailRow("Computer", agent.computer)
        DetailRow("Domain", agent.domain)
        if (agent.impersonated.isNotBlank()) {
            DetailRow("Impersonated", agent.impersonated)
        }
        DetailRow("Internal IP", agent.internalIp)
        DetailRow("External IP", agent.externalIp)

        HorizontalDivider(thickness = 0.5.dp, color = con.debugColor.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))

        // Section: System
        DetailRow("OS", "${agent.osName} — ${agent.osDesc}")
        DetailRow("Architecture", agent.arch)
        DetailRow("Process", agent.process)
        DetailRow("PID / TID", "${agent.pid} / ${agent.tid}")
        DetailRow("Elevated", if (agent.elevated) "Yes" else "No")

        HorizontalDivider(thickness = 0.5.dp, color = con.debugColor.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))

        // Section: Timing
        DetailRow("Sleep", "${agent.sleepDisplay} (jitter ${agent.jitter}%)")
        if (agent.createTime > 0) {
            DetailRow("First Seen", formatTimestamp(agent.createTime))
        }
        if (agent.lastTick > 0) {
            DetailRow("Last Seen", formatTimestamp(agent.lastTick))
        }
        if (agent.tags.isNotBlank()) {
            DetailRow("Tags", agent.tags)
        }
        if (agent.target.isNotBlank()) {
            DetailRow("Target", agent.target)
        }

        // Collapse hint
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.ExpandLess, null, tint = con.operatorColor.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskItem(task: AgentTask, con: ConsoleColors) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Command line header
        if (task.commandLine.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = con.agentMarkerColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    task.client.ifBlank { "system" },
                    color = con.agentMarkerColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    " ${con.inputSymbol} ",
                    color = con.operatorColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    task.commandLine,
                    color = con.commandColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { copyToClipboard(context, "Command", task.commandLine) }
                        )
                )
                if (!task.completed) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = YellowWarning
                    )
                }
            }
        }

        // Output — prefix marker gets status color, body text stays default (matches desktop)
        val hasMessage = task.message.isNotBlank()
        val hasText = task.clearText.isNotBlank()
        if (hasMessage || hasText) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val full = buildString {
                                if (hasMessage) append(task.message)
                                if (hasText) { if (hasMessage) append("\n"); append(task.clearText) }
                            }
                            copyToClipboard(context, "Output", full)
                        }
                    )
                    .padding(start = 4.dp, bottom = 4.dp)
            ) {
                if (hasMessage) {
                    Row {
                        val prefix = when (task.messageType) {
                            2, 5 -> "[*] "
                            4, 7 -> "[+] "
                            3, 6 -> "[-] "
                            else -> " "
                        }
                        val prefixColor = when (task.messageType) {
                            2, 5 -> con.infoColor
                            4, 7 -> con.successColor
                            3, 6 -> con.errorColor
                            else -> con.text
                        }
                        Text(
                            prefix,
                            color = prefixColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                        Text(
                            task.message.trim(),
                            color = con.text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
                if (hasText) {
                    Text(
                        task.clearText.trim(),
                        color = con.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Pending indicator (when no output yet)
        val outputText = task.clearText.ifBlank { task.message }
        if (!task.completed && task.commandLine.isNotBlank() && outputText.isBlank()) {
            Row(
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = YellowWarning
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "waiting for response...",
                    color = YellowWarning,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

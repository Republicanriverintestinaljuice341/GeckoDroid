package com.adaptix.client.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import com.adaptix.client.models.AgentTypeInfo
import com.adaptix.client.models.BuildProfile
import com.adaptix.client.models.Listener
import com.adaptix.client.ui.components.GlassCard
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.theme.*
import com.adaptix.client.viewmodel.BuildLogEntry
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    agentTypes: List<AgentTypeInfo>,
    listeners: List<Listener>,
    isGenerating: Boolean,
    generateError: String?,
    generateSuccess: String?,
    buildLogs: List<BuildLogEntry> = emptyList(),
    buildProfiles: List<BuildProfile> = emptyList(),
    preSelectedListener: String? = null,
    onGenerate: (agentName: String, listenerNames: List<String>, config: String, profileName: String?) -> Unit,
    onDeleteProfile: (String) -> Unit = {},
    onClearStatus: () -> Unit,
    onBack: () -> Unit
) {
    // Agent type selection
    var selectedAgentIndex by remember { mutableStateOf(0) }
    var agentDropdownExpanded by remember { mutableStateOf(false) }

    // Listener selection
    var selectedListenerName by remember { mutableStateOf(preSelectedListener ?: "") }
    var listenerDropdownExpanded by remember { mutableStateOf(false) }

    // Profile
    var profileName by remember { mutableStateOf("") }
    var saveProfile by remember { mutableStateOf(true) }
    var showProfiles by remember { mutableStateOf(false) }

    // --- Shared fields ---
    var format by remember { mutableStateOf("Exe") }
    var formatExpanded by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf("3s") }
    var jitter by remember { mutableStateOf("0") }

    // --- Beacon fields ---
    var arch by remember { mutableStateOf("x64") }
    var archExpanded by remember { mutableStateOf(false) }
    var svcName by remember { mutableStateOf("AgentService") }
    var iatHiding by remember { mutableStateOf(false) }
    var isKilldate by remember { mutableStateOf(false) }
    var killDate by remember { mutableStateOf("") }
    var killTime by remember { mutableStateOf("") }
    var isWorkingtime by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    // Beacon HTTP
    var rotationMode by remember { mutableStateOf("sequential") }
    var rotationExpanded by remember { mutableStateOf(false) }
    var useProxy by remember { mutableStateOf(false) }
    var proxyType by remember { mutableStateOf("http") }
    var proxyTypeExpanded by remember { mutableStateOf(false) }
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("3128") }
    var proxyUsername by remember { mutableStateOf("") }
    var proxyPassword by remember { mutableStateOf("") }

    // --- Kharon fields ---
    var kharonBypass by remember { mutableStateOf("None") }
    var kharonBypassExpanded by remember { mutableStateOf(false) }
    var kharonSyscall by remember { mutableStateOf("None") }
    var kharonSyscallExpanded by remember { mutableStateOf(false) }
    var kharonBofApiProxy by remember { mutableStateOf(false) }
    var kharonSleepMask by remember { mutableStateOf("None") }
    var kharonSleepMaskExpanded by remember { mutableStateOf(false) }
    var kharonHeapObf by remember { mutableStateOf(false) }
    var kharonSpawnTo by remember { mutableStateOf("C:\\Windows\\System32\\notepad.exe") }
    var kharonForkPipename by remember { mutableStateOf("\\\\.\\pipe\\kharon_pipe") }
    var kharonGuardrailsIp by remember { mutableStateOf("") }
    var kharonGuardrailsHostname by remember { mutableStateOf("") }
    var kharonGuardrailsUser by remember { mutableStateOf("") }
    var kharonGuardrailsDomain by remember { mutableStateOf("") }
    var kharonKilldateEnabled by remember { mutableStateOf(false) }
    var kharonKilldateDate by remember { mutableStateOf("") }
    var kharonWorktimeEnabled by remember { mutableStateOf(false) }
    var kharonWorktimeStart by remember { mutableStateOf("") }
    var kharonWorktimeEnd by remember { mutableStateOf("") }

    val selectedAgent = agentTypes.getOrNull(selectedAgentIndex)
    val isBeacon = selectedAgent?.name?.contains("beacon", ignoreCase = true) == true
    val isKharon = selectedAgent?.name?.contains("kharon", ignoreCase = true) == true

    // Auto-select agent type that matches the pre-selected listener
    LaunchedEffect(preSelectedListener, agentTypes) {
        if (preSelectedListener != null && agentTypes.isNotEmpty()) {
            val listener = listeners.find { it.name == preSelectedListener }
            if (listener != null) {
                val matchIdx = agentTypes.indexOfFirst { agentType ->
                    agentType.listeners.any { it == listener.regName }
                }
                if (matchIdx >= 0) selectedAgentIndex = matchIdx
            }
        }
    }

    // Reset format defaults when agent type changes
    LaunchedEffect(selectedAgentIndex) {
        if (isBeacon) {
            format = "Exe"
            sleep = "4s"
        } else if (isKharon) {
            format = "Exe"
            sleep = "3s"
        }
    }

    // Filter listeners compatible with selected agent type
    val compatibleListeners = if (selectedAgent != null) {
        listeners.filter { listener ->
            listener.isRunning && selectedAgent.listeners.any { it == listener.regName }
        }
    } else emptyList()

    // Auto-select first compatible listener
    LaunchedEffect(compatibleListeners) {
        if (selectedListenerName.isBlank() || compatibleListeners.none { it.name == selectedListenerName }) {
            selectedListenerName = compatibleListeners.firstOrNull()?.name ?: ""
        }
    }

    // Determine listener type
    val selectedListener = listeners.find { it.name == selectedListenerName }
    val isSMB = selectedListener?.regName?.contains("SMB", ignoreCase = true) == true
    val isHTTP = selectedListener?.regName?.contains("HTTP", ignoreCase = true) == true

    // Format options per agent
    val formats = when {
        isKharon -> listOf("Exe", "Dll", "Bin", "Svc")
        else -> listOf("Exe", "Service Exe", "DLL", "Shellcode")
    }

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
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            val glowColor = Crimson.copy(alpha = 0.15f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glowColor, Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width * 0.8f
                            )
                        )
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Crimson.copy(alpha = 0.2f),
                                Crimson.copy(alpha = 0.05f)
                            )
                        ),
                        shape = CircleShape
                    )
            ) {
                Icon(Icons.Default.Build, null, tint = Crimson, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Generate Agent", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Build a new payload", color = TextMuted, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error/Success messages
        generateError?.let { error ->
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 10.dp) {
                Box(modifier = Modifier.fillMaxWidth().background(RedError.copy(alpha = 0.10f))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = RedError, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(error, color = RedError, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearStatus, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        generateSuccess?.let { success ->
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 10.dp) {
                Box(modifier = Modifier.fillMaxWidth().background(GreenOnline.copy(alpha = 0.10f))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = GreenOnline, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(success, color = GreenOnline, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearStatus, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (agentTypes.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Warning, null, tint = YellowWarning.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No agent types registered", color = TextMuted, fontSize = 14.sp)
                    Text("Wait for server sync to complete", color = TextMuted.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            return
        }

        // Saved Profiles
        if (buildProfiles.isNotEmpty()) {
            TextButton(
                onClick = { showProfiles = !showProfiles },
                colors = ButtonDefaults.textButtonColors(contentColor = BlueInfo),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(
                    if (showProfiles) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Saved Profiles (${buildProfiles.size})", fontSize = 12.sp)
            }

            if (showProfiles) {
                Column(modifier = Modifier.heightIn(max = 180.dp)) {
                    buildProfiles.forEach { profile ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            cornerRadius = 10.dp,
                            onClick = {
                                // Load profile into form
                                profileName = profile.name
                                val cfg = profile.config

                                // Find and select the agent type
                                val agentIdx = agentTypes.indexOfFirst { it.name == profile.agentName }
                                if (agentIdx >= 0) selectedAgentIndex = agentIdx

                                selectedListenerName = profile.listenerName

                                // Shared
                                format = (cfg["format"] as? String) ?: format
                                sleep = (cfg["sleep"] as? String) ?: sleep
                                jitter = ((cfg["jitter"] as? Number)?.toInt() ?: 0).toString()

                                // Beacon
                                arch = (cfg["arch"] as? String) ?: "x64"
                                svcName = (cfg["svcname"] as? String) ?: "AgentService"
                                iatHiding = (cfg["iat_hiding"] as? Boolean) ?: false
                                isKilldate = (cfg["is_killdate"] as? Boolean) ?: false
                                killDate = (cfg["kill_date"] as? String) ?: ""
                                killTime = (cfg["kill_time"] as? String) ?: ""
                                isWorkingtime = (cfg["is_workingtime"] as? Boolean) ?: false
                                startTime = (cfg["start_time"] as? String) ?: ""
                                endTime = (cfg["end_time"] as? String) ?: ""
                                rotationMode = (cfg["rotation_mode"] as? String) ?: "sequential"
                                useProxy = (cfg["use_proxy"] as? Boolean) ?: false
                                proxyType = (cfg["proxy_type"] as? String) ?: "http"
                                proxyHost = (cfg["proxy_host"] as? String) ?: ""
                                proxyPort = ((cfg["proxy_port"] as? Number)?.toInt() ?: 3128).toString()
                                proxyUsername = (cfg["proxy_username"] as? String) ?: ""
                                proxyPassword = (cfg["proxy_password"] as? String) ?: ""

                                // Kharon
                                kharonGuardrailsIp = (cfg["guardrails_ip"] as? String) ?: ""
                                kharonGuardrailsHostname = (cfg["guardrails_hostname"] as? String) ?: ""
                                kharonGuardrailsUser = (cfg["guardrails_user"] as? String) ?: ""
                                kharonGuardrailsDomain = (cfg["guardrails_domain"] as? String) ?: ""
                                kharonKilldateEnabled = (cfg["killdate_check"] as? Boolean) ?: false
                                kharonKilldateDate = (cfg["killdate_date"] as? String) ?: ""
                                kharonWorktimeEnabled = (cfg["workingtime_check"] as? Boolean) ?: false
                                kharonWorktimeStart = (cfg["workingtime_start"] as? String) ?: ""
                                kharonWorktimeEnd = (cfg["workingtime_end"] as? String) ?: ""
                                kharonForkPipename = (cfg["fork_pipename"] as? String) ?: "\\\\.\\pipe\\kharon_pipe"
                                kharonSpawnTo = (cfg["spawnto"] as? String) ?: "C:\\Windows\\System32\\notepad.exe"
                                kharonBypass = (cfg["bypass"] as? String) ?: "None"
                                kharonSyscall = (cfg["syscall"] as? String) ?: "None"
                                kharonBofApiProxy = (cfg["bof_api_proxy"] as? Boolean) ?: false
                                kharonSleepMask = (cfg["mask_sleep"] as? String) ?: "None"
                                kharonHeapObf = (cfg["mask_heap"] as? Boolean) ?: false

                                showProfiles = false
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
                                        "${profile.agentName} | ${profile.config["format"] ?: "?"} | ${profile.listenerName}",
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

        // Agent Type Selection
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Agent", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = agentDropdownExpanded,
                    onExpandedChange = { agentDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedAgent?.name ?: "Select agent",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agentDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = generateFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = agentDropdownExpanded,
                        onDismissRequest = { agentDropdownExpanded = false }
                    ) {
                        agentTypes.forEachIndexed { index, agentType ->
                            DropdownMenuItem(
                                text = { Text(agentType.name, color = TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    selectedAgentIndex = index
                                    agentDropdownExpanded = false
                                    selectedListenerName = ""
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Listener Selection
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Listener", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                if (compatibleListeners.isEmpty()) {
                    Text(
                        "No compatible running listeners",
                        color = YellowWarning.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = listenerDropdownExpanded,
                        onExpandedChange = { listenerDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedListenerName.ifBlank { "Select listener" },
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = listenerDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = generateFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = listenerDropdownExpanded,
                            onDismissRequest = { listenerDropdownExpanded = false }
                        ) {
                            compatibleListeners.forEach { listener ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(listener.name, color = TextPrimary, fontSize = 13.sp)
                                            Text(
                                                "${listener.type} | ${listener.bindHost}:${listener.bindPort}",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedListenerName = listener.name
                                        listenerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==================== BUILD CONFIG ====================

        if (isBeacon) {
            // --- Beacon Build Config ---
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Build", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)

                    // Arch
                    ExposedDropdownMenuBox(
                        expanded = archExpanded,
                        onExpandedChange = { archExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = arch,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Architecture", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = archExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = generateFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = archExpanded,
                            onDismissRequest = { archExpanded = false }
                        ) {
                            listOf("x64", "x86").forEach { a ->
                                DropdownMenuItem(
                                    text = { Text(a, color = TextPrimary, fontSize = 13.sp) },
                                    onClick = { arch = a; archExpanded = false }
                                )
                            }
                        }
                    }

                    // Format
                    ExposedDropdownMenuBox(
                        expanded = formatExpanded,
                        onExpandedChange = { formatExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = format,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Format", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = generateFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = formatExpanded,
                            onDismissRequest = { formatExpanded = false }
                        ) {
                            formats.forEach { fmt ->
                                DropdownMenuItem(
                                    text = { Text(fmt, color = TextPrimary, fontSize = 13.sp) },
                                    onClick = { format = fmt; formatExpanded = false }
                                )
                            }
                        }
                    }

                    // Service name (only for Service Exe)
                    if (format == "Service Exe") {
                        OutlinedTextField(
                            value = svcName,
                            onValueChange = { svcName = it },
                            label = { Text("Service Name", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = generateFieldColors()
                        )
                    }

                    // IAT Hiding
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceCard.copy(alpha = 0.45f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Security, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("IAT Hiding (empty import table)", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = iatHiding,
                                onCheckedChange = { iatHiding = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Crimson,
                                    checkedTrackColor = CrimsonDark
                                )
                            )
                        }
                    }
                }
            }

            // Sleep/Jitter (only for HTTP/DNS, not SMB/TCP)
            if (isHTTP) {
                Spacer(modifier = Modifier.height(10.dp))
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Callback", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sleep,
                                onValueChange = { sleep = it },
                                label = { Text("Sleep", fontSize = 12.sp) },
                                placeholder = { Text("e.g., 4s, 1m30s", fontSize = 12.sp, color = TextMuted.copy(alpha = 0.5f)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = generateFieldColors()
                            )
                            OutlinedTextField(
                                value = jitter,
                                onValueChange = { jitter = it.filter { c -> c.isDigit() } },
                                label = { Text("Jitter %", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = generateFieldColors()
                            )
                        }
                    }
                }
            }

            // Schedule (killdate / workingtime)
            Spacer(modifier = Modifier.height(10.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Schedule", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)

                    // Killdate
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceCard.copy(alpha = 0.45f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Kill Date", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = isKilldate,
                                onCheckedChange = { isKilldate = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Crimson,
                                    checkedTrackColor = CrimsonDark
                                )
                            )
                        }
                    }
                    if (isKilldate) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = killDate,
                                onValueChange = { killDate = it },
                                label = { Text("Date (DD.MM.YYYY)", fontSize = 12.sp) },
                                placeholder = { Text("28.02.2030", fontSize = 12.sp, color = TextMuted.copy(alpha = 0.5f)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = generateFieldColors()
                            )
                            OutlinedTextField(
                                value = killTime,
                                onValueChange = { killTime = it },
                                label = { Text("Time (HH:mm:ss)", fontSize = 12.sp) },
                                placeholder = { Text("12:00:00", fontSize = 12.sp, color = TextMuted.copy(alpha = 0.5f)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = generateFieldColors()
                            )
                        }
                    }

                    GlassDivider()

                    // Working time
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceCard.copy(alpha = 0.45f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Working Time", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = isWorkingtime,
                                onCheckedChange = { isWorkingtime = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Crimson,
                                    checkedTrackColor = CrimsonDark
                                )
                            )
                        }
                    }
                    if (isWorkingtime) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startTime,
                                onValueChange = { startTime = it },
                                label = { Text("Start (HH:mm)", fontSize = 12.sp) },
                                placeholder = { Text("08:00", fontSize = 12.sp, color = TextMuted.copy(alpha = 0.5f)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = generateFieldColors()
                            )
                            OutlinedTextField(
                                value = endTime,
                                onValueChange = { endTime = it },
                                label = { Text("End (HH:mm)", fontSize = 12.sp) },
                                placeholder = { Text("17:30", fontSize = 12.sp, color = TextMuted.copy(alpha = 0.5f)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = generateFieldColors()
                            )
                        }
                    }
                }
            }

            // HTTP-specific: Rotation + Proxy
            if (isHTTP) {
                Spacer(modifier = Modifier.height(10.dp))
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("HTTP Settings", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)

                        // Rotation mode
                        ExposedDropdownMenuBox(
                            expanded = rotationExpanded,
                            onExpandedChange = { rotationExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = rotationMode,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                label = { Text("Rotation Mode", fontSize = 12.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rotationExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                colors = generateFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = rotationExpanded,
                                onDismissRequest = { rotationExpanded = false }
                            ) {
                                listOf("sequential", "random").forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode, color = TextPrimary, fontSize = 13.sp) },
                                        onClick = { rotationMode = mode; rotationExpanded = false }
                                    )
                                }
                            }
                        }

                        GlassDivider()

                        // Proxy toggle
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceCard.copy(alpha = 0.45f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Language, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Use HTTP/HTTPS Proxy", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = useProxy,
                                    onCheckedChange = { useProxy = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Crimson,
                                        checkedTrackColor = CrimsonDark
                                    )
                                )
                            }
                        }

                        if (useProxy) {
                            // Proxy type
                            ExposedDropdownMenuBox(
                                expanded = proxyTypeExpanded,
                                onExpandedChange = { proxyTypeExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = proxyType,
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    label = { Text("Proxy Type", fontSize = 12.sp) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = proxyTypeExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    colors = generateFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = proxyTypeExpanded,
                                    onDismissRequest = { proxyTypeExpanded = false }
                                ) {
                                    listOf("http", "https").forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text(t, color = TextPrimary, fontSize = 13.sp) },
                                            onClick = { proxyType = t; proxyTypeExpanded = false }
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = proxyHost,
                                    onValueChange = { proxyHost = it },
                                    label = { Text("Server", fontSize = 12.sp) },
                                    placeholder = { Text("192.168.1.1", fontSize = 12.sp, color = TextMuted.copy(alpha = 0.5f)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f),
                                    colors = generateFieldColors()
                                )
                                OutlinedTextField(
                                    value = proxyPort,
                                    onValueChange = { proxyPort = it.filter { c -> c.isDigit() } },
                                    label = { Text("Port", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = generateFieldColors()
                                )
                            }

                            OutlinedTextField(
                                value = proxyUsername,
                                onValueChange = { proxyUsername = it },
                                label = { Text("Username (optional)", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = generateFieldColors()
                            )
                            OutlinedTextField(
                                value = proxyPassword,
                                onValueChange = { proxyPassword = it },
                                label = { Text("Password (optional)", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = generateFieldColors()
                            )
                        }
                    }
                }
            }

        } else if (isKharon) {
            // --- Kharon Build Config ---
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Build", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)

                    // Format
                    ExposedDropdownMenuBox(expanded = formatExpanded, onExpandedChange = { formatExpanded = it }) {
                        OutlinedTextField(value = format, onValueChange = {}, readOnly = true, singleLine = true, label = { Text("Format", fontSize = 12.sp) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), colors = generateFieldColors())
                        ExposedDropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                            formats.forEach { fmt -> DropdownMenuItem(text = { Text(fmt, color = TextPrimary, fontSize = 13.sp) }, onClick = { format = fmt; formatExpanded = false }) }
                        }
                    }

                    // Sleep / Jitter
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sleep, onValueChange = { sleep = it }, label = { Text("Sleep", fontSize = 12.sp) }, placeholder = { Text("1h2m5s", fontSize = 12.sp, color = TextMuted.copy(alpha = 0.5f)) }, singleLine = true, modifier = Modifier.weight(1f), colors = generateFieldColors())
                        OutlinedTextField(value = jitter, onValueChange = { jitter = it }, label = { Text("Jitter %", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.weight(0.5f), colors = generateFieldColors())
                    }

                    HorizontalDivider(color = DividerColor)
                    Text("Guardrails", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = kharonGuardrailsIp, onValueChange = { kharonGuardrailsIp = it }, label = { Text("IP", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = generateFieldColors())
                        OutlinedTextField(value = kharonGuardrailsHostname, onValueChange = { kharonGuardrailsHostname = it }, label = { Text("Hostname", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = generateFieldColors())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = kharonGuardrailsUser, onValueChange = { kharonGuardrailsUser = it }, label = { Text("Username", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = generateFieldColors())
                        OutlinedTextField(value = kharonGuardrailsDomain, onValueChange = { kharonGuardrailsDomain = it }, label = { Text("Domain", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = generateFieldColors())
                    }

                    HorizontalDivider(color = DividerColor)
                    Text("Killdate", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = kharonKilldateEnabled, onCheckedChange = { kharonKilldateEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Crimson, checkedTrackColor = CrimsonDark))
                    }
                    if (kharonKilldateEnabled) {
                        OutlinedTextField(value = kharonKilldateDate, onValueChange = { kharonKilldateDate = it }, label = { Text("Date (dd.MM.yyyy)", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = generateFieldColors())
                    }

                    HorizontalDivider(color = DividerColor)
                    Text("Working Time", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = kharonWorktimeEnabled, onCheckedChange = { kharonWorktimeEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Crimson, checkedTrackColor = CrimsonDark))
                    }
                    if (kharonWorktimeEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = kharonWorktimeStart, onValueChange = { kharonWorktimeStart = it }, label = { Text("Start (HH:mm)", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = generateFieldColors())
                            OutlinedTextField(value = kharonWorktimeEnd, onValueChange = { kharonWorktimeEnd = it }, label = { Text("End (HH:mm)", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.weight(1f), colors = generateFieldColors())
                        }
                    }

                    HorizontalDivider(color = DividerColor)
                    Text("PostEx", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(value = kharonForkPipename, onValueChange = { kharonForkPipename = it }, label = { Text("Fork Pipe Name", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = generateFieldColors())
                    OutlinedTextField(value = kharonSpawnTo, onValueChange = { kharonSpawnTo = it }, label = { Text("Spawn To", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = generateFieldColors())

                    HorizontalDivider(color = DividerColor)
                    Text("Evasion", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    ExposedDropdownMenuBox(expanded = kharonBypassExpanded, onExpandedChange = { kharonBypassExpanded = it }) {
                        OutlinedTextField(value = kharonBypass, onValueChange = {}, readOnly = true, singleLine = true, label = { Text("Bypass", fontSize = 12.sp) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kharonBypassExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), colors = generateFieldColors())
                        ExposedDropdownMenu(expanded = kharonBypassExpanded, onDismissRequest = { kharonBypassExpanded = false }) {
                            listOf("None", "AMSI", "ETW", "AMSI + ETW").forEach { b -> DropdownMenuItem(text = { Text(b, color = TextPrimary, fontSize = 13.sp) }, onClick = { kharonBypass = b; kharonBypassExpanded = false }) }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = kharonSyscallExpanded, onExpandedChange = { kharonSyscallExpanded = it }) {
                        OutlinedTextField(value = kharonSyscall, onValueChange = {}, readOnly = true, singleLine = true, label = { Text("Syscall", fontSize = 12.sp) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kharonSyscallExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), colors = generateFieldColors())
                        ExposedDropdownMenu(expanded = kharonSyscallExpanded, onDismissRequest = { kharonSyscallExpanded = false }) {
                            listOf("None", "Stack Spoof", "Stack Spoof + Indirect").forEach { s -> DropdownMenuItem(text = { Text(s, color = TextPrimary, fontSize = 13.sp) }, onClick = { kharonSyscall = s; kharonSyscallExpanded = false }) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BOF API Proxy", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = kharonBofApiProxy, onCheckedChange = { kharonBofApiProxy = it }, colors = SwitchDefaults.colors(checkedThumbColor = Crimson, checkedTrackColor = CrimsonDark))
                    }

                    HorizontalDivider(color = DividerColor)
                    Text("Mask", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    ExposedDropdownMenuBox(expanded = kharonSleepMaskExpanded, onExpandedChange = { kharonSleepMaskExpanded = it }) {
                        OutlinedTextField(value = kharonSleepMask, onValueChange = {}, readOnly = true, singleLine = true, label = { Text("Sleep Mask", fontSize = 12.sp) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kharonSleepMaskExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), colors = generateFieldColors())
                        ExposedDropdownMenu(expanded = kharonSleepMaskExpanded, onDismissRequest = { kharonSleepMaskExpanded = false }) {
                            listOf("None", "Timer").forEach { m -> DropdownMenuItem(text = { Text(m, color = TextPrimary, fontSize = 13.sp) }, onClick = { kharonSleepMask = m; kharonSleepMaskExpanded = false }) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Heap Obfuscation", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = kharonHeapObf, onCheckedChange = { kharonHeapObf = it }, colors = SwitchDefaults.colors(checkedThumbColor = Crimson, checkedTrackColor = CrimsonDark))
                    }
                }
            }

        } else {
            // --- Unknown agent type: generic raw config ---
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Info, null, tint = BlueInfo.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Unknown agent type", color = TextMuted, fontSize = 13.sp)
                    Text(
                        "Config fields for \"${selectedAgent?.name}\" are not available on mobile. Use the desktop client to generate this agent.",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Profile Name
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Profile", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Save", color = TextMuted, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = saveProfile,
                            onCheckedChange = { saveProfile = it },
                            modifier = Modifier.height(24.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Crimson,
                                checkedTrackColor = CrimsonDark
                            )
                        )
                    }
                }
                if (saveProfile) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile Name", fontSize = 12.sp) },
                        placeholder = { Text("e.g., beacon_http_debug", fontSize = 12.sp, color = TextMuted.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = generateFieldColors()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Generate Button
        val canGenerate = selectedAgent != null && selectedListenerName.isNotBlank() && !isGenerating && (isBeacon || isKharon)
        Button(
            onClick = {
                if (canGenerate) {
                    val configJson = if (isKharon) {
                        buildKharonConfig(
                            format, sleep, jitter,
                            kharonGuardrailsIp, kharonGuardrailsHostname, kharonGuardrailsUser, kharonGuardrailsDomain,
                            kharonKilldateEnabled, kharonKilldateDate,
                            kharonWorktimeEnabled, kharonWorktimeStart, kharonWorktimeEnd,
                            kharonForkPipename, kharonSpawnTo,
                            kharonBypass, kharonSyscall, kharonBofApiProxy,
                            kharonSleepMask, kharonHeapObf
                        )
                    } else {
                        buildBeaconConfig(
                            arch, format, sleep, jitter, isHTTP, isSMB,
                            svcName, iatHiding,
                            isKilldate, killDate, killTime,
                            isWorkingtime, startTime, endTime,
                            rotationMode, useProxy, proxyType, proxyHost, proxyPort, proxyUsername, proxyPassword
                        )
                    }
                    val saveName = if (saveProfile && profileName.isNotBlank()) profileName else null
                    onGenerate(selectedAgent!!.name, listOf(selectedListenerName), configJson, saveName)
                }
            },
            enabled = canGenerate,
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
                        brush = if (canGenerate) {
                            Brush.horizontalGradient(listOf(Crimson, CrimsonLight))
                        } else {
                            Brush.horizontalGradient(
                                listOf(CrimsonDark.copy(alpha = 0.4f), CrimsonDark.copy(alpha = 0.4f))
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "BUILDING...",
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                } else {
                    Text(
                        "GENERATE",
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (canGenerate) Color.White else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Build Logs
        if (buildLogs.isNotEmpty() || isGenerating) {
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Build Output", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        if (isGenerating) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = Crimson
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = SurfaceBlack.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val logScrollState = rememberScrollState()
                        LaunchedEffect(buildLogs.size) {
                            logScrollState.animateScrollTo(logScrollState.maxValue)
                        }
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .heightIn(max = 200.dp)
                                .verticalScroll(logScrollState)
                        ) {
                            buildLogs.forEach { log ->
                                val color = when (log.status) {
                                    1 -> BlueInfo
                                    2 -> RedError
                                    3 -> GreenOnline
                                    else -> TerminalText
                                }
                                Text(
                                    log.message,
                                    color = color,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun buildBeaconConfig(
    arch: String, format: String, sleep: String, jitter: String,
    isHTTP: Boolean, @Suppress("UNUSED_PARAMETER") isSMB: Boolean,
    svcName: String, iatHiding: Boolean,
    isKilldate: Boolean, killDate: String, killTime: String,
    isWorkingtime: Boolean, startTime: String, endTime: String,
    rotationMode: String,
    useProxy: Boolean, proxyType: String, proxyHost: String, proxyPort: String,
    proxyUsername: String, proxyPassword: String
): String {
    val configMap = mutableMapOf<String, Any>(
        "arch" to arch,
        "format" to format,
        "sleep" to sleep,
        "jitter" to (jitter.toIntOrNull() ?: 0),
        "iat_hiding" to iatHiding,
        "is_killdate" to isKilldate,
        "kill_date" to killDate,
        "kill_time" to killTime,
        "is_workingtime" to isWorkingtime,
        "start_time" to startTime,
        "end_time" to endTime,
        "svcname" to svcName
    )
    if (isHTTP) {
        configMap["rotation_mode"] = rotationMode
        configMap["use_proxy"] = useProxy
        if (useProxy) {
            configMap["proxy_type"] = proxyType
            configMap["proxy_host"] = proxyHost
            configMap["proxy_port"] = proxyPort.toIntOrNull() ?: 3128
            configMap["proxy_username"] = proxyUsername
            configMap["proxy_password"] = proxyPassword
        }
    }
    return Gson().toJson(configMap)
}

private fun buildKharonConfig(
    format: String, sleep: String, jitter: String,
    guardrailsIp: String, guardrailsHostname: String, guardrailsUser: String, guardrailsDomain: String,
    killdateEnabled: Boolean, killdateDate: String,
    worktimeEnabled: Boolean, worktimeStart: String, worktimeEnd: String,
    forkPipename: String, spawnTo: String,
    bypass: String, syscall: String, bofApiProxy: Boolean,
    sleepMask: String, heapObf: Boolean
): String {
    val configMap = mutableMapOf<String, Any>(
        "format" to format,
        "sleep" to sleep,
        "jitter" to (jitter.toIntOrNull() ?: 0),
        "guardrails_ip" to guardrailsIp,
        "guardrails_hostname" to guardrailsHostname,
        "guardrails_user" to guardrailsUser,
        "guardrails_domain" to guardrailsDomain,
        "killdate_check" to killdateEnabled,
        "killdate_date" to killdateDate,
        "workingtime_check" to worktimeEnabled,
        "workingtime_start" to worktimeStart,
        "workingtime_end" to worktimeEnd,
        "fork_pipename" to forkPipename,
        "spawnto" to spawnTo,
        "bypass" to bypass,
        "syscall" to syscall,
        "bof_api_proxy" to bofApiProxy,
        "mask_sleep" to sleepMask,
        "mask_heap" to heapObf
    )
    return Gson().toJson(configMap)
}

@Composable
private fun generateFieldColors() = OutlinedTextFieldDefaults.colors(
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

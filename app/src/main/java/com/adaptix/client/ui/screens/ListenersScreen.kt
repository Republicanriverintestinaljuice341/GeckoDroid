package com.adaptix.client.ui.screens

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.gson.Gson
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptix.client.models.Listener
import com.adaptix.client.models.ListenerProfile
import com.adaptix.client.ui.components.ConfirmDialog
import com.adaptix.client.ui.components.GlassCard
import com.adaptix.client.ui.components.GlassDivider
import com.adaptix.client.ui.components.GlowBadge
import com.adaptix.client.ui.theme.*
import com.adaptix.client.viewmodel.ListenerTypeInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenersScreen(
    listeners: List<Listener>,
    listenerTypes: List<ListenerTypeInfo>,
    listenerProfiles: List<ListenerProfile> = emptyList(),
    onCreateListener: (String, String, String) -> Unit,
    onStopListener: (String, String) -> Unit,
    onGenerateFromListener: (String) -> Unit = {},
    onSaveListenerProfile: (ListenerProfile) -> Unit = {},
    onDeleteListenerProfile: (String) -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .padding(12.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Listeners",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${listeners.size}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Create Listener", tint = Crimson)
            }
        }

        GlassDivider(modifier = Modifier.padding(bottom = 8.dp))

        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(listeners) { isRefreshing = false }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; onRefresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (listeners.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Sensors,
                            null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No listeners",
                            color = TextMuted.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listeners, key = { it.name }) { listener ->
                        ListenerCard(
                            listener = listener,
                            onStop = { onStopListener(listener.name, listener.regName) },
                            onGenerate = { onGenerateFromListener(listener.name) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateListenerDialog(
            listenerTypes = listenerTypes,
            listenerProfiles = listenerProfiles,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type, config ->
                onCreateListener(name, type, config)
                showCreateDialog = false
            },
            onSaveProfile = onSaveListenerProfile,
            onDeleteProfile = onDeleteListenerProfile
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListenerCard(listener: Listener, onStop: () -> Unit, onGenerate: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmStop by remember { mutableStateOf(false) }

    val statusColor = when {
        listener.isRunning -> GreenOnline
        listener.isPaused -> YellowWarning
        else -> TextMuted
    }

    Box {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true }
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Sensors,
                        null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        listener.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    GlowBadge(
                        text = listener.statusDisplay,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Protocol", color = TextMuted, fontSize = 10.sp)
                        Text(listener.protocol, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Type", color = TextMuted, fontSize = 10.sp)
                        Text(listener.type, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Bind", color = TextMuted, fontSize = 10.sp)
                        Text("${listener.bindHost}:${listener.bindPort}", color = BlueInfo, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (listener.agentAddr.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Agent: ${listener.agentAddr}", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = DpOffset(12.dp, 0.dp),
            modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp))
        ) {
            if (listener.isRunning) {
                DropdownMenuItem(
                    text = { Text("Generate Agent", color = Crimson, fontSize = 13.sp) },
                    onClick = {
                        showMenu = false
                        onGenerate()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Build, null, tint = Crimson, modifier = Modifier.size(18.dp))
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Remove Listener", color = RedError, fontSize = 13.sp) },
                onClick = {
                    showMenu = false
                    showConfirmStop = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(18.dp))
                }
            )
        }

        if (showConfirmStop) {
            ConfirmDialog(
                title = "Stop Listener",
                message = "Stop listener '${listener.name}'?",
                confirmText = "Stop",
                onConfirm = { showConfirmStop = false; onStop() },
                onDismiss = { showConfirmStop = false }
            )
        }
    }
}

// Detect listener kind from server registration info
private enum class ListenerKind {
    BEACON_HTTP, BEACON_SMB,
    KHARON_HTTP,
    UNKNOWN
}

private fun detectListenerKind(info: ListenerTypeInfo): ListenerKind {
    val n = info.name.lowercase()
    val p = info.protocol.lowercase()
    return when {
        n.contains("kharon") && p.contains("http") -> ListenerKind.KHARON_HTTP
        n.contains("beacon") && p.contains("smb") -> ListenerKind.BEACON_SMB
        n.contains("beacon") && p.contains("http") -> ListenerKind.BEACON_HTTP
        // Fallback heuristics
        p.contains("smb") -> ListenerKind.BEACON_SMB
        p.contains("http") -> ListenerKind.BEACON_HTTP
        else -> ListenerKind.UNKNOWN
    }
}

@Composable
private fun CreateListenerDialog(
    listenerTypes: List<ListenerTypeInfo>,
    listenerProfiles: List<ListenerProfile> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
    onSaveProfile: (ListenerProfile) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<ListenerTypeInfo?>(null) }
    var typeExpanded by remember { mutableStateOf(false) }

    val kind = selectedType?.let { detectListenerKind(it) } ?: ListenerKind.UNKNOWN

    // Filter to only HTTP and SMB listener types
    val supportedTypes = remember(listenerTypes) {
        listenerTypes.filter { detectListenerKind(it) != ListenerKind.UNKNOWN }
    }

    // Shared fields
    var host by remember { mutableStateOf("0.0.0.0") }
    var port by remember { mutableStateOf("443") }
    var ssl by remember { mutableStateOf(false) }
    var encryptKey by remember { mutableStateOf(randomHex(32)) }

    // Beacon HTTP fields
    var callbackAddresses by remember { mutableStateOf("") }
    var httpMethod by remember { mutableStateOf("GET") }
    var uri by remember { mutableStateOf("/api/v1") }
    var userAgent by remember { mutableStateOf("Mozilla/5.0") }
    var hbHeader by remember { mutableStateOf("Cookie") }
    var hostHeader by remember { mutableStateOf("") }
    var requestHeaders by remember { mutableStateOf("") }
    var serverHeaders by remember { mutableStateOf("") }
    var trustXForwardedFor by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf("<!DOCTYPE html>\n<html><head><title>404</title></head><body><h1>404 - Not Found</h1></body></html>") }
    var pagePayload by remember { mutableStateOf("""{"status": "ok", "data": "<<<PAYLOAD_DATA>>>", "metrics": "sync"}""") }

    // SMB fields
    var pipename by remember { mutableStateOf("msagent_${randomHex(8)}") }
    // Dropdown expand states
    var methodExpanded by remember { mutableStateOf(false) }

    // Profile save fields
    var saveProfileName by remember { mutableStateOf("") }
    var saveProfile by remember { mutableStateOf(false) }
    var profilePickerExpanded by remember { mutableStateOf(false) }

    // Kharon HTTP fields
    var kharonDomainRotation by remember { mutableStateOf("Random") }
    var kharonBlockUserAgents by remember { mutableStateOf("") }
    var kharonProxyUrl by remember { mutableStateOf("") }
    var kharonProxyUser by remember { mutableStateOf("") }
    var kharonProxyPass by remember { mutableStateOf("") }
    var kharonRotationExpanded by remember { mutableStateOf(false) }
    var trustXForwardedHost by remember { mutableStateOf(false) }
    var additionalTrustedHosts by remember { mutableStateOf("") }
    var kharonProfileFileB64 by remember { mutableStateOf("") }
    var kharonProfileFileName by remember { mutableStateOf("") }

    val context = LocalContext.current
    val profileFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bytes = context.contentResolver.openInputStream(it)?.readBytes()
                if (bytes != null) {
                    kharonProfileFileB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    kharonProfileFileName = it.lastPathSegment ?: "profile"
                }
            } catch (_: Exception) {}
        }
    }

    // Collect current fields into a map (for saving)
    fun collectFields(): Map<String, Any> = when (kind) {
        ListenerKind.BEACON_HTTP -> mapOf(
            "host" to host, "port" to port, "ssl" to ssl, "encryptKey" to encryptKey,
            "callbackAddresses" to callbackAddresses, "httpMethod" to httpMethod,
            "uri" to uri, "userAgent" to userAgent, "hbHeader" to hbHeader,
            "hostHeader" to hostHeader, "requestHeaders" to requestHeaders,
            "serverHeaders" to serverHeaders, "trustXForwardedFor" to trustXForwardedFor,
            "pageError" to pageError, "pagePayload" to pagePayload
        )
        ListenerKind.BEACON_SMB -> mapOf("pipename" to pipename, "encryptKey" to encryptKey)
        ListenerKind.KHARON_HTTP -> mapOf(
            "host" to host, "port" to port, "ssl" to ssl,
            "kharonDomainRotation" to kharonDomainRotation,
            "kharonBlockUserAgents" to kharonBlockUserAgents,
            "kharonProxyUrl" to kharonProxyUrl, "kharonProxyUser" to kharonProxyUser,
            "kharonProxyPass" to kharonProxyPass,
            "trustXForwardedHost" to trustXForwardedHost,
            "additionalTrustedHosts" to additionalTrustedHosts,
            "kharonProfileFileB64" to kharonProfileFileB64,
            "kharonProfileFileName" to kharonProfileFileName
        )
        ListenerKind.UNKNOWN -> emptyMap()
    }

    // Load profile into fields
    fun loadProfile(profile: ListenerProfile) {
        val f = profile.fields
        // Find and select the matching listener type
        val targetKind = try { ListenerKind.valueOf(profile.listenerKind) } catch (_: Exception) { null }
        if (targetKind != null) {
            supportedTypes.find { detectListenerKind(it) == targetKind }?.let { selectedType = it }
        }
        when (targetKind) {
            ListenerKind.BEACON_HTTP -> {
                host = (f["host"] as? String) ?: host
                port = (f["port"] as? String) ?: port
                ssl = (f["ssl"] as? Boolean) ?: ssl
                encryptKey = (f["encryptKey"] as? String) ?: encryptKey
                callbackAddresses = (f["callbackAddresses"] as? String) ?: callbackAddresses
                httpMethod = (f["httpMethod"] as? String) ?: httpMethod
                uri = (f["uri"] as? String) ?: uri
                userAgent = (f["userAgent"] as? String) ?: userAgent
                hbHeader = (f["hbHeader"] as? String) ?: hbHeader
                hostHeader = (f["hostHeader"] as? String) ?: hostHeader
                requestHeaders = (f["requestHeaders"] as? String) ?: requestHeaders
                serverHeaders = (f["serverHeaders"] as? String) ?: serverHeaders
                trustXForwardedFor = (f["trustXForwardedFor"] as? Boolean) ?: trustXForwardedFor
                pageError = (f["pageError"] as? String) ?: pageError
                pagePayload = (f["pagePayload"] as? String) ?: pagePayload
            }
            ListenerKind.BEACON_SMB -> {
                pipename = (f["pipename"] as? String) ?: pipename
                encryptKey = (f["encryptKey"] as? String) ?: encryptKey
            }
            ListenerKind.KHARON_HTTP -> {
                host = (f["host"] as? String) ?: host
                port = (f["port"] as? String) ?: port
                ssl = (f["ssl"] as? Boolean) ?: ssl
                kharonDomainRotation = (f["kharonDomainRotation"] as? String) ?: kharonDomainRotation
                kharonBlockUserAgents = (f["kharonBlockUserAgents"] as? String) ?: kharonBlockUserAgents
                kharonProxyUrl = (f["kharonProxyUrl"] as? String) ?: kharonProxyUrl
                kharonProxyUser = (f["kharonProxyUser"] as? String) ?: kharonProxyUser
                kharonProxyPass = (f["kharonProxyPass"] as? String) ?: kharonProxyPass
                trustXForwardedHost = (f["trustXForwardedHost"] as? Boolean) ?: trustXForwardedHost
                additionalTrustedHosts = (f["additionalTrustedHosts"] as? String) ?: additionalTrustedHosts
                kharonProfileFileB64 = (f["kharonProfileFileB64"] as? String) ?: ""
                kharonProfileFileName = (f["kharonProfileFileName"] as? String) ?: ""
            }
            else -> {}
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = Crimson,
        unfocusedBorderColor = TextMuted,
        cursorColor = Crimson
    )

    val gson = Gson()

    fun splitList(s: String) = s.split(",").map { it.trim() }.filter { it.isNotBlank() }

    fun buildConfig(): String = gson.toJson(when (kind) {
        ListenerKind.BEACON_HTTP -> mapOf(
            "host_bind" to host,
            "port_bind" to (port.toIntOrNull() ?: 443),
            "callback_addresses" to splitList(callbackAddresses),
            "http_method" to httpMethod,
            "uri" to splitList(uri),
            "user_agent" to splitList(userAgent),
            "hb_header" to hbHeader,
            "encrypt_key" to encryptKey,
            "ssl" to ssl,
            "ssl_cert" to "",
            "ssl_key" to "",
            "x-forwarded-for" to trustXForwardedFor,
            "host_header" to splitList(hostHeader),
            "request_headers" to requestHeaders,
            "server_headers" to serverHeaders,
            "page-error" to pageError,
            "page-payload" to pagePayload
        )
        ListenerKind.BEACON_SMB -> mapOf(
            "pipename" to pipename,
            "encrypt_key" to encryptKey
        )
        ListenerKind.KHARON_HTTP -> mapOf(
            "host_bind" to host,
            "port_bind" to (port.toIntOrNull() ?: 443),
            "block_user_agents" to kharonBlockUserAgents,
            "uploaded_file" to kharonProfileFileB64,
            "domain_rotation_strategy" to kharonDomainRotation,
            "proxy_url" to kharonProxyUrl,
            "proxy_user" to kharonProxyUser,
            "proxy_pass" to kharonProxyPass,
            "ssl" to ssl,
            "ssl_cert" to "",
            "ssl_key" to "",
            "trust_x_forwarded_host" to trustXForwardedHost,
            "additional_trusted_hosts" to additionalTrustedHosts
        )
        ListenerKind.UNKNOWN -> emptyMap<String, Any>()
    })

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark.copy(alpha = 0.95f),
        shape = RoundedCornerShape(16.dp),
        title = { Text("Create Listener", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                // === Saved Profiles Picker ===
                if (listenerProfiles.isNotEmpty()) {
                    SectionLabel("Saved Profiles")
                    DropdownField(
                        label = "Load profile...",
                        expanded = profilePickerExpanded,
                        onToggle = { profilePickerExpanded = it }
                    ) {
                        listenerProfiles.forEach { lp ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(lp.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            Text(lp.listenerKind.replace("_", " "), color = TextMuted, fontSize = 10.sp)
                                        }
                                        IconButton(
                                            onClick = { onDeleteProfile(lp.name) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, "Delete", tint = RedError, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    loadProfile(lp)
                                    profilePickerExpanded = false
                                }
                            )
                        }
                    }
                    GlassDivider()
                }

                // Type dropdown
                DropdownField(
                    label = selectedType?.let { "${it.name} (${it.protocol})" } ?: "Select type...",
                    expanded = typeExpanded,
                    onToggle = { typeExpanded = it }
                ) {
                    supportedTypes.forEach { lt ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(lt.name, color = TextPrimary, fontSize = 13.sp)
                                    Text(lt.protocol, color = TextMuted, fontSize = 11.sp)
                                }
                            },
                            onClick = { selectedType = lt; typeExpanded = false }
                        )
                    }
                    if (supportedTypes.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No types registered", color = TextMuted, fontSize = 13.sp) },
                            onClick = { typeExpanded = false }
                        )
                    }
                }

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth()
                )

                if (selectedType != null) {
                    when (kind) {
                        // ===== BEACON HTTP =====
                        ListenerKind.BEACON_HTTP -> {
                            GlassDivider()
                            SectionLabel("Connection")
                            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Bind Host", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = callbackAddresses, onValueChange = { callbackAddresses = it }, label = { Text("Callback Addresses", color = TextMuted, fontSize = 12.sp) }, placeholder = { Text("host:port per line", color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp) }, colors = fieldColors, modifier = Modifier.fillMaxWidth().height(80.dp), maxLines = 3)

                            GlassDivider()
                            SectionLabel("HTTP Settings")
                            DropdownField(label = "Method: $httpMethod", expanded = methodExpanded, onToggle = { methodExpanded = it }) {
                                listOf("GET", "POST").forEach { m ->
                                    DropdownMenuItem(text = { Text(m, color = TextPrimary, fontSize = 13.sp) }, onClick = { httpMethod = m; methodExpanded = false })
                                }
                            }
                            OutlinedTextField(value = uri, onValueChange = { uri = it }, label = { Text("URI", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = userAgent, onValueChange = { userAgent = it }, label = { Text("User-Agent", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = hbHeader, onValueChange = { hbHeader = it }, label = { Text("Heartbeat Header", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = hostHeader, onValueChange = { hostHeader = it }, label = { Text("Host Header", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())

                            GlassDivider()
                            SectionLabel("Headers & Pages")
                            OutlinedTextField(value = requestHeaders, onValueChange = { requestHeaders = it }, label = { Text("Request Headers", color = TextMuted, fontSize = 12.sp) }, colors = fieldColors, modifier = Modifier.fillMaxWidth().height(70.dp), maxLines = 3)
                            OutlinedTextField(value = serverHeaders, onValueChange = { serverHeaders = it }, label = { Text("Server Headers", color = TextMuted, fontSize = 12.sp) }, colors = fieldColors, modifier = Modifier.fillMaxWidth().height(70.dp), maxLines = 3)
                            OutlinedTextField(value = pageError, onValueChange = { pageError = it }, label = { Text("Error Page HTML", color = TextMuted, fontSize = 12.sp) }, colors = fieldColors, modifier = Modifier.fillMaxWidth().height(70.dp), maxLines = 3)
                            OutlinedTextField(value = pagePayload, onValueChange = { pagePayload = it }, label = { Text("Payload Page", color = TextMuted, fontSize = 12.sp) }, colors = fieldColors, modifier = Modifier.fillMaxWidth().height(70.dp), maxLines = 3)

                            GlassDivider()
                            SectionLabel("Encryption & SSL")
                            OutlinedTextField(value = encryptKey, onValueChange = { encryptKey = it }, label = { Text("Encryption Key (32 hex)", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            SslToggle(ssl, { ssl = it })
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("Trust X-Forwarded-For", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Switch(checked = trustXForwardedFor, onCheckedChange = { trustXForwardedFor = it }, colors = SwitchDefaults.colors(checkedThumbColor = Crimson, checkedTrackColor = Crimson.copy(alpha = 0.3f)))
                            }
                        }

                        // ===== BEACON SMB =====
                        ListenerKind.BEACON_SMB -> {
                            GlassDivider()
                            SectionLabel("SMB Configuration")
                            OutlinedTextField(value = pipename, onValueChange = { pipename = it }, label = { Text("Pipe Name", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = encryptKey, onValueChange = { encryptKey = it }, label = { Text("Encryption Key (32 hex)", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                        }

                        // ===== KHARON HTTP =====
                        ListenerKind.KHARON_HTTP -> {
                            GlassDivider()
                            SectionLabel("Connection")
                            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Bind Host", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())

                            GlassDivider()
                            SectionLabel("Upload Profile")
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = kharonProfileFileName.ifBlank { "No file selected" },
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    label = { Text("Profile File", color = TextMuted, fontSize = 12.sp) },
                                    colors = fieldColors,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { profileFilePicker.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Crimson),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Browse", fontSize = 12.sp)
                                }
                            }

                            GlassDivider()
                            SectionLabel("Domain Rotation")
                            DropdownField(label = "Rotation: $kharonDomainRotation", expanded = kharonRotationExpanded, onToggle = { kharonRotationExpanded = it }) {
                                listOf("Random", "Round Robin", "Failover").forEach { r ->
                                    DropdownMenuItem(text = { Text(r, color = TextPrimary, fontSize = 13.sp) }, onClick = { kharonDomainRotation = r; kharonRotationExpanded = false })
                                }
                            }

                            GlassDivider()
                            SectionLabel("Host Trust")
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("Trust X-Forwarded-Host", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Switch(checked = trustXForwardedHost, onCheckedChange = { trustXForwardedHost = it }, colors = SwitchDefaults.colors(checkedThumbColor = Crimson, checkedTrackColor = Crimson.copy(alpha = 0.3f)))
                            }
                            OutlinedTextField(value = additionalTrustedHosts, onValueChange = { additionalTrustedHosts = it }, label = { Text("Additional Trusted Hosts", color = TextMuted, fontSize = 12.sp) }, placeholder = { Text("domain1.com\\ndomain2.com", color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp) }, colors = fieldColors, modifier = Modifier.fillMaxWidth().height(80.dp), maxLines = 3)

                            GlassDivider()
                            SectionLabel("Traffic Filtering")
                            OutlinedTextField(value = kharonBlockUserAgents, onValueChange = { kharonBlockUserAgents = it }, label = { Text("Block User Agents", color = TextMuted, fontSize = 12.sp) }, placeholder = { Text("curl*\\nwget*", color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp) }, colors = fieldColors, modifier = Modifier.fillMaxWidth().height(80.dp), maxLines = 3)

                            GlassDivider()
                            SectionLabel("Proxy")
                            OutlinedTextField(value = kharonProxyUrl, onValueChange = { kharonProxyUrl = it }, label = { Text("Proxy URL", color = TextMuted, fontSize = 12.sp) }, placeholder = { Text("http://127.0.0.1:8080", color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = kharonProxyUser, onValueChange = { kharonProxyUser = it }, label = { Text("Proxy Username", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = kharonProxyPass, onValueChange = { kharonProxyPass = it }, label = { Text("Proxy Password", color = TextMuted, fontSize = 12.sp) }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())

                            GlassDivider()
                            SectionLabel("SSL / HTTPS")
                            SslToggle(ssl, { ssl = it })
                        }

                        // ===== UNKNOWN =====
                        ListenerKind.UNKNOWN -> {
                            GlassDivider()
                            Text("Unknown listener type. Configuration not available.", color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    // === Save Profile Toggle ===
                    if (kind != ListenerKind.UNKNOWN) {
                        GlassDivider()
                        SectionLabel("Save as Profile")
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Save config", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Switch(checked = saveProfile, onCheckedChange = { saveProfile = it }, colors = SwitchDefaults.colors(checkedThumbColor = Crimson, checkedTrackColor = Crimson.copy(alpha = 0.3f)))
                        }
                        if (saveProfile) {
                            OutlinedTextField(
                                value = saveProfileName,
                                onValueChange = { saveProfileName = it },
                                label = { Text("Profile Name", color = TextMuted, fontSize = 12.sp) },
                                singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val regName = selectedType?.name ?: return@Button
                    if (saveProfile && saveProfileName.isNotBlank() && kind != ListenerKind.UNKNOWN) {
                        onSaveProfile(ListenerProfile(
                            name = saveProfileName,
                            listenerKind = kind.name,
                            fields = collectFields()
                        ))
                    }
                    onCreate(name, regName, buildConfig())
                },
                enabled = name.isNotBlank() && selectedType != null && kind != ListenerKind.UNKNOWN,
                colors = ButtonDefaults.buttonColors(containerColor = Crimson),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Create", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun SslToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Enable SSL", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Crimson, checkedTrackColor = Crimson.copy(alpha = 0.3f)))
    }
}

private fun randomHex(length: Int): String {
    val chars = "0123456789abcdef"
    return (1..length).map { chars.random() }.joinToString("")
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Crimson,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun DropdownField(
    label: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        OutlinedButton(
            onClick = { onToggle(true) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onToggle(false) },
            modifier = Modifier.background(SurfaceCard, RoundedCornerShape(12.dp)),
            content = content
        )
    }
}

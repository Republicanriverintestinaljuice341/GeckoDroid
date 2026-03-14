package com.adaptix.client.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.adaptix.client.BuildConfig
import androidx.lifecycle.viewModelScope
import com.adaptix.client.api.AdaptixApi
import com.adaptix.client.api.WebSocketManager
import com.adaptix.client.models.*
import com.adaptix.client.service.AdaptixService
import com.adaptix.client.service.NotificationHelper
import com.adaptix.client.util.Prefs
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppState(
    val isLoggedIn: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val serverProfile: ServerProfile? = null,
    val agents: List<Agent> = emptyList(),
    val listeners: List<Listener> = emptyList(),
    val downloads: List<Download> = emptyList(),
    val screenshots: List<Screenshot> = emptyList(),
    val credentials: List<Credential> = emptyList(),
    val tunnels: List<Tunnel> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val selectedAgentId: String? = null,
    val agentTasks: List<AgentTask> = emptyList(),
    val wsConnected: Boolean = false,
    val taskCache: Map<String, List<AgentTask>> = emptyMap(),
    val registeredCommands: Map<String, List<RegisteredCommand>> = emptyMap(),
    val consoleClearedAt: Map<String, Long> = emptyMap(),
    val chatClearedAt: Long = 0L,
    val registeredListenerTypes: List<ListenerTypeInfo> = emptyList(),
    val registeredAgentTypes: List<AgentTypeInfo> = emptyList(),
    val isGenerating: Boolean = false,
    val generateError: String? = null,
    val generateSuccess: String? = null,
    val generatePreSelectedListener: String? = null,
    val buildLogs: List<BuildLogEntry> = emptyList(),
    val favoriteCommands: List<String> = emptyList()
)

data class ListenerTypeInfo(
    val name: String,
    val protocol: String,
    val type: String
)

data class BuildLogEntry(
    val status: Int,  // 0=NONE, 1=INFO, 2=ERROR, 3=SUCCESS
    val message: String
)

data class RegisteredCommand(
    val name: String,
    val description: String,
    val subcommands: List<RegisteredCommand> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    val prefs = Prefs(application)
    private var api: AdaptixApi? = null
    private var wsManager: WebSocketManager? = null
    private var reconnectJob: Job? = null
    private var reconnectDelay = 3000L
    private var syncComplete = false
    private val gson = Gson()

    init {
        // Create notification channels early
        createNotificationChannels()

        // Load favorites into reactive state
        _state.value = _state.value.copy(favoriteCommands = prefs.favoriteCommands)

        // Auto-login is triggered from UI after biometric passes (see MainActivity)
    }

    private fun createNotificationChannels() {
        val nm = getApplication<Application>().getSystemService(android.app.NotificationManager::class.java)

        val persistent = android.app.NotificationChannel(
            AdaptixService.CHANNEL_ID_PERSISTENT,
            "Adaptix Connection",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the connection alive in background"
            setShowBadge(false)
        }
        nm.createNotificationChannel(persistent)

        val alerts = android.app.NotificationChannel(
            AdaptixService.CHANNEL_ID_ALERTS,
            "Adaptix Alerts",
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New agent connections and important events"
            enableVibration(true)
            enableLights(true)
        }
        nm.createNotificationChannel(alerts)
    }

    companion object {
        private const val TAG = "AdaptixVM"
        // Static ref so the service can disconnect WS on task removal (app swipe-kill)
        @Volatile var activeWsManager: WebSocketManager? = null
    }

    override fun onCleared() {
        super.onCleared()
        reconnectJob?.cancel()
        wsManager?.disconnect()
        wsManager = null
        activeWsManager = null
    }

    fun login(profile: ServerProfile) {
        _state.value = _state.value.copy(isConnecting = true, connectionError = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scheme = if (profile.ssl) "https" else "http"
                val baseUrl = "$scheme://${profile.host}:${profile.port}${profile.endpoint}"
                if (BuildConfig.DEBUG) Log.d(TAG, "Connecting to $baseUrl")
                val newApi = AdaptixApi(baseUrl, trustAllCerts = true)

                val result = newApi.login(profile.username, profile.password)
                result.onSuccess {
                    api = newApi
                    prefs.lastProfile = profile
                    prefs.addProfile(profile)

                    _state.value = _state.value.copy(
                        isLoggedIn = true,
                        isConnecting = false,
                        serverProfile = profile,
                        connectionError = null
                    )

                    // Start foreground service to keep alive in background
                    startForegroundService()

                    // Initial data sync via REST
                    syncAllData()

                    // Connect WebSocket for real-time updates
                    connectWebSocket()

                }.onFailure { e ->
                    Log.e(TAG, "Login failed: ${e.message}")
                    _state.value = _state.value.copy(
                        isConnecting = false,
                        connectionError = e.message ?: "Login failed"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                _state.value = _state.value.copy(
                    isConnecting = false,
                    connectionError = e.message ?: "Connection error"
                )
            }
        }
    }

    fun logout() {
        reconnectJob?.cancel()
        wsManager?.disconnect()
        wsManager = null
        activeWsManager = null
        api = null
        stopForegroundService()
        _state.value = AppState()
    }

    private fun startForegroundService() {
        try {
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, AdaptixService::class.java)
            ctx.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        try {
            val ctx = getApplication<Application>()
            ctx.stopService(Intent(ctx, AdaptixService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop foreground service: ${e.message}")
        }
    }

    private fun connectWebSocket() {
        val currentApi = api ?: return
        reconnectJob?.cancel()
        val hadPreviousWs = wsManager != null
        wsManager?.disconnect()
        syncComplete = false
        wsManager = WebSocketManager(
            currentApi,
            onPacket = { type, json ->
                viewModelScope.launch(Dispatchers.Main) {
                    handlePacket(type, json)
                }
            },
            onStatus = { event, data ->
                viewModelScope.launch(Dispatchers.Main) {
                    handleWsStatus(event, data)
                }
            },
            onCategoryBatch = { category, packets ->
                viewModelScope.launch(Dispatchers.Main) {
                    handleCategoryBatch(category, packets)
                }
                // Return true for categories we handle as a batch (replacing state)
                category == "chat_history"
            }
        )
        activeWsManager = wsManager

        viewModelScope.launch(Dispatchers.IO) {
            if (hadPreviousWs) {
                // Wait for server to release the old session before reconnecting
                kotlinx.coroutines.delay(2000)
            }
            wsManager?.connect()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleWsStatus(event: String, data: String) {
        when (event) {
            "connected" -> {
                _state.value = _state.value.copy(wsConnected = true)
                reconnectDelay = 3000L
                // Trigger server-side sync so it pushes initial state via WS
                viewModelScope.launch(Dispatchers.IO) {
                    api?.triggerSync()?.onSuccess {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Server sync triggered")
                    }?.onFailure { e ->
                        Log.e(TAG, "Server sync failed: ${e.message}")
                    }
                }
                syncAllData()
            }
            "disconnected" -> {
                _state.value = _state.value.copy(wsConnected = false)
                scheduleReconnect()
            }
            "error" -> {
                _state.value = _state.value.copy(wsConnected = false)
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch(Dispatchers.IO) {
            val jitter = (0..(reconnectDelay / 4)).random()
            val delay = reconnectDelay + jitter
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(30000L)
            if (BuildConfig.DEBUG) Log.d(TAG, "Auto-reconnecting WebSocket in ${delay}ms...")
            kotlinx.coroutines.delay(delay)
            if (_state.value.isLoggedIn && !_state.value.wsConnected) {
                // Refresh token before reconnect attempt
                api?.refreshAccessToken()
                connectWebSocket()
            }
        }
    }

    fun ensureConnected() {
        // If WS is already connected, just refresh data
        if (_state.value.isLoggedIn && _state.value.wsConnected) {
            if (BuildConfig.DEBUG) Log.d(TAG, "App resumed — WS still connected, refreshing data")
            syncAllData()
            return
        }
        if (_state.value.isLoggedIn && wsManager?.isConnected != true) {
            if (BuildConfig.DEBUG) Log.d(TAG, "App resumed — reconnecting WebSocket")
            reconnectJob?.cancel()
            reconnectDelay = 3000L
            viewModelScope.launch(Dispatchers.IO) {
                api?.refreshAccessToken()
                withContext(Dispatchers.Main) {
                    connectWebSocket()
                }
            }
        }
    }

    private fun handlePacket(type: Int, json: JsonObject) {
        try {
            when (type) {
                // --- Agents ---
                WebSocketManager.TYPE_AGENT_NEW -> onAgentNew(json)
                WebSocketManager.TYPE_AGENT_UPDATE -> onAgentUpdate(json)
                WebSocketManager.TYPE_AGENT_REMOVE -> onAgentRemove(json)
                WebSocketManager.TYPE_AGENT_TICK -> onAgentTick(json)

                // --- Listeners ---
                WebSocketManager.TYPE_LISTENER_START -> refreshListeners()
                WebSocketManager.TYPE_LISTENER_EDIT -> refreshListeners()
                WebSocketManager.TYPE_LISTENER_STOP -> refreshListeners()

                // --- Tasks (WS uses a_ prefix) ---
                WebSocketManager.TYPE_AGENT_TASK_SYNC -> onTaskSync(json)
                WebSocketManager.TYPE_AGENT_TASK_UPDATE -> onTaskUpdate(json)
                WebSocketManager.TYPE_AGENT_TASK_SEND -> {} // Task sent to agent, informational only
                WebSocketManager.TYPE_AGENT_TASK_REMOVE -> onTaskRemove(json)

                // --- Console (task output directly) ---
                WebSocketManager.TYPE_AGENT_CONSOLE_OUT -> onConsoleOutput(json)
                WebSocketManager.TYPE_AGENT_CONSOLE_ERROR -> onConsoleOutput(json)
                WebSocketManager.TYPE_AGENT_CONSOLE_LOCAL -> onConsoleOutput(json)
                WebSocketManager.TYPE_AGENT_CONSOLE_TASK_SYNC -> onConsoleTaskSync(json)
                WebSocketManager.TYPE_AGENT_CONSOLE_TASK_UPD -> onConsoleTaskUpdate(json)

                // --- Downloads ---
                WebSocketManager.TYPE_DOWNLOAD_CREATE,
                WebSocketManager.TYPE_DOWNLOAD_UPDATE,
                WebSocketManager.TYPE_DOWNLOAD_DELETE,
                WebSocketManager.TYPE_DOWNLOAD_ACTUAL -> refreshDownloads()

                // --- Screenshots ---
                WebSocketManager.TYPE_SCREEN_CREATE,
                WebSocketManager.TYPE_SCREEN_UPDATE,
                WebSocketManager.TYPE_SCREEN_DELETE -> refreshScreenshots()

                // --- Tunnels ---
                WebSocketManager.TYPE_TUNNEL_CREATE,
                WebSocketManager.TYPE_TUNNEL_EDIT,
                WebSocketManager.TYPE_TUNNEL_DELETE -> refreshTunnels()

                // --- Credentials ---
                WebSocketManager.TYPE_CREDS_CREATE,
                WebSocketManager.TYPE_CREDS_EDIT,
                WebSocketManager.TYPE_CREDS_DELETE,
                WebSocketManager.TYPE_CREDS_SET_TAG -> refreshCredentials()

                // --- Targets ---
                WebSocketManager.TYPE_TARGETS_CREATE,
                WebSocketManager.TYPE_TARGETS_EDIT,
                WebSocketManager.TYPE_TARGETS_DELETE,
                WebSocketManager.TYPE_TARGETS_SET_TAG -> {} // Targets not displayed in mobile UI

                // --- Chat ---
                WebSocketManager.TYPE_CHAT_MESSAGE -> onChatMessage(json)

                // --- Notification ---
                WebSocketManager.TYPE_NOTIFICATION -> onNotification(json)

                // --- Sync ---
                WebSocketManager.TYPE_SYNC_FINISH -> {
                    syncComplete = true
                    if (BuildConfig.DEBUG) Log.d(TAG, "Sync complete — notifications enabled")
                }

                // --- Registration ---
                WebSocketManager.TYPE_LISTENER_REG -> onListenerReg(json)
                WebSocketManager.TYPE_SERVICE_REG -> {}
                WebSocketManager.TYPE_AGENT_REG -> onAgentReg(json)
                WebSocketManager.TYPE_AXSCRIPT_COMMANDS -> onAxScriptCommands(json)

                else -> if (BuildConfig.DEBUG) Log.d(TAG, "Unhandled packet type=0x${type.toString(16)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handlePacket error type=0x${type.toString(16)}", e)
        }
    }

    // --- Agent event handlers ---

    private fun onAgentNew(json: JsonObject) {
        val agent = gson.fromJson(json, Agent::class.java)
        val current = _state.value.agents.toMutableList()
        current.removeAll { it.id == agent.id }
        current.add(0, agent)
        _state.value = _state.value.copy(agents = current)

        // Push notification only for genuinely new agents (after initial sync)
        if (syncComplete) {
            try {
                NotificationHelper.showNewAgentNotification(
                    getApplication(),
                    agent.displayName,
                    agent.osName,
                    agent.internalIp
                )
            } catch (e: Exception) {
                Log.e(TAG, "Notification error: ${e.message}")
            }
        }
    }

    private fun onAgentUpdate(json: JsonObject) {
        val agentId = json.get("a_id")?.asString ?: return
        val current = _state.value.agents.toMutableList()
        val idx = current.indexOfFirst { it.id == agentId }
        if (idx >= 0) {
            // Partial update — merge fields
            val existing = current[idx]
            current[idx] = existing.copy(
                sleep = json.get("a_sleep")?.asLong ?: existing.sleep,
                jitter = json.get("a_jitter")?.asLong ?: existing.jitter,
                impersonated = json.get("a_impersonated")?.asString ?: existing.impersonated,
                tags = json.get("a_tags")?.asString ?: existing.tags,
                mark = json.get("a_mark")?.asString ?: existing.mark,
                color = json.get("a_color")?.asString ?: existing.color,
                internalIp = json.get("a_internal_ip")?.asString ?: existing.internalIp,
                externalIp = json.get("a_external_ip")?.asString ?: existing.externalIp,
                pid = json.get("a_pid")?.asString ?: existing.pid,
                tid = json.get("a_tid")?.asString ?: existing.tid,
                process = json.get("a_process")?.asString ?: existing.process,
                elevated = json.get("a_elevated")?.asBoolean ?: existing.elevated,
                domain = json.get("a_domain")?.asString ?: existing.domain,
                computer = json.get("a_computer")?.asString ?: existing.computer,
                username = json.get("a_username")?.asString ?: existing.username,
                listener = json.get("a_listener")?.asString ?: existing.listener
            )
            _state.value = _state.value.copy(agents = current)
        } else {
            // Unknown agent, refresh
            refreshAgents()
        }
    }

    private fun onAgentRemove(json: JsonObject) {
        val agentId = json.get("a_id")?.asString ?: return
        val current = _state.value.agents.filter { it.id != agentId }
        _state.value = _state.value.copy(agents = current)
    }

    private fun onAgentTick(json: JsonObject) {
        val agentIds = json.getAsJsonArray("a_id") ?: return
        val ids = agentIds.mapNotNull { it.asString }
        val nowSec = System.currentTimeMillis() / 1000
        val previousAgents = _state.value.agents
        val current = previousAgents.map { agent ->
            if (agent.id in ids) agent.copy(lastTick = nowSec) else agent
        }
        _state.value = _state.value.copy(agents = current)

        // Detect agents that just died (were alive, now not)
        if (syncComplete) {
            for (i in current.indices) {
                val prev = previousAgents[i]
                val now = current[i]
                if (prev.isAlive && !now.isAlive) {
                    try {
                        NotificationHelper.showAlertNotification(
                            getApplication(),
                            "Agent Offline",
                            "${now.displayName} (${now.internalIp}) went offline"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Death notification error: ${e.message}")
                    }
                }
            }
        }
    }

    // --- Task cache helpers ---

    /**
     * Upsert a task into the per-agent cache. Also updates agentTasks if
     * the task belongs to the currently selected agent.
     */
    private fun cacheUpsertTask(task: AgentTask) {
        val aid = task.agentId
        if (aid.isBlank()) return

        // Skip tasks from before the clear time
        val clearedAt = _state.value.consoleClearedAt[aid]
        if (clearedAt != null && task.startDate > 0 && (task.startDate * 1000) <= clearedAt) return

        val cache = _state.value.taskCache.toMutableMap()
        val list = (cache[aid] ?: emptyList()).toMutableList()
        val idx = list.indexOfFirst { it.taskId == task.taskId }
        if (idx >= 0) list[idx] = task else list.add(task)
        list.sortBy { it.startDate }
        cache[aid] = list

        if (aid == _state.value.selectedAgentId) {
            _state.value = _state.value.copy(taskCache = cache, agentTasks = list.toList())
        } else {
            _state.value = _state.value.copy(taskCache = cache)
        }
    }

    /**
     * Update an existing task in the cache by taskId+agentId with partial fields.
     */
    private fun cacheUpdateTask(agentId: String, taskId: String, updater: (AgentTask) -> AgentTask) {
        if (agentId.isBlank() || taskId.isBlank()) return

        val cache = _state.value.taskCache.toMutableMap()
        val list = (cache[agentId] ?: emptyList()).toMutableList()
        val idx = list.indexOfFirst { it.taskId == taskId }
        if (idx < 0) return
        list[idx] = updater(list[idx])
        cache[agentId] = list

        if (agentId == _state.value.selectedAgentId) {
            _state.value = _state.value.copy(taskCache = cache, agentTasks = list.toList())
        } else {
            _state.value = _state.value.copy(taskCache = cache)
        }
    }

    private fun cacheRemoveTask(agentId: String, taskId: String) {
        val cache = _state.value.taskCache.toMutableMap()
        val list = (cache[agentId] ?: return).filter { it.taskId != taskId }
        cache[agentId] = list

        if (agentId == _state.value.selectedAgentId) {
            _state.value = _state.value.copy(taskCache = cache, agentTasks = list)
        } else {
            _state.value = _state.value.copy(taskCache = cache)
        }
    }

    // Accumulate output text — server may send data line by line across multiple updates
    private fun appendText(existing: String, new: String): String {
        if (new.isBlank()) return existing
        if (existing.isBlank()) return new
        // If existing already contains the new text, skip (duplicate)
        if (existing.contains(new)) return existing
        // If new already contains existing (full replacement), use new
        if (new.contains(existing)) return new
        return "$existing\n$new"
    }

    // --- Task event handlers (WS uses a_ prefix fields) ---

    private fun wsTaskToAgentTask(json: JsonObject): AgentTask {
        return AgentTask(
            taskId = json.get("a_task_id")?.asString ?: "",
            agentId = json.get("a_id")?.asString ?: "",
            client = json.get("a_client")?.asString ?: "",
            user = json.get("a_user")?.asString ?: "",
            computer = json.get("a_computer")?.asString ?: "",
            commandLine = json.get("a_cmdline")?.asString ?: "",
            startDate = json.get("a_start_time")?.asLong ?: 0,
            finishDate = json.get("a_finish_time")?.asLong ?: 0,
            messageType = json.get("a_msg_type")?.asInt ?: 0,
            message = json.get("a_message")?.asString ?: "",
            clearText = json.get("a_text")?.asString ?: "",
            completed = json.get("a_completed")?.asBoolean ?: false,
            type = json.get("a_task_type")?.asInt ?: 0
        )
    }

    private fun onTaskSync(json: JsonObject) {
        val task = wsTaskToAgentTask(json)
        if (BuildConfig.DEBUG) Log.d(TAG, "onTaskSync: task=${task.taskId.take(8)} agent=${task.agentId.take(8)} cmd='${task.commandLine}' selected=${_state.value.selectedAgentId?.take(8)}")
        // If task already exists (e.g. update arrived first), merge — don't overwrite output with empty
        val existing = _state.value.taskCache[task.agentId]?.find { it.taskId == task.taskId }
        if (existing != null) {
            cacheUpdateTask(task.agentId, task.taskId) { ex ->
                ex.copy(
                    client = task.client.ifBlank { ex.client },
                    commandLine = task.commandLine.ifBlank { ex.commandLine },
                    startDate = if (task.startDate > 0) task.startDate else ex.startDate,
                    finishDate = if (task.finishDate > 0) task.finishDate else ex.finishDate,
                    messageType = if (task.messageType != 0) task.messageType else ex.messageType,
                    message = task.message.ifBlank { ex.message },
                    clearText = appendText(ex.clearText, task.clearText),
                    completed = task.completed || ex.completed
                )
            }
        } else {
            cacheUpsertTask(task)
        }
    }

    private fun onTaskUpdate(json: JsonObject) {
        val taskId = json.get("a_task_id")?.asString ?: return
        val agentId = json.get("a_id")?.asString ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "onTaskUpdate: task=${taskId.take(8)} agent=${agentId.take(8)} msg='${json.get("a_message")?.asString?.take(50)}' text='${json.get("a_text")?.asString?.take(50)}' completed=${json.get("a_completed")?.asBoolean}")
        val exists = _state.value.taskCache[agentId]?.any { it.taskId == taskId } == true
        if (exists) {
            val nowCompleted = json.get("a_completed")?.asBoolean ?: false
            cacheUpdateTask(agentId, taskId) { existing ->
                val newMsg = json.get("a_message")?.asString ?: ""
                val newText = json.get("a_text")?.asString ?: ""
                existing.copy(
                    finishDate = json.get("a_finish_time")?.asLong ?: existing.finishDate,
                    messageType = json.get("a_msg_type")?.asInt ?: existing.messageType,
                    message = newMsg.ifBlank { existing.message },
                    clearText = appendText(existing.clearText, newText),
                    completed = nowCompleted
                )
            }
        } else {
            cacheUpsertTask(wsTaskToAgentTask(json))
        }
    }

    private fun onTaskRemove(json: JsonObject) {
        val taskId = json.get("a_task_id")?.asString ?: return
        // Could belong to any agent; check all cached or use agentId if present
        val agentId = json.get("a_id")?.asString
        if (agentId != null) {
            cacheRemoveTask(agentId, taskId)
        } else {
            // Fallback: remove from selected agent's visible list
            val current = _state.value.agentTasks.filter { it.taskId != taskId }
            _state.value = _state.value.copy(agentTasks = current)
        }
    }

    // --- Console output handlers ---

    private fun onConsoleOutput(json: JsonObject) {
        val agentId = json.get("a_id")?.asString ?: return
        val newMsg = json.get("a_message")?.asString ?: ""
        val newText = json.get("a_text")?.asString ?: ""
        val cmdLine = json.get("a_cmdline")?.asString ?: ""

        // Status messages (no cmdline) get appended to the last task's message field
        val agentTasks = _state.value.taskCache[agentId]
        val lastTask = agentTasks?.lastOrNull()
        if (lastTask != null && cmdLine.isBlank()) {
            cacheUpdateTask(agentId, lastTask.taskId) { existing ->
                existing.copy(
                    // Append status messages (e.g. "[*] BOF implementation: ...", "[*] Agent called server...")
                    message = if (newMsg.isNotBlank()) {
                        if (existing.message.isNotBlank()) "${existing.message}\n$newMsg" else newMsg
                    } else existing.message,
                    // Append text output if provided
                    clearText = if (newText.isNotBlank()) {
                        if (existing.clearText.isNotBlank()) "${existing.clearText}\n$newText" else newText
                    } else existing.clearText
                )
            }
        } else {
            cacheUpsertTask(AgentTask(
                taskId = "console_${System.nanoTime()}",
                agentId = agentId,
                messageType = json.get("a_msg_type")?.asInt ?: 0,
                message = newMsg,
                clearText = newText,
                commandLine = cmdLine,
                completed = true
            ))
        }
    }

    private fun onConsoleTaskSync(json: JsonObject) {
        val agentId = json.get("a_id")?.asString ?: return
        val taskId = json.get("a_task_id")?.asString ?: ""
        val newMsg = json.get("a_message")?.asString ?: ""
        val newText = json.get("a_text")?.asString ?: ""

        // If task already exists, merge (don't overwrite non-empty fields with empty)
        val existing = _state.value.taskCache[agentId]?.find { it.taskId == taskId }
        if (existing != null) {
            cacheUpdateTask(agentId, taskId) { ex ->
                ex.copy(
                    client = json.get("a_client")?.asString?.ifBlank { null } ?: ex.client,
                    commandLine = json.get("a_cmdline")?.asString?.ifBlank { null } ?: ex.commandLine,
                    startDate = json.get("a_start_time")?.asLong ?: ex.startDate,
                    finishDate = json.get("a_finish_time")?.asLong ?: ex.finishDate,
                    messageType = json.get("a_msg_type")?.asInt ?: ex.messageType,
                    message = newMsg.ifBlank { ex.message },
                    clearText = appendText(ex.clearText, newText),
                    completed = json.get("a_completed")?.asBoolean ?: ex.completed
                )
            }
        } else {
            cacheUpsertTask(AgentTask(
                taskId = taskId,
                agentId = agentId,
                client = json.get("a_client")?.asString ?: "",
                commandLine = json.get("a_cmdline")?.asString ?: "",
                startDate = json.get("a_start_time")?.asLong ?: 0,
                finishDate = json.get("a_finish_time")?.asLong ?: 0,
                messageType = json.get("a_msg_type")?.asInt ?: 0,
                message = newMsg,
                clearText = newText,
                completed = json.get("a_completed")?.asBoolean ?: false
            ))
        }
    }

    private fun onConsoleTaskUpdate(json: JsonObject) {
        val agentId = json.get("a_id")?.asString ?: return
        val taskId = json.get("a_task_id")?.asString ?: return
        val exists = _state.value.taskCache[agentId]?.any { it.taskId == taskId } == true
        if (exists) {
            cacheUpdateTask(agentId, taskId) { existing ->
                val newMsg = json.get("a_message")?.asString ?: ""
                val newText = json.get("a_text")?.asString ?: ""
                existing.copy(
                    finishDate = json.get("a_finish_time")?.asLong ?: existing.finishDate,
                    messageType = json.get("a_msg_type")?.asInt ?: existing.messageType,
                    message = newMsg.ifBlank { existing.message },
                    clearText = appendText(existing.clearText, newText),
                    completed = json.get("a_completed")?.asBoolean ?: existing.completed
                )
            }
        } else {
            cacheUpsertTask(AgentTask(
                taskId = taskId,
                agentId = agentId,
                client = json.get("a_client")?.asString ?: "",
                commandLine = json.get("a_cmdline")?.asString ?: "",
                startDate = json.get("a_start_time")?.asLong ?: 0,
                finishDate = json.get("a_finish_time")?.asLong ?: 0,
                messageType = json.get("a_msg_type")?.asInt ?: 0,
                message = json.get("a_message")?.asString ?: "",
                clearText = json.get("a_text")?.asString ?: "",
                completed = json.get("a_completed")?.asBoolean ?: false
            ))
        }
    }

    // --- Category batch handler ---

    private fun handleCategoryBatch(category: String, packets: JsonArray) {
        when (category) {
            "chat_history" -> {
                val clearedAt = _state.value.chatClearedAt
                val messages = mutableListOf<ChatMessage>()
                for (packet in packets) {
                    if (!packet.isJsonObject) continue
                    val obj = packet.asJsonObject
                    val date = obj.get("c_date")?.asLong ?: 0
                    // Filter out messages from before the clear
                    if (clearedAt > 0 && (date * 1000) <= clearedAt) continue
                    messages.add(
                        ChatMessage(
                            username = obj.get("c_username")?.asString ?: "",
                            message = obj.get("c_message")?.asString ?: "",
                            date = date
                        )
                    )
                }
                _state.value = _state.value.copy(chatMessages = messages)
            }
        }
    }

    // --- Chat ---

    private fun onChatMessage(json: JsonObject) {
        val msg = ChatMessage(
            username = json.get("c_username")?.asString ?: "",
            message = json.get("c_message")?.asString ?: "",
            date = json.get("c_date")?.asLong ?: (System.currentTimeMillis() / 1000)
        )
        // Filter out messages from before the clear
        val clearedAt = _state.value.chatClearedAt
        if (clearedAt > 0 && (msg.date * 1000) <= clearedAt) return

        val current = _state.value.chatMessages.toMutableList()
        if (current.none { it.username == msg.username && it.message == msg.message && it.date == msg.date }) {
            current.add(msg)
            _state.value = _state.value.copy(chatMessages = current)
        }
    }

    // --- Notification ---

    private fun onNotification(json: JsonObject) {
        val message = json.get("message")?.asString ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "Server notification: $message")
        // Skip server-side new agent notifications — we show our own from onAgentNew
        if (message.contains("executed on", ignoreCase = true)) return
        // Skip listener start/stop and task done notifications (too noisy)
        if (message.contains("started", ignoreCase = true) && message.contains("Listener", ignoreCase = true)) return
        if (message.contains("stopped", ignoreCase = true) && message.contains("Listener", ignoreCase = true)) return
        if (syncComplete) {
            try {
                NotificationHelper.showAlertNotification(getApplication(), "Adaptix", message)
            } catch (e: Exception) {
                Log.e(TAG, "Notification error: ${e.message}")
            }
        }
    }

    // --- Sync/Refresh ---
    fun syncAllData() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Syncing all data...")
        refreshAgents()
        refreshListeners()
        refreshDownloads()
        refreshScreenshots()
        refreshCredentials()
        refreshTunnels()
    }

    fun refreshAgents() {
        viewModelScope.launch(Dispatchers.IO) {
            api?.getAgents()?.onSuccess { agents ->
                if (BuildConfig.DEBUG) Log.d(TAG, "Got ${agents.size} agents")
                _state.value = _state.value.copy(agents = agents)
            }?.onFailure { e ->
                Log.e(TAG, "refreshAgents failed: ${e.message}")
            }
        }
    }

    fun refreshListeners() {
        viewModelScope.launch(Dispatchers.IO) {
            api?.getListeners()?.onSuccess { listeners ->
                if (BuildConfig.DEBUG) Log.d(TAG, "Got ${listeners.size} listeners")
                _state.value = _state.value.copy(listeners = listeners)
            }?.onFailure { e ->
                Log.e(TAG, "refreshListeners failed: ${e.message}")
            }
        }
    }

    fun refreshDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            api?.getDownloads()?.onSuccess { downloads ->
                _state.value = _state.value.copy(downloads = downloads)
            }?.onFailure { e ->
                Log.e(TAG, "refreshDownloads failed: ${e.message}")
            }
        }
    }

    fun refreshScreenshots() {
        viewModelScope.launch(Dispatchers.IO) {
            api?.getScreenshots()?.onSuccess { screenshots ->
                _state.value = _state.value.copy(screenshots = screenshots)
            }?.onFailure { e ->
                Log.e(TAG, "refreshScreenshots failed: ${e.message}")
            }
        }
    }

    fun removeScreenshots(screenIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            api?.removeScreenshots(screenIds)?.onSuccess {
                refreshScreenshots()
            }?.onFailure { e ->
                Log.e(TAG, "removeScreenshots failed: ${e.message}")
            }
        }
    }

    fun fetchScreenshotImage(screenId: String, onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = api?.getScreenshotImage(screenId)?.getOrNull()
            withContext(Dispatchers.Main) { onResult(bytes) }
        }
    }

    fun refreshCredentials() {
        viewModelScope.launch(Dispatchers.IO) {
            api?.getCredentials()?.onSuccess { creds ->
                _state.value = _state.value.copy(credentials = creds)
            }?.onFailure { e ->
                Log.e(TAG, "refreshCredentials failed: ${e.message}")
            }
        }
    }

    fun refreshTunnels() {
        viewModelScope.launch(Dispatchers.IO) {
            api?.getTunnels()?.onSuccess { tunnels ->
                _state.value = _state.value.copy(tunnels = tunnels)
            }?.onFailure { e ->
                Log.e(TAG, "refreshTunnels failed: ${e.message}")
            }
        }
    }

    // --- Actions ---

    private fun filterTasksByClearTime(agentId: String, tasks: List<AgentTask>): List<AgentTask> {
        val clearedAt = _state.value.consoleClearedAt[agentId] ?: return tasks
        // Keep only tasks created after the clear (startDate is epoch seconds, clearedAt is epoch millis)
        return tasks.filter { task ->
            // Tasks with startDate > 0: compare with cleared time
            // Tasks with startDate == 0 (like help/error pseudo-tasks): keep them (they're new)
            if (task.startDate > 0) {
                (task.startDate * 1000) > clearedAt
            } else {
                // Pseudo-tasks (help, error) — check if taskId contains a timestamp
                true
            }
        }
    }

    fun selectAgent(agentId: String?) {
        // Load cached tasks immediately so the console isn't empty
        val cached = if (agentId != null) {
            filterTasksByClearTime(agentId, _state.value.taskCache[agentId] ?: emptyList())
        } else emptyList()
        _state.value = _state.value.copy(selectedAgentId = agentId, agentTasks = cached)

        if (agentId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                api?.getAgentTasks(agentId)?.onSuccess { restTasks ->
                    if (BuildConfig.DEBUG) Log.d(TAG, "Got ${restTasks.size} tasks for agent $agentId")
                    // Merge: REST is authoritative, but keep any cache-only entries
                    val restIds = restTasks.map { it.taskId }.toSet()
                    val cacheOnly = (_state.value.taskCache[agentId] ?: emptyList())
                        .filter { it.taskId !in restIds }
                    val merged = (restTasks + cacheOnly).sortedBy { it.startDate }
                    val filtered = filterTasksByClearTime(agentId, merged)

                    // Update cache with filtered result
                    val cache = _state.value.taskCache.toMutableMap()
                    cache[agentId] = filtered
                    _state.value = _state.value.copy(agentTasks = filtered, taskCache = cache)
                }?.onFailure { e ->
                    Log.e(TAG, "getAgentTasks failed: ${e.message}")
                }
            }
        }
    }

    fun executeCommand(agentId: String, command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Executing command: '$command' on agent $agentId")
            api?.executeCommand(agentId, command)?.onSuccess { resp ->
                if (BuildConfig.DEBUG) Log.d(TAG, "Command sent: ok=${resp.ok} msg=${resp.message}")
            }?.onFailure { e ->
                Log.e(TAG, "executeCommand failed: ${e.message}")
                val errorTask = AgentTask(
                    taskId = "error_${System.nanoTime()}",
                    agentId = agentId,
                    messageType = 6,
                    message = e.message ?: "Command failed",
                    completed = true,
                    commandLine = command
                )
                viewModelScope.launch(Dispatchers.Main) {
                    cacheUpsertTask(errorTask)
                }
            }
        }
    }


    fun addFavoriteCommand(cmd: String) {
        prefs.addFavoriteCommand(cmd)
        _state.value = _state.value.copy(favoriteCommands = prefs.favoriteCommands)
    }

    fun removeFavoriteCommand(cmd: String) {
        prefs.removeFavoriteCommand(cmd)
        _state.value = _state.value.copy(favoriteCommands = prefs.favoriteCommands)
    }

    fun removeAgents(ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            api?.removeAgents(ids)?.onSuccess { refreshAgents() }
        }
    }

    private fun onListenerReg(json: JsonObject) {
        val info = ListenerTypeInfo(
            name = json.get("l_name")?.asString ?: return,
            protocol = json.get("l_protocol")?.asString ?: "",
            type = json.get("l_type")?.asString ?: ""
        )
        val current = _state.value.registeredListenerTypes.toMutableList()
        current.removeAll { it.name == info.name }
        current.add(info)
        _state.value = _state.value.copy(registeredListenerTypes = current)
        if (BuildConfig.DEBUG) Log.d(TAG, "Registered listener type: ${info.name} (${info.protocol})")
    }

    fun createListener(name: String, type: String, config: String) {
        viewModelScope.launch(Dispatchers.IO) {
            api?.createListener(name, type, config)?.onSuccess { resp ->
                if (resp.ok) {
                    refreshListeners()
                    if (BuildConfig.DEBUG) Log.d(TAG, "Listener created")
                } else {
                    Log.e(TAG, "Create failed: ${resp.message}")
                }
            }?.onFailure { e ->
                Log.e(TAG, "Create listener failed: ${e.message}")
            }
        }
    }

    fun stopListener(name: String, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            api?.stopListener(name, type)?.onSuccess {
                refreshListeners()
            }?.onFailure { e ->
                Log.e(TAG, "Stop listener failed: ${e.message}")
            }
        }
    }

    fun stopTunnel(tunnelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            api?.stopTunnel(tunnelId)?.onSuccess {
                refreshTunnels()
            }?.onFailure { e ->
                Log.e(TAG, "Stop tunnel failed: ${e.message}")
            }
        }
    }

    fun sendChat(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            api?.sendChat(message)?.onFailure { e ->
                Log.e(TAG, "Chat failed: ${e.message}")
            }
        }
    }


    fun clearError() {
        _state.value = _state.value.copy(connectionError = null)
    }

    // --- Command registration handlers ---

    private fun parseCommandDefs(jsonArray: com.google.gson.JsonArray): List<RegisteredCommand> {
        val result = mutableListOf<RegisteredCommand>()
        for (element in jsonArray) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val name = obj.get("name")?.asString ?: continue
            val desc = obj.get("description")?.asString ?: ""
            val subs = if (obj.has("subcommands") && obj.get("subcommands").isJsonArray) {
                parseCommandDefs(obj.getAsJsonArray("subcommands"))
            } else emptyList()
            result.add(RegisteredCommand(name, desc, subs))
        }
        return result
    }

    private fun extractCommandsFromGroups(groupsJson: String): List<RegisteredCommand> {
        return try {
            val arr = com.google.gson.JsonParser.parseString(groupsJson).asJsonArray
            val result = mutableListOf<RegisteredCommand>()
            for (groupEl in arr) {
                if (!groupEl.isJsonObject) continue
                val group = groupEl.asJsonObject
                val commands = group.getAsJsonArray("commands") ?: continue
                result.addAll(parseCommandDefs(commands))
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "extractCommandsFromGroups error: ${e.message}")
            emptyList()
        }
    }

    private fun mergeCommands(agentName: String, new: List<RegisteredCommand>) {
        val map = _state.value.registeredCommands.toMutableMap()
        val existing = (map[agentName] ?: emptyList()).toMutableList()
        for (cmd in new) {
            if (existing.none { it.name == cmd.name }) {
                existing.add(cmd)
            }
        }
        existing.sortBy { it.name }
        map[agentName] = existing
        _state.value = _state.value.copy(registeredCommands = map)
    }

    private fun onAgentReg(json: JsonObject) {
        // Store agent type info
        val agentName = json.get("agent")?.asString ?: ""
        if (agentName.isNotBlank()) {
            val listeners = json.getAsJsonArray("listeners")?.mapNotNull { it.asString } ?: emptyList()
            val multiListeners = json.get("multi_listeners")?.asBoolean ?: false
            val typeInfo = AgentTypeInfo(agentName, listeners, multiListeners)
            val current = _state.value.registeredAgentTypes.toMutableList()
            current.removeAll { it.name == agentName }
            current.add(typeInfo)
            _state.value = _state.value.copy(registeredAgentTypes = current)
            if (BuildConfig.DEBUG) Log.d(TAG, "Registered agent type: $agentName (listeners: $listeners)")
        }

        // TYPE_AGENT_REG contains "groups" array of {agent, listener, os, commands(JSON string)}
        val groups = json.getAsJsonArray("groups") ?: return
        for (g in groups) {
            if (!g.isJsonObject) continue
            val gAgent = g.asJsonObject.get("agent")?.asString ?: continue
            val commandsStr = g.asJsonObject.get("commands")?.asString ?: continue
            val cmds = extractCommandsFromGroups(commandsStr)
            if (cmds.isNotEmpty()) mergeCommands(gAgent, cmds)
        }
    }

    private fun onAxScriptCommands(json: JsonObject) {
        // TYPE_AXSCRIPT_COMMANDS contains "groups" array of {agent, listener, os, commands(JSON string)}
        val groups = json.getAsJsonArray("groups") ?: return
        for (g in groups) {
            if (!g.isJsonObject) continue
            val gAgent = g.asJsonObject.get("agent")?.asString ?: continue
            val commandsStr = g.asJsonObject.get("commands")?.asString ?: continue
            val cmds = extractCommandsFromGroups(commandsStr)
            if (cmds.isNotEmpty()) mergeCommands(gAgent, cmds)
        }
    }

    fun generateAgent(agentName: String, listenerNames: List<String>, config: String, profileName: String? = null) {
        _state.value = _state.value.copy(
            isGenerating = true, generateError = null, generateSuccess = null, buildLogs = emptyList()
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Get OTP for build channel
                val otpResult = api?.generateBuildOtp(agentName, listenerNames)
                    ?: run {
                        _state.value = _state.value.copy(isGenerating = false, generateError = "Not connected")
                        return@launch
                    }
                val otp = otpResult.getOrElse { e ->
                    _state.value = _state.value.copy(isGenerating = false, generateError = e.message ?: "OTP failed")
                    return@launch
                }

                // Step 2: Connect build channel WS
                val latch = java.util.concurrent.CountDownLatch(1)
                val buildWs = api!!.connectBuildChannel(otp, object : okhttp3.WebSocketListener() {
                    override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Build channel open, sending config")
                        webSocket.send(config)
                    }

                    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Build log: ${text.take(200)}")
                        try {
                            val json = gson.fromJson(text, JsonObject::class.java)
                            val status = json.get("status")?.asInt ?: 0
                            val message = json.get("message")?.asString ?: ""

                            if (status == 4) {
                                // SAVE_FILE — contains filename + content
                                val fileName = json.get("filename")?.asString ?: "agent.bin"
                                val contentBytes = json.get("content")?.asString?.let {
                                    android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                                }

                                if (contentBytes != null && contentBytes.isNotEmpty()) {
                                    val ctx = getApplication<Application>()
                                    val savedName = saveToDownloads(ctx, fileName, contentBytes)
                                    addBuildLog(3, "Saved: $savedName (${contentBytes.size / 1024}KB)")
                                    _state.value = _state.value.copy(
                                        isGenerating = false,
                                        generateSuccess = "Saved: $savedName (${contentBytes.size / 1024}KB)"
                                    )
                                } else {
                                    addBuildLog(3, "Build complete: $fileName")
                                    _state.value = _state.value.copy(
                                        isGenerating = false,
                                        generateSuccess = "Build complete: $fileName"
                                    )
                                }
                                // Save build profile on success
                                if (!profileName.isNullOrBlank()) {
                                    val configMap: Map<String, Any> = try {
                                        gson.fromJson(config, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
                                    } catch (_: Exception) { emptyMap() }
                                    prefs.addBuildProfile(com.adaptix.client.models.BuildProfile(
                                        name = profileName,
                                        agentName = agentName,
                                        listenerName = listenerNames.firstOrNull() ?: "",
                                        config = configMap
                                    ))
                                }
                            } else {
                                addBuildLog(status, message)
                                if (status == 2) {
                                    // Error log — don't stop yet, more messages may come
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Build log parse error", e)
                            addBuildLog(0, text)
                        }
                    }

                    override fun onMessage(webSocket: okhttp3.WebSocket, bytes: okio.ByteString) {
                        // Binary message — could be the payload content directly
                        if (BuildConfig.DEBUG) Log.d(TAG, "Build channel binary: ${bytes.size} bytes")
                    }

                    override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                        if (_state.value.isGenerating) {
                            // Channel closed without SAVE_FILE — check if there was an error
                            val hasError = _state.value.buildLogs.any { it.status == 2 }
                            if (hasError) {
                                val errorMsg = _state.value.buildLogs.filter { it.status == 2 }
                                    .joinToString("\n") { it.message }
                                _state.value = _state.value.copy(
                                    isGenerating = false,
                                    generateError = errorMsg.ifBlank { "Build failed" }
                                )
                            } else {
                                _state.value = _state.value.copy(isGenerating = false)
                            }
                        }
                        latch.countDown()
                    }

                    override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                        Log.e(TAG, "Build channel failure", t)
                        _state.value = _state.value.copy(
                            isGenerating = false,
                            generateError = t.message ?: "Build channel failed"
                        )
                        latch.countDown()
                    }
                })

                // Wait for build to finish (up to 5 minutes)
                latch.await(5, java.util.concurrent.TimeUnit.MINUTES)
                if (_state.value.isGenerating) {
                    buildWs.cancel()
                    _state.value = _state.value.copy(
                        isGenerating = false,
                        generateError = "Build timed out"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "generateAgent error", e)
                _state.value = _state.value.copy(
                    isGenerating = false,
                    generateError = e.message ?: "Generation failed"
                )
            }
        }
    }

    private fun addBuildLog(status: Int, message: String) {
        if (message.isBlank()) return
        val entry = BuildLogEntry(status, message)
        val logs = _state.value.buildLogs.toMutableList()
        logs.add(entry)
        _state.value = _state.value.copy(buildLogs = logs)
    }

    private fun saveToDownloads(ctx: android.content.Context, fileName: String, data: ByteArray): String {
        val resolver = ctx.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Failed to create file in Downloads")
        resolver.openOutputStream(uri)?.use { it.write(data) }
            ?: throw Exception("Failed to write file")
        values.clear()
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return fileName
    }

    fun clearGenerateStatus() {
        _state.value = _state.value.copy(generateError = null, generateSuccess = null, buildLogs = emptyList())
    }

    fun setGenerateListener(listenerName: String?) {
        _state.value = _state.value.copy(generatePreSelectedListener = listenerName)
    }

    fun clearConsole() {
        val agentId = _state.value.selectedAgentId ?: return
        val now = System.currentTimeMillis()
        val cache = _state.value.taskCache.toMutableMap()
        cache.remove(agentId)
        val cleared = _state.value.consoleClearedAt.toMutableMap()
        cleared[agentId] = now
        _state.value = _state.value.copy(
            agentTasks = emptyList(),
            taskCache = cache,
            consoleClearedAt = cleared
        )
    }

    fun clearChat() {
        _state.value = _state.value.copy(
            chatMessages = emptyList(),
            chatClearedAt = System.currentTimeMillis()
        )
    }
}

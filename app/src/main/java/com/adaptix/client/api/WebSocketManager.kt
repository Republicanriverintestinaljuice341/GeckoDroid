package com.adaptix.client.api

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class WebSocketManager(
    private val api: AdaptixApi,
    private val onPacket: (Int, com.google.gson.JsonObject) -> Unit,
    private val onStatus: (String, String) -> Unit,
    private val onCategoryBatch: ((String, com.google.gson.JsonArray) -> Boolean)? = null
) {
    private var webSocket: WebSocket? = null
    var isConnected = false
        private set
    private var isReconnecting = false
    @Volatile private var disposed = false

    companion object {
        private const val TAG = "AdaptixWS"

        // Sync control
        const val TYPE_SYNC_START = 0x11
        const val TYPE_SYNC_FINISH = 0x12
        const val TYPE_SYNC_BATCH = 0x14
        const val TYPE_SYNC_CATEGORY_BATCH = 0x15

        // Notifications & chat
        const val TYPE_NOTIFICATION = 0x13
        const val TYPE_CHAT_MESSAGE = 0x18
        const val TYPE_SERVICE_DATA = 0x19

        // Registration
        const val TYPE_LISTENER_REG = 0x21
        const val TYPE_AGENT_REG = 0x22
        const val TYPE_SERVICE_REG = 0x23

        // Listeners
        const val TYPE_LISTENER_START = 0x31
        const val TYPE_LISTENER_EDIT = 0x32
        const val TYPE_LISTENER_STOP = 0x33

        // Agents
        const val TYPE_AGENT_NEW = 0x41
        const val TYPE_AGENT_UPDATE = 0x42
        const val TYPE_AGENT_REMOVE = 0x43
        const val TYPE_AGENT_TICK = 0x44
        const val TYPE_AGENT_LINK = 0x45

        // Tasks
        const val TYPE_AGENT_TASK_SYNC = 0x49
        const val TYPE_AGENT_TASK_UPDATE = 0x4a
        const val TYPE_AGENT_TASK_SEND = 0x4b
        const val TYPE_AGENT_TASK_REMOVE = 0x4c
        const val TYPE_AGENT_TASK_HOOK = 0x4d

        // Downloads
        const val TYPE_DOWNLOAD_CREATE = 0x51
        const val TYPE_DOWNLOAD_UPDATE = 0x52
        const val TYPE_DOWNLOAD_DELETE = 0x53
        const val TYPE_DOWNLOAD_ACTUAL = 0x54

        // Tunnels
        const val TYPE_TUNNEL_CREATE = 0x57
        const val TYPE_TUNNEL_EDIT = 0x58
        const val TYPE_TUNNEL_DELETE = 0x59

        // Screenshots
        const val TYPE_SCREEN_CREATE = 0x5b
        const val TYPE_SCREEN_UPDATE = 0x5c
        const val TYPE_SCREEN_DELETE = 0x5d

        // Console
        const val TYPE_AGENT_CONSOLE_LOCAL = 0x67
        const val TYPE_AGENT_CONSOLE_ERROR = 0x68
        const val TYPE_AGENT_CONSOLE_OUT = 0x69
        const val TYPE_AGENT_CONSOLE_TASK_SYNC = 0x6a
        const val TYPE_AGENT_CONSOLE_TASK_UPD = 0x6b

        // Pivots
        const val TYPE_PIVOT_CREATE = 0x71
        const val TYPE_PIVOT_DELETE = 0x72

        // Credentials
        const val TYPE_CREDS_CREATE = 0x81
        const val TYPE_CREDS_EDIT = 0x82
        const val TYPE_CREDS_DELETE = 0x83
        const val TYPE_CREDS_SET_TAG = 0x84

        // Targets
        const val TYPE_TARGETS_CREATE = 0x87
        const val TYPE_TARGETS_EDIT = 0x88
        const val TYPE_TARGETS_DELETE = 0x89
        const val TYPE_TARGETS_SET_TAG = 0x8a

        // AxScript
        const val TYPE_AXSCRIPT_COMMANDS = 0x91
    }

    fun connect() {
        if (isReconnecting) return
        isReconnecting = true

        val otpData = mapOf(
            "client_type" to 1,
            "console_team_mode" to true,
            "subscriptions" to listOf(
                "tasks_history",
                "tasks_manager",
                "console_history",
                "chat_history",
                "chat_realtime",
                "downloads_history",
                "downloads_realtime",
                "screenshot_history",
                "screenshot_realtime",
                "credentials_history",
                "credentials_realtime",
                "targets_history",
                "targets_realtime",
                "notifications",
                "tunnels"
            )
        )

        Log.d(TAG, "Generating OTP for WebSocket...")
        val otpResult = api.generateOtp("connect", otpData)
        otpResult.onSuccess { otp ->
            Log.d(TAG, "OTP generated, connecting WebSocket...")
            webSocket = api.connectWebSocket(otp, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (disposed) return
                    Log.d(TAG, "WebSocket opened")
                    isConnected = true
                    isReconnecting = false
                    onStatus("connected", "")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (disposed) return
                    handleMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (disposed) return
                    handleMessage(bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code $reason")
                    isConnected = false
                    isReconnecting = false
                    webSocket.close(1000, null)
                    if (!disposed) onStatus("disconnected", reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}", t)
                    isConnected = false
                    isReconnecting = false
                    if (!disposed) onStatus("error", t.message ?: "WebSocket error")
                }
            })
        }.onFailure {
            Log.e(TAG, "OTP generation failed: ${it.message}")
            isReconnecting = false
            onStatus("error", "OTP generation failed: ${it.message}")
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val json = JsonParser.parseString(raw).asJsonObject
            val type = json.get("type")?.asInt
            if (type == null) {
                Log.w(TAG, "WS message without 'type' field: ${raw.take(100)}")
                return
            }

            // Handle batch packets by dispatching each sub-packet
            when (type) {
                TYPE_SYNC_BATCH -> {
                    val packets = json.getAsJsonArray("packets") ?: return
                    Log.d(TAG, "Sync batch: ${packets.size()} packets")
                    for (packet in packets) {
                        if (packet.isJsonObject) {
                            val subJson = packet.asJsonObject
                            val subType = subJson.get("type")?.asInt ?: continue
                            onPacket(subType, subJson)
                        }
                    }
                    return
                }
                TYPE_SYNC_CATEGORY_BATCH -> {
                    val category = json.get("category")?.asString ?: ""
                    val packets = json.getAsJsonArray("packets") ?: return
                    Log.d(TAG, "Category batch '$category': ${packets.size()} packets")
                    // Let the ViewModel handle entire batch if it wants to (returns true if handled)
                    if (onCategoryBatch?.invoke(category, packets) == true) return
                    for (packet in packets) {
                        if (packet.isJsonObject) {
                            val subJson = packet.asJsonObject
                            val subType = subJson.get("type")?.asInt ?: continue
                            onPacket(subType, subJson)
                        }
                    }
                    return
                }
                TYPE_SYNC_START -> {
                    Log.d(TAG, "Sync start, count=${json.get("count")?.asInt}")
                    return
                }
                TYPE_SYNC_FINISH -> {
                    Log.d(TAG, "Sync finish")
                    onPacket(type, json)
                    return
                }
            }

            // Direct packet
            Log.d(TAG, "WS packet type=0x${type.toString(16)}")
            onPacket(type, json)

        } catch (e: Exception) {
            Log.e(TAG, "WS parse error: ${e.message} raw=${raw.take(100)}")
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        disposed = true
        val ws = webSocket
        webSocket = null
        isConnected = false
        isReconnecting = false
        // Send clean close frame so server releases the session immediately
        ws?.close(1000, "client disconnect")
    }
}

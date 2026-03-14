package com.adaptix.client.models

import com.google.gson.annotations.SerializedName

// --- Auth ---
data class LoginRequest(
    val username: String,
    val password: String,
    val version: String = "v1.2"
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?
)

data class ApiResponse(
    val ok: Boolean = false,
    val message: String = ""
)

// --- Server Profile ---
data class ServerProfile(
    val name: String,
    val host: String,
    val port: Int,
    val endpoint: String,
    val ssl: Boolean,
    val username: String,
    val password: String
)

// --- Agent ---
// REST /agent/list returns adaptix.AgentData with a_ prefix fields
data class Agent(
    @SerializedName("a_id") val id: String = "",
    @SerializedName("a_name") val name: String = "",
    @SerializedName("a_listener") val listener: String = "",
    @SerializedName("a_async") val async: Boolean = false,
    @SerializedName("a_external_ip") val externalIp: String = "",
    @SerializedName("a_internal_ip") val internalIp: String = "",
    @SerializedName("a_gmt_offset") val gmtOffset: Int = 0,
    @SerializedName("a_workingtime") val workingTime: Int = 0,
    @SerializedName("a_killdate") val killDate: Int = 0,
    @SerializedName("a_sleep") val sleep: Long = 0,
    @SerializedName("a_jitter") val jitter: Long = 0,
    @SerializedName("a_acp") val acp: Int = 0,
    @SerializedName("a_oemcp") val oemcp: Int = 0,
    @SerializedName("a_pid") val pid: String = "",
    @SerializedName("a_tid") val tid: String = "",
    @SerializedName("a_arch") val arch: String = "",
    @SerializedName("a_elevated") val elevated: Boolean = false,
    @SerializedName("a_process") val process: String = "",
    @SerializedName("a_os") val os: Int = 0,
    @SerializedName("a_os_desc") val osDesc: String = "",
    @SerializedName("a_domain") val domain: String = "",
    @SerializedName("a_computer") val computer: String = "",
    @SerializedName("a_username") val username: String = "",
    @SerializedName("a_impersonated") val impersonated: String = "",
    @SerializedName("a_create_time") val createTime: Long = 0,
    @SerializedName("a_last_tick") val lastTick: Long = 0,
    @SerializedName("a_tags") val tags: String = "",
    @SerializedName("a_mark") val mark: String = "",
    @SerializedName("a_color") val color: String = "",
    @SerializedName("a_target") val target: String = ""
) {
    val osName: String get() = when (os) {
        1 -> "Windows"
        2 -> "Linux"
        3 -> "macOS"
        else -> "Unknown"
    }

    val isAlive: Boolean get() {
        if (lastTick == 0L) return false
        val sleepSec = if (sleep > 0) sleep else 10L
        val thresholdSec = sleepSec * 3 + 30
        return (System.currentTimeMillis() / 1000) - lastTick < thresholdSec
    }

    val displayName: String get() = "$username@$computer"

    val sleepDisplay: String get() {
        val sec = sleep
        return if (sec >= 60) "${sec / 60}m${sec % 60}s" else "${sec}s"
    }
}

// --- Task (REST /agent/task/list and WS both use a_ prefix) ---
data class AgentTask(
    @SerializedName("a_task_type") val type: Int = 0,
    @SerializedName("a_task_id") val taskId: String = "",
    @SerializedName("a_id") val agentId: String = "",
    @SerializedName("a_client") val client: String = "",
    @SerializedName("a_user") val user: String = "",
    @SerializedName("a_computer") val computer: String = "",
    @SerializedName("a_start_time") val startDate: Long = 0,
    @SerializedName("a_finish_time") val finishDate: Long = 0,
    @SerializedName("a_cmdline") val commandLine: String = "",
    @SerializedName("a_msg_type") val messageType: Int = 0,
    @SerializedName("a_message") val message: String = "",
    @SerializedName("a_text") val clearText: String = "",
    @SerializedName("a_completed") val completed: Boolean = false,
    @SerializedName("a_sync") val sync: Boolean = false
)

// --- Listener ---
// REST /listener/list returns adaptix.ListenerData
// Note: l_bind_port is String, l_status is String, create_time is a_create_time
data class Listener(
    @SerializedName("l_name") val name: String = "",
    @SerializedName("l_reg_name") val regName: String = "",
    @SerializedName("l_protocol") val protocol: String = "",
    @SerializedName("l_type") val type: String = "",
    @SerializedName("l_bind_host") val bindHost: String = "",
    @SerializedName("l_bind_port") val bindPort: String = "",
    @SerializedName("l_agent_addr") val agentAddr: String = "",
    @SerializedName("a_create_time") val createTime: Long = 0,
    @SerializedName("l_status") val status: String = "",
    @SerializedName("l_data") val data: String = "",
    @SerializedName("l_watermark") val watermark: String = ""
) {
    val isRunning: Boolean get() = status.equals("Listen", ignoreCase = true)
    val isPaused: Boolean get() = status.equals("Paused", ignoreCase = true)
    val statusDisplay: String get() = when {
        status.equals("Listen", ignoreCase = true) -> "Running"
        status.isBlank() -> "Unknown"
        else -> status
    }
}

// --- Download ---
// REST /download/list: d_file_id, d_agent_id, d_agent_name, d_user, d_computer, d_file, d_size, d_date, d_recv_size, d_state
data class Download(
    @SerializedName("d_file_id") val fileId: String = "",
    @SerializedName("d_agent_id") val agentId: String = "",
    @SerializedName("d_agent_name") val agentName: String = "",
    @SerializedName("d_user") val user: String = "",
    @SerializedName("d_computer") val computer: String = "",
    @SerializedName("d_file") val file: String = "",
    @SerializedName("d_size") val totalSize: Long = 0,
    @SerializedName("d_recv_size") val recvSize: Long = 0,
    @SerializedName("d_date") val date: Long = 0,
    @SerializedName("d_state") val state: Int = 0
) {
    val stateName: String get() = when (state) {
        1 -> "Running"
        2 -> "Stopped"
        3 -> "Finished"
        else -> "Unknown"
    }

    val progress: Float get() = if (totalSize > 0) recvSize.toFloat() / totalSize else 0f

    val fileName: String get() = file.substringAfterLast("\\").substringAfterLast("/")
}

// --- Screenshot ---
// REST /screen/list: s_ prefix fields
data class Screenshot(
    @SerializedName("s_screen_id") val screenId: String = "",
    @SerializedName("s_user") val user: String? = "",
    @SerializedName("s_computer") val computer: String? = "",
    @SerializedName("s_note") val note: String? = "",
    @SerializedName("s_date") val date: Long = 0,
    @SerializedName("s_content") val content: String? = ""
)

// --- Credential ---
// REST /creds/list: c_ prefix fields
data class Credential(
    @SerializedName("c_creds_id") val credId: String = "",
    @SerializedName("c_username") val username: String = "",
    @SerializedName("c_password") val password: String = "",
    @SerializedName("c_realm") val realm: String = "",
    @SerializedName("c_type") val type: String = "",
    @SerializedName("c_tag") val tag: String = "",
    @SerializedName("c_date") val date: Long = 0,
    @SerializedName("c_storage") val storage: String = "",
    @SerializedName("c_agent_id") val agentId: String = "",
    @SerializedName("c_host") val host: String = ""
)

// --- Tunnel ---
// p_port and p_fport are Strings on the server
data class Tunnel(
    @SerializedName("p_tunnel_id") val tunnelId: String = "",
    @SerializedName("p_agent_id") val agentId: String = "",
    @SerializedName("p_computer") val computer: String = "",
    @SerializedName("p_username") val username: String = "",
    @SerializedName("p_process") val process: String = "",
    @SerializedName("p_type") val type: String = "",
    @SerializedName("p_info") val info: String = "",
    @SerializedName("p_interface") val iface: String = "",
    @SerializedName("p_port") val port: String = "",
    @SerializedName("p_client") val client: String = "",
    @SerializedName("p_fhost") val fhost: String = "",
    @SerializedName("p_fport") val fport: String = ""
)

// --- Agent Type (from WS TYPE_AGENT_REG) ---
data class AgentTypeInfo(
    val name: String,
    val listeners: List<String> = emptyList(),
    val multiListeners: Boolean = false
)

// --- Build Profile (saved locally) ---
data class BuildProfile(
    val name: String,
    val agentName: String,
    val listenerName: String,
    val config: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

// --- Listener Profile (saved locally) ---
data class ListenerProfile(
    val name: String,
    val listenerKind: String,  // "BEACON_HTTP", "BEACON_SMB", "KHARON_HTTP"
    val fields: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

// --- Chat ---
// WS sends c_username, c_message, c_date
data class ChatMessage(
    @SerializedName("c_username") val username: String = "",
    @SerializedName("c_message") val message: String = "",
    @SerializedName("c_date") val date: Long = 0
)

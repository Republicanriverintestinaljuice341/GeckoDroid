package com.adaptix.client.api

import android.util.Log
import com.adaptix.client.models.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class AdaptixApi(
    private val baseUrl: String,
    private val trustAllCerts: Boolean = true
) {
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    var accessToken: String? = null
    var refreshToken: String? = null

    companion object {
        private const val TAG = "AdaptixApi"
    }

    private fun OkHttpClient.Builder.applyTrustAll(): OkHttpClient.Builder {
        if (trustAllCerts) {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            sslSocketFactory(sslContext.socketFactory, trustManager)
            hostnameVerifier { _, _ -> true }
        }
        return this
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .applyTrustAll()
            .build()
    }

    private val longClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .applyTrustAll()
            .build()
    }

    private val wsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .applyTrustAll()
            .build()
    }

    private fun buildUrl(path: String): String = "$baseUrl$path"

    private fun authHeader(): String = "Bearer ${accessToken ?: ""}"

    private fun post(path: String, body: Any?, authenticated: Boolean = true): Response {
        val json = if (body != null) gson.toJson(body) else "{}"
        val requestBody = json.toRequestBody(jsonType)
        Log.d(TAG, "POST $path body=$json")

        val builder = Request.Builder()
            .url(buildUrl(path))
            .post(requestBody)

        if (authenticated && accessToken != null) {
            builder.addHeader("Authorization", authHeader())
        }

        return client.newCall(builder.build()).execute()
    }

    private fun get(path: String, authenticated: Boolean = true): Response {
        Log.d(TAG, "GET $path")
        val builder = Request.Builder()
            .url(buildUrl(path))
            .get()

        if (authenticated && accessToken != null) {
            builder.addHeader("Authorization", authHeader())
        }

        return client.newCall(builder.build()).execute()
    }

    private fun postLong(path: String, body: Any?, authenticated: Boolean = true): Response {
        val json = if (body != null) gson.toJson(body) else "{}"
        val requestBody = json.toRequestBody(jsonType)
        Log.d(TAG, "POST(long) $path body=$json")

        val builder = Request.Builder()
            .url(buildUrl(path))
            .post(requestBody)

        if (authenticated && accessToken != null) {
            builder.addHeader("Authorization", authHeader())
        }

        return longClient.newCall(builder.build()).execute()
    }

    private fun authPostLong(path: String, reqBody: Any?): Pair<Int, String> {
        var resp = postLong(path, reqBody)
        var body = resp.body?.string() ?: ""
        Log.d(TAG, "POST(long) $path -> ${resp.code} body=${body.take(200)}")

        if (resp.code == 401 || resp.code == 403) {
            Log.d(TAG, "Token expired, refreshing...")
            val refreshResult = refreshAccessToken()
            if (refreshResult.isSuccess) {
                resp = postLong(path, reqBody)
                body = resp.body?.string() ?: ""
                Log.d(TAG, "POST(long) $path (retry) -> ${resp.code} body=${body.take(200)}")
            }
        }
        return Pair(resp.code, body)
    }

    /**
     * Parse a list endpoint response. Server returns raw JSON array on success,
     * but may return {"ok":false,"message":"..."} on error.
     */
    private fun <T> parseListResponse(body: String, listType: Type): Result<List<T>> {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            val list: List<T>? = gson.fromJson(body, listType)
            return Result.success(list ?: emptyList())
        }
        // Not an array - try to parse as error response
        if (trimmed.startsWith("{")) {
            val apiResp = gson.fromJson(body, ApiResponse::class.java)
            if (!apiResp.ok) {
                return Result.failure(Exception(apiResp.message))
            }
        }
        return Result.success(emptyList())
    }

    /**
     * Execute an authenticated GET, auto-refreshing token on 401.
     */
    private fun authGet(path: String): Pair<Int, String> {
        var resp = get(path)
        var body = resp.body?.string() ?: ""
        Log.d(TAG, "GET $path -> ${resp.code} body=${body.take(200)}")

        if (resp.code == 401 || resp.code == 403) {
            Log.d(TAG, "Token expired, refreshing...")
            val refreshResult = refreshAccessToken()
            if (refreshResult.isSuccess) {
                resp = get(path)
                body = resp.body?.string() ?: ""
                Log.d(TAG, "GET $path (retry) -> ${resp.code} body=${body.take(200)}")
            }
        }
        return Pair(resp.code, body)
    }

    /**
     * Execute an authenticated POST, auto-refreshing token on 401.
     */
    private fun authPost(path: String, reqBody: Any?): Pair<Int, String> {
        var resp = post(path, reqBody)
        var body = resp.body?.string() ?: ""
        Log.d(TAG, "POST $path -> ${resp.code} body=${body.take(200)}")

        if (resp.code == 401 || resp.code == 403) {
            Log.d(TAG, "Token expired, refreshing...")
            val refreshResult = refreshAccessToken()
            if (refreshResult.isSuccess) {
                resp = post(path, reqBody)
                body = resp.body?.string() ?: ""
                Log.d(TAG, "POST $path (retry) -> ${resp.code} body=${body.take(200)}")
            }
        }
        return Pair(resp.code, body)
    }

    // --- Auth ---
    fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            val resp = post("/login", LoginRequest(username, password), authenticated = false)
            val body = resp.body?.string() ?: return Result.failure(Exception("Empty response"))
            Log.d(TAG, "login -> ${resp.code} body=${body.take(200)}")

            if (resp.code == 404) {
                return Result.failure(Exception("Authentication failed"))
            }

            val loginResp = gson.fromJson(body, LoginResponse::class.java)
            if (loginResp.accessToken != null) {
                accessToken = loginResp.accessToken
                refreshToken = loginResp.refreshToken
                Log.d(TAG, "Login success, token=${accessToken?.take(20)}...")
                Result.success(loginResp)
            } else {
                Result.failure(Exception("Invalid credentials"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "login error", e)
            Result.failure(e)
        }
    }

    fun refreshAccessToken(): Result<String> {
        return try {
            val builder = Request.Builder()
                .url(buildUrl("/refresh"))
                .post("{}".toRequestBody(jsonType))
                .addHeader("Authorization", "Bearer ${refreshToken ?: ""}")

            val resp = client.newCall(builder.build()).execute()
            val body = resp.body?.string() ?: return Result.failure(Exception("Empty response"))
            Log.d(TAG, "refresh -> ${resp.code} body=${body.take(200)}")
            val json = JsonParser.parseString(body).asJsonObject
            val newToken = json.get("access_token")?.asString
            if (newToken != null) {
                accessToken = newToken
                Log.d(TAG, "Token refreshed")
                Result.success(newToken)
            } else {
                Result.failure(Exception("Token refresh failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "refresh error", e)
            Result.failure(e)
        }
    }

    fun generateOtp(type: String, data: Map<String, Any>? = null): Result<String> {
        return try {
            val body = mutableMapOf<String, Any>("type" to type)
            if (data != null) body["data"] = data
            val (_, respBody) = authPost("/otp/generate", body)
            if (respBody.isEmpty()) return Result.failure(Exception("Empty response"))
            val apiResp = gson.fromJson(respBody, ApiResponse::class.java)
            if (apiResp.ok) Result.success(apiResp.message) else Result.failure(Exception(apiResp.message))
        } catch (e: Exception) {
            Log.e(TAG, "generateOtp error", e)
            Result.failure(e)
        }
    }

    // --- Agents ---
    fun getAgents(): Result<List<Agent>> {
        return try {
            val (_, body) = authGet("/agent/list")
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            parseListResponse(body, object : TypeToken<List<Agent>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "getAgents error", e)
            Result.failure(e)
        }
    }

    fun executeCommand(agentId: String, cmdline: String): Result<ApiResponse> {
        return try {
            val reqBody = mapOf(
                "id" to agentId,
                "cmdline" to cmdline
            )
            val (_, respBody) = authPost("/agent/command/raw", reqBody)
            if (respBody.isEmpty()) return Result.failure(Exception("Empty response"))
            val apiResp = gson.fromJson(respBody, ApiResponse::class.java)
            Log.d(TAG, "executeCommand -> ok=${apiResp.ok} msg=${apiResp.message}")
            if (apiResp.ok) Result.success(apiResp) else Result.failure(Exception(apiResp.message))
        } catch (e: Exception) {
            Log.e(TAG, "executeCommand error", e)
            Result.failure(e)
        }
    }

    fun removeAgents(agentIds: List<String>): Result<ApiResponse> {
        return try {
            val (_, body) = authPost("/agent/remove", mapOf("agent_id_array" to agentIds))
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(body, ApiResponse::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "removeAgents error", e)
            Result.failure(e)
        }
    }

    fun setAgentTag(agentIds: List<String>, tag: String): Result<ApiResponse> {
        return try {
            val (_, body) = authPost("/agent/set/tag", mapOf("agent_id_array" to agentIds, "tag" to tag))
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(body, ApiResponse::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "setAgentTag error", e)
            Result.failure(e)
        }
    }

    // --- Tasks ---
    fun getAgentTasks(agentId: String, limit: Int = 200, offset: Int = 0): Result<List<AgentTask>> {
        return try {
            val (_, body) = authGet("/agent/task/list?agent_id=$agentId&limit=$limit&offset=$offset")
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            parseListResponse(body, object : TypeToken<List<AgentTask>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "getAgentTasks error", e)
            Result.failure(e)
        }
    }

    fun cancelTasks(agentId: String, taskIds: List<String>): Result<ApiResponse> {
        return try {
            val (_, body) = authPost("/agent/task/cancel", mapOf("agent_id" to agentId, "tasks_array" to taskIds))
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(body, ApiResponse::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "cancelTasks error", e)
            Result.failure(e)
        }
    }

    // --- Listeners ---
    fun getListeners(): Result<List<Listener>> {
        return try {
            val (_, body) = authGet("/listener/list")
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            parseListResponse(body, object : TypeToken<List<Listener>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "getListeners error", e)
            Result.failure(e)
        }
    }

    fun createListener(name: String, type: String, config: String): Result<ApiResponse> {
        return try {
            val (_, body) = authPost("/listener/create", mapOf("name" to name, "type" to type, "config" to config))
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(body, ApiResponse::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "createListener error", e)
            Result.failure(e)
        }
    }

    fun stopListener(name: String, type: String): Result<ApiResponse> {
        return try {
            val (_, body) = authPost("/listener/stop", mapOf("name" to name, "type" to type))
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(body, ApiResponse::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "stopListener error", e)
            Result.failure(e)
        }
    }

    // --- Downloads ---
    fun getDownloads(): Result<List<Download>> {
        return try {
            val (_, body) = authGet("/download/list")
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            parseListResponse(body, object : TypeToken<List<Download>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "getDownloads error", e)
            Result.failure(e)
        }
    }

    // --- Screenshots ---
    fun getScreenshots(): Result<List<Screenshot>> {
        return try {
            val (_, body) = authGet("/screen/list")
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            parseListResponse(body, object : TypeToken<List<Screenshot>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "getScreenshots error", e)
            Result.failure(e)
        }
    }

    fun getScreenshotImage(screenId: String): Result<ByteArray> {
        return try {
            var resp = get("/screen/image?screen_id=$screenId")
            if (resp.code == 401 || resp.code == 403) {
                refreshAccessToken()
                resp = get("/screen/image?screen_id=$screenId")
            }
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (bytes.isEmpty()) Result.failure(Exception("Empty image"))
            else Result.success(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "getScreenshotImage error", e)
            Result.failure(e)
        }
    }

    fun removeScreenshots(screenIds: List<String>): Result<Unit> {
        return try {
            val body = mapOf("screen_id_array" to screenIds)
            val (_, resp) = authPost("/screen/remove", body)
            val json = JsonParser.parseString(resp).asJsonObject
            if (json.get("ok")?.asBoolean == true) Result.success(Unit)
            else Result.failure(Exception(json.get("message")?.asString ?: "Remove failed"))
        } catch (e: Exception) {
            Log.e(TAG, "removeScreenshots error", e)
            Result.failure(e)
        }
    }

    // --- Credentials ---
    fun getCredentials(): Result<List<Credential>> {
        return try {
            val (_, body) = authGet("/creds/list")
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            parseListResponse(body, object : TypeToken<List<Credential>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "getCredentials error", e)
            Result.failure(e)
        }
    }

    // --- Tunnels ---
    fun getTunnels(): Result<List<Tunnel>> {
        return try {
            val (_, body) = authGet("/tunnel/list")
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            parseListResponse(body, object : TypeToken<List<Tunnel>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "getTunnels error", e)
            Result.failure(e)
        }
    }

    fun stopTunnel(tunnelId: String): Result<ApiResponse> {
        return try {
            val (_, body) = authPost("/tunnel/stop", mapOf("p_tunnel_id" to tunnelId))
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(body, ApiResponse::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "stopTunnel error", e)
            Result.failure(e)
        }
    }

    // --- Sync & Subscribe ---
    fun triggerSync(): Result<ApiResponse> {
        return try {
            val (_, body) = authPost("/sync", emptyMap<String, Any>())
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(body, ApiResponse::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "triggerSync error", e)
            Result.failure(e)
        }
    }

    fun subscribe(categories: List<String>, consoleTeamMode: Boolean = false): Result<ApiResponse> {
        return try {
            val reqBody = mutableMapOf<String, Any>(
                "categories" to categories,
                "console_team_mode" to consoleTeamMode
            )
            val (_, body) = authPost("/subscribe", reqBody)
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(body, ApiResponse::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "subscribe error", e)
            Result.failure(e)
        }
    }

    // --- Chat ---
    fun sendChat(message: String): Result<ApiResponse> {
        return try {
            val (_, body) = authPost("/chat/send", mapOf("message" to message))
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            Result.success(gson.fromJson(body, ApiResponse::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "sendChat error", e)
            Result.failure(e)
        }
    }

    // --- Agent Generate (build channel with logs) ---
    fun generateBuildOtp(agentName: String, listenerNames: List<String>): Result<String> {
        return try {
            val channelData = gson.toJson(mapOf(
                "agent_name" to agentName,
                "listeners_name" to listenerNames
            ))
            val reqBody = mapOf(
                "type" to "channel_agent_build",
                "data" to gson.fromJson(channelData, com.google.gson.JsonElement::class.java)
            )
            val (_, body) = authPost("/otp/generate", reqBody)
            if (body.isEmpty()) return Result.failure(Exception("Empty response"))
            val apiResp = gson.fromJson(body, ApiResponse::class.java)
            if (!apiResp.ok) return Result.failure(Exception(apiResp.message))
            Result.success(apiResp.message)
        } catch (e: Exception) {
            Log.e(TAG, "generateBuildOtp error", e)
            Result.failure(e)
        }
    }

    fun connectBuildChannel(otp: String, listener: WebSocketListener): WebSocket {
        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/channel?otp=$otp"

        Log.d(TAG, "Build channel connecting to $wsUrl")
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        return longClient.newWebSocket(request, listener)
    }

    // --- WebSocket ---
    fun connectWebSocket(otp: String, listener: WebSocketListener): WebSocket {
        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/connect?otp=$otp"

        Log.d(TAG, "WebSocket connecting to $wsUrl")
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        return wsClient.newWebSocket(request, listener)
    }
}

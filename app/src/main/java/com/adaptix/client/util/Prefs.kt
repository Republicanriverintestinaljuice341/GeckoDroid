package com.adaptix.client.util

import android.content.Context
import android.content.SharedPreferences
import com.adaptix.client.models.BuildProfile
import com.adaptix.client.models.ListenerProfile
import com.adaptix.client.models.ServerProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("adaptix_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var lastProfile: ServerProfile?
        get() {
            val json = prefs.getString("last_profile", null) ?: return null
            return gson.fromJson(json, ServerProfile::class.java)
        }
        set(value) {
            prefs.edit().putString("last_profile", if (value != null) gson.toJson(value) else null).apply()
        }

    var savedProfiles: List<ServerProfile>
        get() {
            val json = prefs.getString("saved_profiles", "[]")
            val type = object : TypeToken<List<ServerProfile>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) {
            prefs.edit().putString("saved_profiles", gson.toJson(value)).apply()
        }

    fun addProfile(profile: ServerProfile) {
        val list = savedProfiles.toMutableList()
        list.removeAll { it.name == profile.name }
        list.add(0, profile)
        savedProfiles = list
    }

    fun removeProfile(name: String) {
        savedProfiles = savedProfiles.filter { it.name != name }
    }

    var themeName: String
        get() = prefs.getString("theme", "ADAPTIX_DARK") ?: "ADAPTIX_DARK"
        set(value) = prefs.edit().putString("theme", value).apply()

    var autoLogin: Boolean
        get() = prefs.getBoolean("auto_login", true)
        set(value) = prefs.edit().putBoolean("auto_login", value).apply()

    var favoriteCommands: List<String>
        get() {
            val json = prefs.getString("favorite_commands", "[]")
            val type = object : TypeToken<List<String>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) {
            prefs.edit().putString("favorite_commands", gson.toJson(value)).apply()
        }

    fun addFavoriteCommand(cmd: String) {
        val list = favoriteCommands.toMutableList()
        if (cmd !in list) {
            list.add(0, cmd)
            if (list.size > 20) list.subList(20, list.size).clear()
            favoriteCommands = list
        }
    }

    fun removeFavoriteCommand(cmd: String) {
        favoriteCommands = favoriteCommands.filter { it != cmd }
    }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric_enabled", false)
        set(value) = prefs.edit().putBoolean("biometric_enabled", value).apply()

    var agentSortBy: String
        get() = prefs.getString("agent_sort", "last_seen") ?: "last_seen"
        set(value) = prefs.edit().putString("agent_sort", value).apply()

    var buildProfiles: List<BuildProfile>
        get() {
            val json = prefs.getString("build_profiles", "[]")
            val type = object : TypeToken<List<BuildProfile>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) {
            prefs.edit().putString("build_profiles", gson.toJson(value)).apply()
        }

    fun addBuildProfile(profile: BuildProfile) {
        val list = buildProfiles.toMutableList()
        list.removeAll { it.name == profile.name }
        list.add(0, profile)
        if (list.size > 20) list.subList(20, list.size).clear()
        buildProfiles = list
    }

    fun removeBuildProfile(name: String) {
        buildProfiles = buildProfiles.filter { it.name != name }
    }

    var listenerProfiles: List<ListenerProfile>
        get() {
            val json = prefs.getString("listener_profiles", "[]")
            val type = object : TypeToken<List<ListenerProfile>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) {
            prefs.edit().putString("listener_profiles", gson.toJson(value)).apply()
        }

    fun addListenerProfile(profile: ListenerProfile) {
        val list = listenerProfiles.toMutableList()
        list.removeAll { it.name == profile.name }
        list.add(0, profile)
        if (list.size > 20) list.subList(20, list.size).clear()
        listenerProfiles = list
    }

    fun removeListenerProfile(name: String) {
        listenerProfiles = listenerProfiles.filter { it.name != name }
    }
}

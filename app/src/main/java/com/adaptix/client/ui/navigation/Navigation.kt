package com.adaptix.client.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val showInNav: Boolean = true
) {
    Dashboard("dashboard", "Dashboard", Icons.Default.Dashboard),
    Agents("agents", "Agents", Icons.Default.Devices),
    Console("console", "Console", Icons.Default.Terminal, showInNav = false),
    Listeners("listeners", "Listeners", Icons.Default.Sensors),
    Downloads("downloads", "Downloads", Icons.Default.CloudDownload),
    Screenshots("screenshots", "Screens", Icons.Default.Screenshot),
    Credentials("credentials", "Creds", Icons.Default.Key),
    Tunnels("tunnels", "Tunnels", Icons.Default.SwapHoriz),
    Chat("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    Generate("generate", "Generate", Icons.Default.Build, showInNav = false)
}

val bottomNavScreens = Screen.entries.filter { it.showInNav }

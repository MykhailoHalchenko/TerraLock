package com.example.terralock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.terralock.auth.AuthRepository
import com.example.terralock.auth.AuthScreen
import com.example.terralock.chat.ChatRoomScreen
import com.example.terralock.chat.ChatsScreen
import com.example.terralock.map.MapViewParams
import com.example.terralock.nav.SensorFusionEngine
import com.example.terralock.navigation.AppNavReducer
import com.example.terralock.navigation.Route
import com.example.terralock.profile.ProfileScreen
import com.example.terralock.regions.RegionsScreen
import com.example.terralock.ui.MapHudScreen
import com.example.terralock.ui.theme.TerraLockTheme

enum class MainTab {
    MAP, REGIONS, CHATS, PROFILE
}

@Composable
fun App(
    sensorEngine: SensorFusionEngine,
    mapView: @Composable (MapViewParams) -> Unit,
    onGoogleSignInClick: (suspend () -> Result<String>)? = null
) {
    val navReducer = remember { AppNavReducer() }
    val authRepo = remember { AuthRepository() }
    val navState by navReducer.state.collectAsState()

    var selectedTab by remember { mutableStateOf(MainTab.MAP) }
    var activeChatRoomId by remember { mutableStateOf<String?>(null) }

    TerraLockTheme {
        when (navState.currentRoute) {
            is Route.Auth -> AuthScreen(navReducer, authRepo, onGoogleSignInClick)
            is Route.VehicleProfileConfig -> ProfileScreen(navReducer, authRepo)
            is Route.MapHud -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isTabletLandscape = maxWidth >= 600.dp

                    Scaffold(
                        bottomBar = {
                            if (!isTabletLandscape && activeChatRoomId == null) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = selectedTab == MainTab.MAP,
                                        onClick = { selectedTab = MainTab.MAP },
                                        icon = { Icon(Icons.Default.LocationOn, contentDescription = "Map") },
                                        label = { Text("Map") }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == MainTab.REGIONS,
                                        onClick = { selectedTab = MainTab.REGIONS },
                                        icon = { Icon(Icons.Default.Info, contentDescription = "Regions") },
                                        label = { Text("Regions") }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == MainTab.CHATS,
                                        onClick = { selectedTab = MainTab.CHATS },
                                        icon = { Icon(Icons.Default.Email, contentDescription = "Chats") },
                                        label = { Text("Chats") }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == MainTab.PROFILE,
                                        onClick = { selectedTab = MainTab.PROFILE },
                                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                        label = { Text("Profile") }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (isTabletLandscape && activeChatRoomId == null) {
                                NavigationRail {
                                    NavigationRailItem(
                                        selected = selectedTab == MainTab.MAP,
                                        onClick = { selectedTab = MainTab.MAP },
                                        icon = { Icon(Icons.Default.LocationOn, contentDescription = "Map") },
                                        label = { Text("Map") }
                                    )
                                    NavigationRailItem(
                                        selected = selectedTab == MainTab.REGIONS,
                                        onClick = { selectedTab = MainTab.REGIONS },
                                        icon = { Icon(Icons.Default.Info, contentDescription = "Regions") },
                                        label = { Text("Regions") }
                                    )
                                    NavigationRailItem(
                                        selected = selectedTab == MainTab.CHATS,
                                        onClick = { selectedTab = MainTab.CHATS },
                                        icon = { Icon(Icons.Default.Email, contentDescription = "Chats") },
                                        label = { Text("Chats") }
                                    )
                                    NavigationRailItem(
                                        selected = selectedTab == MainTab.PROFILE,
                                        onClick = { selectedTab = MainTab.PROFILE },
                                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                        label = { Text("Profile") }
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                activeChatRoomId?.let { chatId ->
                                    ChatRoomScreen(
                                        chatId = chatId,
                                        onBack = { activeChatRoomId = null }
                                    )
                                } ?: run {
                                    when (selectedTab) {
                                        MainTab.MAP -> MapHudScreen(navReducer, sensorEngine, mapView)
                                        MainTab.REGIONS -> RegionsScreen()
                                        MainTab.CHATS -> ChatsScreen(onOpenChatRoom = { activeChatRoomId = it })
                                        MainTab.PROFILE -> ProfileScreen(navReducer, authRepo)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
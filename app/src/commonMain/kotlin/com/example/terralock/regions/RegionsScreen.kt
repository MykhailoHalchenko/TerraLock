package com.example.terralock.regions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun RegionsScreen(
    repository: OfflineRegionRepository = remember { OfflineRegionRepository() }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val localPacks by repository.localPacks.collectAsState()
    var remotePacks by remember { mutableStateOf<List<OfflineRegionPack>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        remotePacks = repository.getRemoteRegionPacks()
    }

    val displayPacks = if (selectedTab == 0) localPacks else remotePacks

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Downloaded (${localPacks.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Explore Packs") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (displayPacks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == 0) "No downloaded offline region packs." else "No community packs available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayPacks) { pack ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = pack.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = pack.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${pack.customPoints.size} custom POIs",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (selectedTab == 1) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    repository.saveRegionPack(pack)
                                                }
                                            }
                                        ) {
                                            Text("Download")
                                        }
                                    } else {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("Downloaded") }
                                        )
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
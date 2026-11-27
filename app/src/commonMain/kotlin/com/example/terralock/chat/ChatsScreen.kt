package com.example.terralock.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    currentUid: String = "guest_operator",
    chatRepo: ChatRepository = remember { ChatRepository() },
    onOpenChatRoom: (String) -> Unit
) {
    val threads by chatRepo.observeThreads(currentUid).collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var newChatTitle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Communications") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Text("+")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (threads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No active chat threads. Tap + to start a conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(threads) { thread ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChatRoom(thread.id) },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = thread.title.take(2).uppercase().ifEmpty { "CH" },
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = thread.title.ifEmpty { "Group Chat" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = thread.lastMessageText.ifEmpty { "No messages yet" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("New Chat") },
                text = {
                    OutlinedTextField(
                        value = newChatTitle,
                        onValueChange = { newChatTitle = it },
                        label = { Text("Chat / Group Title") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newId = "chat_${System.currentTimeMillis()}"
                            val newThread = ChatThread(
                                id = newId,
                                title = newChatTitle.ifEmpty { "Tactical Group" },
                                memberIds = listOf(currentUid)
                            )
                            scope.launch {
                                chatRepo.createChatThread(newThread)
                            }
                            showCreateDialog = false
                            newChatTitle = ""
                            onOpenChatRoom(newId)
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
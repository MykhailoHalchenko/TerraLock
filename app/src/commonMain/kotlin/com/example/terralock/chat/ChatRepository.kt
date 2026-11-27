package com.example.terralock.chat

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ChatRepository {
    private val firestore: FirebaseFirestore? by lazy {
        runCatching { Firebase.firestore }.getOrNull()
    }

    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads.asStateFlow()

    fun observeThreads(currentUid: String): Flow<List<ChatThread>> {
        val ref = firestore?.collection("chats") ?: return flowOf(emptyList())
        return ref.snapshots.map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                runCatching { doc.data(ChatThread.serializer()) }.getOrNull()
            }.filter { it.memberIds.contains(currentUid) }
        }
    }

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> {
        val ref = firestore?.collection("chats")?.document(chatId)?.collection("messages")
            ?: return flowOf(emptyList())
        return ref.snapshots.map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                runCatching { doc.data(ChatMessage.serializer()) }.getOrNull()
            }.sortedBy { it.timestampMs }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun sendMessage(message: ChatMessage) {
        runCatching {
            firestore?.collection("chats")
                ?.document(message.chatId)
                ?.collection("messages")
                ?.document(message.id)
                ?.set(ChatMessage.serializer(), message)

            firestore?.collection("chats")
                ?.document(message.chatId)
                ?.update(
                    Pair("lastMessageText", message.text),
                    Pair("lastMessageAtMs", message.timestampMs)
                )
        }
    }

    suspend fun createChatThread(thread: ChatThread) {
        runCatching {
            firestore?.collection("chats")
                ?.document(thread.id)
                ?.set(ChatThread.serializer(), thread)
        }
    }
}
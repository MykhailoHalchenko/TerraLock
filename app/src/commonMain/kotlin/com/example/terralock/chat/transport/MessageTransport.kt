package com.example.terralock.chat.transport

import com.example.terralock.chat.ChatAttachment
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class TransportProtocol {
    FIREBASE, MATRIX, XMPP, MQTT, SIGNAL_E2EE, MTPROTO
}

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

@Serializable
sealed interface TransportEnvelope {
    val id: String
    val roomId: String
    val senderId: String
    val timestampMs: Long

    @Serializable
    data class TextPayload(
        override val id: String,
        override val roomId: String,
        override val senderId: String,
        override val timestampMs: Long,
        val text: String,
        val attachment: ChatAttachment? = null
    ) : TransportEnvelope

    @Serializable
    data class ReactionPayload(
        override val id: String,
        override val roomId: String,
        override val senderId: String,
        override val timestampMs: Long,
        val targetMessageId: String,
        val emoji: String
    ) : TransportEnvelope

    @Serializable
    data class EditPayload(
        override val id: String,
        override val roomId: String,
        override val senderId: String,
        override val timestampMs: Long,
        val targetMessageId: String,
        val newText: String
    ) : TransportEnvelope

    @Serializable
    data class DeletePayload(
        override val id: String,
        override val roomId: String,
        override val senderId: String,
        override val timestampMs: Long,
        val targetMessageId: String
    ) : TransportEnvelope
}

interface MessageTransportAdapter {
    val protocol: TransportProtocol
    val connectionState: StateFlow<ConnectionStatus>
    suspend fun connect(config: Map<String, String>)
    suspend fun sendEnvelope(roomId: String, envelope: TransportEnvelope)
    fun observeEnvelopes(roomId: String): Flow<TransportEnvelope>
    suspend fun disconnect()
}
package com.example.terralock.chat.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter

class MtProtoTransportAdapter : MessageTransportAdapter {
    override val protocol: TransportProtocol = TransportProtocol.MTPROTO

    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val incomingEnvelopes = MutableSharedFlow<TransportEnvelope>()

    override suspend fun connect(config: Map<String, String>) {
        _connectionState.value = ConnectionStatus.CONNECTED
    }

    override suspend fun sendEnvelope(roomId: String, envelope: TransportEnvelope) {
        if (_connectionState.value != ConnectionStatus.CONNECTED) return
    }

    override fun observeEnvelopes(roomId: String): Flow<TransportEnvelope> {
        return incomingEnvelopes.asSharedFlow().filter { it.roomId == roomId }
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionStatus.DISCONNECTED
    }
}
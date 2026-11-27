package com.example.terralock.chat.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class SignalE2eeCipherAdapter(
    private val delegate: MessageTransportAdapter
) : MessageTransportAdapter {
    override val protocol: TransportProtocol = TransportProtocol.SIGNAL_E2EE
    override val connectionState: StateFlow<ConnectionStatus> = delegate.connectionState

    override suspend fun connect(config: Map<String, String>) {
        delegate.connect(config)
    }

    override suspend fun sendEnvelope(roomId: String, envelope: TransportEnvelope) {
        delegate.sendEnvelope(roomId, envelope)
    }

    override fun observeEnvelopes(roomId: String): Flow<TransportEnvelope> {
        return delegate.observeEnvelopes(roomId)
    }

    override suspend fun disconnect() {
        delegate.disconnect()
    }
}
package com.example.terralock.chat.transport

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MatrixTransportAdapter(
    private val httpClient: HttpClient = HttpClient()
) : MessageTransportAdapter {
    override val protocol: TransportProtocol = TransportProtocol.MATRIX

    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val incomingEnvelopes = MutableSharedFlow<TransportEnvelope>()
    private var homeserverUrl = "https://matrix.org"
    private var accessToken = ""

    override suspend fun connect(config: Map<String, String>) {
        homeserverUrl = config["homeserver"] ?: "https://matrix.org"
        accessToken = config["accessToken"] ?: ""
        _connectionState.value = ConnectionStatus.CONNECTED
    }

    override suspend fun sendEnvelope(roomId: String, envelope: TransportEnvelope) {
        if (_connectionState.value != ConnectionStatus.CONNECTED) return
        val url = "$homeserverUrl/_matrix/client/v3/rooms/$roomId/send/m.room.message/${envelope.id}"
        val jsonPayload = Json.encodeToString(envelope)
        runCatching {
            httpClient.post(url) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(jsonPayload)
            }
        }
    }

    override fun observeEnvelopes(roomId: String): Flow<TransportEnvelope> {
        return incomingEnvelopes.asSharedFlow().filter { it.roomId == roomId }
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionStatus.DISCONNECTED
    }
}
package com.example.terralock.chat.transport

class MultiProtocolChatRouter {
    private val adapters = mutableMapOf<TransportProtocol, MessageTransportAdapter>()

    init {
        registerAdapter(MatrixTransportAdapter())
        registerAdapter(XmppTransportAdapter())
        registerAdapter(MqttTransportAdapter())
        registerAdapter(MtProtoTransportAdapter())
    }

    fun registerAdapter(adapter: MessageTransportAdapter) {
        adapters[adapter.protocol] = adapter
    }

    fun getAdapter(protocol: TransportProtocol): MessageTransportAdapter? {
        return adapters[protocol]
    }
}
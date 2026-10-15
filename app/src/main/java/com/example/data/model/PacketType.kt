package com.example.data.model

enum class PacketType {
    TEXT,
    AUDIO,
    ANNOUNCE,
    ACK,
    PING,
    DELETE_MESSAGE,
    CALL_AWAKEN,
    CALL_OFFER,
    CALL_ANSWER,
    CALL_ICE,
    CALL_END,
    DEAD_DROP_RELAY
}

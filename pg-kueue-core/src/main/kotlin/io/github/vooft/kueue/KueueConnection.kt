package io.github.vooft.kueue

import kotlinx.coroutines.channels.ReceiveChannel

interface KueueConnection {

    val closed: Boolean

    val messages: ReceiveChannel<KueueMessage>

    suspend fun subscribe(channel: KueueTopic)
    suspend fun send(channel: KueueTopic, message: String)

    suspend fun close()
}

data class KueueMessage(val channel: KueueTopic, val message: String)

interface KueueConnectionFactory {
    suspend fun create(): KueueConnection
}

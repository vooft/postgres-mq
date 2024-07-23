package io.github.vooft.kueue

import kotlinx.coroutines.channels.ReceiveChannel

interface KueueConnection {

    val closed: Boolean

    val messages: ReceiveChannel<KueueMessage>

    suspend fun listen(topic: KueueTopic)
    suspend fun notify(topic: KueueTopic, message: String)

    suspend fun close()
}

data class KueueMessage(val topic: KueueTopic, val message: String)

interface KueueConnectionFactory {
    suspend fun create(): KueueConnection
}

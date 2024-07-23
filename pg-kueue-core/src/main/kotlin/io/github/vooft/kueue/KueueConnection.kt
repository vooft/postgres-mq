package io.github.vooft.kueue

import kotlinx.coroutines.channels.ReceiveChannel

interface KueueConnection {

    val messages: ReceiveChannel<KueueMessage>

    suspend fun subscribe(channel: KueueChannel)
    suspend fun send(channel: KueueChannel, message: String)

    suspend fun close()
}

data class KueueMessage(val channel: KueueChannel, val message: String)

interface KueueConnectionFactory {
    suspend fun create(): KueueConnection
}

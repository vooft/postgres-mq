package io.github.vooft.kueue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile

interface KueueConnection {

    val messages: Flow<KueueMessageEnvelope>

    suspend fun subscribe(channel: KueueChannel)
    suspend fun send(channel: KueueChannel, message: String)

    suspend fun close()
}

sealed interface KueueMessageEnvelope {
    data object Closed : KueueMessageEnvelope

    @JvmInline
    value class Message(val message: KueueMessage) : KueueMessageEnvelope
}

data class KueueMessage(val channel: KueueChannel, val message: String)

suspend fun Flow<KueueMessageEnvelope>.collectUntilClosed(action: suspend (KueueMessage) -> Unit) =
    takeWhile { it != KueueMessageEnvelope.Closed }
        .map { it as KueueMessageEnvelope.Message }
        .collect { action(it.message) }

interface KueueConnectionFactory {
    suspend fun create(): KueueConnection
}

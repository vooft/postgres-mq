package io.github.vooft.kueue

import kotlinx.coroutines.channels.ReceiveChannel

interface KueueTransport<KC : KueueConnection<*>> {

    suspend fun notify(kueueConnection: KC, topic: KueueTopic, message: String)
    suspend fun createListener(kueueConnection: KC): Listener

    interface Listener {
        val messages: ReceiveChannel<KueueMessage>
        val isClosed: Boolean
        suspend fun listen(topic: KueueTopic)
        suspend fun unlisten(topic: KueueTopic)
        suspend fun close()
    }
}

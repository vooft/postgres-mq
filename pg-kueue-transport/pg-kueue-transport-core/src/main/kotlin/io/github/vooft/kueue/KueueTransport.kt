package io.github.vooft.kueue

import kotlinx.coroutines.flow.Flow

interface KueueTransport<KC : KueueConnection<*>> {

    suspend fun notify(kueueConnection: KC, topic: KueueTopic, message: String)
    suspend fun createListener(kueueConnection: KC): Listener

    interface Listener {
        val messages: Flow<TopicMessage>
        val isClosed: Boolean
        suspend fun listen(topic: KueueTopic)
        suspend fun unlisten(topic: KueueTopic)
        suspend fun close()
    }
}

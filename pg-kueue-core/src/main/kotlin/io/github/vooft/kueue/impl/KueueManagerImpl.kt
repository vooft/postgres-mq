package io.github.vooft.kueue.impl

import io.github.vooft.kueue.KueueConnection
import io.github.vooft.kueue.KueueConnectionFactory
import io.github.vooft.kueue.KueueConsumer
import io.github.vooft.kueue.KueueManager
import io.github.vooft.kueue.KueueMessage
import io.github.vooft.kueue.KueueProducer
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.common.LoggerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("detekt:UnusedPrivateProperty")
class KueueManagerImpl(
    private val connectionFactory: KueueConnectionFactory,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())
) : KueueManager {

    @Volatile
    private var closed = false

    private val subscribedTopicsMutex = Mutex()
    private val subscribedTopics = mutableSetOf<KueueTopic>()

    private val connectionMutex = Mutex()
    private val connectionState = MutableStateFlow<KueueConnection>(ClosedKueueConnection)

    private val broadcaster = KueueMessageBroadcaster(connectionState.map(coroutineScope) { it.messages })

    override suspend fun createProducer(topic: KueueTopic) = object  : KueueProducer {
        override val channel: KueueTopic = topic
        override suspend fun send(message: String) = getConnection().send(topic, message)
    }

    override suspend fun createConsumer(topic: KueueTopic): KueueConsumer {
        subscribedTopicsMutex.withLock { subscribedTopics.add(topic) }
        return object : KueueConsumer {
            override val channel: KueueTopic = topic
            override suspend fun receive(): String = broadcaster.receive(topic)
        }
    }

    override suspend fun close() {
        closed = true

        subscribedTopicsMutex.withLock { subscribedTopics.clear() }
        connectionMutex.withLock { connectionState.value.close() }
        broadcaster.close()
        coroutineScope.cancel()
    }

    private suspend fun getConnection(): KueueConnection {
        require(!closed) { "KueueManager is closed" }

        return connectionState.openedConnection ?: connectionMutex.withLock {
            connectionState.openedConnection ?: createConnection().also {
                connectionState.value = it
            }
        }
    }

    private suspend fun createConnection(): KueueConnection {
        val connection = connectionFactory.create()
        try {
            subscribedTopicsMutex.withLock { subscribedTopics.toSet() }.forEach { connection.subscribe(it) }
        } catch (e: Exception) {
            connection.close()
            throw e
        }

        return connection
    }

    companion object : LoggerHolder()
}

private fun <T, R> StateFlow<T>.map(coroutineScope: CoroutineScope, block: (T) -> R): StateFlow<R>  {
    val initial = block(value)
    return drop(1).map(block).stateIn(coroutineScope, SharingStarted.Eagerly, initial)
}

private val StateFlow<KueueConnection>.openedConnection: KueueConnection? get() = value.takeUnless { it.closed }

private object ClosedKueueConnection : KueueConnection {
    override val closed: Boolean = true
    override val messages: ReceiveChannel<KueueMessage> = Channel<KueueMessage>().also { runBlocking { close() } }
    override suspend fun subscribe(channel: KueueTopic) = error("Connection is closed")
    override suspend fun send(channel: KueueTopic, message: String) = error("Connection is closed")
    override suspend fun close() = Unit

}

package io.github.vooft.kueue

import io.github.vooft.kueue.common.withNonCancellable
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex

interface KueueConnection<C> {
    val isClosed: Boolean

    suspend fun acquire(): C
    suspend fun release()
}

suspend fun <C, T> KueueConnection<C>.use(block: suspend (C) -> T): T {
    val connection = acquire()
    try {
        return block(connection)
    } finally {
        withNonCancellable { release() }
    }
}

abstract class SimpleKueueConnection<C>(protected val connection: C) : KueueConnection<C> {
    private val mutex = Mutex()

    final override suspend fun acquire(): C {
        mutex.lock()
        return connection
    }

    final override suspend fun release() {
        mutex.unlock()
    }
}

interface KueueConnectionProvider<C, KC : KueueConnection<C>> {
    suspend fun wrap(connection: C): KC
    suspend fun create(): KC
    suspend fun close(connection: KC)
}

interface KueueConnectionPubSub<KC : KueueConnection<*>> {

    suspend fun notify(kueueConnection: KC, topic: KueueTopic, message: String)
    suspend fun listen(kueueConnection: KC): ListenSubscription

    interface ListenSubscription {
        val messages: ReceiveChannel<KueueMessage>
        val isClosed: Boolean
        suspend fun listen(topic: KueueTopic)
        suspend fun unlisten(topic: KueueTopic)
        suspend fun close()
    }
}

interface KueueEventPersister<KC : KueueConnection<*>> {
    suspend fun persist(kueueConnection: KC, topic: KueueTopic, message: String)
}

data class KueueMessage(val topic: KueueTopic, val message: String)

package io.github.vooft.kueue.impl

import io.github.vooft.kueue.Kueue
import io.github.vooft.kueue.Kueue.KueueSubscription
import io.github.vooft.kueue.KueueConnection
import io.github.vooft.kueue.KueueConnectionProvider
import io.github.vooft.kueue.KueueConnectionPubSub
import io.github.vooft.kueue.KueueConnectionPubSub.ListenSubscription
import io.github.vooft.kueue.KueueMessage
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.common.LoggerHolder
import io.github.vooft.kueue.common.loggingExceptionHandler
import io.github.vooft.kueue.common.withNonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("detekt:UnusedPrivateProperty")
class KueueImpl<C, KC : KueueConnection<C>>(
    private val connectionProvider: KueueConnectionProvider<C, KC>,
    private val pubSub: KueueConnectionPubSub<KC>
) : Kueue<C, KC> {

    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + loggingExceptionHandler())

    @Volatile
    private var closed = false

    private val subscribedTopicsMutex = Mutex()
    private val subscribedTopics = mutableMapOf<KueueTopic, KueueSubscriptionImpl>()

    private val defaultConnectionMutex = Mutex()
    private val defaultConnectionState = MutableStateFlow<KC?>(null)

    private val multiplexer = Channel<KueueMessage>()
    private val broadcaster = KueueMessageBroadcaster(multiplexer)

    override suspend fun send(topic: KueueTopic, message: String, kueueConnection: KC?) {
        logger.debug { "Sending via $kueueConnection to $topic: $message" }
        val connection = kueueConnection ?: getDefaultConnection()
        pubSub.notify(connection, topic, message)
        logger.debug { "Successfully sent via $kueueConnection to $topic: $message" }
    }

    override suspend fun subscribe(topic: KueueTopic, block: suspend (String) -> Unit): KueueSubscription {
        logger.debug { "Subscribing to $topic" }

        // subscribe to hot flow
        val flow = broadcaster.subscribe(topic)

        val subscriptionJob = coroutineScope.launch(start = UNDISPATCHED) {
            flow.collect { block(it) }
        }

        // only start listening after we have subscribed
        val connection = getDefaultConnection()
        return subscribedTopicsMutex.withLock(MUTEX_OWNER) {
            val existing = subscribedTopics.remove(topic) ?: run {
                logger.info { "Creating new subscription" }
                val listenSubscription = pubSub.listen(connection, topic)

                // fire and forget is fine, because `messages` will close with the subscription
                coroutineScope.launch { listenSubscription.messages.consumeEach { multiplexer.send(it) } }

                KueueSubscriptionImpl(topic, listOf(), listenSubscription)
            }

            existing.copy(subscriptionJobs = existing.subscriptionJobs + subscriptionJob).also {
                subscribedTopics[topic] = it
            }
        }
    }

    override suspend fun close() {
        closed = true

        subscribedTopicsMutex.withLock(MUTEX_OWNER) { subscribedTopics.clear() }
        defaultConnectionMutex.withLock(MUTEX_OWNER) { defaultConnectionState.value?.let { connectionProvider.close(it) } }
        broadcaster.close()
        coroutineScope.cancel()
    }

    private suspend fun getDefaultConnection(): KC {
        require(!closed) { "KueueManager is closed" }

        return defaultConnectionState.openedConnection ?: defaultConnectionMutex.withLock(MUTEX_OWNER) {
            logger.debug { "Default connection was closed, trying to look for a new one" }
            defaultConnectionState.openedConnection ?: createConnection().also {
                defaultConnectionState.value = it
            }
        }
    }

    private suspend fun createConnection(): KC {
        logger.debug { "Creating new connection" }
        val connection = connectionProvider.create()

        try {
            // resubscribe
            subscribedTopicsMutex.withLock(MUTEX_OWNER) {
                val topics = subscribedTopics.toMap()

                for ((topic, oldSub) in topics) {
                    val listenSubscription = pubSub.listen(connection, topic)
                    coroutineScope.launch { listenSubscription.messages.consumeEach { multiplexer.send(it) } }
                    subscribedTopics[topic] = oldSub.copy(listenSubscription = listenSubscription)
                }
            }
        } catch (e: Exception) {
            withNonCancellable {
                subscribedTopicsMutex.withLock(MUTEX_OWNER) {
                    subscribedTopics.values.forEach { it.listenSubscription.close() }
                    subscribedTopics.clear()
                }

                connectionProvider.close(connection)
            }

            throw e
        }

        return connection
    }

    companion object : LoggerHolder() {
        private const val MUTEX_OWNER = "Kueue"
    }

    private val StateFlow<KC?>.openedConnection: KC? get() = value?.takeUnless { it.isClosed }
}

private data class KueueSubscriptionImpl(
    override val topic: KueueTopic,
    val subscriptionJobs: List<Job>,
    val listenSubscription: ListenSubscription
) : KueueSubscription {
    override suspend fun close() = withNonCancellable {
        listenSubscription.close()
        subscriptionJobs.forEach { it.cancel() }
        subscriptionJobs.joinAll()
    }
}

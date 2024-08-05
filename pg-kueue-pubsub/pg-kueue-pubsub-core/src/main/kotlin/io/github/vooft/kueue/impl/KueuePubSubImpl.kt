package io.github.vooft.kueue.impl

import io.github.vooft.kueue.KueueConnection
import io.github.vooft.kueue.KueueConnectionProvider
import io.github.vooft.kueue.KueuePubSub
import io.github.vooft.kueue.KueuePubSub.KueueSubscription
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.KueueTransport
import io.github.vooft.kueue.TopicMessage
import io.github.vooft.kueue.common.LoggerHolder
import io.github.vooft.kueue.common.loggingExceptionHandler
import io.github.vooft.kueue.common.withNonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("detekt:UnusedPrivateProperty")
class KueuePubSubImpl<C, KC : KueueConnection<C>>(
    private val connectionProvider: KueueConnectionProvider<C, KC>,
    private val transport: KueueTransport<KC>,
) : KueuePubSub<C, KC> {

    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + loggingExceptionHandler())

    @Volatile
    private var closed = false

    private val topicsSubscribersMutex = Mutex()
    private val topicSubscribers = mutableMapOf<KueueTopic, Int>()

    private val listenSubscriptionMutex = Mutex()

    @Volatile
    private var runningListener: KueueTransport.Listener? = null

    private val defaultConnectionMutex = Mutex()
    private val defaultConnectionState = MutableStateFlow<KC?>(null)

    private val multiplexer = Channel<TopicMessage>()
    private val broadcaster = KueueMessageBroadcaster(multiplexer)

    override suspend fun publish(topic: KueueTopic, message: String, kueueConnection: KC?) {
        logger.debug { "Sending via $kueueConnection to $topic: $message" }
        val connection = kueueConnection ?: getDefaultConnection()

        transport.notify(connection, topic, message)

        logger.debug { "Successfully sent via $kueueConnection to $topic: $message" }
    }

    override suspend fun subscribe(topic: KueueTopic, block: suspend (String) -> Unit): KueueSubscription {
        logger.debug { "Subscribing to $topic" }

        // subscribe to hot flow
        val flow = broadcaster.subscribe(topic)

        val subscriptionJob = coroutineScope.launch(start = UNDISPATCHED) {
            flow.collect { block(it) }
        }

        val subscribers = topicsSubscribersMutex.withLock { topicSubscribers.compute(topic) { _, value -> value?.plus(1) ?: 1 } }
        if (subscribers == 1) {
            getListenSubscription().listen(topic)
        }

        getListenSubscription().listen(topic)

        return KueueSubscriptionImpl(topic, subscriptionJob)
    }

    override suspend fun close() {
        closed = true

        topicsSubscribersMutex.withLock(MUTEX_OWNER) { topicSubscribers.clear() }
        defaultConnectionMutex.withLock(MUTEX_OWNER) { defaultConnectionState.value?.let { connectionProvider.close(it) } }
        broadcaster.close()
        coroutineScope.cancel()
    }

    private suspend fun getListenSubscription(): KueueTransport.Listener = runningListener?.takeUnless {
        it.isClosed
    } ?: listenSubscriptionMutex.withLock(MUTEX_OWNER) {
        runningListener?.takeUnless { it.isClosed } ?: run {
            val connection = getDefaultConnection()
            transport.createListener(connection).also { listener ->
                resubscribe(listener)
                coroutineScope.launch { listener.messages.collect { multiplexer.send(it) } }
            }
        }.also { runningListener = it }
    }

    private suspend fun resubscribe(listenSubscription: KueueTransport.Listener) {
        val topics = topicsSubscribersMutex.withLock(MUTEX_OWNER) { topicSubscribers.keys }
        for (topic in topics) {
            listenSubscription.listen(topic)
        }
    }

    private suspend fun getDefaultConnection(): KC {
        require(!closed) { "KueueManager is closed" }

        return defaultConnectionState.openedConnection ?: defaultConnectionMutex.withLock(MUTEX_OWNER) {
            logger.debug { "Default connection was closed, trying to look for a new one" }
            defaultConnectionState.openedConnection ?: connectionProvider.create().also {
                defaultConnectionState.value = it
            }
        }
    }

    private suspend fun unsubscribe(topic: KueueTopic) {
        topicsSubscribersMutex.withLock(MUTEX_OWNER) {
            val subscribers = topicSubscribers.remove(topic) ?: 1
            if (subscribers > 1) {
                topicSubscribers[topic] = subscribers - 1
                return
            }
        }

        getListenSubscription().unlisten(topic)
    }

    companion object : LoggerHolder() {
        private const val MUTEX_OWNER = "Kueue"
    }

    private val StateFlow<KC?>.openedConnection: KC? get() = value?.takeUnless { it.isClosed }

    private inner class KueueSubscriptionImpl(override val topic: KueueTopic, val messagesJob: Job) : KueueSubscription {
        override suspend fun close() = withNonCancellable {
            messagesJob.cancel()
            unsubscribe(topic)
        }
    }
}

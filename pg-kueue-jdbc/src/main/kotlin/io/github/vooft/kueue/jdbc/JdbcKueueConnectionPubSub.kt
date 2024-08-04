package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueConnectionPubSub
import io.github.vooft.kueue.KueueConnectionPubSub.ListenSubscription
import io.github.vooft.kueue.KueueMessage
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.common.LoggerHolder
import io.github.vooft.kueue.common.loggingExceptionHandler
import io.github.vooft.kueue.common.withNonCancellable
import io.github.vooft.kueue.common.withVirtualThreadDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JdbcKueueConnectionPubSub(private val bufferSize: Int = 100, private val notificationDelay: Duration = 10.milliseconds) :
    KueueConnectionPubSub<JdbcKueueConnection> {

    private val coroutineScope = CoroutineScope(SupervisorJob() + loggingExceptionHandler())

    override suspend fun notify(kueueConnection: JdbcKueueConnection, topic: KueueTopic, message: String) {
        logger.debug { "Sending to $topic: $message" }

        try {
            kueueConnection.useUnwrapped { connection ->
                withNonCancellable {
                    val escapedChannel = connection.escapeIdentifier(topic.topic)
                    val escapedMessage = connection.escapeString(message)

                    logger.debug { "Executing query" }
                    connection.execute("NOTIFY $escapedChannel, '$escapedMessage'")
                    logger.debug { "Query executed" }
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to send message to channel $topic: $message" }
            throw e
        }

        logger.debug { "Successfully sent message to $topic: $message" }
    }

    override suspend fun listen(kueueConnection: JdbcKueueConnection): ListenSubscription {
        val channel = Channel<KueueMessage>(capacity = bufferSize)
        val job = coroutineScope.launch {
            while (isActive && !kueueConnection.isClosed) {
                val messages = withNonCancellable { kueueConnection.queryNotifications() }
                messages.forEach { channel.send(it) }
                delay(notificationDelay)
            }
        }

        val mutex = Mutex()
        val listenedTopics = mutableSetOf<KueueTopic>()

        return object : ListenSubscription {

            override val messages = channel

            override val isClosed: Boolean get() = kueueConnection.isClosed

            override suspend fun listen(topic: KueueTopic) {
                if (mutex.withLock { listenedTopics.add(topic) }) {
                    kueueConnection.useUnwrapped { connection ->
                        val escapedChannel = connection.escapeIdentifier(topic.topic)
                        connection.execute("LISTEN $escapedChannel")
                    }
                }
            }

            override suspend fun unlisten(topic: KueueTopic) {
                kueueConnection.useUnwrapped { connection ->
                    val escapedChannel = connection.escapeIdentifier(topic.topic)
                    connection.execute("UNLISTEN $escapedChannel")
                }
            }

            override suspend fun close() {
                withNonCancellable {
                    val topics = mutex.withLock { listenedTopics.toList() }
                    for (topic in topics) {
                        unlisten(topic)
                    }

                    job.cancelAndJoin()
                }
            }
        }
    }

    private suspend fun JdbcKueueConnection.queryNotifications(): List<KueueMessage> = useUnwrapped { connection ->
        withVirtualThreadDispatcher { connection.notifications }
            ?.map { KueueMessage(KueueTopic(it.name), it.parameter) }
            ?: listOf()
    }

    companion object : LoggerHolder()
}

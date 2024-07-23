package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueConnection
import io.github.vooft.kueue.KueueMessage
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.common.LoggerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.postgresql.core.BaseConnection
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

class JdbcKueueConnection(
    private val connection: BaseConnection,
    private val notificationTimeout: Duration = Duration.ofMillis(10),
    bufferSize: Int = 100,
    coroutineScope: CoroutineScope = CoroutineScope(Job())
) : KueueConnection {

    private val subscriptionsMutex = Mutex()
    private val subscriptions = mutableSetOf<String>()

    private val connectionMutex = Mutex()
    private val dispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()

    private val listenJob = coroutineScope.launch(context = dispatcher, start = CoroutineStart.LAZY) {
        while (isActive) {
            val batch = connectionMutex.withLock {
                connection.getNotifications(notificationTimeout.toMillis().toInt())
            } ?: continue

            for (pgNotification in batch) {
                val message = KueueMessage(KueueTopic(pgNotification.name), pgNotification.parameter)
                messages.send(message)
            }
        }
    }
    override val closed: Boolean get() = connection.isClosed

    override val messages = Channel<KueueMessage>(capacity = bufferSize)

    override suspend fun listen(topic: KueueTopic) {
        logger.debug { "Subscribing to $topic" }

        if (!subscriptionsMutex.withLock { subscriptions.add(topic.channel) }) {
            logger.debug { "Already subscribed to $topic" }
            return
        }

        try {
            withDispatcher {
                connectionMutex.withLock {
                    val escapedChannel = connection.escapeIdentifier(topic.channel)
                    connection.createStatement().use {
                        it.execute("LISTEN $escapedChannel")
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to subscribe to $topic" }
            throw e
        }

        logger.debug { "Successfully subscribed to $topic" }

        when (listenJob.start()) {
            true -> logger.debug { "Started listen job" }
            false -> logger.debug { "Listen was already started or completed" }
        }
    }

    override suspend fun notify(topic: KueueTopic, message: String) {
        logger.debug { "Sending to $topic: $message" }

        try {
            withDispatcher {
                connectionMutex.withLock {
                    val escapedChannel = connection.escapeIdentifier(topic.channel)
                    val escapedMessage = connection.escapeString(message)

                    connection.createStatement().use {
                        it.execute("NOTIFY $escapedChannel, '$escapedMessage'")
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to send message to channel $topic: $message" }
            throw e
        }

        logger.debug { "Successfully sent message to $topic: $message" }
    }

    override suspend fun close() {
        logger.debug { "Closing jdbc connection" }

        withContext(coroutineContext + NonCancellable) {
            listenJob.cancelAndJoin()
            logger.debug { "Cancelled listen job" }

            messages.close()
            logger.debug { "Emitted closed message" }

            withDispatcher { connection.close() }
            logger.debug { "Closed PG connection" }

            dispatcher.close()
            logger.debug { "Closed dispatcher" }
        }
    }

    private suspend fun <T> withDispatcher(block: suspend () -> T) = withContext(coroutineContext + dispatcher) { block() }

    companion object : LoggerHolder()
}

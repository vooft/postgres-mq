package io.github.vooft.kueue.jdbc

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vooft.kueue.KueueChannel
import io.github.vooft.kueue.KueueConnection
import io.github.vooft.kueue.KueueMessage
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

    private val connectionMutex = Mutex()
    private val dispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()

    private val listenJob = coroutineScope.launch(context = dispatcher, start = CoroutineStart.LAZY) {
        while (isActive) {
            val batch = connectionMutex.withLock {
                connection.getNotifications(notificationTimeout.toMillis().toInt())
            } ?: continue

            for (pgNotification in batch) {
                val message = KueueMessage(KueueChannel(pgNotification.name), pgNotification.parameter)
                messages.send(message)
            }
        }
    }

    override val messages = Channel<KueueMessage>(capacity = bufferSize)

    override suspend fun subscribe(channel: KueueChannel) {
        logger.debug { "Subscribing to $channel" }

        try {
            withDispatcher {
                connectionMutex.withLock {
                    val escapedChannel = connection.escapeIdentifier(channel.channel)
                    connection.createStatement().use {
                        it.execute("LISTEN $escapedChannel")
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to subscribe to $channel" }
            throw e
        }

        logger.debug { "Successfully subscribed to $channel" }

        when (listenJob.start()) {
            true -> logger.debug { "Started listen job" }
            false -> logger.debug { "Listen was already started or completed" }
        }
    }

    override suspend fun send(channel: KueueChannel, message: String) {
        logger.debug { "Sending to $channel: $message" }

        try {
            withDispatcher {
                connectionMutex.withLock {
                    val escapedChannel = connection.escapeIdentifier(channel.channel)
                    val escapedMessage = connection.escapeString(message)

                    connection.createStatement().use {
                        it.execute("NOTIFY $escapedChannel, '$escapedMessage'")
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to send message to channel $channel: $message" }
            throw e
        }

        logger.debug { "Successfully sent message to $channel: $message" }
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

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

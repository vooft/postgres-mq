package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueConnectionPubSub
import io.github.vooft.kueue.KueueMessage
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.common.LoggerHolder
import io.github.vooft.kueue.common.loggingExceptionHandler
import io.github.vooft.kueue.common.withNonCancellable
import io.github.vooft.kueue.common.withVirtualThreadDispatcher
import io.github.vooft.kueue.use
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import org.postgresql.core.BaseConnection

class JdbcKueueConnectionPubSub(private val bufferSize: Int = 100) : KueueConnectionPubSub<JdbcKueueConnection> {

    private val coroutineScope = CoroutineScope(SupervisorJob() + loggingExceptionHandler())

    override suspend fun notify(kueueConnection: JdbcKueueConnection, topic: KueueTopic, message: String) {
        logger.debug { "Sending to $topic: $message" }

        try {
            kueueConnection.use { connection ->
                withNonCancellable {
                    val escapedChannel = connection.escapeIdentifier(topic.channel)
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

    override suspend fun listen(kueueConnection: JdbcKueueConnection, topic: KueueTopic): KueueConnectionPubSub.ListenSubscription {
        kueueConnection.use { connection ->
            val escapedChannel = connection.escapeIdentifier(topic.channel)
            connection.execute("LISTEN $escapedChannel")
        }

        val channel = Channel<KueueMessage>(capacity = bufferSize)
        val job = coroutineScope.launch {
            while (isActive) {
                val messages = withNonCancellable { kueueConnection.queryNotifications() }
                messages.forEach { channel.send(it) }
            }
        }

        return object : KueueConnectionPubSub.ListenSubscription {
            override val messages = channel
            override suspend fun close() {
                withNonCancellable {
                    kueueConnection.use { connection ->
                        val escapedChannel = connection.escapeIdentifier(topic.channel)
                        connection.execute("UNLISTEN $escapedChannel")
                    }

                    job.cancelAndJoin()
                }
            }
        }
    }

    private suspend fun JdbcKueueConnection.queryNotifications(): List<KueueMessage> = use { connection ->
        withVirtualThreadDispatcher { connection.notifications }
            ?.map { KueueMessage(KueueTopic(it.name), it.parameter) }
            ?: listOf()
    }

    companion object : LoggerHolder()
}

private suspend fun BaseConnection.execute(@Language("SQL") query: String) = withVirtualThreadDispatcher {
    createStatement().use {
        it.execute(query)
    }
}

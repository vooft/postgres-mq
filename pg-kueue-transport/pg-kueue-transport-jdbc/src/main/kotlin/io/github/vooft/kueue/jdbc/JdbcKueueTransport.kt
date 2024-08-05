package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.KueueTransport
import io.github.vooft.kueue.TopicMessage
import io.github.vooft.kueue.common.LoggerHolder
import io.github.vooft.kueue.common.loggingExceptionHandler
import io.github.vooft.kueue.common.withNonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JdbcKueueTransport(private val bufferSize: Int = 100, private val notificationDelay: Duration = 10.milliseconds) :
    KueueTransport<JdbcKueueConnection> {

    private val coroutineScope = CoroutineScope(SupervisorJob() + loggingExceptionHandler())

    override suspend fun notify(kueueConnection: JdbcKueueConnection, topic: KueueTopic, message: String) {
        logger.debug { "Sending to $topic: $message" }

        try {
            kueueConnection.useBaseConnection { connection ->
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

    override suspend fun createListener(kueueConnection: JdbcKueueConnection): KueueTransport.Listener {
        val channel = Channel<TopicMessage>(capacity = bufferSize)
        val job = coroutineScope.launch {
            while (isActive && !kueueConnection.isClosed) {
                val messages = withNonCancellable { kueueConnection.queryNotifications() }
                messages.forEach { channel.send(it) }
                delay(notificationDelay)
            }
        }

        return JdbcKueueListener(channel.consumeAsFlow(), kueueConnection, job)
    }

    companion object : LoggerHolder()
}

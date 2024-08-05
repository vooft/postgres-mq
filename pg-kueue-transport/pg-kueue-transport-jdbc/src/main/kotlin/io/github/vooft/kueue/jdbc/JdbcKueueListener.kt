package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.KueueTransport
import io.github.vooft.kueue.TopicMessage
import io.github.vooft.kueue.common.withNonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class JdbcKueueListener(
    override val messages: Flow<TopicMessage>,
    private val connection: JdbcKueueConnection,
    private val queryNotificationsJob: Job
) : KueueTransport.Listener {

    private val listenedTopicsMutex = Mutex()
    private val listenedTopics = mutableSetOf<KueueTopic>()

    override val isClosed: Boolean get() = connection.isClosed || !queryNotificationsJob.isActive

    override suspend fun listen(topic: KueueTopic) {
        if (listenedTopicsMutex.withLock { listenedTopics.add(topic) }) {
            connection.useBaseConnection { connection ->
                val escapedChannel = connection.escapeIdentifier(topic.topic)
                connection.execute("LISTEN $escapedChannel")
            }
        }
    }

    override suspend fun unlisten(topic: KueueTopic): Unit = connection.useBaseConnection { connection ->
        val escapedChannel = connection.escapeIdentifier(topic.topic)
        connection.execute("UNLISTEN $escapedChannel")
    }

    override suspend fun close() = withNonCancellable {
        val topics = listenedTopicsMutex.withLock { listenedTopics.toList() }
        for (topic in topics) {
            unlisten(topic)
        }

        queryNotificationsJob.cancelAndJoin()
    }
}

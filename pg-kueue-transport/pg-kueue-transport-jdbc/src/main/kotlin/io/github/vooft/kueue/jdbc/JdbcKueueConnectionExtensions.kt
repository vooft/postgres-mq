package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.TopicMessage
import io.github.vooft.kueue.common.withVirtualThreadDispatcher
import io.github.vooft.kueue.useUnwrapped
import org.intellij.lang.annotations.Language
import org.postgresql.core.BaseConnection

internal suspend fun BaseConnection.execute(@Language("SQL") query: String) =
    withVirtualThreadDispatcher { createStatement().use { it.execute(query) } }

internal suspend fun <T> JdbcKueueConnection.useBaseConnection(block: suspend (BaseConnection) -> T): T =
    useUnwrapped { block(connection.unwrap(BaseConnection::class.java)) }

internal suspend fun JdbcKueueConnection.queryNotifications(): List<TopicMessage> = useBaseConnection { connection ->
    withVirtualThreadDispatcher { connection.notifications }
        ?.map { TopicMessage(KueueTopic(it.name), it.parameter) }
        ?: listOf()
}

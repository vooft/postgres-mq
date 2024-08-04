package io.github.vooft.kueue.persistence.jooq

import io.github.vooft.kueue.jdbc.JdbcKueueConnection
import io.github.vooft.kueue.jooq.withConnection
import io.github.vooft.kueue.persistence.KueuePersister
import org.jooq.DSLContext
import java.sql.Connection

suspend fun KueuePersister<Connection, JdbcKueueConnection>.save(topic: String, message: String, transactionalDsl: DSLContext) {
    transactionalDsl.withConnection { saveMessage(topic, message, JdbcKueueConnection(it)) }
}

package io.github.vooft.kueue.persistence

import io.github.vooft.kueue.KueueConnection

interface KueuePersister<C, KC : KueueConnection<C>> {
    suspend fun saveMessage(topic: String, message: String, kueueConnection: KC)
}

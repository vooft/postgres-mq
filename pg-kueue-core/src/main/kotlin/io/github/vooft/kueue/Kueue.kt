package io.github.vooft.kueue

interface KueueProducer {
    val topic: KueueTopic
    suspend fun send(message: String)
}

interface KueueSubscription {
    val channel: KueueTopic
    suspend fun close()
}

@JvmInline
value class KueueTopic(val channel: String)

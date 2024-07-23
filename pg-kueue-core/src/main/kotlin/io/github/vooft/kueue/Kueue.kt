package io.github.vooft.kueue

interface KueueProducer {
    val channel: KueueTopic
    suspend fun send(message: String)
}

interface KueueConsumer {
    val channel: KueueTopic
    suspend fun receive(): String
}

@JvmInline
value class KueueTopic(val channel: String)

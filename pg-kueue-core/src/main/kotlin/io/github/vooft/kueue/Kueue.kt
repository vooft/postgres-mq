package io.github.vooft.kueue

interface KueueProducer {
    val channel: KueueChannel
    suspend fun sent(message: String)
}

interface KueueConsumer {
    val channel: KueueChannel
    suspend fun receive(): String
}

@JvmInline
value class KueueChannel(val channel: String)

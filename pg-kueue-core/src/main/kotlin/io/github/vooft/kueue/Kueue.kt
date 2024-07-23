package io.github.vooft.kueue

interface Kueue {
    suspend fun createProducer(topic: KueueTopic): KueueProducer
    suspend fun createSubscription(topic: KueueTopic, block: suspend (String) -> Unit): KueueSubscription

    suspend fun close()

    interface KueueProducer {
        val topic: KueueTopic
        suspend fun send(message: String)
    }

    interface KueueSubscription {
        val channel: KueueTopic
        suspend fun close()
    }

    companion object
}

@JvmInline
value class KueueTopic(val channel: String)

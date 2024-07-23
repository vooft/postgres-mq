package io.github.vooft.kueue

interface KueueManager {
    suspend fun createProducer(topic: KueueTopic): KueueProducer
    suspend fun createSubscription(topic: KueueTopic, block: suspend (String) -> Unit): KueueSubscription

    suspend fun close()
}

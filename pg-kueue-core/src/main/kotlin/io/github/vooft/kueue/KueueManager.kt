package io.github.vooft.kueue

interface KueueManager {
    suspend fun createProducer(topic: KueueTopic): KueueProducer
    suspend fun createConsumer(topic: KueueTopic): KueueConsumer

    suspend fun close()
}

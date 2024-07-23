package io.github.vooft.kueue

interface KueueManager {
    suspend fun createProducer(channel: KueueChannel): KueueProducer
    suspend fun createConsumer(channel: KueueChannel): KueueConsumer

    suspend fun close()
}

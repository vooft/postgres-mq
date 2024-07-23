package io.github.vooft.kueue

interface KueueManager {
    suspend fun createNotifier(channel: KueueChannel): KueueProducer
    suspend fun createListener(channel: KueueChannel): KueueConsumer

    suspend fun close()
}

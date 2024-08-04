package io.github.vooft.kueue

interface KueuePubSub<C, KC : KueueConnection<C>> {

    suspend fun publish(topic: KueueTopic, message: String, kueueConnection: KC? = null)
    suspend fun subscribe(topic: KueueTopic, block: suspend (String) -> Unit): KueueSubscription

    suspend fun close()

    interface KueueSubscription {
        val topic: KueueTopic
        suspend fun close()
    }

    companion object
}

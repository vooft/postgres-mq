package io.github.vooft.kueue

interface Kueue<C, KC : KueueConnection<C>> {
    suspend fun send(topic: KueueTopic, message: String, kueueConnection: KC? = null)
    suspend fun subscribe(topic: KueueTopic, block: suspend (String) -> Unit): KueueSubscription

    suspend fun close()

    interface KueueSubscription {
        val topic: KueueTopic
        suspend fun close()
    }

    companion object
}

@JvmInline
value class KueueTopic(val channel: String)

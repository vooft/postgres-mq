package io.github.vooft.kueue.impl

import io.github.vooft.kueue.KueueChannel
import io.github.vooft.kueue.KueueConnectionFactory
import io.github.vooft.kueue.KueueConsumer
import io.github.vooft.kueue.KueueManager
import io.github.vooft.kueue.KueueProducer
import kotlinx.coroutines.CoroutineScope

@Suppress("detekt:UnusedPrivateProperty")
class KueueManagerImpl(private val connectionFactory: KueueConnectionFactory, private val coroutineScope: CoroutineScope) : KueueManager {

    override suspend fun createNotifier(channel: KueueChannel): KueueProducer {
        TODO("Not yet implemented")
    }

    override suspend fun createListener(channel: KueueChannel): KueueConsumer {
        TODO("Not yet implemented")
    }

    override suspend fun close() {
        TODO("Not yet implemented")
    }
}

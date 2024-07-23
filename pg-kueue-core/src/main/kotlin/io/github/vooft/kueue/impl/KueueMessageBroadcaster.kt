package io.github.vooft.kueue.impl

import io.github.vooft.kueue.KueueMessage
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.common.LoggerHolder
import io.github.vooft.kueue.common.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

internal class KueueMessageBroadcaster(
    private val channel: StateFlow<ReceiveChannel<KueueMessage>>,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + loggingExceptionHandler())
) {

    private val listeners = ConcurrentHashMap<KueueTopic, List<SendChannel<String>>>()

    private val dispatchJob = coroutineScope.launch {
        while (isActive) {
            dispatch()
        }
    }

    suspend fun receive(topic: KueueTopic): String {
        val channel = Channel<String>()

        listeners.compute(topic) { _, value ->
            val newValue = (value ?: emptyList()) + channel
            newValue
        }

        return channel.receive()
    }

    suspend fun close() {
        dispatchJob.cancelAndJoin()
    }

    private suspend fun dispatch() {
        val message = try {
            channel.value.receive()
        } catch (_: ClosedReceiveChannelException) {
            // channel is closed, which probably means the connection is being replaced
            return
        }


        withContext(coroutineContext + NonCancellable) {
            listeners.remove(message.channel)?.map {
                coroutineScope.launch { it.send(message.message) }
            }?.joinAll()
        }
    }

    companion object : LoggerHolder()
}

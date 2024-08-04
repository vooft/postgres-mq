package io.github.vooft.kueue.impl

import io.github.vooft.kueue.KueueMessage
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.common.LoggerHolder
import io.github.vooft.kueue.common.loggingExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal class KueueMessageBroadcaster(
    private val channel: ReceiveChannel<KueueMessage>,
    coroutineScope: CoroutineScope = CoroutineScope(Job() + loggingExceptionHandler())
) {

    private val listenersMutex = Mutex()
    private val listeners = mutableMapOf<KueueTopic, MutableSharedFlow<String>>()

    private val dispatchJob = coroutineScope.launch {
        while (isActive) {
            dispatch()
        }
    }

    suspend fun subscribe(topic: KueueTopic): Flow<String> = listenersMutex.withLock {
        listeners.computeIfAbsent(topic) { MutableSharedFlow() }
    }.asSharedFlow()

    suspend fun close() {
        dispatchJob.cancelAndJoin()
    }

    private suspend fun dispatch() {
        val message = try {
            channel.receive()
        } catch (_: ClosedReceiveChannelException) {
            // channel is closed, which probably means the connection is being replaced
            return
        }

        withContext(coroutineContext + NonCancellable) {
            listenersMutex.withLock { listeners[message.topic] }?.emit(message.message)
        }
    }

    companion object : LoggerHolder()
}

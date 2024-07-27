package io.github.vooft.kueue.common

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

suspend fun <T> withNonCancellable(block: suspend () -> T) = withContext(coroutineContext + NonCancellable) { block() }

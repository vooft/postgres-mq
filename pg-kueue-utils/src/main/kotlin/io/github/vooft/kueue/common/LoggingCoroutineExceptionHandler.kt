package io.github.vooft.kueue.common

import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class LoggingCoroutineExceptionHandler(private val logger: KLogger) :
    AbstractCoroutineContextElement(CoroutineExceptionHandler),
    CoroutineExceptionHandler {

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        logger.debug(exception) { "Coroutine failed" }
    }
}

fun LoggerHolder.loggingExceptionHandler() = LoggingCoroutineExceptionHandler(getLoggerInternal())

package io.github.vooft.kueue.common

import io.github.oshai.kotlinlogging.KotlinLogging

abstract class LoggerHolder {
    protected val logger by lazy { KotlinLogging.logger(this::class.java.name.removeSuffix("\$Companion")) }

    internal fun getLoggerInternal() = logger
}

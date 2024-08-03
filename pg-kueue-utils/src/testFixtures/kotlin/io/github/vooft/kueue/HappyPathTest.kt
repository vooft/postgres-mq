package io.github.vooft.kueue

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

object HappyPathTest {
    @Suppress("detekt:MemberNameEqualsClassName")
    suspend fun Kueue<*, *>.happyPathTest() {
        val topics = List(10) { KueueTopic(UUID.randomUUID().toString()) }
        try {
            val mutex = Mutex()
            val consumed = mutableMapOf<KueueTopic, MutableList<String>>()
            val subscriptions = topics.map { topic ->
                subscribe(topic) {
                    mutex.withLock { consumed.computeIfAbsent(topic) { mutableListOf() }.add(it) }
                }
            }

            val messagesPerTopic = 100
            val produced = topics.associateWith { MutableList(messagesPerTopic) { UUID.randomUUID().toString() } }

            for (topic in topics) {
                repeat(messagesPerTopic) {
                    val message = produced.getValue(topic)[it]
                    send(topic, message)
                }
            }

            eventually(1.seconds) {
                for ((topic, messages) in produced) {
                    consumed[topic] shouldContainExactly messages
                }

                delay(10)
            }

            subscriptions.forEach { it.close() }
        } finally {
            close()
        }
    }
}

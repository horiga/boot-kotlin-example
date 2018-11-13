/*
 * Copyright (c) 2018 LINE Corporation. All rights reserved.
 * LINE Corporation PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.horiga.study.reactor

import org.junit.jupiter.api.Test
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Flux

class FluxTutorials {

    companion object {
        val log = LoggerFactory.getLogger(FluxTutorials::class.java)!!
    }

    @Test
    fun `Flux - simple subscribe`() {
        val values = Flux.range(1, 3)
        values.subscribe { log.info("value: $it") }
    }

    @Test
    fun `Flux - subscribe with error handle`() {
        val values = Flux.range(1, 4)
        values.map {
            if (it <= 3) it
            else throw IllegalArgumentException("The value must be higher than 3!! value=$it")
        }.subscribe(
            { i: Int -> log.info("value is $i") }, // consumer
            { cause: Throwable -> log.warn("Error, ${cause.message}") }, // error consumer
            { log.info("Done!!") } // completion handler (do not handle completion)
        )
    }

    @Test
    fun `Flux - subscribe with completion handle`() {
        val values = Flux.range(1, 4)
        values.subscribe(
            { i: Int -> log.info("value is $i") }, // consumer
            { cause: Throwable -> log.warn("Error, ${cause.message}") }, // error consumer
            { log.info("Done!!") } // completion handler
        )
    }

    @Test
    fun `Flux - subscribe with subscriptionConsumer`() {
        val values = Flux.range(1, 4)
        val disposable = values.subscribe(
            { i: Int -> log.info("value is $i") }, // consumer
            { cause: Throwable -> log.warn("Error, ${cause.message}") }, // error consumer
            { log.info("Done!!") }, // completion handler
            { it.request(2) } // subscription consumer
        )
    }

    @Test
    fun `Flux - Alternative to lambdas - BaseSubscriber`() {
        val values = Flux.range(1, 4)
        values.subscribe(
            { i: Int -> log.info("value is $i") }, // consumer
            { cause: Throwable -> log.warn("Error, ${cause.message}") }, // error consumer
            { log.info("Done!!") }, // completion handler
            { it.request(10) } // subscription consumer
        )
        values.subscribe(SampleSubscriber<Int>())
    }

    class SampleSubscriber<T> : BaseSubscriber<T>() {

        override fun hookOnSubscribe(subscription: Subscription) {
            log.info("subscribed!")
            request(1)
        }

        override fun hookOnNext(value: T) {
            log.info("hook on next, value=$value")
            request(1)
        }
    }
}
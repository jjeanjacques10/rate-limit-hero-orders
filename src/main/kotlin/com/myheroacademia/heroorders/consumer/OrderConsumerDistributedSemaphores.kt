package com.myheroacademia.heroorders.consumer

import com.myheroacademia.heroorders.model.HeroOrderRequest
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.context.annotation.Profile
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Profile("distributed-semaphores")
@Component
class OrderConsumerDistributedSemaphores {

    @SqsListener(value = ["event-hero-orders-queue"])
    fun consumeMessage(message: Message<HeroOrderRequest>) {
        log.info("Received message from SQS: ${message.payload.heroName}")
        // Sleep to simulate processing time
        Thread.sleep(2000)
        log.info("Finished processing message: ${message.payload.heroName}")
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
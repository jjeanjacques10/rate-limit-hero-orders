package com.myheroacademia.heroorders.consumer

import com.myheroacademia.heroorders.model.HeroOrderRequest
import com.myheroacademia.heroorders.service.OrderService
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.messaging.Message
import org.springframework.stereotype.Component


@Profile("local-control")
@Component
class OrderConsumerLocalControl(
    val orderService: OrderService
) {

    @SqsListener(
        value = ["event-hero-orders-queue"],
        maxConcurrentMessages = "3",
        maxMessagesPerPoll = "3",
        id = "local-control-order-consumer"
    )
    fun consumeMessage(message: Message<HeroOrderRequest>) {
            orderService.processOrder(message.payload)
            log.info("Message acknowledged successfully for hero: ${message.payload.heroName}")
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
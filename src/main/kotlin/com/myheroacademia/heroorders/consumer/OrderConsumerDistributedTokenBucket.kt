package com.myheroacademia.heroorders.consumer

import com.myheroacademia.heroorders.model.HeroOrderRequest
import com.myheroacademia.heroorders.service.OrderService
import com.myheroacademia.heroorders.service.RedisDistributedTokenBucket
import io.awspring.cloud.sqs.annotation.SqsListener
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.messaging.Message
import org.springframework.stereotype.Component


@Profile("distributed-token-bucket")
@Component
class OrderConsumerDistributedTokenBucket(
    val orderService: OrderService,
    val tokenBucket: RedisDistributedTokenBucket
) {

    @SqsListener(
        value = ["event-hero-orders-queue"],
        acknowledgementMode = "MANUAL",
        id = "distributed-token-bucket-order-consumer"
    )
    fun consumeMessage(message: Message<HeroOrderRequest>, acknowledgement: Acknowledgement) {
        try {
            // Try to consume a token from the bucket
            if (!tokenBucket.tryConsume()) {
                // No token available, reject the message to be retried later
                log.warn("No token available for order: ${message.payload.heroName}. Message will NOT be acknowledged (returning to queue).")
                // Do NOT acknowledge - message will return to the queue
                return
            }

            // Process the order if token was consumed
            orderService.processOrder(message.payload)

            // Acknowledge the message only after successful processing
            acknowledgement.acknowledge()
            log.info("Message acknowledged successfully for hero: ${message.payload.heroName}")
        } catch (e: Exception) {
            // If processing fails, do NOT acknowledge - message will return to queue
            log.error("Error processing message for hero: ${message.payload.heroName}. Message will NOT be acknowledged.", e)
            // Message will automatically return to the queue
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
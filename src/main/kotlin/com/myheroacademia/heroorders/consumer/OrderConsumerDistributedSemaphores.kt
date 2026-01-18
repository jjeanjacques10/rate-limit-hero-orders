package com.myheroacademia.heroorders.consumer

import com.myheroacademia.heroorders.model.HeroOrderRequest
import com.myheroacademia.heroorders.service.OrderService
import com.myheroacademia.heroorders.service.RedisDistributedSemaphore
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.context.annotation.Profile
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Profile("distributed-semaphores")
@Component
class OrderConsumerDistributedSemaphores(
    val orderService: OrderService,
    val distributedSemaphore: RedisDistributedSemaphore
) {

    @SqsListener(value = ["event-hero-orders-queue"], acknowledgementMode = "MANUAL", id = "distributed-semaphores-order-consumer")
    fun consumeMessage(message: Message<HeroOrderRequest>, acknowledgement: Acknowledgement) {
        var permitId: String? = null

        try {
            // Try to acquire a permit from the distributed semaphore
            permitId = distributedSemaphore.tryAcquire(message.payload.heroId)

            if (permitId == null) {
                // Could not acquire permit, reject the message to be retried later
                log.warn("Could not acquire semaphore permit for order: ${message.payload.heroName}. Message will NOT be acknowledged (returning to queue).")
                // Do NOT acknowledge - message will return to the queue
                return
            }

            // Process the order with the permit
            orderService.processOrder(message.payload)

            // Acknowledge the message only after successful processing
            acknowledgement.acknowledge()
            log.info("Message acknowledged successfully for hero: ${message.payload.heroName}")
        } catch (e: Exception) {
            // If processing fails, do NOT acknowledge - message will return to queue
            log.error("Error processing message for hero: ${message.payload.heroName}. Message will NOT be acknowledged.", e)
            // Message will automatically return to the queue
        } finally {
            // Always release the permit if it was acquired
            permitId?.let {
                distributedSemaphore.release(it)
            }
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
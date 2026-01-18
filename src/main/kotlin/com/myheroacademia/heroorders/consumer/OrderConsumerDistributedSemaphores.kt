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

    @SqsListener(
        value = ["event-hero-orders-queue"],
        acknowledgementMode = "MANUAL",
        id = "distributed-semaphores-order-consumer"
    )
    fun consumeMessage(message: Message<HeroOrderRequest>, acknowledgement: Acknowledgement) {
        var permitId: String? = null

        try {
            // Tenta adquirir uma permissão do semáforo distribuído
            permitId = distributedSemaphore.tryAcquire(message.payload.heroId)

            if (permitId == null) {
                // Não foi possível adquirir permissão, rejeita a mensagem para ser retentada depois
                log.warn("Could not acquire semaphore permit for order: ${message.payload.heroName}. Message will NOT be acknowledged (returning to queue).")
                // NÃO faz o acknowledge - a mensagem retornará à fila
                return
            }

            // Processa o pedido com a permissão
            orderService.processOrder(message.payload)

            // Faz o acknowledge da mensagem apenas após o processamento bem-sucedido
            acknowledgement.acknowledge()
            log.info("Message acknowledged successfully for hero: ${message.payload.heroName}")
        } catch (e: Exception) {
            // Se o processamento falhar, NÃO faz acknowledge - a mensagem retornará à fila
            log.error("Error processing message for hero: ${message.payload.heroName}. Message will NOT be acknowledged.", e)
            // A mensagem retornará automaticamente à fila
        } finally {
            // Sempre libera a permissão se ela foi adquirida
            permitId?.let {
                distributedSemaphore.release(it)
            }
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
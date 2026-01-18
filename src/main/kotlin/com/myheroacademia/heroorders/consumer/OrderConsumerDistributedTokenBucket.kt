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
            // Tenta consumir um token do bucket
            if (!tokenBucket.tryConsume()) {
                // Nenhum token disponível, rejeita a mensagem para ser retentada depois
                log.warn("No token available for order: ${message.payload.heroName}. Message will NOT be acknowledged (returning to queue).")
                // NÃO faz o acknowledge - a mensagem retornará à fila
                return
            }

            // Processa o pedido se o token foi consumido
            orderService.processOrder(message.payload)

            // Faz o acknowledge a mensagem apenas após o processamento bem-sucedido
            acknowledgement.acknowledge()
            log.info("Message acknowledged successfully for hero: ${message.payload.heroName}")
        } catch (e: Exception) {
            // Se o processamento falhar, NÃO faz acknowledge - a mensagem retornará à fila
            log.error("Error processing message for hero: ${message.payload.heroName}. Message will NOT be acknowledged.", e)
            // A mensagem retornará automaticamente à fila
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
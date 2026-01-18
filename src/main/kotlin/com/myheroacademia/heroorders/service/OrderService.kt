package com.myheroacademia.heroorders.service

import com.myheroacademia.heroorders.model.HeroOrderRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OrderService {
    fun processOrder(order: HeroOrderRequest) {
        log.info("Received message from SQS: ${order.heroName}")
        // Sleep to simulate processing time
        Thread.sleep(2000)
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
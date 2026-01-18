package com.myheroacademia.heroorders.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

@Service
class RedisDistributedSemaphore(
    private val redisTemplate: RedisTemplate<String, Any>
) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        private const val SEMAPHORE_KEY = "hero:orders:semaphore"
        private const val MAX_PERMITS = 10
        private const val PERMIT_TTL_SECONDS = 60L
    }

    /**
     * Tries to acquire a permit from the distributed semaphore
     * @return permit ID if acquired, null otherwise
     */
    fun tryAcquire(permitId: String): String? {
        val currentCount = getCurrentPermits()

        if (currentCount >= MAX_PERMITS) {
            log.warn("Semaphore limit reached: $currentCount/$MAX_PERMITS permits in use")
            return null
        }

        // Add permit to Redis set with TTL
        val added = redisTemplate.opsForZSet().add(
            SEMAPHORE_KEY,
            permitId,
            System.currentTimeMillis().toDouble()
        )

        if (added == true) {
            // Set expiration on the key
            redisTemplate.expire(SEMAPHORE_KEY, Duration.ofSeconds(PERMIT_TTL_SECONDS))

            // Verify we didn't exceed the limit due to race condition
            val finalCount = getCurrentPermits()
            if (finalCount > MAX_PERMITS) {
                // Too many permits, release this one
                release(permitId)
                log.warn("Race condition detected, releasing permit. Count: $finalCount/$MAX_PERMITS")
                return null
            }

            log.info("Permit acquired: $permitId (Current: $finalCount/$MAX_PERMITS)")
            return permitId
        }

        return null
    }

    /**
     * Releases a permit back to the semaphore
     */
    fun release(permitId: String) {
        val removed = redisTemplate.opsForZSet().remove(SEMAPHORE_KEY, permitId)
        if (removed != null && removed > 0) {
            val currentCount = getCurrentPermits()
            log.info("Permit released: $permitId (Current: $currentCount/$MAX_PERMITS)")
        }
    }

    /**
     * Gets the current number of active permits
     */
    fun getCurrentPermits(): Long {
        // Clean up expired permits first
        cleanupExpiredPermits()

        return redisTemplate.opsForZSet().size(SEMAPHORE_KEY) ?: 0
    }

    /**
     * Removes permits that are older than TTL
     */
    private fun cleanupExpiredPermits() {
        val expirationTime = System.currentTimeMillis() - (PERMIT_TTL_SECONDS * 1000)
        redisTemplate.opsForZSet().removeRangeByScore(
            SEMAPHORE_KEY,
            Double.MIN_VALUE,
            expirationTime.toDouble()
        )
    }

}


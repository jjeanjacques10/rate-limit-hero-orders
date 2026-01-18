package com.myheroacademia.heroorders.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RedisDistributedTokenBucket(
    private val redisTemplate: RedisTemplate<String, Any>
) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        private const val TOKEN_BUCKET_KEY = "hero:orders:token_bucket"
        private const val LAST_REFILL_KEY = "hero:orders:token_bucket:last_refill"
        private const val MAX_TOKENS = 10 // Maximum number of tokens in the bucket
        private const val REFILL_RATE = 2 // Tokens added per second
        private const val KEY_TTL_SECONDS = 300L // 5 minutes
    }

    /**
     * Tries to consume a token from the distributed token bucket
     * @param tokensNeeded number of tokens needed (default 1)
     * @return true if token was consumed, false otherwise
     */
    fun tryConsume(tokensNeeded: Int = 1): Boolean {
        // Refill tokens based on time elapsed
        refillTokens()

        // Try to consume the token
        val currentTokens = getCurrentTokens()

        if (currentTokens >= tokensNeeded) {
            // Consume the token(s)
            val newTokenCount = redisTemplate.opsForValue().decrement(TOKEN_BUCKET_KEY, tokensNeeded.toLong())

            if (newTokenCount != null && newTokenCount >= 0) {
                log.info("Token(s) consumed: $tokensNeeded. Remaining tokens: $newTokenCount/$MAX_TOKENS")
                // Reset TTL on the key
                redisTemplate.expire(TOKEN_BUCKET_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
                return true
            } else {
                // Rollback if we went negative
                redisTemplate.opsForValue().increment(TOKEN_BUCKET_KEY, tokensNeeded.toLong())
                log.warn("Token consumption failed due to race condition. Current tokens: ${getCurrentTokens()}/$MAX_TOKENS")
                return false
            }
        }

        log.warn("Not enough tokens available. Current: $currentTokens/$MAX_TOKENS, Needed: $tokensNeeded")
        return false
    }

    /**
     * Refills tokens based on elapsed time since last refill
     */
    private fun refillTokens() {
        val currentTimeMillis = System.currentTimeMillis()

        // Get last refill time
        val lastRefillTime = redisTemplate.opsForValue().get(LAST_REFILL_KEY) as? Long

        if (lastRefillTime == null) {
            // Initialize the bucket
            redisTemplate.opsForValue().set(TOKEN_BUCKET_KEY, MAX_TOKENS)
            redisTemplate.opsForValue().set(LAST_REFILL_KEY, currentTimeMillis)
            redisTemplate.expire(TOKEN_BUCKET_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
            redisTemplate.expire(LAST_REFILL_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
            log.info("Token bucket initialized with $MAX_TOKENS tokens")
            return
        }

        // Calculate tokens to add based on elapsed time
        val elapsedSeconds = (currentTimeMillis - lastRefillTime) / 1000.0
        val tokensToAdd = (elapsedSeconds * REFILL_RATE).toLong()

        if (tokensToAdd > 0) {
            // Add tokens, but don't exceed max
            val currentTokens = getCurrentTokens()
            val newTokenCount = minOf(currentTokens + tokensToAdd, MAX_TOKENS.toLong())

            redisTemplate.opsForValue().set(TOKEN_BUCKET_KEY, newTokenCount)
            redisTemplate.opsForValue().set(LAST_REFILL_KEY, currentTimeMillis)
            redisTemplate.expire(TOKEN_BUCKET_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
            redisTemplate.expire(LAST_REFILL_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))

            log.debug("Tokens refilled: +$tokensToAdd. Current: $newTokenCount/$MAX_TOKENS (elapsed: ${elapsedSeconds}s)")
        }
    }

    /**
     * Gets the current number of available tokens
     */
    fun getCurrentTokens(): Long {
        val tokens = redisTemplate.opsForValue().get(TOKEN_BUCKET_KEY) as? Number
        return tokens?.toLong() ?: MAX_TOKENS.toLong()
    }

    /**
     * Resets the token bucket to maximum capacity
     */
    fun reset() {
        redisTemplate.opsForValue().set(TOKEN_BUCKET_KEY, MAX_TOKENS)
        redisTemplate.opsForValue().set(LAST_REFILL_KEY, System.currentTimeMillis())
        redisTemplate.expire(TOKEN_BUCKET_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
        redisTemplate.expire(LAST_REFILL_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
        log.info("Token bucket reset to $MAX_TOKENS tokens")
    }

}


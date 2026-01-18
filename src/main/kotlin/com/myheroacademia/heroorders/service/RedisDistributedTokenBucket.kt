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
        private const val MAX_TOKENS = 10 // Número máximo de tokens no bucket
        private const val REFILL_RATE = 2 // Tokens adicionados por segundo
        private const val KEY_TTL_SECONDS = 300L // 5 minutos
    }

    /**
     * Tenta consumir um token do bucket de tokens distribuído
     * @param tokensNeeded número de tokens necessários (padrão 1)
     * @return true se o token foi consumido, false caso contrário
     */
    fun tryConsume(tokensNeeded: Int = 1): Boolean {
        // Reabastece tokens com base no tempo decorrido
        refillTokens()

        // Tenta consumir o token
        val currentTokens = getCurrentTokens()

        if (currentTokens >= tokensNeeded) {
            // Consome o(s) token(s)
            val newTokenCount = redisTemplate.opsForValue().decrement(TOKEN_BUCKET_KEY, tokensNeeded.toLong())

            if (newTokenCount != null && newTokenCount >= 0) {
                log.info("Token(s) consumed: $tokensNeeded. Remaining tokens: $newTokenCount/$MAX_TOKENS")
                // Reinicia o TTL da chave
                redisTemplate.expire(TOKEN_BUCKET_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
                return true
            } else {
                // Reverte se ficou negativo
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
            // Inicializa o bucket
            redisTemplate.opsForValue().set(TOKEN_BUCKET_KEY, MAX_TOKENS)
            redisTemplate.opsForValue().set(LAST_REFILL_KEY, currentTimeMillis)
            redisTemplate.expire(TOKEN_BUCKET_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
            redisTemplate.expire(LAST_REFILL_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
            log.info("Token bucket initialized with $MAX_TOKENS tokens")
            return
        }

        // Calcula os tokens a adicionar com base no tempo decorrido
        val elapsedSeconds = (currentTimeMillis - lastRefillTime) / 1000.0
        val tokensToAdd = (elapsedSeconds * REFILL_RATE).toLong()

        if (tokensToAdd > 0) {
            // Adiciona tokens, mas não excede o máximo
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
     * Obtém o número atual de tokens disponíveis
     */
    fun getCurrentTokens(): Long {
        val tokens = redisTemplate.opsForValue().get(TOKEN_BUCKET_KEY) as? Number
        return tokens?.toLong() ?: MAX_TOKENS.toLong()
    }

    /**
     * Reinicia o bucket de tokens para a capacidade máxima
     */
    fun reset() {
        redisTemplate.opsForValue().set(TOKEN_BUCKET_KEY, MAX_TOKENS)
        redisTemplate.opsForValue().set(LAST_REFILL_KEY, System.currentTimeMillis())
        redisTemplate.expire(TOKEN_BUCKET_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
        redisTemplate.expire(LAST_REFILL_KEY, Duration.ofSeconds(KEY_TTL_SECONDS))
        log.info("Token bucket reset to $MAX_TOKENS tokens")
    }

}


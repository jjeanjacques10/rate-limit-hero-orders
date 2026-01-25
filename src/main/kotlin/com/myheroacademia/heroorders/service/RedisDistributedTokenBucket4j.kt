package com.myheroacademia.heroorders.service

import com.myheroacademia.heroorders.config.BucketConfig.Companion.MAX_TOKENS
import io.github.bucket4j.Bucket
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RedisDistributedTokenBucket4j(
    private val bucket: Bucket
) {

    /**
     * Tenta consumir um token do bucket
     * @param tokensNeeded número de tokens necessários (padrão 1)
     * @return true se o(s) token(s) foi(ram) consumido(s), false caso contrário
     */
    fun tryConsume(tokensNeeded: Long = 1): Boolean {
        val consumed = bucket.tryConsume(tokensNeeded)

        if (consumed) {
            val availableTokens = bucket.availableTokens
            log.info("Token(s) consumed: $tokensNeeded. Available tokens: $availableTokens/$MAX_TOKENS")
        } else {
            val availableTokens = bucket.availableTokens
            log.warn("Not enough tokens available. Current: $availableTokens/$MAX_TOKENS, Needed: $tokensNeeded")
        }

        return consumed
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}

package com.myheroacademia.heroorders.config

import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class BucketConfig(
    @Value("\${spring.data.redis.host:localhost}") private val redisHost: String,
    @Value("\${spring.data.redis.port:6379}") private val redisPort: Int,
    @Value("\${spring.data.redis.password}") private val redisPassword: String
) {

    @Bean
    fun bucket4jRedisClient(): RedisClient {
        val redisUri = if (redisPassword.isNotBlank()) {
            "redis://$redisPassword@$redisHost:$redisPort"
        } else {
            "redis://$redisHost:$redisPort"
        }
        return RedisClient.create(redisUri)
    }

    @Bean
    fun bucket4jRedisConnection(redisClient: RedisClient): StatefulRedisConnection<String, ByteArray> {
        return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
    }

    @Bean
    fun bucket4jProxyManager(connection: StatefulRedisConnection<String, ByteArray>): ProxyManager<String> {
        return LettuceBasedProxyManager.builderFor(connection).build()
    }

    @Bean
    fun bucket4jConfigurationConfig(): BucketConfiguration {
        return BucketConfiguration.builder()
            .addLimit { limit ->
                limit.capacity(MAX_TOKENS)
                    .refillGreedy(REFILL_TOKENS, Duration.ofSeconds(REFILL_PERIOD_SECONDS))
            }.build()
    }

    @Bean
    fun bucket(proxyManager: ProxyManager<String>, bucketConfiguration: BucketConfiguration): Bucket {
        val bucket = proxyManager.builder().build(BUCKET_KEY) { bucketConfiguration }
        log.info("Bucket4j initialized with capacity: $MAX_TOKENS tokens, refill rate: $REFILL_TOKENS tokens per $REFILL_PERIOD_SECONDS second(s)")
        return bucket
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        const val BUCKET_KEY = "hero:orders:bucket4j:token_bucket"
        const val MAX_TOKENS = 10L // Capacidade máxima do bucket
        const val REFILL_TOKENS = 2L // Tokens adicionados
        const val REFILL_PERIOD_SECONDS = 1L // Período de reabastecimento (1 segundo)
    }
}
package com.myheroacademia.heroorders.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class RedisDistributedTokenBucketTest {

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, Any>

    private lateinit var tokenBucket: RedisDistributedTokenBucket

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        tokenBucket = RedisDistributedTokenBucket(redisTemplate)
    }

    @Test
    fun `Given no last refill key When tryConsume Then initialize bucket and consume token`() {
        // Given
        whenever(valueOperations.get("hero:orders:token_bucket:last_refill")).thenReturn(null)
        whenever(valueOperations.get("hero:orders:token_bucket")).thenReturn(10)
        whenever(valueOperations.decrement("hero:orders:token_bucket", 1L)).thenReturn(9L)
        whenever(redisTemplate.expire(eq("hero:orders:token_bucket"), any<Duration>())).thenReturn(true)
        whenever(redisTemplate.expire(eq("hero:orders:token_bucket:last_refill"), any<Duration>())).thenReturn(true)

        // When
        val consumed = tokenBucket.tryConsume()

        // Then
        assertTrue(consumed)
        verify(valueOperations).set("hero:orders:token_bucket", 10)
        verify(valueOperations).set(eq("hero:orders:token_bucket:last_refill"), any<Long>())
        verify(valueOperations).decrement("hero:orders:token_bucket", 1L)
        verify(redisTemplate, times(2)).expire(eq("hero:orders:token_bucket"), any<Duration>())
        verify(redisTemplate).expire(eq("hero:orders:token_bucket:last_refill"), any<Duration>())
    }

    @Test
    fun `Given enough tokens When tryConsume Then returns true and refreshes token ttl`() {
        // Given
        val now = System.currentTimeMillis()
        whenever(valueOperations.get("hero:orders:token_bucket:last_refill")).thenReturn(now)
        whenever(valueOperations.get("hero:orders:token_bucket")).thenReturn(5)
        whenever(valueOperations.decrement("hero:orders:token_bucket", 2L)).thenReturn(3L)
        whenever(redisTemplate.expire(eq("hero:orders:token_bucket"), any<Duration>())).thenReturn(true)

        // When
        val consumed = tokenBucket.tryConsume(2)

        // Then
        assertTrue(consumed)
        verify(valueOperations).decrement("hero:orders:token_bucket", 2L)
        verify(redisTemplate).expire(eq("hero:orders:token_bucket"), any<Duration>())
    }

    @Test
    fun `Given not enough tokens When tryConsume Then returns false and does not decrement`() {
        // Given
        val now = System.currentTimeMillis()
        whenever(valueOperations.get("hero:orders:token_bucket:last_refill")).thenReturn(now)
        whenever(valueOperations.get("hero:orders:token_bucket")).thenReturn(1)

        // When
        val consumed = tokenBucket.tryConsume(2)

        // Then
        assertFalse(consumed)
        verify(valueOperations, never()).decrement(any(), any())
    }

    @Test
    fun `Given race condition negative decrement When tryConsume Then rollback and return false`() {
        // Given
        val now = System.currentTimeMillis()
        whenever(valueOperations.get("hero:orders:token_bucket:last_refill")).thenReturn(now)
        whenever(valueOperations.get("hero:orders:token_bucket")).thenReturn(1)
        whenever(valueOperations.decrement("hero:orders:token_bucket", 1L)).thenReturn(-1L)
        whenever(valueOperations.increment("hero:orders:token_bucket", 1L)).thenReturn(0L)

        // When
        val consumed = tokenBucket.tryConsume(1)

        // Then
        assertFalse(consumed)
        verify(valueOperations).increment("hero:orders:token_bucket", 1L)
    }

    @Test
    fun `Given race condition null decrement When tryConsume Then rollback and return false`() {
        // Given
        val now = System.currentTimeMillis()
        whenever(valueOperations.get("hero:orders:token_bucket:last_refill")).thenReturn(now)
        whenever(valueOperations.get("hero:orders:token_bucket")).thenReturn(1)
        whenever(valueOperations.decrement("hero:orders:token_bucket", 1L)).thenReturn(null)
        whenever(valueOperations.increment("hero:orders:token_bucket", 1L)).thenReturn(2L)

        // When
        val consumed = tokenBucket.tryConsume(1)

        // Then
        assertFalse(consumed)
        verify(valueOperations).increment("hero:orders:token_bucket", 1L)
    }

    @Test
    fun `Given elapsed time refills tokens over max When tryConsume Then caps at max tokens before consume`() {
        // Given
        val lastRefill = System.currentTimeMillis() - 6000
        whenever(valueOperations.get("hero:orders:token_bucket:last_refill")).thenReturn(lastRefill)
        whenever(valueOperations.get("hero:orders:token_bucket")).thenReturn(3)
        whenever(valueOperations.decrement("hero:orders:token_bucket", 1L)).thenReturn(9L)
        whenever(redisTemplate.expire(eq("hero:orders:token_bucket"), any<Duration>())).thenReturn(true)
        whenever(redisTemplate.expire(eq("hero:orders:token_bucket:last_refill"), any<Duration>())).thenReturn(true)

        // When
        val consumed = tokenBucket.tryConsume(1)

        // Then
        assertTrue(consumed)
        verify(valueOperations).set("hero:orders:token_bucket", 10L)
        verify(valueOperations).set(eq("hero:orders:token_bucket:last_refill"), any<Long>())
        verify(redisTemplate, times(2)).expire(eq("hero:orders:token_bucket"), any<Duration>())
        verify(redisTemplate).expire(eq("hero:orders:token_bucket:last_refill"), any<Duration>())
    }

    @Test
    fun `Given elapsed time without enough refill When tryConsume Then does not refill`() {
        // Given
        val lastRefill = System.currentTimeMillis() - 200
        whenever(valueOperations.get("hero:orders:token_bucket:last_refill")).thenReturn(lastRefill)
        whenever(valueOperations.get("hero:orders:token_bucket")).thenReturn(4)
        whenever(valueOperations.decrement("hero:orders:token_bucket", 1L)).thenReturn(3L)
        whenever(redisTemplate.expire(eq("hero:orders:token_bucket"), any<Duration>())).thenReturn(true)

        // When
        val consumed = tokenBucket.tryConsume()

        // Then
        assertTrue(consumed)
        verify(valueOperations, never()).set(eq("hero:orders:token_bucket"), eq(10L))
    }

    @Test
    fun `Given numeric token value When getCurrentTokens Then returns value as long`() {
        // Given
        whenever(valueOperations.get("hero:orders:token_bucket")).thenReturn(7)

        // When
        val current = tokenBucket.getCurrentTokens()

        // Then
        assertEquals(7L, current)
    }

    @Test
    fun `Given null token value When getCurrentTokens Then returns max default`() {
        // Given
        whenever(valueOperations.get("hero:orders:token_bucket")).thenReturn(null)

        // When
        val current = tokenBucket.getCurrentTokens()

        // Then
        assertEquals(10L, current)
    }

    @Test
    fun `Given reset requested When reset Then restores max tokens and updates ttl`() {
        // Given
        whenever(redisTemplate.expire(eq("hero:orders:token_bucket"), any<Duration>())).thenReturn(true)
        whenever(redisTemplate.expire(eq("hero:orders:token_bucket:last_refill"), any<Duration>())).thenReturn(true)

        // When
        tokenBucket.reset()

        // Then
        verify(valueOperations).set("hero:orders:token_bucket", 10)
        verify(valueOperations).set(eq("hero:orders:token_bucket:last_refill"), any<Long>())
        verify(redisTemplate).expire(eq("hero:orders:token_bucket"), any<Duration>())
        verify(redisTemplate).expire(eq("hero:orders:token_bucket:last_refill"), any<Duration>())
    }
}
